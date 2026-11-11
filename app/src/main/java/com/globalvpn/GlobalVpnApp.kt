package com.globalvpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.globalvpn.ads.AdMobManager
import com.globalvpn.analytics.Analytics
import com.globalvpn.data.Preferences
import com.globalvpn.data.VpnRepository
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Application class for Global VPN.
 *
 * Initialises Firebase (Analytics + Crashlytics) and Google Mobile Ads.
 * Firebase is initialised from app/google-services.json. If the file still
 * contains the placeholder values from google-services.json.example,
 * Firebase will log errors and drop events — the app itself keeps working.
 * See README.md "Firebase setup" for the real setup steps.
 */
class GlobalVpnApp : Application() {

    lateinit var repository: VpnRepository
        private set
    lateinit var adMobManager: AdMobManager
        private set
    lateinit var preferences: Preferences
        private set

    override fun onCreate() {
        super.onCreate()

        // Firebase: Analytics (anonymous usage counts) + Crashlytics.
        // No personal data is ever logged (see analytics/Analytics.kt).
        try {
            FirebaseApp.initializeApp(this)
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.setCustomKey("app", "global-vpn")
        } catch (e: Exception) {
            // Firebase not configured (placeholder google-services.json).
            // The app works without it; Analytics events are dropped.
            android.util.Log.w("GlobalVpnApp", "Firebase init failed: ${e.message}")
        }

        // Google Mobile Ads (LIVE production units — see ads/AdMobManager.kt).
        MobileAds.initialize(this) {}

        createNotificationChannel()

        preferences = Preferences(this)
        repository = VpnRepository(this, preferences)
        adMobManager = AdMobManager(this)
        adMobManager.preloadAds()
        // Ad failures go to the backend for the admin error reports.
        adMobManager.onAdError = { message ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                repository.reportError(message, "admob")
            }
        }

        Analytics.init(this)
        Analytics.logAppOpen()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the Global VPN connection status."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "global_vpn_connection"
        const val NOTIFICATION_ID = 1001
    }
}
