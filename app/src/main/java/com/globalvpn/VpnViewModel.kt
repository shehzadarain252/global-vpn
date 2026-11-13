package com.globalvpn

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.globalvpn.ads.AdMobManager
import com.globalvpn.analytics.Analytics
import com.globalvpn.data.AppConfig
import com.globalvpn.data.ConnectionSession
import com.globalvpn.data.CreditTransaction
import com.globalvpn.data.VpnServer
import com.globalvpn.data.Wallet
import com.globalvpn.vpn.PingMeasurer
import com.globalvpn.vpn.ServerSelector
import com.globalvpn.vpn.VpnState
import com.globalvpn.vpn.VpnStateHolder
import com.globalvpn.vpn.WireGuardManager
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant

enum class CreditState { OK, LOW, EMPTY }

/**
 * Central ViewModel for Global VPN.
 *
 * Implements the two user-confirmed flows:
 *
 * CREDIT LOOP (the core free-use loop):
 *  1. VPN use bills 10 credits / 30 min (server-side, via heartbeats).
 *  2. LOW balance (<= lowCreditThreshold) shows a warning banner.
 *  3. ZERO credits: the VPN is safely disconnected and the prominent
 *     "Watch Ad & Earn Credits" option appears.
 *  4. A rewarded ad, once VERIFIED server-side, adds 20 credits.
 *  5. With credits restored the user can connect again.
 *  The "Watch Ad & Earn" button is always visible on Home + Wallet.
 *
 * AD FLOW (user-confirmed 2026-11-11):
 *  - Connect tap -> interstitial ad FIRST -> on dismiss, connect.
 *  - Disconnect tap -> interstitial ad FIRST -> on dismiss, disconnect.
 *  - No ad loaded / ad fails -> the action runs IMMEDIATELY.
 *  - No ads while a connection is being established or torn down.
 *  - Everyone sees ads (there is no ad-free tier).
 */
