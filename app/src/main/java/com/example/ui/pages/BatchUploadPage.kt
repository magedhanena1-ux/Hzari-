package com.example.ui.pages

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import com.example.model.HistoryManager
import com.example.model.Product
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

data class ParsedRow(
    val product: Product,
    val isValid: Boolean,
    val errorMsg: String = "",
    var uploadStatus: String = "pending" // "pending", "uploading", "success", "failed"
)

@Composable
fun BatchUploadPage(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf<String?>(null) }
    var parsedRows by remember { mutableStateOf<List<ParsedRow>>(emptyList()) }
    var uploadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) } // Current, Total
    var isUploading by remember { mutableStateOf(false) }
    var summaryText by remember { mutableStateOf<String?>(null) }

    // Launcher for file picker (supports csv & txt)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = context.contentResolver
                // Extract file name
                var name = "ملف_مستورد.csv"
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
                fileName = name

                // Read and parse
                val inputStream = cr.openInputStream(uri)
                if (inputStream != null) {
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val fullContent = reader.readText()
                    inputStream.close()

                    val rows = parseCsvString(fullContent)
                    parsedRows = rows
                    summaryText = null
                    uploadProgress = null
                } else {
                    Toast.makeText(context, "فشل فتح الملف المختار", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "حدث خطأ أثناء فحص الملف: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("batch_upload_root")
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Explanatory Banner
        item {
            Text(
                text = "استيراد ورفع المنتجات جماعياً",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "يمكنك رفع ملف CSV يضم مئات المنتجات دفعة واحدة وسيقوم التطبيق برفعها للموقع تلقائياً.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // Upload Area Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isUploading) {
                            filePickerLauncher.launch("*/*")
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (fileName == null) Icons.Default.CloudUpload else Icons.Default.InsertDriveFile,
                        contentDescription = "",
                        modifier = Modifier.size(52.dp),
                        tint = if (fileName == null) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                    )

                    Text(
                        text = fileName ?: "اضغط هنا لاختيار ملف استيراد (.csv)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "يجب أن يحتوي الملف على الأعمدة التالية:\nitem_name*, expiry_date*, barcode, location, quantity, status, notes\n(تاريخ الانتهاء بتنسيق YYYY-MM-DD)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Action controls
        if (parsedRows.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val validCount = parsedRows.count { it.isValid }
                        val invalidCount = parsedRows.count { !it.isValid }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الملف يحتوي على: ${parsedRows.size} صف",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Badge(containerColor = Color(0xFF22C55E).copy(alpha = 0.15f)) {
                                    Text("صالح: $validCount", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                                }
                                if (invalidCount > 0) {
                                    Badge(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)) {
                                        Text("أخطاء: $invalidCount", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Progress state
                        if (uploadProgress != null) {
                            val current = uploadProgress!!.first
                            val total = uploadProgress!!.second
                            val ratio = if (total > 0) current.toFloat() / total.toFloat() else 0f
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF10B981),
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                text = "جاري الرفع: تم إكمال $current من أصل $total منتج...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (summaryText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = summaryText ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF10B981)
                            )
                        }

                        Button(
                            onClick = {
                                if (isUploading) return@Button
                                val validRows = parsedRows.filter { it.isValid }
                                if (validRows.isEmpty()) {
                                    Toast.makeText(context, "لا توجد أي صفوف صالحة للرفع في الملف!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                isUploading = true
                                summaryText = null
                                uploadProgress = Pair(0, validRows.size)

                                scope.launch {
                                    var successCount = 0
                                    var failCount = 0
                                    
                                    validRows.forEachIndexed { idx, row ->
                                        row.uploadStatus = "uploading"
                                        // Update state to trigger table item recomposition
                                        parsedRows = parsedRows.toList()

                                        val res = ApiClient.uploadProduct(context, row.product)
                                        if (res.success) {
                                            row.uploadStatus = "success"
                                            successCount++
                                            HistoryManager.addHistory(context, row.product, true)
                                        } else {
                                            row.uploadStatus = "failed"
                                            failCount++
                                            HistoryManager.addHistory(context, row.product, false, res.error)
                                        }

                                        uploadProgress = Pair(idx + 1, validRows.size)
                                        parsedRows = parsedRows.toList()
                                    }

                                    isUploading = false
                                    summaryText = "اكتمل الرفع: تم رفع $successCount بنجاح، وفشل $failCount منتج."
                                    Toast.makeText(context, summaryText, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("start_upload_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            enabled = !isUploading && parsedRows.any { it.isValid }
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("بدء رفع المنتجات الصالحة للمنصة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Preview Items Table Title
        if (parsedRows.isNotEmpty()) {
            item {
                Text(
                    text = "معاينة بيانات الملف",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(parsedRows) { row ->
                PreviewRowCard(row = row)
            }
        }
    }
}

@Composable
fun PreviewRowCard(row: ParsedRow) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (row.isValid) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isValid) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (row.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = "",
                        tint = if (row.isValid) Color(0xFF22C55E) else Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = row.product.itemName.ifEmpty { "[صنف فارغ]" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (row.isValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "التاريخ: ${row.product.expiryDate.ifEmpty { "[فارغ]" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (!row.product.barcode.isNullOrEmpty()) {
                        Text(
                            text = "الباركود: ${row.product.barcode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                if (!row.isValid) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "الخطأ: ${row.errorMsg}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Upload Status Marker
            Box(
                modifier = Modifier
                    .background(
                        when (row.uploadStatus) {
                            "success" -> Color(0xFF22C55E).copy(alpha = 0.12f)
                            "failed" -> Color(0xFFEF4444).copy(alpha = 0.12f)
                            "uploading" -> Color(0xFF3B82F6).copy(alpha = 0.12f)
                            else -> Color(0xFF94A3B8).copy(alpha = 0.12f)
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (row.uploadStatus) {
                        "success" -> "تم الرفع ✅"
                        "failed" -> "فشل ❌"
                        "uploading" -> "جاري الرفع..."
                        else -> if (row.isValid) "جاهز" else "غير صالح"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = when (row.uploadStatus) {
                        "success" -> Color(0xFF22C55E)
                        "failed" -> Color(0xFFEF4444)
                        "uploading" -> Color(0xFF3B82F6)
                        else -> if (row.isValid) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color(0xFFEF4444)
                    }
                )
            }
        }
    }
}

// Custom CSV Parser supporting basic quotations, commas, and Arabic columns
fun parseCsvString(csvText: String): List<ParsedRow> {
    val lines = csvText.split(Regex("[\r\n]+"))
    if (lines.isEmpty()) return emptyList()

    val parsed = mutableListOf<ParsedRow>()

    // Get Header and trim columns
    val headerLine = lines[0]
    val headers = splitCsvLine(headerLine).map { it.trim().lowercase() }

    // Find indices matching standard columns (supports Arabic synonyms)
    val nameIdx = headers.indexOfFirst { it.contains("item_name") || it.contains("name") || it.contains("الصنف") || it.contains("اسم") }
    val dateIdx = headers.indexOfFirst { it.contains("expiry_date") || it.contains("expiry") || it.contains("تاريخ") || it.contains("التاريخ") }
    val barcodeIdx = headers.indexOfFirst { it.contains("barcode") || it.contains("باركود") || it.contains("ترميز") }
    val locationIdx = headers.indexOfFirst { it.contains("location") || it.contains("موقع") || it.contains("مخزن") }
    val qtyIdx = headers.indexOfFirst { it.contains("quantity") || it.contains("الكمية") || it.contains("عدد") }
    val statusIdx = headers.indexOfFirst { it.contains("status") || it.contains("الحالة") }
    val notesIdx = headers.indexOfFirst { it.contains("notes") || it.contains("ملاحظات") || it.contains("ملاحظة") }

    for (i in 1 until lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty()) continue

        val parts = splitCsvLine(line)

        val itemVal = if (nameIdx >= 0 && nameIdx < parts.size) parts[nameIdx].trim() else ""
        val dateVal = if (dateIdx >= 0 && dateIdx < parts.size) parts[dateIdx].trim() else ""
        val bcodeVal = if (barcodeIdx >= 0 && barcodeIdx < parts.size) parts[barcodeIdx].trim() else ""
        val locVal = if (locationIdx >= 0 && locationIdx < parts.size) parts[locationIdx].trim() else ""
        val qtyVal = if (qtyIdx >= 0 && qtyIdx < parts.size) parts[qtyIdx].trim() else "1"
        val statusVal = if (statusIdx >= 0 && statusIdx < parts.size) parts[statusIdx].trim() else "متاح"
        val notesVal = if (notesIdx >= 0 && notesIdx < parts.size) parts[notesIdx].trim() else ""

        val quantity = qtyVal.toIntOrNull() ?: 1

        val isValid = itemVal.isNotEmpty() && dateVal.isNotEmpty() && isValidDateString(dateVal)
        var errorReason = ""
        if (itemVal.isEmpty()) errorReason += "اسم الصنف فارغ. "
        if (dateVal.isEmpty()) errorReason += "تاريخ الانتهاء فارغ. "
        else if (!isValidDateString(dateVal)) errorReason += "تنسيق التاريخ خاطئ (يجب أن يكون YYYY-MM-DD). "

        parsed.add(
            ParsedRow(
                product = Product(
                    itemName = itemVal,
                    expiryDate = dateVal,
                    barcode = bcodeVal.ifEmpty { null },
                    location = locVal.ifEmpty { null },
                    quantity = quantity,
                    status = statusVal.ifEmpty { "متاح" },
                    notes = notesVal.ifEmpty { null }
                ),
                isValid = isValid,
                errorMsg = errorReason.trim()
            )
        )
    }
    return parsed
}

// Simple splitter for CSV columns supporting Quotations (e.g. "Juice, Orange", 1234)
fun splitCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val currentPart = StringBuilder()

    for (ch in line) {
        when {
            ch == '\"' -> {
                inQuotes = !inQuotes
            }
            ch == ',' && !inQuotes -> {
                result.add(currentPart.toString())
                currentPart.setLength(0)
            }
            else -> {
                currentPart.append(ch)
            }
        }
    }
    result.add(currentPart.toString())
    return result
}

fun isValidDateString(dateStr: String): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.isLenient = false
        format.parse(dateStr)
        true
    } catch (e: Exception) {
        false
    }
}
