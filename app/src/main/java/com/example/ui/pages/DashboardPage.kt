package com.example.ui.pages

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import com.example.model.Product
import com.example.model.PlatformItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardPage(
    onNavigateToAddProduct: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToBatchUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLang by com.example.model.LanguageManager.currentLanguage.collectAsState()

    // Real-Time Clock State
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(currentLang) {
        val locale = if (currentLang == "ar") Locale("ar") else Locale("en")
        val datePattern = if (currentLang == "ar") "EEEE، dd MMMM yyyy" else "EEEE, d MMMM yyyy"
        val timeFormat = SimpleDateFormat("hh:mm:ss a", locale)
        val dateFormat = SimpleDateFormat(datePattern, locale)
        while (true) {
            val calendar = Calendar.getInstance()
            currentTimeString = timeFormat.format(calendar.time)
            currentDateString = dateFormat.format(calendar.time)
            delay(1000)
        }
    }

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var locations by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedLocation by remember { mutableStateOf<String?>(null) } // null means "All locations"
    var expiringItems by remember { mutableStateOf<List<PlatformItem>>(emptyList()) }
    
    var loading by remember { mutableStateOf(true) }
    var expiringItemsLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isOfflineMode by remember { mutableStateOf(false) }

    val loadData = {
        loading = true
        errorMessage = null
        isOfflineMode = false
        scope.launch {
            try {
                // Proactively auto-sync pending offline products and pull platform data if online
                com.example.model.AutoSyncManager.startAutoSync(context)

                // Read available platform branch locations
                val fetchedLocations = ApiClient.fetchLocations(context)
                if (fetchedLocations != null) {
                    locations = fetchedLocations
                    com.example.model.OfflineCacheManager.saveLocationsCache(context, fetchedLocations)
                } else {
                    locations = com.example.model.OfflineCacheManager.loadLocationsCache(context)
                }

                // Call location products endpoint
                val loadedProducts = ApiClient.fetchMyProducts(context, location = selectedLocation)
                val loadedExpiring = ApiClient.fetchExpiringItems(context, location = selectedLocation, days = 30)

                if (loadedProducts != null && loadedExpiring != null) {
                    products = loadedProducts
                    expiringItems = loadedExpiring
                    isOfflineMode = false
                    
                    // Update cache for the current state
                    if (selectedLocation == null) {
                        com.example.model.OfflineCacheManager.saveMyProductsCache(context, loadedProducts)
                        com.example.model.OfflineCacheManager.saveExpiringItemsCache(context, loadedExpiring)
                    }

                    try {
                        com.example.notification.NotificationHelper.evaluateAndNotify(context, loadedProducts)
                    } catch (e: Exception) {
                        // Fail-safe
                    }
                } else {
                    // Fail fallback to cache
                    val cachedProducts = com.example.model.OfflineCacheManager.loadMyProductsCache(context)
                    val cachedExpiring = com.example.model.OfflineCacheManager.loadExpiringItemsCache(context)

                    if (cachedProducts.isNotEmpty() || cachedExpiring.isNotEmpty()) {
                        products = if (selectedLocation != null) {
                            cachedProducts.filter { it.location == selectedLocation }
                        } else {
                            cachedProducts
                        }

                        expiringItems = if (selectedLocation != null) {
                            cachedExpiring.filter { it.location == selectedLocation }
                        } else {
                            cachedExpiring
                        }

                        isOfflineMode = true
                        
                        try {
                            com.example.notification.NotificationHelper.notifyOfflineMode(context)
                        } catch (e: Exception) {}
                    } else {
                        errorMessage = "تعذّر الاتصال بالشبكة وجلب البيانات، ولا توجد نسخة مخبأة محلياً بعد."
                    }
                }
            } catch (e: Exception) {
                val cachedProducts = com.example.model.OfflineCacheManager.loadMyProductsCache(context)
                val cachedExpiring = com.example.model.OfflineCacheManager.loadExpiringItemsCache(context)

                if (cachedProducts.isNotEmpty() || cachedExpiring.isNotEmpty()) {
                    products = if (selectedLocation != null) {
                        cachedProducts.filter { it.location == selectedLocation }
                    } else {
                        cachedProducts
                    }

                    expiringItems = if (selectedLocation != null) {
                        cachedExpiring.filter { it.location == selectedLocation }
                    } else {
                        cachedExpiring
                    }

                    isOfflineMode = true

                    try {
                        com.example.notification.NotificationHelper.notifyOfflineMode(context)
                    } catch (ex: Exception) {}
                } else {
                    errorMessage = "خطأ في الاتصال بالشبكة وجلب البيانات"
                }
            } finally {
                loading = false
                expiringItemsLoading = false
            }
        }
    }

    LaunchedEffect(selectedLocation) {
        loadData()
    }

    // Statistics metrics based on current loaded products
    val getDays: (Product) -> Int = { prod ->
        prod.daysRemaining ?: com.example.api.BarcodeLookup.calculateDaysRemaining(prod.expiryDate) ?: 999
    }
    val totalCount = products.size
    val count10 = products.count { getDays(it) in 0..10 }
    val count20 = products.count { getDays(it) in 11..20 }
    val count30 = products.count { getDays(it) in 21..30 }
    val safeCount = products.count { getDays(it) > 30 }
    val expiredCount = products.count { getDays(it) < 0 }
    val spoiledCount = products.count {
        it.location == "قسم التوالف" || 
        it.status == "تم إدخالها إلى قسم التوالف" || 
        it.status == "في حالة المراجعة / تم الرفع" || 
        it.status?.contains("توالف") == true
    }

    var activeTab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Welcome Header Banner & Fresh refresh trigger
        val user = ApiClient.getStoredUser(context)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "مرحباً، ${user.username}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "دورك في النظام: ${if (user.role == "agent") "مندوب الحساب" else user.role}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }

            IconButton(
                onClick = { loadData() },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "تحديث البيانات",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Offline mode indicator banner if offline
        if (isOfflineMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("offline_mode_banner"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "دون اتصال",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "وضع التشغيل دون اتصال (أوفلاين)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "البيانات المعروضة مخزنة محلياً. ستصلك التنبيهات للأصناف منتهية الصلاحية حتى في هذا الوضع.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Modern Tab Row to separate "الرئيسية والتقارير" / "مستودع المخازن والمنتجات"
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "", modifier = Modifier.size(18.dp))
                        Text("الرئيسية والتقارير", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Inventory, contentDescription = "", modifier = Modifier.size(18.dp))
                        Text("مستودع المخازن", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }
            )
        }

        // Dynamic content based on activeTab
        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("dashboard_root"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (loading) {
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            ) {
                                SkeletonCard(height = 120.dp)
                                SkeletonCard(height = 90.dp)
                                SkeletonCard(height = 200.dp)
                            }
                        }
                    } else if (errorMessage != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = errorMessage ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { loadData() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("إعادة المحاولة", color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        // Real-Time Dynamic Date & Time Widget Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("real_time_clock_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarMonth,
                                                contentDescription = "التاريخ اليوم",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "تاريخ اليوم الساري",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = currentDateString.ifEmpty { "جاري تحميل التاريخ..." },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = "الوقت الساري",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = currentTimeString.ifEmpty { "--:--:--" },
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Analytics counts grid
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    StatCard(
                                        title = "المنتجات الكلية",
                                        value = totalCount.toString(),
                                        color = MaterialTheme.colorScheme.primary,
                                        icon = Icons.Default.Inventory,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        title = "قسم التوالف / المراجعة",
                                        value = spoiledCount.toString(),
                                        color = Color(0xFF94A3B8),
                                        icon = Icons.Default.DeleteSweep,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    StatCard(
                                        title = "منتهي الصلاحية 🔴",
                                        value = expiredCount.toString(),
                                        color = Color(0xFFEF4444),
                                        icon = Icons.Default.Cancel,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        title = "خلال 10 أيام 🔴",
                                        value = count10.toString(),
                                        color = Color(0xFFEF4444),
                                        icon = Icons.Default.Warning,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    StatCard(
                                        title = "خلال 20 يوماً 🟡",
                                        value = count20.toString(),
                                        color = Color(0xFFF59E0B),
                                        icon = Icons.Default.NotificationsActive,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        title = "خلال 30 يوماً 🔵",
                                        value = count30.toString(),
                                        color = Color(0xFF3B82F6),
                                        icon = Icons.Default.Info,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    StatCard(
                                        title = "سليمة (أبعد من 30) 🟢",
                                        value = safeCount.toString(),
                                        color = Color(0xFF22C55E),
                                        icon = Icons.Default.CheckCircle,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Quick Operations shortcuts Row
                        item {
                            Text(
                                text = "الوصول السريع",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ActionShortcut(
                                    title = "إضافة منتج",
                                    icon = Icons.Default.Add,
                                    backgroundColor = MaterialTheme.colorScheme.primary,
                                    iconColor = MaterialTheme.colorScheme.onPrimary,
                                    onClick = onNavigateToAddProduct,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionShortcut(
                                    title = "مسح باركود",
                                    icon = Icons.Default.QrCodeScanner,
                                    backgroundColor = Color(0xFF3B82F6),
                                    iconColor = Color.White,
                                    onClick = onNavigateToScanner,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionShortcut(
                                    title = "رفع Excel",
                                    icon = Icons.Default.UploadFile,
                                    backgroundColor = Color(0xFF10B981),
                                    iconColor = Color.White,
                                    onClick = onNavigateToBatchUpload,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 1. New Section: Expiring Items Near Date (الأصناف المقربة من الانتهاء)
                        if (expiringItems.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ReportProblem,
                                            contentDescription = "المنتجات قريبة الانتهاء",
                                            tint = Color(0xFFEF4444)
                                        )
                                        Text(
                                            text = "أصناف مقربة من الانتهاء (30 يوم) ⚠️",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                    IconButton(
                                        onClick = { sharePlatformItemsCsv(context, expiringItems) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "تصدير أصناف قريبة الانتهاء",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            items(expiringItems) { platformItem ->
                                ExpiringItemCard(platformItem)
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "", tint = Color(0xFF22C55E))
                                        Text(
                                            text = "لا تتوفر أصناف منتهية أو قاربت على الانتهاء حالياً 🎉",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (loading) {
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            ) {
                                SkeletonCard(height = 90.dp)
                                SkeletonCard(height = 200.dp)
                            }
                        }
                    } else if (errorMessage != null) {
                        item {
                            Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        // Branch Store / Location Filter chips (Horizontal Scroll bar)
                        if (locations.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "تصفية حسب الفرع / المخزن:",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedLocation == null,
                                            onClick = { selectedLocation = null },
                                            label = { Text("الجميع", fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )

                                        locations.forEach { loc ->
                                            FilterChip(
                                                selected = selectedLocation == loc,
                                                onClick = { selectedLocation = loc },
                                                label = { Text(loc, fontWeight = FontWeight.Bold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Recent Products list header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "كافة منتجات الفرع / المخزن المحدد:",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { shareProductsCsv(context, products) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "تصدير كافة المنتجات",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (products.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HourglassEmpty,
                                            contentDescription = "",
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "لا توجد منتجات مسجلة في الفرع المحدد حالياً.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(products) { item ->
                                ProductRowCard(product = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(84.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ActionShortcut(
    title: String,
    icon: ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(backgroundColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExpiringItemCard(item: PlatformItem) {
    val remaining = item.daysRemaining ?: 0
    val textStatus = item.colorStatus ?: "danger"
    val badgeColor = when (textStatus.lowercase()) {
        "danger" -> Color(0xFFEF4444)
        "warning" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الصلاحية: ${item.expiryDate ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (!item.location.isNullOrEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = item.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (remaining < 0) "منتهي صلاحية" else "متبقي $remaining يوم",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
            }
        }
    }
}

@Composable
fun ProductRowCard(product: Product) {
    val remaining = product.daysRemaining ?: com.example.api.BarcodeLookup.calculateDaysRemaining(product.expiryDate) ?: 0
    val statusText = product.status?.ifEmpty { null } ?: when {
        remaining < 0 -> "منتهي صلاحية"
        remaining < 15 -> "عاجل"
        remaining < 25 -> "تحذير"
        else -> "سليم"
    }
    val badgeColor = when {
        remaining < 0 -> Color(0xFFEF4444)
        remaining < 15 -> Color(0xFFEF4444)
        remaining < 25 -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.itemName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                if (!product.barcode.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "باركود: ${product.barcode}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "التاريخ: ${product.expiryDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (!product.location.isNullOrEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = product.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (remaining < 0) "منتهي صلاحية" else "متبقي $remaining يوم",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "الحالة: $statusText",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun SkeletonCard(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    )
}

private fun shareProductsCsv(context: Context, products: List<Product>) {
    if (products.isEmpty()) {
        Toast.makeText(context, "قائمة المنتجات فارغة", Toast.LENGTH_SHORT).show()
        return
    }
    // Sort by daysRemaining ascending so closest to expiring is at the top!
    val sortedProducts = products.sortedBy { it.daysRemaining ?: Int.MAX_VALUE }

    val csvHeader = "اسم الصنف,تاريخ الانتهاء,الباركود,الموقع/المخزن,الأيام المتبقية,الكمية,الحالة,ملاحظات\n"
    val csvBody = StringBuilder()
    for (p in sortedProducts) {
        val name = p.itemName.replace(",", " ")
        val expiry = p.expiryDate
        val barcode = p.barcode ?: ""
        val loc = (p.location ?: "").replace(",", " ")
        val days = p.daysRemaining ?: ""
        val qty = p.quantity ?: 1
        val status = p.status ?: ""
        val notes = (p.notes ?: "").replace(",", " ").replace("\n", " ")
        csvBody.append("$name,$expiry,$barcode,$loc,$days,$qty,$status,$notes\n")
    }
    
    val fullCsv = csvHeader + csvBody.toString()
    
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, fullCsv)
            type = "text/csv"
        }
        val chooseIntent = Intent.createChooser(sendIntent, "تصدير تقرير كافة المنتجات")
        context.startActivity(chooseIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل تصدير محتويات الملف", Toast.LENGTH_SHORT).show()
    }
}

private fun sharePlatformItemsCsv(context: Context, items: List<PlatformItem>) {
    if (items.isEmpty()) {
        Toast.makeText(context, "قائمة الأصناف قاربت على الانتهاء فارغة", Toast.LENGTH_SHORT).show()
        return
    }
    // Sort by daysRemaining ascending so closest to expiring is at the top!
    val sortedItems = items.sortedBy { it.daysRemaining ?: Int.MAX_VALUE }

    val csvHeader = "اسم الصنف,تاريخ الانتهاء,الباركود,الموقع/المخزن,الأيام المتبقية,الكمية,الحالة\n"
    val csvBody = StringBuilder()
    for (item in sortedItems) {
        val name = item.itemName.replace(",", " ")
        val expiry = item.expiryDate ?: ""
        val barcode = item.barcode ?: ""
        val loc = (item.location ?: "").replace(",", " ")
        val days = item.daysRemaining ?: ""
        val qty = item.quantity ?: ""
        val status = item.status ?: ""
        csvBody.append("$name,$expiry,$barcode,$loc,$days,$qty,$status\n")
    }
    
    val fullCsv = csvHeader + csvBody.toString()
    
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, fullCsv)
            type = "text/csv"
        }
        val chooseIntent = Intent.createChooser(sendIntent, "تصدير الأصناف قاربت على الانتهاء")
        context.startActivity(chooseIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل تصدير محتويات الملف", Toast.LENGTH_SHORT).show()
    }
}