class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GlobalVpnApp
    private val repository = app.repository
    private val adMobManager: AdMobManager = app.adMobManager
    private val preferences = app.preferences

    // ---- Backend data ----
    private val _config = MutableStateFlow<AppConfig?>(null)
    val config: StateFlow<AppConfig?> = _config.asStateFlow()

    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet.asStateFlow()

    private val _backendReachable = MutableStateFlow(true)
    val backendReachable: StateFlow<Boolean> = _backendReachable.asStateFlow()

    // True while the first backend contact is still being retried
    // (Render free tier cold start). The UI shows "Starting the
    // server…" instead of the unreachable error during this window.
    private val _isWakingBackend = MutableStateFlow(false)
    val isWakingBackend: StateFlow<Boolean> = _isWakingBackend.asStateFlow()

    // ---- Selection / measurement ----
    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _autoSelectFastest = MutableStateFlow(true)
    val autoSelectFastest: StateFlow<Boolean> = _autoSelectFastest.asStateFlow()

    private val _pings = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val pings: StateFlow<Map<String, Int?>> = _pings.asStateFlow()

    private val _measuringPings = MutableStateFlow(false)
    val measuringPings: StateFlow<Boolean> = _measuringPings.asStateFlow()

    // ---- VPN state (mirrored from VpnStateHolder) ----
    val vpnState: StateFlow<VpnState> = VpnStateHolder.state
    val connectionStats = VpnStateHolder.stats
    val currentServerName = VpnStateHolder.currentServerName
    val currentServerCode = VpnStateHolder.currentServerCode
    val creditsExhausted = VpnStateHolder.creditsExhausted

    // ---- Misc UI state ----
    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp: StateFlow<String?> = _publicIp.asStateFlow()

    private val _transactions = MutableStateFlow<List<CreditTransaction>>(emptyList())
    val transactions: StateFlow<List<CreditTransaction>> = _transactions.asStateFlow()

    // Connection history (past sessions, newest first). Loaded from local
    // storage at startup and appended on every disconnect; shown on the
    // Connection History screen (WalletScreens.HistoryScreen).
    private val _history = MutableStateFlow<List<ConnectionSession>>(emptyList())
    val history: StateFlow<List<ConnectionSession>> = _history.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showEarnPrompt = MutableStateFlow(false)
    val showEarnPrompt: StateFlow<Boolean> = _showEarnPrompt.asStateFlow()

    private val _rewardStatus = MutableStateFlow<String?>(null)
    val rewardStatus: StateFlow<String?> = _rewardStatus.asStateFlow()

    private val _vpnPermissionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val vpnPermissionRequest: SharedFlow<Unit> = _vpnPermissionRequest.asSharedFlow()

    private var pendingConnect = false
    private var adShowing = false
    private var heartbeatJob: Job? = null
    private var connectedAtMs: Long = 0L

    init {
        viewModelScope.launch { loadInitial() }
        viewModelScope.launch { _history.value = preferences.getHistory() }
        viewModelScope.launch { refreshPublicIp() }
    }

    fun creditState(): CreditState {
        val wallet = _wallet.value ?: return CreditState.OK
        val cfg = _config.value ?: AppConfig()
        return when {
            wallet.isEmpty(cfg.creditsPer30Min) -> CreditState.EMPTY
            wallet.isLow(cfg.lowCreditThreshold) -> CreditState.LOW
            else -> CreditState.OK
        }
    }

    /**
     * Average measured speed (bytes/sec) for a server's country from
     * past sessions, or null if this server was never used. This is
     * real measured data — never invented. While connected, the live
     * up/down speed comes from the tunnel statistics instead.
     */
    fun lastSpeedForServer(server: VpnServer): Long? {
        val sessions = _history.value.filter {
            it.serverCountryCode == server.countryCode && it.endedAt != null
        }
        if (sessions.isEmpty()) return null
        var totalBytes = 0L
        var totalSeconds = 0L
        for (s in sessions) {
            val start = runCatching { Instant.parse(s.startedAt) }.getOrNull() ?: continue
            val end = runCatching { Instant.parse(s.endedAt!!) }.getOrNull() ?: continue
            val seconds = (end.toEpochMilli() - start.toEpochMilli()) / 1_000L
            if (seconds > 0) {
                totalBytes += s.bytesRx + s.bytesTx
                totalSeconds += seconds
            }
        }
        if (totalSeconds <= 0) return null
        return totalBytes / totalSeconds
    }

    fun clearError() { _errorMessage.value = null }
    fun dismissEarnPrompt() { _showEarnPrompt.value = false }

    // ---------- Initial load ----------

    suspend fun loadInitial() {
        // Render free tier sleeps after inactivity; the first request
        // wakes it and can take ~50s. Retry instead of reporting
        // "Cannot reach the Global VPN server" on a cold start.
        _isWakingBackend.value = true
        var cfg: AppConfig? = null
        for (attempt in 0 until 3) {
            cfg = repository.fetchConfig()
            if (cfg != null) break
            if (attempt < 2) delay(5_000L)
        }
        _isWakingBackend.value = false
        if (cfg == null) {
            _backendReachable.value = false
            _errorMessage.value = "Cannot reach the Global VPN server. Check your connection and try again."
            return
        }
        _backendReachable.value = true
        _config.value = cfg

        if (!preferences.isWelcomeClaimed()) {
            repository.claimWelcome()
        }
        refreshWallet()
        refreshServers()
    }

    suspend fun refreshWallet() {
        repository.fetchWallet()?.let { _wallet.value = it }
        _transactions.value = repository.fetchTransactions()
    }

    suspend fun refreshServers() {
        val list = repository.fetchServers()
        _servers.value = list
        if (list.isEmpty()) return
        // Restore a previously picked server, otherwise auto-select fastest.
        val savedId = preferences.selectedServerId.firstOrNull()
        val saved = list.firstOrNull { it.id == savedId }
        if (saved != null) {
            _autoSelectFastest.value = false
            _selectedServer.value = saved
        }
        refreshPings()
    }

    suspend fun refreshPings() {
        val list = _servers.value
        if (list.isEmpty()) return
        _measuringPings.value = true
        val measured = PingMeasurer.measureAll(list)
        _pings.value = measured
        _measuringPings.value = false
        if (_autoSelectFastest.value) {
            ServerSelector.selectFastest(list, measured)?.let { fastest ->
                _selectedServer.value = fastest
            }
        }
    }

    fun selectServer(server: VpnServer) {
        _autoSelectFastest.value = false
        _selectedServer.value = server
        viewModelScope.launch {
            preferences.setSelectedServerId(server.id)
            Analytics.logServerCountrySelected(server.countryCode)
        }
    }

    fun selectFastestServer() {
        _autoSelectFastest.value = true
        viewModelScope.launch {
            preferences.setSelectedServerId(null)
            refreshPings()
        }
    }

    suspend fun refreshPublicIp() {
        _publicIp.value = repository.getPublicIp()
    }

    // ---------- CONNECT (ad first, then connect) ----------

    /**
     * Connect flow: interstitial ad FIRST, then the VPN connection.
     * If no ad is loaded or it fails, [doConnect] runs immediately.
     * Ignored while an ad is showing or the VPN is changing state.
     */
    fun connectWithAd(activity: Activity) {
        if (adShowing) return
        val state = VpnStateHolder.state.value
        if (state == VpnState.CONNECTING || state == VpnState.CONNECTED ||
            state == VpnState.DISCONNECTING
        ) return
        val server = _selectedServer.value
        if (server == null) {
            _errorMessage.value =
                if (_servers.value.isEmpty()) "No servers configured yet. Servers are added by the owner in the admin panel."
                else "Please choose a server first."
            return
        }
        if (creditState() == CreditState.EMPTY) {
            _showEarnPrompt.value = true
            return
        }
        adShowing = true
        adMobManager.showInterstitial(activity) {
            adShowing = false
            doConnect(server)
        }
    }

    private fun doConnect(server: VpnServer) {
        viewModelScope.launch {
            val prepareIntent = VpnService.prepare(getApplication())
            if (prepareIntent != null) {
                pendingConnect = true
                _vpnPermissionRequest.emit(Unit)
            } else {
                proceedConnect(server)
            }
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val server = _selectedServer.value
        if (granted && pendingConnect && server != null) {
            pendingConnect = false
            proceedConnect(server)
        } else {
            pendingConnect = false
            if (!granted) {
                _errorMessage.value = "VPN permission is required to connect."
            }
        }
    }

    private fun proceedConnect(server: VpnServer) {
        viewModelScope.launch {
            VpnStateHolder.setState(VpnState.CONNECTING)
            VpnStateHolder.setError(null)
            VpnStateHolder.setCreditsExhausted(false)
            val keyPair = KeyPair()
            val session = repository.startSession(server, keyPair.publicKey.toBase64())
            if (session == null) {
                VpnStateHolder.setState(VpnState.ERROR)
                _errorMessage.value = "Could not start a session with the server. Please try again."
                return@launch
            }
            _wallet.value = session.wallet
            val configText = WireGuardManager.buildConfig(
                server = server,
                clientPrivateKey = keyPair.privateKey.toBase64(),
                clientAddress = session.clientAddress,
                dnsServers = session.dns
            )
            preferences.saveLastConfig(configText, server.country, server.countryCode)
            connectedAtMs = System.currentTimeMillis()
            val intent = Intent(getApplication(), com.globalvpn.vpn.GlobalVpnService::class.java).apply {
                action = com.globalvpn.vpn.GlobalVpnService.ACTION_CONNECT
                putExtra(com.globalvpn.vpn.GlobalVpnService.EXTRA_CONFIG, configText)
                putExtra(com.globalvpn.vpn.GlobalVpnService.EXTRA_SERVER_NAME, server.country)
                putExtra(com.globalvpn.vpn.GlobalVpnService.EXTRA_SERVER_CODE, server.countryCode)
                putExtra(com.globalvpn.vpn.GlobalVpnService.EXTRA_SESSION_ID, session.sessionId)
            }
            getApplication<Application>().startForegroundService(intent)
            Analytics.logVpnConnect(server.countryCode)
            startHeartbeat(session.sessionId)
            // Refresh the displayed IP after the tunnel comes up (exit IP).
            launch {
                delay(3_000L)
                refreshPublicIp()
            }
        }
    }

    private fun startHeartbeat(sessionId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val response = repository.heartbeat(sessionId) ?: continue
                _wallet.value = _wallet.value?.copy(balance = response.balance)
                if (response.creditsExhausted) {
                    // Credits ran out mid-session: safely disconnect and
                    // show the earn-credits option.
                    VpnStateHolder.setCreditsExhausted(true)
                    _showEarnPrompt.value = true
                    doDisconnectInternal()
                    break
                }
            }
        }
    }

    // ---------- DISCONNECT (ad first, then disconnect) ----------

    /**
     * Disconnect flow: interstitial ad FIRST, then the VPN stops.
     * If no ad is loaded or it fails, the disconnect runs immediately.
     */
    fun disconnectWithAd(activity: Activity) {
        if (adShowing) return
        val state = VpnStateHolder.state.value
        if (state != VpnState.CONNECTED && state != VpnState.CONNECTING) return
        adShowing = true
        adMobManager.showInterstitial(activity) {
            adShowing = false
            doDisconnectInternal()
        }
    }

    private fun doDisconnectInternal() {
        viewModelScope.launch {
            heartbeatJob?.cancel()
            heartbeatJob = null
            val sessionId = VpnStateHolder.sessionId.value
            val stats = connectionStats.value
            val intent = Intent(getApplication(), com.globalvpn.vpn.GlobalVpnService::class.java).apply {
                action = com.globalvpn.vpn.GlobalVpnService.ACTION_DISCONNECT
            }
            getApplication<Application>().startService(intent)
            if (sessionId != null) {
                repository.endSession(sessionId, stats.rxBytes, stats.txBytes)?.let {
                    _wallet.value = it
                }
                val serverName = currentServerName.value ?: ""
                val serverCode = currentServerCode.value ?: ""
                val entry = ConnectionSession(
                    id = sessionId,
                    serverCountry = serverName,
                    serverCountryCode = serverCode,
                    startedAt = Instant.ofEpochMilli(connectedAtMs).toString(),
                    endedAt = Instant.now().toString(),
                    bytesRx = stats.rxBytes,
                    bytesTx = stats.txBytes
                )
                preferences.addHistoryEntry(entry)
                _history.value = preferences.getHistory()
            }
            val durationSec = (System.currentTimeMillis() - connectedAtMs) / 1_000L
            Analytics.logVpnDisconnect(durationSec)
            refreshWallet()
            refreshPublicIp()
        }
    }

    // ---------- WATCH AD & EARN (credit loop step 4-5) ----------

    /**
     * Rewarded-ad flow: watch an ad, the reward is verified server-side,
     * then 20 credits (configurable) are added and the user can connect
     * again. Never leaves the user stuck at zero credits.
     */
    fun watchAdAndEarn(activity: Activity) {
        if (adShowing) return
        adShowing = true
        _rewardStatus.value = null
        adMobManager.showRewarded(
            activity = activity,
            onRewardEarned = {
                viewModelScope.launch {
                    val result = repository.claimReward()
                    when (result?.status) {
                        "CREDITED" -> {
                            result.wallet?.let { _wallet.value = it }
                            _rewardStatus.value = "20 credits added to your wallet."
                            VpnStateHolder.setCreditsExhausted(false)
                            _showEarnPrompt.value = false
                            val cfg = _config.value
                            Analytics.logRewardedAdEarned(cfg?.rewardCredits ?: 20)
                            Analytics.logCreditsEarned(cfg?.rewardCredits ?: 20, "rewarded_ad")
                            refreshWallet()
                        }
                        "PENDING" -> {
                            _rewardStatus.value = "Verifying your reward… credits will appear shortly."
                            // Poll the wallet — the server credits it when
                            // the AdMob server-side verification arrives.
                            launch {
                                delay(5_000L)
                                refreshWallet()
                                delay(10_000L)
                                refreshWallet()
                            }
                        }
                        "DUPLICATE" -> {
                            _rewardStatus.value = "This reward was already credited."
                        }
                        else -> {
                            _rewardStatus.value = "Reward could not be verified. Please try again."
                            Analytics.recordNonFatal(
                                IllegalStateException("Reward claim failed: ${result?.status}")
                            )
                        }
                    }
                }
            },
            onDismissed = {
                adShowing = false
                adMobManager.loadRewarded()
            }
        )
    }

    fun preloadAds() {
        adMobManager.preloadAds()
    }

    /** Deletes the guest account server-side and wipes local data. */
    suspend fun deleteAccount(): Boolean = repository.deleteAccount()

    override fun onCleared() {
        heartbeatJob?.cancel()
        super.onCleared()
    }
}
