package com.example.ui.pages

import android.content.Context
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppConfig
import com.example.model.OfflineDatabase
import com.example.model.SubscriptionRequest
import com.example.model.UserAccount
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelPage(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { OfflineDatabase.getDatabase(context) }

    // Live observing datasets
    val pendingRequests by db.subscriptionRequestDao().getAllRequests().collectAsState(initial = emptyList())
    val userAccounts by db.userAccountDao().getAllUsersFlow().collectAsState(initial = emptyList())

    var activeTab by remember { mutableStateOf(0) } // 0 = Financial, 1 = Pending Requests, 2 = Users List

    // Dialog trigger states
    var showAddUserDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Upper Navigation Tabs for Admin Panels
        TabRow(
            selectedTabIndex = activeTab,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("التحليلات والمالية", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الطلبات المعلقة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val pendingCount = pendingRequests.count { it.status == "PENDING" }
                        if (pendingCount > 0) {
                            Badge(modifier = Modifier.padding(start = 4.dp)) {
                                Text(pendingCount.toString())
                            }
                        }
                    }
                }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("المشتركون والمستخدمون", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        when (activeTab) {
            0 -> FinancialAnalyticsPanel(userAccounts, pendingRequests)
            1 -> PendingRequestsPanel(context, scope, db, pendingRequests)
            2 -> UsersManagementPanel(context, scope, db, userAccounts, onAddUserClick = { showAddUserDialog = true })
        }

        // Add User Dialog
        if (showAddUserDialog) {
            AddUserDialog(context, scope, db) { showAddUserDialog = false }
        }
    }
}

