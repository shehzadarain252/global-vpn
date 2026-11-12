package com.globalvpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.globalvpn.data.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Reconnects the VPN after a device reboot, but only when the user
 * explicitly enabled auto-reconnect in Settings and a tunnel config
 * was saved from a previous session.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runBlocking {
            val prefs = Preferences(context)
            val autoReconnect = runCatching {
                prefs.autoReconnect.first()
            }.getOrDefault(false)
            if (!autoReconnect) return@runBlocking
            val config = prefs.lastConfig() ?: return@runBlocking
            val serverName = prefs.lastServerName()
            val serverCode = prefs.lastServerCode()
            val serviceIntent = Intent(context, GlobalVpnService::class.java).apply {
                action = GlobalVpnService.ACTION_CONNECT
                putExtra(GlobalVpnService.EXTRA_CONFIG, config)
                putExtra(GlobalVpnService.EXTRA_SERVER_NAME, serverName)
                putExtra(GlobalVpnService.EXTRA_SERVER_CODE, serverCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
