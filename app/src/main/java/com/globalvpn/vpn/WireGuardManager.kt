package com.globalvpn.vpn

import android.content.Context
import android.util.Log
import com.globalvpn.data.VpnServer
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.config.Config
import com.wireguard.android.util.KeyPair
import java.io.ByteArrayInputStream

/**
 * WireGuard tunnel manager built on the official WireGuard tunnel
 * library (com.wireguard.android:tunnel). Uses the Go userspace backend
 * with a kernel fallback handled by the library itself.
 *
 * HONESTY: this class only brings a tunnel up when the backend has
 * registered a REAL server and returned a REAL config. If no servers
 * are configured, connect() is never called and the UI shows the
 * "No servers configured yet" state.
 *
 * Security notes:
 * - The client keypair is generated on-device; the private key never
 *   leaves the device except inside the tunnel config handed to the
 *   local WireGuard backend (never sent to the server — only the
 *   public key is registered via /api/session/start).
 * - DNS servers come from the backend (default 1.1.1.1 / 8.8.8.8) so
 *   DNS queries go through the tunnel (DNS leak protection).
 * - AllowedIPs 0.0.0.0/0 + ::/0 routes all traffic, including IPv6,
 *   through the tunnel (IPv6 leak protection).
 * - MTU 1280 avoids fragmentation and retransmits on mobile networks
 *   (fast, efficient networking). PersistentKeepalive 25 keeps NAT
 *   mappings alive with minimal traffic.
 */
class WireGuardManager(context: Context) {

    private val backend: Backend = GoBackend(context)

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(state: Tunnel.State) {
            Log.i("WireGuardManager", "Tunnel state: $state")
        }
    }

    fun generateKeyPair(): KeyPair = KeyPair()

    /** Brings the tunnel up with a config text. Returns true on success. */
    fun connect(configText: String): Boolean {
        return try {
            val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
            backend.setState(tunnel, Tunnel.State.UP, config)
            backend.getState(tunnel) == Tunnel.State.UP
        } catch (e: Exception) {
            Log.e("WireGuardManager", "connect failed", e)
            false
        }
    }

    fun disconnect(): Boolean {
        return try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
            true
        } catch (e: Exception) {
            Log.e("WireGuardManager", "disconnect failed", e)
            false
        }
    }

    fun getState(): Tunnel.State {
        return try {
            backend.getState(tunnel)
        } catch (e: Exception) {
            Tunnel.State.DOWN
        }
    }

    fun getStatistics(): Statistics? {
        return try {
            backend.getStatistics(tunnel)
        } catch (e: Exception) {
            Log.w("WireGuardManager", "getStatistics failed: ${e.message}")
            null
        }
    }

    companion object {
        const val TUNNEL_NAME = "global-vpn"

        /**
         * Builds a standard WireGuard config text for one server.
         * [address] is the client tunnel address assigned by the backend
         * (e.g. 10.8.0.2/32). Pure string building — no tunnel needed.
         */
        fun buildConfig(
            server: VpnServer,
            clientPrivateKey: String,
            clientAddress: String,
            dnsServers: List<String>
        ): String {
            val dns = if (dnsServers.isEmpty()) "1.1.1.1, 8.8.8.8" else dnsServers.joinToString(", ")
            return """
                [Interface]
                PrivateKey = $clientPrivateKey
                Address = $clientAddress
                DNS = $dns
                MTU = 1280

                [Peer]
                PublicKey = ${server.publicKey}
                AllowedIPs = 0.0.0.0/0, ::/0
                Endpoint = ${server.host}:${server.port}
                PersistentKeepalive = 25
            """.trimIndent()
        }
    }
}