@Composable
fun CardSummary(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = color, modifier = Modifier.padding(vertical = 4.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Icon(imageVector = icon, contentDescription = title, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
fun FinancialAnalyticsPanel(users: List<UserAccount>, requests: List<SubscriptionRequest>) {
    // Analytics calculations
    val totalUsersCount = users.size
    val activeUsersCount = users.count { it.isActive && it.subscriptionExpiry > System.currentTimeMillis() }
    val expiredUsersCount = users.count { it.subscriptionExpiry <= System.currentTimeMillis() }

    // Estimating total cash collected from approved plans and prospective plans
    var totalCashVolume = 0
    users.forEach { user ->
        // Accumulate based on common plan pricing
        totalCashVolume += 5000 // default mock registration base YER
    }
    // Also estimate pending requests potential volume
    var potentialRevenue = 0
    requests.forEach { req ->
        val planPrice = AppConfig.PLANS.find { it.id == req.duration }?.price ?: 5000
        potentialRevenue += planPrice
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "التقارير المالية والتحليلات السحابية",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            CardSummary(
                title = "العوائد الكلية للمنصة",
                value = "$totalCashVolume ريال يمني",
                subtitle = "الاشتراكات المحصلة من الحسابات المحلية والمفعلة",
                icon = Icons.Default.MonetizationOn,
                color = Color(0xFF22C55E)
            )
        }
        item {
            CardSummary(
                title = "العوائد المتوقعة (المعلقة)",
                value = "$potentialRevenue ريال يمني",
                subtitle = "عائد الطلبات المعلقة في انتظار الموافقة",
                icon = Icons.Default.HourglassEmpty,
                color = Color(0xFFEAB308)
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CardSummary(
                        title = "الاشتراكات النشطة",
                        value = activeUsersCount.toString(),
                        subtitle = "حسابات سارية المفعول",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF06B6D4)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CardSummary(
                        title = "الاشتراكات المنتهية",
                        value = expiredUsersCount.toString(),
                        subtitle = "حسابات مقفلة ومحجوبة",
                        icon = Icons.Default.Block,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@Composable
fun PendingRequestsPanel(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    db: OfflineDatabase,
    requests: List<SubscriptionRequest>
) {
    val pendingOnly = requests.filter { it.status == "PENDING" }

    if (pendingOnly.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد طلبات اشتراك معلقة حالياً", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pendingOnly) { req ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "اسم الشركة: ${req.companyName}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(text = "الاسم بالتفصيل: ${req.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "اسم المستخدم: ${req.username}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(text = "رقم الهاتف: ${req.phone}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "رقم الواتساب: ${req.whatsapp}", style = MaterialTheme.typography.bodyMedium)
                    
                    val planDetail = AppConfig.PLANS.find { it.id == req.duration }
                    Text(text = "الباقة المطلوبة: ${planDetail?.name ?: req.duration} (${planDetail?.price ?: 5000} YER)", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val days = planDetail?.days ?: 90
                                    val expiryTimestamp = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000)
                                    
                                    // 1. Create a fully authenticated user account locally
                                    val user = UserAccount(
                                        username = req.username,
                                        password = req.password,
                                        name = req.name,
                                        companyName = req.companyName,
                                        email = req.email,
                                        phone = req.phone,
                                        whatsapp = req.whatsapp,
                                        subscriptionExpiry = expiryTimestamp,
                                        isActive = true
                                    )
                                    db.userAccountDao().insertUser(user)

                                    // 2. Mark subscription request as approved
                                    db.subscriptionRequestDao().updateRequestStatus(req.id, "APPROVED")
                                    Toast.makeText(context, "تم تفعيل الاشتراك للمستخدم '${req.username}' بنجاح لفترة $days يوماً!", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "تفعيل وسماح")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("موافقة وتفعيل")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    db.subscriptionRequestDao().updateRequestStatus(req.id, "REJECTED")
                                    Toast.makeText(context, "تم رفض الطلب بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "رفض")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("رفض الطلب")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UsersManagementPanel(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    db: OfflineDatabase,
    users: List<UserAccount>,
    onAddUserClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "إدارة رخص وحسابات المشتركين",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Button(onClick = onAddUserClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة")
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة مشترك")
            }
        }

        if (users.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا يوجد مستخدمون مسجلون حالياً", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users) { user ->
                val isExpired = user.subscriptionExpiry <= System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val expiryText = dateFormat.format(Date(user.subscriptionExpiry))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (user.isActive && !isExpired) Color(0xFFE6F4EA) else Color(0xFFFCE8E6))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (!user.isActive) "معطل" else if (isExpired) "منتهي الاشتراك" else "نشط",
                                    color = if (user.isActive && !isExpired) Color(0xFF137333) else Color(0xFFC5221F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "اسم المستخدم: ${user.username}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "اسم الشركة: ${user.companyName ?: '—'}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "تاريخ انتهاء الترخيص: $expiryText", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        // CRUD Commands on current User
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        // Extend subscription with 30 more days
                                        val extendedExpiry = user.subscriptionExpiry + (30L * 24L * 60L * 60L * 1000L)
                                        val updatedUser = user.copy(subscriptionExpiry = extendedExpiry)
                                        db.userAccountDao().insertUser(updatedUser)
                                        Toast.makeText(context, "تم تمديد صلاحية '${user.username}' لمدة 30 يوماً إضافية!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Event, contentDescription = "تمديد", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("تمديد 30 يوم", fontSize = 11.sp)
                            }

                            TextButton(
                                onClick = {
                                    scope.launch {
                                        // Toggle active/inactive status
                                        val updatedUser = user.copy(isActive = !user.isActive)
                                        db.userAccountDao().insertUser(updatedUser)
                                        Toast.makeText(context, "تم تعديل حالة حساب '${user.username}'", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(imageVector = if (user.isActive) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "قفل", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(if (user.isActive) "تعطيل الحساب" else "تفعيل الحساب", fontSize = 11.sp)
                            }

                            TextButton(
                                onClick = {
                                    scope.launch {
                                        db.userAccountDao().deleteUser(user.username)
                                        Toast.makeText(context, "تم حذف الحساب بنجاح", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("حذف المشترك", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserDialog(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    db: OfflineDatabase,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var daysStr by remember { mutableStateOf("90") }
    var errors by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حساب مشترك جديد مباشر", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errors != null) {
                    Text(text = errors ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الكامل") }, singleLine = true)
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("اسم المستخدم") }, singleLine = true)
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("كلمة المرور") }, singleLine = true)
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("المنشأة والشركة") }, singleLine = true)
                OutlinedTextField(
                    value = daysStr, 
                    onValueChange = { daysStr = it }, 
                    label = { Text("مدة الترخيص بالأيام") }, 
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty() || user.trim().isEmpty() || pass.trim().isEmpty()) {
                        errors = "الرجاء ملء الاسم، اسم المستخدم وكلمة المرور"
                        return@Button
                    }
                    val days = daysStr.toIntOrNull() ?: 90
                    scope.launch {
                        val expiryTimestamp = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000)
                        val newUser = UserAccount(
                            username = user.trim(),
                            password = pass.trim(),
                            name = name.trim(),
                            companyName = company.trim(),
                            subscriptionExpiry = expiryTimestamp,
                            isActive = true
                        )
                        db.userAccountDao().insertUser(newUser)
                        Toast.makeText(context, "تم حفظ حساب المشترك بنجاح", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            ) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
