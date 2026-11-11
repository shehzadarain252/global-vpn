package com.globalvpn.vpn

import android.util.Log
import com.globalvpn.data.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Measures REAL network latency to VPN servers.
 *
 * Method: TCP connect timing to the server's host:port. WireGuard
 * itself runs over UDP, so a TCP connect to the WireGuard port is
 * refused by the host — but the time until that refusal arrives IS
 * the round-trip latency to the server, which is exactly what
 * "fastest server" means. A timeout or DNS failure means the server
 * did not answer and returns null (shown as "—" in the UI).
 *
 * HONESTY: only measured values are shown. No latency or speed is
 * ever invented. Two attempts are made and the faster one is kept.
 */
object PingMeasurer {

    suspend fun measurePing(host: String, port: Int, timeoutMs: Int = 3_000): Int? =
        withContext(Dispatchers.IO) {
            var best: Long? = null
            repeat(2) {
                val attempt = measureOnce(host, port, timeoutMs)
                if (attempt != null && (best == null || attempt < best!!)) best = attempt
            }
            best?.toInt()
        }

    private fun measureOnce(host: String, port: Int, timeoutMs: Int): Long? {
        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            // A full TCP connect succeeded (rare for a UDP port).
            (System.nanoTime() - start) / 1_000_000
        } catch (e: ConnectException) {
            // Host answered with RST ("connection refused") — the host is
            // reachable and the elapsed time is the real round-trip time.
            (System.nanoTime() - start) / 1_000_000
        } catch (e: SocketTimeoutException) {
            null // No answer within the timeout.
        } catch (e: UnknownHostException) {
            null // DNS failure.
        } catch (e: Exception) {
            Log.w("PingMeasurer", "ping $host:$port failed: ${e.message}")
            null
        }
    }

    /** Measures ping for all servers concurrently. Returns serverId -> pingMs (null = no answer). */
    suspend fun measureAll(servers: List<VpnServer>): Map<String, Int?> = coroutineScope {
        servers.map { server ->
            async { server.id to measurePing(server.host, server.port) }
        }.map { it.await() }.toMap()
    }
}

/**
 * Fastest-server selection.
 *
 * The fastest server is the online server with the lowest REAL measured
 * ping. Lowest latency naturally prefers geographically closer servers
 * (fewer hops, shorter paths). Server load (reported by the backend)
 * breaks ties. A server with no ping answer is only chosen when no
 * server answered at all.
 */
object ServerSelector {
    fun selectFastest(
        servers: List<VpnServer>,
        pings: Map<String, Int?>
    ): VpnServer? {
        val online = servers.filter { it.status == "online" }
        if (online.isEmpty()) return null
        return online.sortedWith(
            compareBy<VpnServer> { pings[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.load }
        ).firstOrNull()
    }
}
