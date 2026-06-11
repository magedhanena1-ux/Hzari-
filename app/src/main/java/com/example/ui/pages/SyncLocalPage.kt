package com.example.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import com.example.model.HistoryManager
import com.example.model.OfflineDatabase
import com.example.model.OfflineProduct
import com.example.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncLocalPage(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { OfflineDatabase.getDatabase(context) }

    var offlineProducts by remember { mutableStateOf<List<OfflineProduct>>(emptyList()) }
    var syncLoading by remember { mutableStateOf(false) }
    var syncResultMsg by remember { mutableStateOf<String?>(null) }
    var syncResultOk by remember { mutableStateOf(false) }

    // Load offline products
    fun loadOfflineProducts() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                db.offlineProductDao().getAllOfflineProductsList()
            }
            offlineProducts = list
        }
    }

    LaunchedEffect(Unit) {
        loadOfflineProducts()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("sync_local_root")
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "المزامنة اليدوية للبيانات",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "رفع الأصناف والمنتجات المسجلة دون إنترنت إلى حسابك",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Reload Button
            IconButton(
                onClick = { loadOfflineProducts() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "تحديث القائمة",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Summary Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (offlineProducts.isEmpty()) Color(0xFFF0FDF4) else Color(0xFFFEF3C7)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (offlineProducts.isEmpty()) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                    contentDescription = "",
                    tint = if (offlineProducts.isEmpty()) Color(0xFF16A34A) else Color(0xFFD97706),
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = if (offlineProducts.isEmpty()) "كل البيانات متزامنة بنجاح!" else "بانتظار الرفع للمنصة",
                        fontWeight = FontWeight.Bold,
                        color = if (offlineProducts.isEmpty()) Color(0xFF14532D) else Color(0xFF78350F)
                    )
                    Text(
                        text = if (offlineProducts.isEmpty()) "لا توجد أي أصناف معلقة محلياً على جهازك." else "لديك ${offlineProducts.size} من الأصناف التي تم إدخالها دون إنترنت وتنتظر الرفع.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (offlineProducts.isEmpty()) Color(0xFF15803D) else Color(0xFF92400E)
                    )
                }
            }
        }

        // Sync Action Button
        if (offlineProducts.isNotEmpty()) {
            Button(
                onClick = {
                    syncLoading = true
                    syncResultMsg = null
                    scope.launch {
                        var successCount = 0
                        var failureCount = 0
                        
                        // Sync each item
                        offlineProducts.forEach { localItem ->
                            val prod = Product(
                                itemName = localItem.itemName,
                                barcode = localItem.barcode,
                                expiryDate = localItem.expiryDate,
                                location = localItem.location,
                                status = localItem.status,
                                quantity = localItem.quantity,
                                notes = localItem.notes
                            )
                            
                            val response = ApiClient.uploadProduct(context, prod)
                            if (response.success) {
                                // Delete from local DB if uploaded successfully
                                withContext(Dispatchers.IO) {
                                    db.offlineProductDao().deleteOfflineProduct(localItem)
                                }
                                HistoryManager.addHistory(context, prod, success = true, errorMsg = null)
                                successCount++
                            } else {
                                val errorDetail = response.error ?: response.message ?: "عطل غير معروف"
                                HistoryManager.addHistory(context, prod, success = false, errorMsg = "فشل مزامنة: $errorDetail")
                                failureCount++
                            }
                        }
                        
                        syncLoading = false
                        loadOfflineProducts()
                        
                        if (failureCount == 0) {
                            syncResultMsg = "تمت المزامنة بنجاح! تم رفع $successCount من المنتجات."
                            syncResultOk = true
                        } else {
                            syncResultMsg = "اكتملت المزامنة مع وجود أخطاء: تم رفع $successCount بنجاح وفشل رفع $failureCount."
                            syncResultOk = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_sync_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                enabled = !syncLoading
            ) {
                if (syncLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بدء رفع ومزامنة ${offlineProducts.size} أصناف الآن",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Sync Alert/Result Banner
        if (syncResultMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (syncResultOk) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = syncResultMsg ?: "",
                    color = if (syncResultOk) Color(0xFF15803D) else Color(0xFF991B1B),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                )
            }
        }

        // Section Title
        Text(
            text = "الأصناف المعلقة في قائمة المزامنة",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        // List representation
        if (offlineProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "قائمة المزامنة فارغة",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "عندما تقوم بإضافة منتج بدون اتصال بالإنترنت، سيظهر هنا فوراً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(offlineProducts) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = item.itemName,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الرمز: ${item.barcode ?: "[بدون باركود]"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "الكمية: ${item.quantity}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الموقع: ${item.location ?: "[غير محدد]"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "تاريخ الانتهاء: ${item.expiryDate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEA580C)
                                    )
                                }
                            }

                            // Delete draft button
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            db.offlineProductDao().deleteOfflineProduct(item)
                                        }
                                        loadOfflineProducts()
                                        Toast.makeText(context, "تم حذف المسودة المحلية بنجاح", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "حذف المسودة",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
