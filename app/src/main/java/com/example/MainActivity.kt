package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.api.ApiClient
import com.example.ui.AppLayout
import com.example.ui.pages.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.model.LanguageManager.init(applicationContext)
        com.example.ui.theme.ThemeConfig.loadTheme(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigationHost()
            }
        }
    }
}

@Composable
fun AppNavigationHost() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Observers language state so the entire Navigation Graph triggers recomposition
    val currentLanguage by com.example.model.LanguageManager.currentLanguage.collectAsState()

    LaunchedEffect(Unit) {
        com.example.model.AppUpdateManager.initUpdateNotification(context)
        com.example.model.AppUpdateManager.triggerAutoUpdateIfNeeded(context, scope)
    }
    
    // Check if security lock is enabled
    val prefs = remember { context.getSharedPreferences("hathari_prefs", android.content.Context.MODE_PRIVATE) }
    val isLockEnabled = remember { prefs.getBoolean("security_lock_enabled", false) }
    var isAppUnlocked by remember { mutableStateOf(!isLockEnabled) }

    // Check if user is logged in
    val initialRoute = remember {
        val token = ApiClient.getToken(context)
        if (token.isNullOrEmpty()) "login" else "dashboard"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "splash"
        ) {
        composable("splash") {
            SplashPage(
                onFinished = { targetRoute ->
                    navController.navigate(targetRoute) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // ── Auth Route ────────────────────────────────────────────────────────
        composable("login") {
            LoginPage(
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ── Secure Layout routes ──────────────────────────────────────────────
        composable("dashboard") {
            AppLayout(
                currentRoute = "dashboard",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                DashboardPage(
                    onNavigateToAddProduct = { navController.navigate("add_product") },
                    onNavigateToScanner = { navController.navigate("barcode_scanner") },
                    onNavigateToBatchUpload = { navController.navigate("batch_upload") },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable(
            route = "add_product?barcode={barcode}",
            arguments = listOf(navArgument("barcode") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val barcodeArg = backStackEntry.arguments?.getString("barcode")
            AppLayout(
                currentRoute = "add_product",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                AddProductPage(
                    scannedBarcode = barcodeArg,
                    onNavigateBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable("barcode_scanner") {
            AppLayout(
                currentRoute = "barcode_scanner",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                BarcodeScannerPage(
                    onBarcodeScanned = { scannedValue ->
                        navController.navigate("add_product?barcode=$scannedValue") {
                            popUpTo("barcode_scanner") { inclusive = true }
                        }
                    },
                    onNavigateToManualAdd = {
                        navController.navigate("add_product") {
                            popUpTo("barcode_scanner") { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable("batch_upload") {
            AppLayout(
                currentRoute = "batch_upload",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                BatchUploadPage(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable("history") {
            AppLayout(
                currentRoute = "history",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                HistoryPage(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable("sync_local") {
            AppLayout(
                currentRoute = "sync_local",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                SyncLocalPage(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable("settings") {
            AppLayout(
                currentRoute = "settings",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                SettingsPage(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable("support") {
            AppLayout(
                currentRoute = "support",
                onNavigateToRoute = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            ) { innerPadding ->
                SupportPage(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (!isAppUnlocked) {
        com.example.ui.components.SecurityLockOverlay(
            onUnlockSuccess = {
                isAppUnlocked = true
            }
        )
    }
}
}
