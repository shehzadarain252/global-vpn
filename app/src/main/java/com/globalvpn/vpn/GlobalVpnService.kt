package com.globalvpn.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.globalvpn.GlobalVpnApp
import com.globalvpn.MainActivity
import com.globalvpn.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The VpnService that owns the WireGuard tunnel.
 *
 * Lifecycle:
 * - ACTION_CONNECT (with EXTRA_CONFIG): brings the tunnel up, runs in
 *   the foreground with a persistent notification, and polls tunnel
 *   statistics once a second for live speed + duration.
 * - ACTION_DISCONNECT: brings the tunnel down and stops.
 *
 * Auto-reconnect: if the tunnel drops unexpectedly while it should be
 * up, the service retries up to 3 times (5s apart) before reporting
 * an ERROR state. The UI never pretends the VPN is up when it is not.
 *
 * Kill switch: the tunnel routes ALL traffic (0.0.0.0/0 + ::/0) with
 * no bypass. If the tunnel drops, traffic is not silently sent outside
 * it — the service either reconnects or reports ERROR. Android's
 * system-level "Block connections without VPN" (Always-on VPN) setting
 * is the OS kill switch; Settings links the user to the system VPN
 * settings page to enable it.
 */
class GlobalVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.globalvpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.globalvpn.ACTION_DISCONNECT"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_SERVER_CODE = "extra_server_code"
        const val EXTRA_SESSION_ID = "extra_session_id"
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 5_000L
    }

    private var wireGuardManager: WireGuardManager? = null
    private var statsJob: Job? = null
    private var currentConfig: String? = null
    private var shouldBeConnected = false
    private var reconnectAttempts = 0
    private var connectedAtMs: Long = 0L
    private var lastRx: Long = 0L
    private var lastTx: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME)
                val serverCode = intent.getStringExtra(EXTRA_SERVER_CODE)
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (config.isNullOrBlank()) {
                    VpnStateHolder.setError("Missing VPN configuration.")
                    VpnStateHolder.setState(VpnState.ERROR)
                    stopSelf()
                } else {
                    connect(config, serverName, serverCode, sessionId)
                }
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun connect(
        config: String,
        serverName: String?,
        serverCode: String?,
        sessionId: String?
    ) {
        VpnStateHolder.setState(VpnState.CONNECTING)
        VpnStateHolder.setError(null)
        currentConfig = config
        shouldBeConnected = true
        reconnectAttempts = 0
        VpnStateHolder.setCurrentServer(serverName, serverCode)
        VpnStateHolder.setSessionId(sessionId)

        startForeground(GlobalVpnApp.NOTIFICATION_ID, buildNotification(serverName))

        val manager = WireGuardManager(this)
        wireGuardManager = manager
        val ok = manager.connect(config)
        if (!ok) {
            VpnStateHolder.setError("Could not start the VPN tunnel. Please try again.")
            VpnStateHolder.setState(VpnState.ERROR)
            shouldBeConnected = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        connectedAtMs = System.currentTimeMillis()
        lastRx = 0L
        lastTx = 0L
        VpnStateHolder.resetStats()
        VpnStateHolder.setState(VpnState.CONNECTED)
        startStatsPolling()
    }

    private fun disconnect() {
        VpnStateHolder.setState(VpnState.DISCONNECTING)
        shouldBeConnected = false
        statsJob?.cancel()
        statsJob = null
        try {
            wireGuardManager?.disconnect()
        } catch (e: Exception) {
            Log.w("GlobalVpnService", "disconnect error: ${e.message}")
        }
        wireGuardManager = null
        currentConfig = null
        VpnStateHolder.setSessionId(null)
        VpnStateHolder.setState(VpnState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && shouldBeConnected) {
                delay(1_000L)
                val manager = wireGuardManager ?: break
                // Detect an unexpected tunnel drop.
                if (manager.getState() != com.wireguard.android.backend.Tunnel.State.UP) {
                    handleUnexpectedDrop()
                    break
                }
                val stats = manager.getStatistics()
                if (stats != null) {
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    val rxSpeed = (rx - lastRx).coerceAtLeast(0L)
                    val txSpeed = (tx - lastTx).coerceAtLeast(0L)
                    lastRx = rx
                    lastTx = tx
                    val durationSec = (System.currentTimeMillis() - connectedAtMs) / 1_000L
                    VpnStateHolder.setStats(
                        ConnectionStats(
                            rxBytes = rx,
                            txBytes = tx,
                            rxSpeedBps = rxSpeed,
                            txSpeedBps = txSpeed,
                            durationSec = durationSec
                        )
                    )
                }
            }
        }
    }

    private fun handleUnexpectedDrop() {
        if (!shouldBeConnected) return
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            Log.i("GlobalVpnService", "Tunnel dropped, reconnect attempt $reconnectAttempts")
            CoroutineScope(Dispatchers.IO).launch {
                delay(RECONNECT_DELAY_MS)
                val config = currentConfig
                val manager = wireGuardManager
                if (shouldBeConnected && config != null && manager != null) {
                    val ok = manager.connect(config)
                    if (ok) {
                        startStatsPolling()
                    } else {
                        handleUnexpectedDrop()
                    }
                }
            }
        } else {
            VpnStateHolder.setError(
                "The VPN connection dropped and could not be restored. Please try again."
            )
            VpnStateHolder.setState(VpnState.ERROR)
            shouldBeConnected = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(serverName: String?): Notification {
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val disconnectIntent = Intent(this, GlobalVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }.let {
            PendingIntent.getService(
                this, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, GlobalVpnApp.CHANNEL_ID)
            .setContentTitle("Global VPN connected")
            .setContentText(serverName?.let { "Connected to $it" } ?: "VPN is active")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_shield, "Disconnect", disconnectIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        shouldBeConnected = false
        statsJob?.cancel()
        try {
            wireGuardManager?.disconnect()
        } catch (e: Exception) {
            Log.w("GlobalVpnService", "onDestroy disconnect error: ${e.message}")
        }
        super.onDestroy()
    }
}
