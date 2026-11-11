package com.globalvpn.data

import android.content.Context
import android.util.Log
import com.globalvpn.BuildConfig
import com.globalvpn.analytics.Analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

/**
 * Repository for all backend calls. The backend is the source of truth
 * for wallets, sessions and rewards.
 *
 * THE CREDIT LOOP (the core free-use loop of the app):
 *  1. User connects -> backend starts a session and bills 10 credits
 *     per 30 minutes of use (server-side, atomic, via heartbeats).
 *  2. While connected the app sends a heartbeat every 60 seconds; the
 *     backend returns the remaining balance and a creditsExhausted flag.
 *  3. When the balance is LOW (<= lowCreditThreshold, default 20) the
 *     app shows a warning before credits run out.
 *  4. When credits reach ZERO the backend flags creditsExhausted and
 *     the app safely disconnects the VPN, then shows the prominent
 *     "Watch Ad & Earn Credits" option.
 *  5. Watching a rewarded ad sends a claim to the backend; credits are
 *     added ONLY after server-side verification (AdMob SSV callback, or
 *     duplicate-checked claim when SSV is disabled by the admin).
 *  6. With credits restored the user can connect again.
 * The loop never leaves the user stuck: the "Watch Ad & Earn" button is
 * always visible on the Home and Wallet screens.
 */
class VpnRepository(
    context: Context,
    private val preferences: Preferences
) {
    private val api: GlobalVpnApi = ApiClient.create(BuildConfig.BACKEND_URL)
    private val httpClient = OkHttpClient()

    suspend fun guestId(): String = preferences.guestId()

    suspend fun fetchConfig(): AppConfig? = safeCall { api.getConfig() }

    suspend fun fetchServers(): List<VpnServer> =
        safeCall { api.getServers() } ?: emptyList()

    suspend fun fetchWallet(): Wallet? {
        val guestId = guestId()
        return safeCall { api.getWallet(guestId) }
    }

    /** Credit transaction history (server-side truth). */
    suspend fun fetchTransactions(): List<CreditTransaction> =
        safeCall { api.getTransactions(guestId()) } ?: emptyList()

    /** Deletes the guest account server-side and wipes local data. */
    suspend fun deleteAccount(): Boolean {
        val ok = safeCall { api.deleteAccount(guestId()) } != null
        if (ok) preferences.clearAll()
        return ok
    }

    /** Claims the one-time welcome credits (default 100). Server enforces once-per-guest. */
    suspend fun claimWelcome(): Wallet? {
        val guestId = guestId()
        val wallet = safeCall { api.claimWelcome(mapOf("guestId" to guestId)) }
        if (wallet != null) {
            preferences.setWelcomeClaimed()
            Analytics.logCreditsEarned(wallet.balance, "welcome")
        }
        return wallet
    }

    suspend fun startSession(server: VpnServer, clientPublicKey: String): SessionStartResponse? {
        val guestId = guestId()
        return safeCall {
            api.startSession(
                mapOf(
                    "guestId" to guestId,
                    "serverId" to server.id,
                    "clientPublicKey" to clientPublicKey
                )
            )
        }
    }

    suspend fun heartbeat(sessionId: String): HeartbeatResponse? {
        val guestId = guestId()
        return safeCall {
            api.heartbeat(mapOf("sessionId" to sessionId, "guestId" to guestId))
        }
    }

    suspend fun endSession(sessionId: String, bytesRx: Long, bytesTx: Long): Wallet? {
        val guestId = guestId()
        return safeCall {
            api.endSession(
                mapOf(
                    "sessionId" to sessionId,
                    "guestId" to guestId,
                    "bytesRx" to bytesRx,
                    "bytesTx" to bytesTx
                )
            )
        }
    }

    /**
     * Claims a rewarded-ad reward. The claimId (UUID) makes the claim
     * idempotent — a repeated claim with the same ID is reported as
     * DUPLICATE and never credited twice.
     */
    suspend fun claimReward(): RewardClaimResponse? {
        val guestId = guestId()
        val claimId = UUID.randomUUID().toString()
        return safeCall {
            api.claimReward(
                mapOf(
                    "guestId" to guestId,
                    "claimId" to claimId,
                    "adUnit" to "rewarded"
                )
            )
        }
    }

    /** Fire-and-forget error report for the admin error reports (no PII). */
    suspend fun reportError(message: String, source: String) {
        val guestId = guestId()
        safeCall {
            api.reportError(
                mapOf("guestId" to guestId, "message" to message, "source" to source)
            )
        }
    }

    /** Fetches the current public IP (real value from ipify, or null on failure). */
    suspend fun getPublicIp(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                JSONObject(body).optString("ip", "").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w("VpnRepository", "getPublicIp failed: ${e.message}")
            null
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            Log.w("VpnRepository", "API call failed: ${e.message}")
            Analytics.recordNonFatal(e)
            null
        }
    }
}
