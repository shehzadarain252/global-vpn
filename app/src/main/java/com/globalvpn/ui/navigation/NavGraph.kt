package com.globalvpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.globalvpn.VpnViewModel
import com.globalvpn.ui.screens.AppInfoScreen
import com.globalvpn.ui.screens.DeleteAccountScreen
import com.globalvpn.ui.screens.EarnScreen
import com.globalvpn.ui.screens.HelpScreens
import com.globalvpn.ui.screens.HistoryScreen
import com.globalvpn.ui.screens.HomeScreen
import com.globalvpn.ui.screens.LegalScreen
import com.globalvpn.ui.screens.OnboardingScreen
import com.globalvpn.ui.screens.ServerListScreen
import com.globalvpn.ui.screens.SettingsScreen
import com.globalvpn.ui.screens.SplashScreen
import com.globalvpn.ui.screens.WalletScreen

/**
 * Navigation graph. 16 app pages (Premium Plans and Restore Purchases
 * were removed — the app is 100% free with ads, no purchases):
 * Home, Servers, Wallet, Earn, History, Settings, Privacy, Terms,
 * Disclaimer, About, Contact, FAQ, Help, Delete Account, App Info,
 * plus Splash and Onboarding.
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SERVERS = "servers"
    const val WALLET = "wallet"
    const val EARN = "earn"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val DISCLAIMER = "disclaimer"
    const val ABOUT = "about"
    const val CONTACT = "contact"
    const val FAQ = "faq"
    const val HELP = "help"
    const val APP_INFO = "app_info"
    const val DELETE_ACCOUNT = "delete_account"
}

@Composable
fun GlobalVpnNavHost(viewModel: VpnViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onDone = { seen ->
                    navController.navigate(if (seen) Routes.HOME else Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.SERVERS) {
            ServerListScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.WALLET) {
            WalletScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.EARN) {
            EarnScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.HISTORY) {
            HistoryScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.PRIVACY) { LegalScreen(kind = "privacy", navController = navController) }
        composable(Routes.TERMS) { LegalScreen(kind = "terms", navController = navController) }
        composable(Routes.DISCLAIMER) { LegalScreen(kind = "disclaimer", navController = navController) }
        composable(Routes.ABOUT) { LegalScreen(kind = "about", navController = navController) }
        composable(Routes.CONTACT) { LegalScreen(kind = "contact", navController = navController) }
        composable(Routes.FAQ) { LegalScreen(kind = "faq", navController = navController) }
        composable(Routes.HELP) { HelpScreens(navController = navController) }
        composable(Routes.APP_INFO) {
            AppInfoScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.DELETE_ACCOUNT) {
            DeleteAccountScreen(viewModel = viewModel, navController = navController)
        }
    }
}
