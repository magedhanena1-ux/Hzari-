package com.example.ui.pages

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import com.example.model.HistoryEntry
import com.example.model.HistoryManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryPage(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var filterSelected by remember { mutableStateOf("جميعها") } // "جميعها", "ناجحة", "فاشلة"
    var historyItems by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }

    // Load history
    val reloadHistory = {
        historyItems = HistoryManager.getHistory(context)
    }

    LaunchedEffect(Unit) {
        reloadHistory()
    }

    // Filtered items
    val filteredHistory = remember(searchQuery, filterSelected, historyItems) {
        historyItems.filter { entry ->
            val matchQuery = entry.product.itemName.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (filterSelected) {
                "ناجحة" -> entry.result == "success"
                "فاشلة" -> entry.result == "error"
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_root")
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Headers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "سجل العمليات",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "يعرض آخر 200 عملية رفع تمت محلياً على هذا الجهاز.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Export CSV
                    IconButton(
                        onClick = {
                            if (historyItems.isEmpty()) {
                                Toast.makeText(context, "السجل فارغ. لا يوجد بيانات للتصدير", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            // Compile CSV String
                            val csvContent = buildString {
                                append("الصنف,التاريخ,الباركود,المستودع,الكمية,النتيجة,الوقت,سبب الخطأ\n")
                                historyItems.forEach { entry ->
                                    append("\"${entry.product.itemName}\",")
                                    append("\"${entry.product.expiryDate}\",")
                                    append("\"${entry.product.barcode ?: ""}\",")
                                    append("\"${entry.product.location ?: ""}\",")
                                    append("${entry.product.quantity ?: 1},")
                                    append("\"${if (entry.result == "success") "نجاح" else "فشل"}\",")
                                    append("\"${entry.timestamp}\",")
                                    append("\"${entry.errorMessage ?: ""}\"\n")
                                }
                            }
                            shareCsvString(context, csvContent)
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "تصدير CSV", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Clear items
                    IconButton(
                        onClick = {
                            HistoryManager.clearHistory(context)
                            reloadHistory()
                            Toast.makeText(context, "تم مسح السجل بالكامل", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "مسح السجل", tint = Color(0xFFEF4444))
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                placeholder = { Text("بحث باسم الصنف...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // Filters segmented row style
        item {
            val filters = listOf("جميعها", "ناجحة", "فاشلة")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = filter == filterSelected
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .clickable { filterSelected = filter }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                }
            }
        }

        // List elements
        if (filteredHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterListOff,
                            contentDescription = "",
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = "لا توجد عمليات تطابق البحث والاختيار حالياً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredHistory) { entry ->
                HistoryEntryCard(entry = entry)
            }
        }
    }
}

@Composable
fun HistoryEntryCard(entry: HistoryEntry) {
    val isSuccess = entry.result == "success"
    val resultColor = if (isSuccess) Color(0xFF22C55E) else Color(0xFFEF4444)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.product.itemName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .background(resultColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isSuccess) "نجحت العملية" else "فشلت العملية",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = resultColor
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "تاريخ الانتهاء: ${entry.product.expiryDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (!entry.product.location.isNullOrEmpty()) {
                    Text(
                        text = "الموقع: ${entry.product.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (!isSuccess && !entry.errorMessage.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = entry.errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الكمية: ${entry.product.quantity ?: 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun shareCsvString(context: Context, csvText: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvText)
            type = "text/csv"
        }
        val chooseIntent = Intent.createChooser(sendIntent, "تصدير السجل عبر")
        context.startActivity(chooseIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل تصدير محتويات السجل", Toast.LENGTH_SHORT).show()
    }
}
