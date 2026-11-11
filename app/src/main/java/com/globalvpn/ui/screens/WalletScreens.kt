package com.globalvpn.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.globalvpn.VpnViewModel
import com.globalvpn.ui.navigation.Routes

/**
 * Wallet screen: balance, lifetime totals, transaction history, and
 * the "Watch Ad & Earn Credits" button — always visible here too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(viewModel: VpnViewModel, navController: NavController) {
    val activity = LocalContext.current as Activity
    val wallet by viewModel.wallet.collectAsState()
    val config by viewModel.config.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val rewardStatus by viewModel.rewardStatus.collectAsState()
    val rewardCredits = config?.rewardCredits ?: 20

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wallet") }) },
        bottomBar = { GlobalBottomNav(navController = navController, currentRoute = Routes.WALLET) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Credit balance", fontSize = 13.sp)
                        Text(
                            "${wallet?.balance ?: "—"} credits",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "100 welcome credits • ${config?.creditsPer30Min ?: 10} credits per 30 min of VPN use • $rewardCredits credits per rewarded ad",
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                DetailRow("Total earned", "${wallet?.totalEarned ?: 0} credits")
                DetailRow("Total used", "${wallet?.totalUsed ?: 0} credits")
                Spacer(Modifier.height(12.dp))

                // Watch Ad & Earn — always visible on Wallet.
                Button(
                    onClick = { viewModel.watchAdAndEarn(activity) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Watch Ad & Earn $rewardCredits Credits")
                }
                rewardStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text("Credit history", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (transactions.isEmpty()) {
                item { Text("No transactions yet.", fontSize = 13.sp) }
            } else {
                items(transactions) { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.reason, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(tx.createdAt, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = if (tx.amount >= 0) "+${tx.amount}" else "${tx.amount}",
                            fontWeight = FontWeight.Bold,
                            color = if (tx.amount >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * Earn Credits screen — explains the credit loop in five steps and
 * offers the rewarded ad. This is the screen users land on when their
 * credits run out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarnScreen(viewModel: VpnViewModel, navController: NavController) {
    val activity = LocalContext.current as Activity
    val wallet by viewModel.wallet.collectAsState()
    val config by viewModel.config.collectAsState()
    val rewardStatus by viewModel.rewardStatus.collectAsState()
    val rewardCredits = config?.rewardCredits ?: 20
    val per30 = config?.creditsPer30Min ?: 10

    Scaffold(
        topBar = { TopAppBar(title = { Text("Earn Credits") }) },
        bottomBar = { GlobalBottomNav(navController = navController, currentRoute = Routes.HOME) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text("How free credits work", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text("Your balance: ${wallet?.balance ?: "—"} credits", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val steps = listOf(
                "1. Use the VPN — $per30 credits are used per 30 minutes.",
                "2. When credits get low, you get a warning before they run out.",
                "3. When credits reach zero, the VPN disconnects safely.",
                "4. Watch a short ad — after the reward is verified, you earn $rewardCredits credits.",
                "5. Connect again and keep using Global VPN — free, forever."
            )
            steps.forEach { step ->
                Text(step, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.watchAdAndEarn(activity) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Watch Ad & Earn $rewardCredits Credits")
            }
            rewardStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { navController.navigate(Routes.HOME) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Home")
            }
        }
    }
}

/** Connection History screen — past sessions with duration and data used. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: VpnViewModel, navController: NavController) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Connection History") }) },
        bottomBar = { GlobalBottomNav(navController = navController, currentRoute = Routes.HOME) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (history.isEmpty()) {
                item { Text("No connections yet. Your past VPN sessions will appear here.", fontSize = 13.sp) }
            } else {
                items(history) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(session.serverCountry, fontWeight = FontWeight.Bold)
                            Text("Started: ${session.startedAt}", fontSize = 12.sp)
                            Text(
                                "Data: ${formatBytes(session.bytesRx + session.bytesTx)}",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
