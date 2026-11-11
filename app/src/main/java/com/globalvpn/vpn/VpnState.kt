package com.globalvpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR
}

data class ConnectionStats(
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxSpeedBps: Long = 0,
    val txSpeedBps: Long = 0,
    val durationSec: Long = 0
)

/**
 * Single source of truth for VPN state, shared between the VpnService
 * (which owns the tunnel) and the UI (ViewModel + Compose screens).
 * The service writes; the UI reads.
 */
object VpnStateHolder {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(ConnectionStats())
    val stats: StateFlow<ConnectionStats> = _stats.asStateFlow()

    private val _currentServerName = MutableStateFlow<String?>(null)
    val currentServerName: StateFlow<String?> = _currentServerName.asStateFlow()

    private val _currentServerCode = MutableStateFlow<String?>(null)
    val currentServerCode: StateFlow<String?> = _currentServerCode.asStateFlow()

    /** True when the last disconnect happened because credits ran out. */
    private val _creditsExhausted = MutableStateFlow(false)
    val creditsExhausted: StateFlow<Boolean> = _creditsExhausted.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    fun setState(state: VpnState) { _state.value = state }
    fun setStats(stats: ConnectionStats) { _stats.value = stats }
    fun setCurrentServer(name: String?, code: String?) {
        _currentServerName.value = name
        _currentServerCode.value = code
    }
    fun setCreditsExhausted(exhausted: Boolean) { _creditsExhausted.value = exhausted }
    fun setError(message: String?) { _errorMessage.value = message }
    fun setSessionId(id: String?) { _sessionId.value = id }

    fun resetStats() { _stats.value = ConnectionStats() }
}
