package com.example.ui.pages

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import com.example.model.HistoryManager
import com.example.model.PlatformItem
import com.example.model.Product
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.ui.text.style.TextAlign
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddProductPage(
    scannedBarcode: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Form inputs state
    var itemName by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf(scannedBarcode ?: "") }
    var expiryDate by remember { mutableStateOf("") }
    var locationSelected by remember { mutableStateOf("") }
    var statusSelected by remember { mutableStateOf("متاح") }
    var quantityStr by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }

    // Offline suggest dialog and helper
    var showOfflineSuggestDialog by remember { mutableStateOf(false) }
    var pendingOfflineProduct by remember { mutableStateOf<com.example.model.Product?>(null) }
    var showCameraScannerDialog by remember { mutableStateOf(false) }

    fun saveProductLocally(product: com.example.model.Product) {
        scope.launch {
            try {
                val db = com.example.model.OfflineDatabase.getDatabase(context)
                
                // Save to legacy offline products queue
                val offlineProd = com.example.model.OfflineProduct(
                    itemName = product.itemName,
                    barcode = product.barcode,
                    expiryDate = product.expiryDate,
                    location = product.location,
                    status = product.status,
                    quantity = product.quantity,
                    notes = product.notes
                )
                db.offlineProductDao().insertOfflineProduct(offlineProd)
                
                // Save to new local products catalog (offline-first state)
                val localProd = com.example.model.LocalProduct(
                    id = "local_" + java.util.UUID.randomUUID().toString(),
                    name = product.itemName,
                    barcode = product.barcode,
                    expiryDate = product.expiryDate,
                    location = product.location,
                    status = product.status,
                    stockQuantity = product.quantity ?: 1,
                    notes = product.notes,
                    isSynced = false
                )
                db.localProductDao().insertProduct(localProd)

                // Add to history
                com.example.model.HistoryManager.addHistory(
                    context, 
                    product, 
                    success = true, 
                    errorMsg = "محفوظ محلياً (سيتم رفعه تلقائياً)"
                )
                Toast.makeText(context, "تم حفظ المنتج '${product.itemName}' محلياً وسيرفع تلقائياً فور توفر الإنترنت!", Toast.LENGTH_LONG).show()

                // Trigger background auto sync immediately
                com.example.model.AutoSyncManager.startAutoSync(context)

                // Clear whole form
                itemName = ""
                barcode = ""
                expiryDate = ""
                notes = ""
                quantityStr = "1"
                locationSelected = ""
                statusSelected = "متاح"
            } catch (e: Exception) {
                Toast.makeText(context, "فشل الحفظ دون اتصال: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Dynamic API fields state
    var locations by remember { mutableStateOf<List<String>>(emptyList()) }
    var locationsLoading by remember { mutableStateOf(false) }
    var locationsError by remember { mutableStateOf(false) }

    // Suggestions state
    var itemSuggestions by remember { mutableStateOf<List<PlatformItem>>(emptyList()) }
    var suggestionLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isSuggestionFocused by remember { mutableStateOf(false) }

    var submitLoading by remember { mutableStateOf(false) }

    // Dropdown list states
    val statusOptions = listOf("متاح", "غير متاح", "محجوز")
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    var locationDropdownExpanded by remember { mutableStateOf(false) }

    // Date computation helper
    val daysRemaining = remember(expiryDate) {
        if (expiryDate.isEmpty()) return@remember null
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val expiry = format.parse(expiryDate) ?: return@remember null
            val today = format.parse(format.format(Date())) ?: return@remember null
            val diffMs = expiry.time - today.time
            (diffMs / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            null
        }
    }

    // Load locations
    val loadLocations = {
        locationsLoading = true
        locationsError = false
        scope.launch {
            try {
                val data = ApiClient.fetchLocations(context)
                if (data != null) {
                    locations = data
                    com.example.model.OfflineCacheManager.saveLocationsCache(context, data)
                } else {
                    // fall back to local locations cache if offline
                    val cachedLocs = com.example.model.OfflineCacheManager.loadLocationsCache(context)
                    if (cachedLocs.isNotEmpty()) {
                        locations = cachedLocs
                    } else {
                        locationsError = true
                    }
                }
            } catch (e: Exception) {
                locationsError = true
            } finally {
                locationsLoading = false
            }
        }
    }

    var isIdentifyingBarcode by remember { mutableStateOf(false) }

    val identifyBarcode = { codeToIdentify: String ->
        if (codeToIdentify.isNotBlank() && !isIdentifyingBarcode) {
            isIdentifyingBarcode = true
            scope.launch {
                try {
                    val identifiedName = com.example.api.BarcodeLookup.lookupBarcode(context, codeToIdentify)
                    if (!identifiedName.isNullOrEmpty()) {
                        itemName = identifiedName
                        Toast.makeText(context, "تم التعرف على المنتج: $identifiedName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "لم يُعثر على اسم المنتج تلقائياً لهذا الباركود، يرجى إدخاله يدوياً", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "فشل أثناء التعرف على الباركود", Toast.LENGTH_SHORT).show()
                } finally {
                    isIdentifyingBarcode = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLocations()
        if (!barcode.isNullOrEmpty()) {
            identifyBarcode(barcode)
        }
    }

    // Debounced search for item suggestions
    val onItemNameChange = { query: String ->
        itemName = query
        isSuggestionFocused = true
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = scope.launch {
                delay(300) // Debounce 300ms
                suggestionLoading = true
                try {
                    val matchingItems = ApiClient.fetchItems(context, query)
                    itemSuggestions = matchingItems
                } catch (e: Exception) {
                    itemSuggestions = emptyList()
                } finally {
                    suggestionLoading = false
                }
            }
        } else {
            itemSuggestions = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_product_root")
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "إضافة منتج منتظر",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        // Visual warning if expiring soon
        val warningMessage = remember(expiryDate) {
            com.example.api.BarcodeLookup.checkScannedProductExpiry(expiryDate)
        }

        if (warningMessage != null) {
            val warningColor = Color(android.graphics.Color.parseColor("#" + warningMessage.colorHex))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expiry_warning_alert"),
                colors = CardDefaults.cardColors(containerColor = warningColor.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, warningColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(warningColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (warningMessage.warningType == "EXPIRED") Icons.Default.ReportProblem else Icons.Default.Info,
                            contentDescription = "تحذير انتهاء الصلاحية",
                            tint = warningColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = warningMessage.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = warningColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = warningMessage.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = warningColor.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Item Name (With Suggestions)
                Column {
                    Text(
                        text = "اسم الصنف *",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = onItemNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_name_input"),
                        placeholder = { Text("مثال: عصير برتقال 1 لتر") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Autocomplete Suggestions popup
                    if (isSuggestionFocused && itemSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(itemSuggestions) { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                itemName = suggestion.itemName
                                                if (!suggestion.barcode.isNullOrEmpty()) {
                                                    barcode = suggestion.barcode
                                                }
                                                if (!suggestion.location.isNullOrEmpty()) {
                                                    locationSelected = suggestion.location
                                                }
                                                isSuggestionFocused = false
                                            }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = suggestion.itemName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!suggestion.barcode.isNullOrEmpty()) {
                                            Text(
                                                text = suggestion.barcode,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }

                // Barcode Field
                Column {
                    Text(
                        text = "الباركود",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = barcode ?: "",
                            onValueChange = { barcode = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("barcode_input"),
                            placeholder = { Text("مسح أو إدخال يدوي للباركود") },
                            singleLine = true,
                            trailingIcon = {
                                if (!barcode.isNullOrBlank()) {
                                    if (isIdentifyingBarcode) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { identifyBarcode(barcode) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "التعرف على الصنف",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Camera Scan Button Next to TextField
                        IconButton(
                            onClick = { showCameraScannerDialog = true },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "مسح بالكاميرا"
                            )
                        }
                    }
                    if (!barcode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "انقر على رمز البحث للتعرف التلقائي على اسم هذا المنتج عبر قاعدة البيانات والذكاء الاصطناعي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                // Expiry Date (Date dialog)
                Column {
                    Text(
                        text = "تاريخ الانتهاء *",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expiry_date_input")
                            .clickable {
                                val c = Calendar.getInstance()
                                val dialog = DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val m = if (month + 1 < 10) "0${month + 1}" else "${month + 1}"
                                        val d = if (dayOfMonth < 10) "0$dayOfMonth" else "$dayOfMonth"
                                        expiryDate = "$year-$m-$d"
                                    },
                                    c.get(Calendar.YEAR),
                                    c.get(Calendar.MONTH),
                                    c.get(Calendar.DAY_OF_MONTH)
                                )
                                dialog.show()
                            },
                        placeholder = { Text("YYYY-MM-DD") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "اختر التاريخ",
                                modifier = Modifier.clickable {
                                    val c = Calendar.getInstance()
                                    val dialog = DatePickerDialog(
                                        context,
                                        { _, year, month, dOfMonth ->
                                            val m = if (month + 1 < 10) "0${month + 1}" else "${month + 1}"
                                            val d = if (dOfMonth < 10) "0$dOfMonth" else "$dOfMonth"
                                            expiryDate = "$year-$m-$d"
                                        },
                                        c.get(Calendar.YEAR),
                                        c.get(Calendar.MONTH),
                                        c.get(Calendar.DAY_OF_MONTH)
                                    )
                                    dialog.show()
                                }
                            )
                        },
                        readOnly = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Real-time calculated days remaining indicator
                    if (daysRemaining != null) {
                        val badgeColor = when {
                            daysRemaining < 0 -> Color(0xFFEF4444)
                            daysRemaining < 15 -> Color(0xFFEF4444)
                            daysRemaining < 25 -> Color(0xFFF59E0B)
                            else -> Color(0xFF22C55E)
                        }
                        val label = when {
                            daysRemaining < 0 -> "منتهي الصلاحية 🔴"
                            daysRemaining < 15 -> "عاجل جداً 🔴"
                            daysRemaining < 25 -> "تحذير انتهاء 🟡"
                            else -> "حالة سليمة ومتاحة 🟢"
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(badgeColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "",
                                tint = badgeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "متبقي $daysRemaining يوم ($label)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor
                            )
                        }
                    }
                }

                // Locations Dropdown
                Column {
                    Text(
                        text = "الموقع/المخزن",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (locationsLoading) {
                        Text(
                            text = "جاري جلب المخازن...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    } else if (locationsError || locations.isEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا توجد مخازن مسجلة في المنصة",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEF4444)
                            )
                            IconButton(onClick = { loadLocations() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تحديث",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = locationDropdownExpanded,
                            onExpandedChange = { locationDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = locationSelected,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("location_dropdown"),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationDropdownExpanded) },
                                placeholder = { Text("اختر المخزن") },
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = locationDropdownExpanded,
                                onDismissRequest = { locationDropdownExpanded = false }
                            ) {
                                locations.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            locationSelected = item
                                            locationDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Status Dynamic select
                Column {
                    Text(
                        text = "حالة المنتج",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = statusDropdownExpanded,
                        onExpandedChange = { statusDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = statusSelected,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("status_dropdown"),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusDropdownExpanded) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = statusDropdownExpanded,
                            onDismissRequest = { statusDropdownExpanded = false }
                        ) {
                            statusOptions.forEach { statusOption ->
                                DropdownMenuItem(
                                    text = { Text(statusOption) },
                                    onClick = {
                                        statusSelected = statusOption
                                        statusDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Quantity and Notes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الكمية",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quantity_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "ملاحظات",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("notes_input"),
                        placeholder = { Text("أدخل أي تفاصيل إضافية هنا...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Upload button
                Button(
                    onClick = {
                        if (itemName.trim().isEmpty() || expiryDate.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء تعبئة الحقول الإجبارية (*) أولاً", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val quantity = quantityStr.toIntOrNull() ?: 1
                        val prod = Product(
                            itemName = itemName.trim(),
                            barcode = barcode?.trim()?.ifEmpty { null },
                            expiryDate = expiryDate,
                            location = locationSelected.ifEmpty { null },
                            status = statusSelected,
                            quantity = quantity,
                            notes = notes.trim().ifEmpty { null }
                        )

                        // Save locally FIRST (instantly) to guarantee speed and 100% offline-readiness
                        saveProductLocally(prod)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("upload_product_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !submitLoading
                ) {
                    if (submitLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "رفع المنتج للمنصة",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        if (itemName.trim().isEmpty() || expiryDate.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء تعبئة الحقول الإجبارية (*) أولاً", Toast.LENGTH_LONG).show()
                            return@OutlinedButton
                        }
                        val quantity = quantityStr.toIntOrNull() ?: 1
                        val prod = Product(
                            itemName = itemName.trim(),
                            barcode = barcode?.trim()?.ifEmpty { null },
                            expiryDate = expiryDate,
                            location = locationSelected.ifEmpty { null },
                            status = statusSelected,
                            quantity = quantity,
                            notes = notes.trim().ifEmpty { null }
                        )
                        saveProductLocally(prod)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_offline_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !submitLoading
                ) {
                    Icon(imageVector = Icons.Default.CloudOff, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حفظ بدون إنترنت (محلياً)",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    if (showOfflineSuggestDialog && pendingOfflineProduct != null) {
        AlertDialog(
            onDismissRequest = { showOfflineSuggestDialog = false },
            title = { Text("تعذّر الاتصال بالخادم") },
            text = { Text("لم نتمكن من رفع الصنف للمنصة حالياً. هل ترغب بحفظه محلياً في جهازك للرفع اليدوي لاحقاً؟") },
            confirmButton = {
                Button(
                    onClick = {
                        saveProductLocally(pendingOfflineProduct!!)
                        showOfflineSuggestDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("نعم، احفظ محلياً", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineSuggestDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showCameraScannerDialog) {
        AlertDialog(
            onDismissRequest = { showCameraScannerDialog = false },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                    Text("مسح الباركود بالكاميرا", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "وجه الكاميرا نحو الباركود ليتم مسحه وإدخاله مباشرة.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cameraPermissionState.status.isGranted) {
                            CameraLiveView(
                                onBarcodeFound = { scannedCode ->
                                    barcode = scannedCode
                                    showCameraScannerDialog = false
                                    // Trigger feedback
                                    try {
                                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                        vibrator.vibrate(120)
                                    } catch (e: Exception) {}
                                    // Identify barcode
                                    identifyBarcode(scannedCode)
                                }
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "", tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                Text("صلاحية الكاميرا مطلوبة", color = Color.White)
                                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                                    Text("منح الصلاحية")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCameraScannerDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}
