package com.globalvpn.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Analytics wrapper.
 *
 * PRIVACY: only anonymous usage events are logged. No personal data, no
 * user IDs, no e-mail addresses, no IP addresses, no browsing history and
 * no VPN traffic content is ever sent. The only parameters are anonymous
 * counters (country code of the selected server, credit amounts). This is what the owner sees in the Firebase console as
 * DAU/MAU, retention and crash reports.
 *
 * If Firebase is not configured (placeholder google-services.json),
 * every call is a no-op — the app keeps working normally.
 */
object Analytics {

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        } catch (e: Exception) {
            Log.w("Analytics", "Firebase Analytics unavailable: ${e.message}")
            firebaseAnalytics = null
        }
    }

    fun logAppOpen() {
        log(FirebaseAnalytics.Event.APP_OPEN, null)
    }

    fun logVpnConnect(countryCode: String) {
        log("vpn_connect", Bundle().apply {
            putString("country_code", countryCode)
        })
    }

    fun logVpnDisconnect(durationSeconds: Long) {
        log("vpn_disconnect", Bundle().apply {
            putLong("duration_seconds", durationSeconds)
        })
    }

    fun logServerCountrySelected(countryCode: String) {
        log("server_country_selected", Bundle().apply {
            putString("country_code", countryCode)
        })
    }

    fun logRewardedAdEarned(credits: Int) {
        log("rewarded_ad_earned", Bundle().apply {
            putInt("credits", credits)
        })
    }

    fun logCreditsEarned(amount: Int, source: String) {
        log("credits_earned", Bundle().apply {
            putInt("amount", amount)
            putString("source", source)
        })
    }

    fun recordNonFatal(throwable: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            Log.w("Analytics", "Crashlytics unavailable: ${e.message}")
        }
    }

    private fun log(event: String, params: Bundle?) {
        try {
            firebaseAnalytics?.logEvent(event, params)
        } catch (e: Exception) {
            Log.w("Analytics", "logEvent failed: ${e.message}")
        }
    }
}
