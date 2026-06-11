package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val titleKey: String, val defaultTitle: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "dashboard", "الرئيسية", Icons.Default.Home)
    object AddProduct : Screen("add_product", "add_product", "إضافة منتج", Icons.Default.AddBox)
    object BarcodeScanner : Screen("barcode_scanner", "barcode_scanner", "مسح باركود", Icons.Default.QrCodeScanner)
    object BatchUpload : Screen("batch_upload", "batch_upload", "رفع Excel", Icons.Default.UploadFile)
    object SyncLocal : Screen("sync_local", "sync_local", "المزامنة اليدوية", Icons.Default.Sync)
    object History : Screen("history", "history", "السجل وبطاقات العمليات", Icons.Default.History)
    object Support : Screen("support", "support", "الدعم الفني", Icons.Default.ContactSupport)
    object Settings : Screen("settings", "settings", "الإعدادات واللغة", Icons.Default.Settings)

    val title: String
        get() = com.example.model.LanguageManager.getString(titleKey, defaultTitle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLayout(
    currentRoute: String,
    onNavigateToRoute: (String) -> Unit,
    onLogout: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var isServerConnected by remember { mutableStateOf(false) }
    val storedUser = remember { ApiClient.getStoredUser(context) }

    // Run connection probe on startup
    LaunchedEffect(Unit) {
        val token = ApiClient.getToken(context)
        if (!token.isNullOrEmpty()) {
            val res = ApiClient.testConnection(token)
            isServerConnected = res.ok
        }
    }

    // Dynamic language direction
    val isArabic = com.example.model.LanguageManager.isArabic()
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Sidebar Header Banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = com.example.model.LanguageManager.getString("app_name", "حذاري للرقابة والمخزون"),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Connection dot indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isServerConnected) Color(0xFF22C55E) else Color(0xFFEF4444))
                            )
                            Text(
                                text = if (isServerConnected) 
                                    com.example.model.LanguageManager.getString("connected", "متصل بالمنصة") 
                                else 
                                    com.example.model.LanguageManager.getString("disconnected", "غير متصل بالمنصة"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isServerConnected) Color(0xFF22C55E) else Color(0xFFEF4444)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Navigation Links list
                    val items = listOf(
                        Screen.Dashboard,
                        Screen.AddProduct,
                        Screen.BarcodeScanner,
                        Screen.BatchUpload,
                        Screen.SyncLocal,
                        Screen.History,
                        Screen.Support,
                        Screen.Settings
                    )

                    items.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationDrawerItem(
                            label = { 
                                Text(
                                    text = screen.title, 
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                ) 
                            },
                            selected = selected,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToRoute(screen.route)
                            },
                            icon = { 
                                Icon(
                                    imageVector = screen.icon, 
                                    contentDescription = screen.title,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                ) 
                            },
                            badge = {
                                if (screen == Screen.Settings && com.example.model.AppUpdateManager.isUpdateNotificationActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .testTag("nav_item_${screen.route}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                     // Drawer footer user info and Logout Button
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .clickable {
                                ApiClient.clearToken(context)
                                onLogout()
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = com.example.model.LanguageManager.getString("logout", "تسجيل الخروج"),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = com.example.model.LanguageManager.getString("logout", "تسجيل الخروج"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = when (currentRoute) {
                                        Screen.Dashboard.route -> Screen.Dashboard.title
                                        Screen.AddProduct.route -> Screen.AddProduct.title
                                        Screen.BarcodeScanner.route -> Screen.BarcodeScanner.title
                                        Screen.BatchUpload.route -> Screen.BatchUpload.title
                                        Screen.SyncLocal.route -> Screen.SyncLocal.title
                                        Screen.History.route -> Screen.History.title
                                        Screen.Support.route -> Screen.Support.title
                                        Screen.Settings.route -> Screen.Settings.title
                                        else -> "لوحة التحكم"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Visual connection light bubble on header
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isServerConnected) Color(0xFF22C55E) else Color(0xFFEF4444))
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("hamburger_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = com.example.model.LanguageManager.getString("menu", "القائمة الجانبية"),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                content = content
            )
        }
    }
}
