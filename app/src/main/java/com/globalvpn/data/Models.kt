package com.globalvpn.data

import com.google.gson.annotations.SerializedName

// ---------- Servers ----------

data class VpnServer(
    @SerializedName("id") val id: String,
    @SerializedName("country") val country: String,
    @SerializedName("countryCode") val countryCode: String,
    @SerializedName("city") val city: String,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("load") val load: Int = 0,
    @SerializedName("pingMs") val pingMs: Int? = null,
    @SerializedName("status") val status: String = "online"
)

// ---------- Wallet / credits ----------

/**
 * Credit wallet. The backend is the source of truth; the app only caches
 * what the backend returns. Balances are never trusted from the client.
 *
 * Credit rules (configurable in the admin panel):
 * - welcomeCredits: granted once per guest account (default 100)
 * - rewardCredits: granted per verified rewarded ad (default 20)
 * - creditsPer30Min: deducted per 30 minutes of VPN use (default 10)
 */
data class Wallet(
    @SerializedName("guestId") val guestId: String,
    @SerializedName("balance") val balance: Int,
    @SerializedName("totalEarned") val totalEarned: Int,
    @SerializedName("totalUsed") val totalUsed: Int,
    @SerializedName("welcomeClaimed") val welcomeClaimed: Boolean = false
) {
    /** True when the balance cannot pay for even one 30-minute chunk. */
    fun isEmpty(creditsPer30Min: Int): Boolean = balance < creditsPer30Min

    /** True when the balance is low enough to warn the user. */
    fun isLow(lowThreshold: Int): Boolean = balance <= lowThreshold
}

data class CreditTransaction(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String, // WELCOME, REWARD, USAGE, PURCHASE, ADMIN
    @SerializedName("amount") val amount: Int, // +earned / -used
    @SerializedName("reason") val reason: String,
    @SerializedName("createdAt") val createdAt: String
)

// ---------- Sessions ----------

data class ConnectionSession(
    @SerializedName("id") val id: String,
    @SerializedName("serverCountry") val serverCountry: String,
    @SerializedName("serverCountryCode") val serverCountryCode: String,
    @SerializedName("startedAt") val startedAt: String,
    @SerializedName("endedAt") val endedAt: String? = null,
    @SerializedName("bytesRx") val bytesRx: Long = 0,
    @SerializedName("bytesTx") val bytesTx: Long = 0
)

data class SessionStartResponse(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("clientAddress") val clientAddress: String,
    @SerializedName("dns") val dns: List<String>,
    @SerializedName("wallet") val wallet: Wallet
) // Note: the client generates its own WireGuard keypair — the private
// key never leaves the device, so the backend never returns one.

data class HeartbeatResponse(
    @SerializedName("balance") val balance: Int,
    @SerializedName("billed") val billed: Int = 0,
    @SerializedName("creditsExhausted") val creditsExhausted: Boolean
)

data class RewardClaimResponse(
    @SerializedName("status") val status: String, // PENDING, CREDITED, DUPLICATE, REJECTED
    @SerializedName("wallet") val wallet: Wallet? = null
)

// ---------- App config ----------

data class AppConfig(
    @SerializedName("welcomeCredits") val welcomeCredits: Int = 100,
    @SerializedName("rewardCredits") val rewardCredits: Int = 20,
    @SerializedName("creditsPer30Min") val creditsPer30Min: Int = 10,
    @SerializedName("lowCreditThreshold") val lowCreditThreshold: Int = 20,
    @SerializedName("maintenanceMode") val maintenanceMode: Boolean = false,
    @SerializedName("supportEmail") val supportEmail: String = "",
    @SerializedName("minVersion") val minVersion: String = "1.0.0"
)

data class PublicIpResponse(
    @SerializedName("ip") val ip: String
)

data class ApiResult<T>(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: String? = null
)
