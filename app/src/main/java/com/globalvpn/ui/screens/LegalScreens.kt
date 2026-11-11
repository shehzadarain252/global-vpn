package com.globalvpn.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * Legal / info pages. The text here matches the backend pages served at
 * /privacy-policy (full policy), /terms and /disclaimer. Honest wording:
 * no absolute-anonymity claims, GPS note included, Firebase Analytics +
 * Crashlytics disclosed, free with ads, no premium, no purchases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(kind: String, navController: NavController) {
    val title = when (kind) {
        "privacy" -> "Privacy Policy"
        "terms" -> "Terms & Conditions"
        "disclaimer" -> "Disclaimer"
        "about" -> "About Global VPN"
        "contact" -> "Contact Us"
        "faq" -> "FAQs"
        else -> "Info"
    }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            Text(legalText(kind), fontSize = 14.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun legalText(kind: String): String = when (kind) {
    "privacy" -> """
        GLOBAL VPN — Privacy Policy (summary)
        Last updated: November 2026

        What Global VPN is: a free VPN app that routes your internet traffic through WireGuard servers so websites see the server's IP address and country instead of yours.

        What we collect:
        • A random guest ID created on your device (no name, email or phone is required).
        • Your credit balance and credit transactions, so the credit system works.
        • Anonymous usage analytics via Firebase Analytics (for example: how many people open the app, which countries are selected) — no personal data.
        • Anonymous crash reports via Firebase Crashlytics, to fix bugs.
        • AdMob (Google) may collect advertising identifiers to show ads, per Google's policies.

        What we do NOT collect: your browsing history, your real name, email, phone number, passwords, or the content of your traffic. We do not sell your data.

        Important limits:
        • A VPN changes your IP address and apparent country. It does NOT change your GPS location or your SIM/mobile-network location.
        • No VPN can promise complete anonymity. Your internet provider still sees that you connect to a VPN server, and websites you log in to still know who you are.
        • VPN traffic is encrypted between your device and the VPN server; after the server, normal internet rules apply.

        Your data: you can delete your guest account and wallet at any time from Settings → Delete Account. Contact: support@globalvpn.app
    """.trimIndent()
    "terms" -> """
        GLOBAL VPN — Terms & Conditions
        Last updated: November 2026

        1. The app is 100% free. There is no premium plan and no in-app purchases. The app is funded by ads and by the credit system.
        2. Credits: new users receive 100 welcome credits. VPN use costs 10 credits per 30 minutes (rates can change in the app settings). Watching a rewarded ad earns 20 credits after the reward is verified. Credits have no cash value and cannot be transferred, sold or refunded.
        3. Fair use: do not use the service for illegal activity, spam, abuse, fraud, or to harm others. We may block accounts that abuse the service.
        4. Availability: servers may be added, removed or temporarily unavailable. We do not guarantee any specific speed, server or country — real speed depends on the server's location, bandwidth and load, and on your own connection.
        5. No warranty: the service is provided "as is". We are not liable for interruptions, data loss, or any loss arising from use of the app.
        6. You must follow the laws of your country while using the app.
        7. We may update these terms; continued use means you accept the updated terms.
    """.trimIndent()
    "disclaimer" -> """
        GLOBAL VPN — Disclaimer

        Global VPN is a tool that encrypts traffic between your device and a VPN server and changes the IP address websites see. It does not make you anonymous, it does not hide your GPS or SIM location, and it does not protect you from malware, phishing, or websites you choose to trust with your information.

        Speeds shown in the app are real measured values (ping measured to the server; speed measured from actual tunnel traffic). Real speed depends on the server's location, bandwidth and current load, and on your own internet connection — results will vary.

        Server availability is shown honestly: a country is listed only when a real, operational server exists for it. If no server is configured, the app says so instead of pretending to connect.

        Use of the app is at your own risk. The developers are not responsible for how the service is used or for any loss arising from its use.
    """.trimIndent()
    "about" -> """
        About Global VPN

        Global VPN — "Your Privacy. Your Connection. Your World."

        A free, fast VPN app built on WireGuard, the modern VPN protocol. Choose a country, tap Connect, and your traffic is encrypted through a real server in that country.

        • 100% free — no premium, no purchases. Ads and credits keep it free.
        • Credit system: 100 welcome credits, 20 credits per rewarded ad, 10 credits per 30 minutes of use.
        • Fastest-server auto-selection: the app measures real ping to each server and picks the fastest by default.
        • Real servers only: a country appears only when a real server is online.
        • No account needed — a random guest ID is created on your device.
    """.trimIndent()
    "contact" -> """
        Contact Us

        Questions, problems or suggestions? Reach us at:

        Email: support@globalvpn.app

        Please include your guest ID (shown in App Info) only if the problem is about your wallet or credits — never share passwords or payment details with anyone.

        For server or admin enquiries, the admin panel is at /admin on the backend.
    """.trimIndent()
    "faq" -> """
        Frequently Asked Questions

        Is Global VPN really free?
        Yes — 100% free. There is no premium plan and no in-app purchases. The app is funded by ads and by the credit system.

        How do credits work?
        You start with 100 welcome credits. VPN use costs 10 credits per 30 minutes. When credits run out, watch a rewarded ad to earn 20 more credits.

        What happens when my credits run out?
        The VPN disconnects safely and a "Watch Ad & Earn Credits" button appears. Watch an ad, earn 20 credits, and connect again.

        Why is there an ad when I tap Connect?
        An interstitial ad shows before the VPN connects (and before it disconnects). If no ad is available, the connection starts immediately — ads never block the VPN.

        Does the VPN change my GPS location?
        No. A VPN changes your IP address and apparent country. Your GPS and SIM location stay the same.

        Why is my speed slower than my normal connection?
        Real speed depends on the server's location, bandwidth and load, and on your own connection. The app auto-selects the fastest server by measured ping; you can also pick a server manually.

        Why does a country not appear in the list?
        A country appears only when a real server is online for it. If no servers are configured yet, the app says so honestly.

        Do I need an account?
        No. A random guest ID is created on your device. You can delete your account and wallet at any time from Settings → Delete Account.
    """.trimIndent()
    else -> ""
}

/** Help & Support page — contact routes and quick links. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreens(navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Help & Support") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            Text("Need help?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Common problems:\n\n" +
                    "• VPN will not connect — check that you have credits, that a server is listed, and that you allowed the VPN permission.\n\n" +
                    "• Credits ran out — tap \"Watch Ad & Earn Credits\" on the Home screen.\n\n" +
                    "• Reward not credited — rewards are verified server-side and can take a few seconds; check your Wallet again shortly.\n\n" +
                    "• Slow speed — try the \"Fastest free server\" option, or pick a closer country.\n\n" +
                    "• App crashes — crash reports are sent anonymously via Firebase Crashlytics so we can fix them.\n\n" +
                    "Still stuck? Email support@globalvpn.app and describe what happened.",
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
