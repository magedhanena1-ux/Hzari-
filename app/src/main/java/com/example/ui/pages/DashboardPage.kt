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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import com.example.model.Product
import com.example.model.PlatformItem
import com.example.model.LocalProduct
import com.example.model.OfflineDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    // Room database observation
    val db = remember { OfflineDatabase.getDatabase(context) }
    val localProductDao = remember { db.localProductDao() }
    val localProductsCached by localProductDao.getAllLocalProducts().collectAsState(initial = emptyList())

    // Filtered Products for mainstream view (Only active non-archived products!)
    val dbProductsMapped = remember(localProductsCached) {
        localProductsCached.filter { it.isDamaged == 0 }.map { lp ->
            Product(
                id = lp.id,
                itemName = lp.name,
                barcode = lp.barcode,
                expiryDate = lp.expiryDate,
                location = lp.location,
                status = lp.status,
                quantity = lp.stockQuantity,
                notes = lp.notes,
                daysRemaining = lp.daysRemaining ?: com.example.api.BarcodeLookup.calculateDaysRemaining(lp.expiryDate),
                colorStatus = lp.colorStatus ?: when (com.example.api.BarcodeLookup.calculateDaysRemaining(lp.expiryDate) ?: 999) {
                    in Int.MIN_VALUE..10 -> "red"
                    in 11..30 -> "yellow"
                    else -> "green"
                },
                createdAt = null,
                imagePath = lp.imagePath
            )
        }
    }

    // Archived Damaged Products (Only damaged products)
    val damagedProducts = remember(localProductsCached) {
        localProductsCached.filter { it.isDamaged == 1 }
    }

    // Auto-trigger alerts and evaluate notifications
    LaunchedEffect(dbProductsMapped) {
        if (dbProductsMapped.isNotEmpty()) {
            com.example.notification.NotificationHelper.evaluateAndNotify(context, dbProductsMapped)
        }
    }

    var selectedLocation by remember { mutableStateOf<String?>(null) } // null means "All locations"
    var expiringItems by remember { mutableStateOf<List<PlatformItem>>(emptyList()) }
    
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isOfflineMode by remember { mutableStateOf(false) }

    // Map departments/locations dynamically derived from active products list
    val locations = remember(dbProductsMapped) {
        dbProductsMapped.mapNotNull { it.location }.distinct()
    }

    // Warehouse state lists persisted locally via SharedPreferences
    val listCustomWarehouses = remember { mutableStateListOf<String>() }
    var selectedWarehouseFilter by remember { mutableStateOf<String?>("الجميع") }

    fun refreshWarehouses() {
        val list = getCustomWarehouses(context)
        listCustomWarehouses.clear()
        listCustomWarehouses.addAll(list)
    }

    LaunchedEffect(Unit) {
        refreshWarehouses()
    }

    val productsFiltered = remember(dbProductsMapped, selectedLocation, selectedWarehouseFilter) {
        var list = dbProductsMapped
        if (selectedLocation != null) {
            list = list.filter { it.location == selectedLocation }
        }
        if (selectedWarehouseFilter != "الجميع" && selectedWarehouseFilter != null) {
            list = list.filter {
                val name = it.notes?.let { n -> extractWarehouse(n) } ?: "مخزن غير محدد"
                it.notes?.contains("المستودع:") == true && name.contains(selectedWarehouseFilter!!)
            }
        }
        list
    }

    val loadData = {
        loading = true
        errorMessage = null
        isOfflineMode = !com.example.model.AutoSyncManager.isNetworkAvailable(context)
        scope.launch {
            try {
                // Proactively auto-sync pending offline products and pull platform data if online
                com.example.model.AutoSyncManager.startAutoSync(context)

                // Call location products endpoint (handled gracefully if offline)
                if (!isOfflineMode) {
                    val loadedExpiring = ApiClient.fetchExpiringItems(context, location = selectedLocation, days = 30)
                    if (loadedExpiring != null) {
                        expiringItems = loadedExpiring
                        com.example.model.OfflineCacheManager.saveExpiringItemsCache(context, loadedExpiring)
                    }
                } else {
                    val cachedExpiring = com.example.model.OfflineCacheManager.loadExpiringItemsCache(context)
                    expiringItems = if (selectedLocation != null) {
                        cachedExpiring.filter { it.location == selectedLocation }
                    } else {
                        cachedExpiring
                    }
                }
            } catch (e: Exception) {
                isOfflineMode = true
                val cachedExpiring = com.example.model.OfflineCacheManager.loadExpiringItemsCache(context)
                expiringItems = if (selectedLocation != null) {
                    cachedExpiring.filter { it.location == selectedLocation }
                } else {
                    cachedExpiring
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedLocation) {
        loadData()
    }

    // Unified dynamic local system alerting (nearing expiration <=30 days, or low stock <=5 units)
    val localMergableAlertRecords = remember(dbProductsMapped) {
        dbProductsMapped.filter { prod ->
            val days = prod.daysRemaining ?: com.example.api.BarcodeLookup.calculateDaysRemaining(prod.expiryDate) ?: 999
            days <= 30 || (prod.quantity ?: 1) <= 5
        }.sortedWith(
            compareBy<Product> { it.daysRemaining ?: 999 }
            .thenBy { it.quantity ?: 0 }
        )
    }

    // Statistics metrics based on current loaded products
    val getDays: (Product) -> Int = { prod ->
        prod.daysRemaining ?: com.example.api.BarcodeLookup.calculateDaysRemaining(prod.expiryDate) ?: 999
    }
    val totalCount = dbProductsMapped.size
    val count7 = dbProductsMapped.count { getDays(it) in 0..7 }
    val count10 = dbProductsMapped.count { getDays(it) in 0..10 }
    val count20 = dbProductsMapped.count { getDays(it) in 11..20 }
    val count30 = dbProductsMapped.count { getDays(it) in 21..30 }
    val safeCount = dbProductsMapped.count { getDays(it) > 30 }
    val expiredCount = dbProductsMapped.count { getDays(it) < 0 }
    val spoiledCount = damagedProducts.size

    var activeTab by remember { mutableStateOf(0) }

    // Dialogs States
    var show7DaysAlertProductsDialog by remember { mutableStateOf(false) }
    val productsExpiringIn7Days = remember(dbProductsMapped) {
        dbProductsMapped.filter { getDays(it) in 0..7 }
    }

    var showCreateWarehouseDialog by remember { mutableStateOf(false) }
    var showRenameWarehouseDialog by remember { mutableStateOf(false) }
    var newWarehouseNameInput by remember { mutableStateOf("") }
    var renameWarehouseOldName by remember { mutableStateOf("") }
    var renameWarehouseNewNameInput by remember { mutableStateOf("") }

    var productPendingMove by remember { mutableStateOf<Product?>(null) }
    var productPendingDamagedArchive by remember { mutableStateOf<Product?>(null) }

    var damageQuantityInput by remember { mutableStateOf("") }
    var damageNotesInput by remember { mutableStateOf("") }

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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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

        // Expanded M3 Tab Row with three cohesive sections (Main, Warehouses, Damaged)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "", modifier = Modifier.size(16.dp))
                        Text("الرئيسية والتقارير", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Store, contentDescription = "", modifier = Modifier.size(16.dp))
                        Text("المخازن والمستودعات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "", modifier = Modifier.size(16.dp))
                        Text("سجل التوالف ($spoiledCount)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }

        // Dynamic content based on activeTab
        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("dashboard_root"),
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
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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

                        // 7 Days Expiry Notification Banner
                        if (count7 > 0) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("expiry_7day_notification_card")
                                        .clickable { show7DaysAlertProductsDialog = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = "جرس تنبيه",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "تنبيه انتهاء الصلاحية خلال 7 أيام! 🚨",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "يوجد عدد $count7 من المنتجات شارفت صلاحيتها على الانتهاء خلال أسبوع أو أقل. اضغط هنا لعرض المنتجات واتخاذ الإجراء.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ArrowBack, // Standard arrow back pointing to the left in RTL Arabic context
                                            contentDescription = "عرض التفاصيل",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
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
                                        title = "إجمالي التوالف",
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
                                        title = "حد الخطر (10 أيام)",
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

                        // Local System Unified Merged Report Block (High Risk or Expiration Underway)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "قائمة التحذيرات والرقابة",
                                        tint = Color(0xFFEF4444)
                                    )
                                    Text(
                                        text = "تقرير الأصناف قريبة الانتهاء وحد الخطر ⚠️",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFEF4444)
                                    )
                                }
                                IconButton(
                                    onClick = { shareProductsCsv(context, localMergableAlertRecords) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "تصدير التفرير بالكامل",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (localMergableAlertRecords.isNotEmpty()) {
                            items(localMergableAlertRecords) { rec ->
                                ProductRowCard(
                                    product = rec,
                                    onMoveWarehouse = { productPendingMove = it },
                                    onArchiveDamaged = { productPendingDamagedArchive = it }
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "", tint = Color(0xFF22C55E))
                                        Text(
                                            text = "جميع الأقسام والأصناف في حالة ممتازة وسليمة حالياً! 🎉",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeTab == 1) {
                // TAB 1: SMART WAREHOUSE MANAGEMENT WITH FULL LOCAL CRUD & CHIPS SELECTOR
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        // Warehouse controls bar
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "إدارة مستودعات المخازن",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(
                                    onClick = { showCreateWarehouseDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مستودع جديد", fontSize = 12.sp)
                                }
                            }

                            // Dynamic Selector Scrolling Chippy list
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedWarehouseFilter == "الجميع",
                                    onClick = { selectedWarehouseFilter = "الجميع" },
                                    label = { Text("الجميع (${dbProductsMapped.size})") }
                                )
                                FilterChip(
                                    selected = selectedWarehouseFilter == "مخزن غير محدد",
                                    onClick = { selectedWarehouseFilter = "مخزن غير محدد" },
                                    label = { Text("غير محدد (${dbProductsMapped.count { it.notes?.contains("المستودع:") != true }})") }
                                )
                                listCustomWarehouses.forEach { wh ->
                                    val count = dbProductsMapped.count { 
                                        it.notes?.contains("المستودع:") == true && extractWarehouse(it.notes).contains(wh)
                                    }
                                    FilterChip(
                                        selected = selectedWarehouseFilter == wh,
                                        onClick = { selectedWarehouseFilter = wh },
                                        label = { Text("$wh ($count)") }
                                    )
                                }
                            }

                            // Rename / Delete Actions if a custom warehouse is selected
                            if (selectedWarehouseFilter != "الجميع" && selectedWarehouseFilter != "مخزن غير محدد" && selectedWarehouseFilter != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            renameWarehouseOldName = selectedWarehouseFilter!!
                                            renameWarehouseNewNameInput = selectedWarehouseFilter!!
                                            showRenameWarehouseDialog = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تعديل الاسم", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val name = selectedWarehouseFilter!!
                                            deleteCustomWarehouse(context, name)
                                            selectedWarehouseFilter = "الجميع"
                                            refreshWarehouses()
                                            Toast.makeText(context, "تم حذف المستودع بنجاح", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إزالة المستودع", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Listed Items filtered under the active warehouse
                    if (productsFiltered.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Storefront, "", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("لا توجد منتجات مسجلة في هذا المستودع حالياً.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        items(productsFiltered) { prod ->
                            ProductRowCard(
                                product = prod,
                                onMoveWarehouse = { productPendingMove = it },
                                onArchiveDamaged = { productPendingDamagedArchive = it }
                            )
                        }
                    }
                }
            } else if (activeTab == 2) {
                // TAB 2: DETAILED ARCHIVED DAMAGED GOODS
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "", tint = MaterialTheme.colorScheme.error)
                                Column {
                                    Text("قسم وبنك التوالف التابعة للرقابة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    Text("تعتبر هذه السجلات معزولة كلياً من الجرد العام ومخزون التشغيل للمستودعات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (damagedProducts.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.DoneAll, "", modifier = Modifier.size(48.dp), tint = Color(0xFF22C55E))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("قائمة التوالف فارغة حالياً. لا توجد أي مخاسر مسجلة.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        items(damagedProducts) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = "تالف ومرحل", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(text = "الكمية التالفة: ${item.damagedQuantity} وحدات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                            Text(text = "تاريخ الترحيل والفرز: ${item.damagedDate ?: "-"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    localProductDao.deleteById(item.id)
                                                    Toast.makeText(context, "تم مسح سجل التالف نهائياً", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "مسح", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                        }
                                    }

                                    if (!item.notes.isNullOrEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(8.dp)
                                        ) {
                                            Text(text = item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // dialogs implementation

    // Expiration alerts modal (within 7 days)
    if (show7DaysAlertProductsDialog) {
        AlertDialog(
            onDismissRequest = { show7DaysAlertProductsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "منتجات تنتهي خلال 7 أيام! ⚠️",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "المنتجات التالية ستنتهي صلاحيتها خلال أسبوع أو أقل. نوصي بالإسراع في صرفها أو وضع خصومات عليها لمنع تلفها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (productsExpiringIn7Days.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد منتجات تنتهي صلاحيتها في الأيام السبعة القادمة! 🎉")
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 280.dp).fillMaxWidth()
                        ) {
                            items(productsExpiringIn7Days) { product ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Product Image
                                        if (!product.imagePath.isNullOrEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = java.io.File(product.imagePath),
                                                contentDescription = "",
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = "",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = product.itemName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = "الموقع: ${product.location ?: "غير محدد"}",
                                                    style = Modifier.alignByBaseline().let { null } ?: MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "الكمية: ${product.quantity ?: 0}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        val days = getDays(product)
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    days == 0 -> "اليوم!"
                                                    days == 1 -> "غداً"
                                                    else -> "$days أيام"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { show7DaysAlertProductsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حسناً، فهمت")
                }
            }
        )
    }

    // 1. Create Warehouse Dialog
    if (showCreateWarehouseDialog) {
        AlertDialog(
            onDismissRequest = { showCreateWarehouseDialog = false },
            title = { Text("إنشاء مستودع مخازن جديد") },
            text = {
                OutlinedTextField(
                    value = newWarehouseNameInput,
                    onValueChange = { newWarehouseNameInput = it },
                    label = { Text("اسم المستودع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWarehouseNameInput.isNotBlank()) {
                            addCustomWarehouse(context, newWarehouseNameInput.trim())
                            newWarehouseNameInput = ""
                            showCreateWarehouseDialog = false
                            refreshWarehouses()
                            Toast.makeText(context, "تمت إضافة المستودع بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateWarehouseDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 2. Rename Warehouse Dialog
    if (showRenameWarehouseDialog) {
        AlertDialog(
            onDismissRequest = { showRenameWarehouseDialog = false },
            title = { Text("تعديل اسم المستودع") },
            text = {
                OutlinedTextField(
                    value = renameWarehouseNewNameInput,
                    onValueChange = { renameWarehouseNewNameInput = it },
                    label = { Text("الاسم الجديد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameWarehouseNewNameInput.isNotBlank()) {
                            val newName = renameWarehouseNewNameInput.trim()
                            scope.launch {
                                localProductsCached.forEach { lp ->
                                    if (lp.notes?.contains("المستودع: $renameWarehouseOldName") == true) {
                                        val updatedNotes = lp.notes.replace("المستودع: $renameWarehouseOldName", "المستودع: $newName")
                                        localProductDao.insertProduct(lp.copy(notes = updatedNotes, isSynced = false))
                                    }
                                }
                                deleteCustomWarehouse(context, renameWarehouseOldName)
                                addCustomWarehouse(context, newName)
                                selectedWarehouseFilter = newName
                                showRenameWarehouseDialog = false
                                refreshWarehouses()
                                Toast.makeText(context, "تم تحديث اسم المستودع بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameWarehouseDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 3. Move Product warehouse dialog coordinator
    if (productPendingMove != null) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        var selectedWhMove by remember { mutableStateOf("المستودع الرئيسي") }
        AlertDialog(
            onDismissRequest = { productPendingMove = null },
            title = { Text("تغيير ونقل مستودع الصنف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "الصنف المحدد: ${productPendingMove!!.itemName}", fontWeight = FontWeight.Bold)
                    Text(text = "اختر المستودع الوجهة لنقل وتوطين هذا الصنف إليه:")
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = selectedWhMove)
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            listCustomWarehouses.forEach { wh ->
                                DropdownMenuItem(
                                    text = { Text(wh) },
                                    onClick = {
                                        selectedWhMove = wh
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prod = productPendingMove!!
                        scope.launch {
                            val lpOriginal = localProductsCached.find { it.id == prod.id }
                            if (lpOriginal != null) {
                                val currentNotes = lpOriginal.notes ?: ""
                                val cleanedNotes = if (currentNotes.contains("المستودع:")) {
                                    currentNotes.replace(Regex("المستودع:\\s*[^\\n]*"), "المستودع: $selectedWhMove")
                                } else {
                                    "$currentNotes\nالمستودع: $selectedWhMove".trim()
                                }
                                localProductDao.insertProduct(lpOriginal.copy(notes = cleanedNotes, isSynced = false))
                                Toast.makeText(context, "تم نقل الصنف إلى $selectedWhMove", Toast.LENGTH_SHORT).show()
                            }
                            productPendingMove = null
                        }
                    }
                ) {
                    Text("نقل وتسكين")
                }
            },
            dismissButton = {
                TextButton(onClick = { productPendingMove = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 4. Archive Product to Damaged Goods coordinates
    if (productPendingDamagedArchive != null) {
        val original = productPendingDamagedArchive!!
        val maxQty = original.quantity ?: 1
        AlertDialog(
            onDismissRequest = { productPendingDamagedArchive = null },
            title = { Text("ترحيل وإتلاف صنف للمراجعة 🔴") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "اسم الصنف: ${original.itemName}", fontWeight = FontWeight.Bold)
                    Text(text = "الكمية المتاحة حالياً بالجرد: $maxQty", style = MaterialTheme.typography.bodySmall)
                    
                    OutlinedTextField(
                        value = damageQuantityInput,
                        onValueChange = { damageQuantityInput = it },
                        label = { Text("الكمية التالفة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = damageNotesInput,
                        onValueChange = { damageNotesInput = it },
                        label = { Text("أسباب وتفاصيل الإتلاف") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val enteredQty = damageQuantityInput.toIntOrNull()
                        if (enteredQty == null || enteredQty <= 0 || enteredQty > maxQty) {
                            Toast.makeText(context, "يرجى إدخال كمية صالحة لا تزيد عن $maxQty", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                val lpOriginal = localProductsCached.find { it.id == original.id }
                                if (lpOriginal != null) {
                                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
                                    
                                    // 1. Create a isolated record with isDamaged = 1
                                    val damagedReceipt = LocalProduct(
                                        id = UUID.randomUUID().toString(),
                                        name = lpOriginal.name,
                                        barcode = lpOriginal.barcode,
                                        expiryDate = lpOriginal.expiryDate,
                                        location = lpOriginal.location,
                                        status = "تالف ومفرز",
                                        stockQuantity = 0,
                                        notes = "دواعي التلف: " + damageNotesInput.trim(),
                                        daysRemaining = lpOriginal.daysRemaining,
                                        colorStatus = "red",
                                        warehouseName = lpOriginal.warehouseName,
                                        isDamaged = 1,
                                        damagedDate = todayStr,
                                        damagedQuantity = enteredQty,
                                        isSynced = false
                                    )
                                    localProductDao.insertProduct(damagedReceipt)

                                    // 2. Reduce the original product active count
                                    val remain = lpOriginal.stockQuantity - enteredQty
                                    if (remain <= 0) {
                                        localProductDao.deleteById(lpOriginal.id)
                                    } else {
                                        localProductDao.insertProduct(lpOriginal.copy(stockQuantity = remain, isSynced = false))
                                    }

                                    // 3. Post to Supabase server in the background
                                    try {
                                        val prodToSend = Product(
                                            id = damagedReceipt.id,
                                            itemName = damagedReceipt.name,
                                            barcode = damagedReceipt.barcode,
                                            expiryDate = damagedReceipt.expiryDate,
                                            location = damagedReceipt.location,
                                            status = damagedReceipt.status,
                                            quantity = 0,
                                            notes = "DAMAGE_REPORT: count=${damagedReceipt.damagedQuantity}, cause=${damageNotesInput.trim()}"
                                        )
                                        ApiClient.uploadProduct(context, prodToSend)
                                    } catch (e: Exception) {
                                        // Ignore and let sync manager handle offline sync later
                                    }

                                    Toast.makeText(context, "تم تصفية وترحيل الكمية بنجاح للتوالف", Toast.LENGTH_SHORT).show()
                                }
                                productPendingDamagedArchive = null
                                damageQuantityInput = ""
                                damageNotesInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد الترحيل الأحمر")
                }
            },
            dismissButton = {
                TextButton(onClick = { productPendingDamagedArchive = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// Helper methods for parsing warehouse labels stored inside notes
private fun extractWarehouse(notes: String): String {
    val regex = Regex("المستودع:\\s*([^\\n]*)")
    val match = regex.find(notes)
    return match?.groupValues?.get(1)?.trim() ?: "مخزن غير محدد"
}

// Warehouse collection preferences manager
private fun getCustomWarehouses(context: Context): List<String> {
    val prefs = context.getSharedPreferences("hzari_warehouses_v2", Context.MODE_PRIVATE)
    val set = prefs.getStringSet("warehouses_set", setOf("المستودع الرئيسي", "مستودع الأدوية", "مستودع المبردات")) ?: emptySet()
    return set.toList().sorted()
}

private fun addCustomWarehouse(context: Context, name: String) {
    val prefs = context.getSharedPreferences("hzari_warehouses_v2", Context.MODE_PRIVATE)
    val set = prefs.getStringSet("warehouses_set", setOf("المستودع الرئيسي", "مستودع الأدوية", "مستودع المبردات"))?.toMutableSet() ?: mutableSetOf()
    set.add(name)
    prefs.edit().putStringSet("warehouses_set", set).apply()
}

private fun deleteCustomWarehouse(context: Context, name: String) {
    val prefs = context.getSharedPreferences("hzari_warehouses_v2", Context.MODE_PRIVATE)
    val set = prefs.getStringSet("warehouses_set", setOf("المستودع الرئيسي", "مستودع الأدوية", "مستودع المبردات"))?.toMutableSet() ?: mutableSetOf()
    set.remove(name)
    prefs.edit().putStringSet("warehouses_set", set).apply()
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
fun ProductRowCard(
    product: Product,
    onMoveWarehouse: (Product) -> Unit,
    onArchiveDamaged: (Product) -> Unit
) {
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

    var detailsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { detailsExpanded = !detailsExpanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!product.imagePath.isNullOrEmpty()) {
                    coil.compose.AsyncImage(
                        model = java.io.File(product.imagePath),
                        contentDescription = "صورة المنتج",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "لا توجد صورة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

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
                        val wh = product.notes?.let { extractWarehouse(it) } ?: "مخزن غير محدد"
                        Text(
                            text = "• $wh",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
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
                        text = "الكمية: ${product.quantity ?: 1} وحدات",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Expanded Actions panel (Move warehouse, Archive to damaged)
            if (detailsExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onMoveWarehouse(product) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.MoveToInbox, contentDescription = "Move", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نقل المستودع", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onArchiveDamaged(product) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Dangerous, contentDescription = "Damaged", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ترحيل للتوالف", fontSize = 12.sp)
                    }
                }
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
    val sortedProducts = products.sortedBy { it.daysRemaining ?: Int.MAX_VALUE }

    val csvHeader = "اسم الصنف,تاريخ الانتهاء,الباركود,المستودع,الأيام المتبقية,الكمية,الحالة,ملاحظات\n"
    val csvBody = StringBuilder()
    for (p in sortedProducts) {
        val name = p.itemName.replace(",", " ")
        val expiry = p.expiryDate
        val barcode = p.barcode ?: ""
        val wh = (p.notes?.let { extractWarehouse(it) } ?: "مخزن غير محدد").replace(",", " ")
        val days = p.daysRemaining ?: ""
        val qty = p.quantity ?: 1
        val status = p.status ?: ""
        val notes = (p.notes ?: "").replace(",", " ").replace("\n", " ")
        csvBody.append("$name,$expiry,$barcode,$wh,$days,$qty,$status,$notes\n")
    }
    
    val fullCsv = csvHeader + csvBody.toString()
    
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, fullCsv)
            type = "text/csv"
        }
        val chooseIntent = Intent.createChooser(sendIntent, "تصدير تقرير المخازن")
        context.startActivity(chooseIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل تصدير محتويات الملف", Toast.LENGTH_SHORT).show()
    }
}
