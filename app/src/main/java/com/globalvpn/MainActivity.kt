package com.globalvpn

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.globalvpn.ui.navigation.GlobalVpnNavHost
import com.globalvpn.ui.theme.GlobalVpnTheme
import kotlinx.coroutines.launch

/**
 * Single-activity app. Owns the VPN permission request flow and the
 * notification permission, then hosts the Compose navigation graph.
 *
 * VPN permission: the ViewModel asks for it (vpnPermissionRequest)
 * before connecting; this activity launches the system dialog and
 * reports the result back to the ViewModel.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification permission is optional; the service still works. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.preloadAds()
        requestNotificationPermission()

        lifecycleScope.launch {
            viewModel.vpnPermissionRequest.collect {
                val intent = VpnService.prepare(this@MainActivity)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    viewModel.onVpnPermissionResult(true)
                }
            }
        }

        setContent {
            val app = application as GlobalVpnApp
            val themeMode by app.preferences.theme.collectAsState(initial = "SYSTEM")
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            GlobalVpnTheme(darkTheme = darkTheme) {
                GlobalVpnNavHost(viewModel = viewModel)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
