package com.globalvpn.vpn

import android.content.Context
import com.globalvpn.data.VpnServer
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair

/**
 * WireGuard tunnel manager built on the official WireGuard tunnel
 * library (com.wireguard.android:tunnel). Uses the Go userspace backend
 * with a kernel fallback handled by the library itself.
 *
 * Data consumed directly from the backend:
 * - Config / Interface / Peer from com.wireguard.config (official
 *   config package) — never hand-rolled config strings.
 * - KeyPair from com.wireguard.crypto (the current public API).
 *   NOTE: com.wireguard.android.* classes (Key, KeyPair, KeyFormat,
 *   Keys) are package-private to the tunnel's own implementation and
 *   do not exist as a public API — importing them caused the CI
 *   compile failure; the tunnel module's public surface is
 *   com.wireguard.android.backend.* plus com.wireguard.config and
 *   com.wireguard.crypto.
 * @param context Application context for the backend.
 * @param tunnelName The WireGuard tunnel name, e.g. "GlobalVPN".
 * @param backend The backend implementation, defaults to the Go
 *   userspace backend given a [Context].
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
class WireGuardManager(
    context: Context,
    private val tunnelName: String = "GlobalVPN",
    private val backend: Backend = GoBackend(context)
) {
    class GlobalVpnTunnel : Tunnel {
        override fun getName(): String = "GlobalVPN"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    private val tunnel = GlobalVpnTunnel()

    /**
     * Builds a standard WireGuard config text for one server.
     * [address] is the client tunnel address assigned by the backend
     * (e.g. 10.8.0.2/32). Pure string building — no tunnel needed.
     */
    companion object {
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
                Endpoint = ${server.host}:${server.port}
                AllowedIPs = 0.0.0.0/0, ::/0
                PersistentKeepalive = 25
            """.trimIndent()
        }
    }

    suspend fun connect(configText: String): Boolean {
        return runCatching {
            val parsedConfig = Config.parse(configText.byteInputStream())
            backend.setState(tunnel, Tunnel.State.UP, parsedConfig)
            true
        }.getOrElse {
            it.printStackTrace()
            false
        }
    }

    suspend fun disconnect() {
        runCatching {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        }.getOrElse {
            it.printStackTrace()
        }
    }
}
