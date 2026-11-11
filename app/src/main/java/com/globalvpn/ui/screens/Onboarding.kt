package com.globalvpn.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalvpn.GlobalVpnApp
import com.globalvpn.ui.theme.BrandGradient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Splash screen — logo + tagline, then routes to Onboarding or Home. */
@Composable
fun SplashScreen(onDone: (onboardingSeen: Boolean) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(1_200L)
        val app = context.applicationContext as GlobalVpnApp
        val seen = app.preferences.onboardingSeen.first()
        onDone(seen)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "GLOBAL VPN",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            )
            Text(
                "Your Privacy. Your Connection. Your World.",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String
)

/** Three-page onboarding: privacy, credits, fastest server. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.Shield,
            title = "Private by design",
            body = "WireGuard encryption protects your traffic. No account needed — a random guest ID is created on your device."
        ),
        OnboardingPage(
            icon = Icons.Default.Wallet,
            title = "Free with credits",
            body = "You start with 100 welcome credits. VPN use costs 10 credits per 30 minutes. When credits run out, watch a short ad to earn 20 more."
        ),
        OnboardingPage(
            icon = Icons.Default.Bolt,
            title = "Fastest server, automatically",
            body = "Global VPN measures real ping to every server and picks the fastest one for you. Pick any country yourself whenever you like."
        )
    )
    val current = pages[page]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(current.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(current.title, fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(current.body, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == page) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (page < pages.lastIndex) {
                    page++
                } else {
                    val app = context.applicationContext as GlobalVpnApp
                    CoroutineScope(Dispatchers.IO).launch {
                        app.preferences.setOnboardingSeen(true)
                    }
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (page < pages.lastIndex) "Next" else "Get Started")
        }
        if (page < pages.lastIndex) {
            TextButton(onClick = {
                val app = context.applicationContext as GlobalVpnApp
                CoroutineScope(Dispatchers.IO).launch {
                    app.preferences.setOnboardingSeen(true)
                }
                onFinish()
            }) {
                Text("Skip")
            }
        }
    }
}
