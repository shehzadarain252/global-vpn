package com.globalvpn.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.globalvpn.CreditState
import com.globalvpn.VpnViewModel
import com.globalvpn.data.VpnServer
import com.globalvpn.ui.navigation.Routes
import com.globalvpn.ui.theme.BrandGradient
import com.globalvpn.ui.theme.SuccessGreen
import com.globalvpn.ui.theme.WarningAmber
import com.globalvpn.vpn.VpnState

/**
 * Home dashboard — the main screen. Design follows the reference
 * screenshot: dark purple theme, large circular Connect button with a
 * globe (timer while connected), selected-server card, upload/download
 * speeds, and a bottom navigation bar.
 *
 * The credit balance is always visible at the top, and the
 * "Watch Ad & Earn Credits" button is always visible — the credit
 * loop (use -> run out -> watch ad -> earn -> use again) never leaves
 * the user stuck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: VpnViewModel, navController: NavController) {
    val activity = LocalContext.current as Activity
    val vpnState by viewModel.vpnState.collectAsState()
    val stats by viewModel.connectionStats.collectAsState()
    val wallet by viewModel.wallet.collectAsState()
    val config by viewModel.config.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val autoFastest by viewModel.autoSelectFastest.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val publicIp by viewModel.publicIp.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showEarnPrompt by viewModel.showEarnPrompt.collectAsState()
    val creditsExhausted by viewModel.creditsExhausted.collectAsState()
    val backendReachable by viewModel.backendReachable.collectAsState()
    val creditState = viewModel.creditState()
    val rewardCredits = config?.rewardCredits ?: 20

    val isConnected = vpnState == VpnState.CONNECTED
    val isBusy = vpnState == VpnState.CONNECTING || vpnState == VpnState.DISCONNECTING

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("GLOBAL VPN", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    // Credit balance — always visible on the dashboard.
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Wallet, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${wallet?.balance ?: "—"} credits",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            Column {
                GlobalBottomNav(navController = navController, currentRoute = Routes.HOME)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // Status line
                val statusText = when (vpnState) {
                    VpnState.CONNECTED -> "Connected"
                    VpnState.CONNECTING -> "Connecting…"
                    VpnState.DISCONNECTING -> "Disconnecting…"
                    VpnState.ERROR -> "Connection error"
                    VpnState.DISCONNECTED -> "Not Connected"
                }
                val statusColor = when (vpnState) {
                    VpnState.CONNECTED -> SuccessGreen
                    VpnState.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    text = if (isConnected) "Your connection is protected"
                    else "Your connection is not protected",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                // Big circular Connect button (globe, or timer when connected).
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(BrandGradient)
                        .clickable(enabled = !isBusy) {
                            if (isConnected) viewModel.disconnectWithAd(activity)
                            else viewModel.connectWithAd(activity)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isBusy) {
                            CircularProgressIndicator(color = Color.White)
                        } else if (isConnected) {
                            Text(
                                formatDuration(stats.durationSec),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                            Text("connected", color = Color.White, fontSize = 13.sp)
                        } else {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(84.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Connect / Disconnect button.
                Button(
                    onClick = {
                        if (isConnected) viewModel.disconnectWithAd(activity)
                        else viewModel.connectWithAd(activity)
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isConnected) "Disconnect" else "Connect")
                }
                Spacer(Modifier.height(12.dp))

                // Selected server card.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.SERVERS) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    selectedServer != null -> selectedServer!!.country
                                    autoFastest -> "Fastest free server"
                                    else -> "Choose a server"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    selectedServer != null ->
                                        "${selectedServer!!.city} • Auto-selected fastest"
                                    autoFastest -> "Auto-selected from measured ping"
                                    else -> "Tap to pick a server"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Change >", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Speeds + IP row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedBox("Upload", formatSpeed(stats.txSpeedBps))
                    SpeedBox("Download", formatSpeed(stats.rxSpeedBps))
                    SpeedBox("Used", formatBytes(stats.rxBytes + stats.txBytes))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your IP: ${publicIp ?: "—"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                // Low-credit warning (before credits run out).
                if (creditState == CreditState.LOW && !isConnected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Low credits: ${wallet?.balance ?: 0} left. Watch an ad to earn $rewardCredits more before they run out.",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Out-of-credits state: the user must immediately see how
                // to earn more — big, prominent Watch Ad button.
                if (creditState == CreditState.EMPTY || creditsExhausted || showEarnPrompt) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Out of credits",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                "Watch a short ad to earn $rewardCredits credits and connect again.",
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.watchAdAndEarn(activity) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Watch Ad & Earn $rewardCredits Credits")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Watch Ad & Earn — ALWAYS visible on Home.
                OutlinedButton(
                    onClick = { viewModel.watchAdAndEarn(activity) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Watch Ad & Earn $rewardCredits Credits")
                }
                Spacer(Modifier.height(12.dp))

                // No-servers honest state.
                if (servers.isEmpty() && backendReachable) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No servers configured yet. The owner adds real VPN servers in the admin panel — countries appear here only when a real server is online.",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (!backendReachable) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Cannot reach the Global VPN server. Check your connection and pull to retry.",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SpeedBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/**
 * Server list. Shows each server's REAL measured ping, backend-reported
 * load, and last measured speed (from past sessions) so the user can
 * manually pick the fastest. The top card auto-selects the fastest
 * server from measured ping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(viewModel: VpnViewModel, navController: NavController) {
    val servers by viewModel.servers.collectAsState()
    val pings by viewModel.pings.collectAsState()
    val measuring by viewModel.measuringPings.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val autoFastest by viewModel.autoSelectFastest.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = servers.filter {
        query.isBlank() ||
            it.country.contains(query, ignoreCase = true) ||
            it.city.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Locations") },
                actions = {
                    IconButton(onClick = { /* refresh handled in VM */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh pings")
                    }
                }
            )
        },
        bottomBar = { GlobalBottomNav(navController = navController, currentRoute = Routes.SERVERS) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search countries") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Fastest-server auto card (matches the reference design).
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectFastestServer() },
                colors = CardDefaults.cardColors(
                    containerColor = if (autoFastest) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fastest free server", fontWeight = FontWeight.Bold)
                        Text(
                            "Auto-selected from real measured ping",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (autoFastest) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (measuring) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Measuring ping…", fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn {
                items(filtered) { server ->
                    ServerRow(
                        server = server,
                        pingMs = pings[server.id],
                        lastSpeedBps = viewModel.lastSpeedForServer(server),
                        selected = selectedServer?.id == server.id && !autoFastest,
                        onClick = {
                            viewModel.selectServer(server)
                            navController.popBackStack()
                        }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No servers yet. Countries appear here only when a real server is online.",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: VpnServer,
    pingMs: Int?,
    lastSpeedBps: Long?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Country-code badge (no flag images — honest, always renders).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    server.countryCode,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(server.country, fontWeight = FontWeight.Bold)
                Text(
                    "${server.city} • Load ${server.load}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = pingMs?.let { "$it ms" } ?: "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = lastSpeedBps?.let { formatSpeed(it) } ?: "speed —",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
