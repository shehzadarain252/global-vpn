package com.globalvpn.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.globalvpn.GlobalVpnApp
import com.globalvpn.VpnViewModel
import com.globalvpn.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Settings screen: theme, auto-reconnect, kill switch, legal links, account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VpnViewModel, navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as GlobalVpnApp
    val themeMode by app.preferences.theme.collectAsState(initial = "SYSTEM")
    val autoReconnect by app.preferences.autoReconnect.collectAsState(initial = true)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = { GlobalBottomNav(navController = navController, currentRoute = Routes.SETTINGS) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf("SYSTEM" to "System default", "LIGHT" to "Light", "DARK" to "Dark").forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                CoroutineScope(Dispatchers.IO).launch { app.preferences.setTheme(value) }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        if (themeMode == value) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Connection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-reconnect", fontWeight = FontWeight.SemiBold)
                        Text("Reconnect automatically if the VPN drops", fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoReconnect,
                        onCheckedChange = {
                            CoroutineScope(Dispatchers.IO).launch { app.preferences.setAutoReconnect(it) }
                        }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kill switch", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Android's built-in 'Always-on VPN' + 'Block connections without VPN' " +
                                "prevents traffic from leaking if the tunnel drops. Enable it in the system VPN settings.",
                            fontSize = 12.sp
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open System VPN Settings")
                }
                Spacer(Modifier.height(16.dp))
                Text("Legal & info", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
            }
            val links = listOf(
                "Privacy Policy" to Routes.PRIVACY,
                "Terms & Conditions" to Routes.TERMS,
                "Disclaimer" to Routes.DISCLAIMER,
                "About Global VPN" to Routes.ABOUT,
                "Contact Us" to Routes.CONTACT,
                "FAQs" to Routes.FAQ,
                "Help & Support" to Routes.HELP,
                "Connection History" to Routes.HISTORY,
                "App Info" to Routes.APP_INFO
            )
            links.forEach { (label, route) ->
                item {
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(route) }
                            .padding(vertical = 12.dp),
                        fontSize = 15.sp
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Delete Account",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.DELETE_ACCOUNT) }
                        .padding(vertical = 12.dp),
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** App Info screen: version, package, backend and feature status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(viewModel: VpnViewModel, navController: NavController) {
    val context = LocalContext.current
    val backendReachable by viewModel.backendReachable.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("App Info") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("GLOBAL VPN", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Your Privacy. Your Connection. Your World.", fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            DetailRow("Version", packageInfo?.versionName ?: "1.0.0")
            DetailRow("Package", context.packageName)
            DetailRow("Protocol", "WireGuard")
            DetailRow("Backend", if (backendReachable) "Reachable" else "Unreachable")
            DetailRow("Servers online", "${servers.count { it.status == "online" }} of ${servers.size}")
            DetailRow("Ads", "AdMob — free with ads, no purchases")
            DetailRow("Analytics", "Firebase Analytics + Crashlytics (anonymous)")
            Spacer(Modifier.height(16.dp))
            Text(
                "Global VPN is 100% free. There is no premium plan and no in-app purchases. " +
                    "The app is funded by ads and by the credit system: use the VPN with credits, " +
                    "earn more credits by watching ads.",
                fontSize = 13.sp
            )
        }
    }
}

/** Delete Account screen — wipes the guest account server-side and locally. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(viewModel: VpnViewModel, navController: NavController) {
    var showConfirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Delete Account") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Deleting your account permanently removes your guest account, credit wallet and " +
                    "transaction history from the Global VPN server, and clears all local data on this device. " +
                    "This cannot be undone. Your unused credits are lost.",
                fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { showConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete My Account")
            }
            result?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 13.sp)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete account?") },
            text = { Text("Your wallet, credits and history will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    CoroutineScope(Dispatchers.IO).launch {
                        val ok = viewModel.deleteAccount()
                        result = if (ok) "Account deleted."
                        else "Could not delete the account. Please check your connection and try again."
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
