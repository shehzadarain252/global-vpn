package com.globalvpn.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Google Mobile Ads (AdMob) manager — LIVE production units.
 *
 * SECURITY: the ad unit IDs below live ONLY in this file (app code).
 * They must never be copied into docs, README files, memory notes or
 * any other file.
 *
 * AD FLOW (user-confirmed, 2026-11-11 — this supersedes any earlier
 * "no ads at connect/disconnect" wording):
 * - CONNECT: user taps Connect -> a pre-loaded interstitial shows
 *   FIRST -> when it is dismissed, the WireGuard connection starts.
 *   If no interstitial is loaded or it fails to show, the connection
 *   starts IMMEDIATELY — an ad failure never blocks connecting.
 * - DISCONNECT: user taps Disconnect -> a pre-loaded interstitial
 *   shows FIRST -> when dismissed, the VPN stops. Same immediate
 *   fallback if the ad is unavailable.
 * - At most ONE interstitial per Connect tap and ONE per Disconnect
 *   tap. The ad always comes BEFORE the action, never while a
 *   connection is being established or torn down.
 * - There is no ad-free tier: every user sees an interstitial before
 *   Connect and before Disconnect, and rewarded ads for credits.
 *   (No banner ads — removed at the owner's request.)
 * - Rewarded ads grant credits ONLY after the reward callback fires,
 *   and the credit is then verified server-side by the backend
 *   (see VpnRepository.claimReward + backend /api/reward/).
 *
 * Ad load/show failures are reported through [onAdError] so the app
 * can log them to the backend for the admin error reports.
 */
class AdMobManager(private val context: Context) {

    companion object {
        // LIVE production ad units (developer-supplied).
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4510714397469784/7398560475"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-4510714397469784/2417696533"
    }

    /** Called on ad load/show failures (no PII) for admin error reports. */
    var onAdError: ((String) -> Unit)? = null

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isLoadingInterstitial = false
    private var isLoadingRewarded = false

    fun preloadAds() {
        loadInterstitial()
        loadRewarded()
    }

    fun loadInterstitial() {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    Log.w("AdMobManager", "Interstitial failed to load: ${error.message}")
                    onAdError?.invoke("interstitial_load_failed: ${error.code}")
                }
            }
        )
    }

    /**
     * Shows the pre-loaded interstitial, then runs [onDismissed].
     * Used BEFORE Connect and BEFORE Disconnect (user-confirmed flow):
     * the ad shows first, and the VPN action runs only after the ad is
     * dismissed. If no ad is loaded or it fails to show, [onDismissed]
     * runs immediately so the VPN action is never blocked on an ad.
     * At most one interstitial is shown per call.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial()
            onDismissed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial()
                Log.w("AdMobManager", "Interstitial failed to show: ${error.message}")
                onAdError?.invoke("interstitial_show_failed: ${error.code}")
                onDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                interstitialAd = null
            }
        }
        ad.show(activity)
    }

    fun loadRewarded() {
        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingRewarded = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingRewarded = false
                    rewardedAd = null
                    Log.w("AdMobManager", "Rewarded failed to load: ${error.message}")
                    onAdError?.invoke("rewarded_load_failed: ${error.code}")
                }
            }
        )
    }

    /**
     * Shows a rewarded ad. [onRewardEarned] fires only when the user
     * actually earned the reward — the caller must still verify the
     * reward server-side (backend /api/reward/claim) before crediting.
     */
    fun showRewarded(
        activity: Activity,
        onRewardEarned: (RewardItem) -> Unit,
        onDismissed: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null) {
            loadRewarded()
            onDismissed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewarded()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadRewarded()
                Log.w("AdMobManager", "Rewarded failed to show: ${error.message}")
                onAdError?.invoke("rewarded_show_failed: ${error.code}")
                onDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
            }
        }
        ad.show(activity) { rewardItem -> onRewardEarned(rewardItem) }
    }
}
