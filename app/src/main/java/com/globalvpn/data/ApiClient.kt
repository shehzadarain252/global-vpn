package com.globalvpn.data

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Backend API (Node.js/Express). All wallet and session state lives on
 * the server — the app never trusts a balance computed on the client.
 * See backend/server.js for the matching routes.
 */
interface GlobalVpnApi {

    @GET("api/health")
    suspend fun health(): Map<String, Any>

    @GET("api/config")
    suspend fun getConfig(): AppConfig

    @GET("api/servers")
    suspend fun getServers(): List<VpnServer>

    @GET("api/wallet/{guestId}")
    suspend fun getWallet(@Path("guestId") guestId: String): Wallet

    @POST("api/wallet/welcome")
    suspend fun claimWelcome(@Body body: Map<String, String>): Wallet

    @GET("api/wallet/{guestId}/transactions")
    suspend fun getTransactions(@Path("guestId") guestId: String): List<CreditTransaction>

    @DELETE("api/account/{guestId}")
    suspend fun deleteAccount(@Path("guestId") guestId: String): Map<String, Any>

    @POST("api/session/start")
    suspend fun startSession(@Body body: Map<String, String>): SessionStartResponse

    @POST("api/session/heartbeat")
    suspend fun heartbeat(@Body body: Map<String, Any>): HeartbeatResponse

    @POST("api/session/end")
    suspend fun endSession(@Body body: Map<String, Any>): Wallet

    @POST("api/reward/claim")
    suspend fun claimReward(@Body body: Map<String, String>): RewardClaimResponse

    @POST("api/error/report")
    suspend fun reportError(@Body body: Map<String, String>): Map<String, Any>
}

object ApiClient {
    fun create(baseUrl: String): GlobalVpnApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        // Render free tier sleeps after inactivity and takes ~50s to
        // wake. OkHttp's 10s defaults made the first call time out, so
        // the app wrongly reported "Cannot reach the Global VPN
        // server" on a cold start. Generous timeouts fix that.
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        val gson = GsonBuilder().create()
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GlobalVpnApi::class.java)
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
