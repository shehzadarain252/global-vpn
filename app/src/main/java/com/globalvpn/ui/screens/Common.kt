package com.globalvpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.globalvpn.ui.navigation.Routes

/** Bottom navigation: Home, Servers, Wallet, Settings. */
@Composable
fun GlobalBottomNav(navController: NavController, currentRoute: String) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.HOME,
            onClick = { if (currentRoute != Routes.HOME) navController.navigate(Routes.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SERVERS,
            onClick = { if (currentRoute != Routes.SERVERS) navController.navigate(Routes.SERVERS) },
            icon = { Icon(Icons.Default.List, contentDescription = "Servers") },
            label = { Text("Servers") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.WALLET,
            onClick = { if (currentRoute != Routes.WALLET) navController.navigate(Routes.WALLET) },
            icon = { Icon(Icons.Default.Wallet, contentDescription = "Wallet") },
            label = { Text("Wallet") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = { if (currentRoute != Routes.SETTINGS) navController.navigate(Routes.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}

/** Formats bytes as B / KB / MB / GB. */
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_024 * 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        bytes < 1_024 * 1_024 * 1_024 -> "%.1f MB".format(bytes / (1_024.0 * 1_024.0))
        else -> "%.2f GB".format(bytes / (1_024.0 * 1_024.0 * 1_024.0))
    }
}

/** Formats a speed in bytes/sec as KB/s or MB/s. */
fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec < 1_024 -> "$bytesPerSec B/s"
        bytesPerSec < 1_024 * 1_024 -> "%.1f KB/s".format(bytesPerSec / 1_024.0)
        else -> "%.2f MB/s".format(bytesPerSec / (1_024.0 * 1_024.0))
    }
}

/** Formats seconds as HH:MM:SS. */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** A simple labelled row used on detail screens. */
@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value)
    }
}
