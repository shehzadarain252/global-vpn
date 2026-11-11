package com.globalvpn.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.globalvpn.ads.AdMobManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner ad slot. Shown ONLY while the VPN is disconnected (including
 * on app open) and ONLY when a Banner Ad Unit ID is configured.
 * Until the developer provides the Banner Ad Unit ID, this renders
 * nothing — the slot simply stays hidden.
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    if (!AdMobManager.isBannerConfigured()) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdMobManager.BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
