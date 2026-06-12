package com.example.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPage(
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Username & Password, 1 = API Token

    // Form states
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .testTag("login_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo Title
                Text(
                    text = "حذاري Scanner",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "واجهة إدارة وتتبع صلاحيات المنتجات",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF64748B)
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                    textAlign = TextAlign.Center
                )

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            errorMessage = null
                        },
                        text = { Text("تسجيل بالحساب", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            errorMessage = null
                        },
                        text = { Text("رمز الـ API", fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // Username & Password Mode
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("username_input"),
                        label = { Text("اسم المستخدم") },
                        placeholder = { Text("أدخل اسم المستخدم بالمنصة") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "اسم المستخدم"
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        label = { Text("كلمة المرور") },
                        placeholder = { Text("أدخل كلمة المرور الخاصة بحسابك") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "كلمة المرور"
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    // API Token Mode
                    OutlinedTextField(
                        value = apiToken,
                        onValueChange = {
                            apiToken = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("token_input"),
                        label = { Text("رمز API الخاص بمنصة حذاري") },
                        placeholder = { Text("أدخل رمز السيرفر UUID") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "مفتاح الدخول"
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "إخفاء الرمز" else "إظهار الرمز"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Submit Button
                Button(
                    onClick = {
                        val computedToken = if (selectedTab == 0) {
                            if (username.trim().isEmpty() || password.trim().isEmpty()) {
                                errorMessage = "يرجى إدخال اسم المستخدم وكلمة المرور كاملين"
                                return@Button
                            }
                            "usr_pwd:${username.trim()}:::${password.trim()}"
                        } else {
                            if (apiToken.trim().isEmpty()) {
                                errorMessage = "يرجى إدخال رمز الـ API للاتصال"
                                return@Button
                            }
                            apiToken.trim()
                        }

                        loading = true
                        errorMessage = null
                        scope.launch {
                            val result = ApiClient.testConnection(computedToken, context)
                            loading = false
                            if (result.ok) {
                                ApiClient.saveToken(
                                    context,
                                    result.token ?: computedToken,
                                    result.username,
                                    result.role
                                )
                                Toast.makeText(
                                    context,
                                    "أهلاً بك يا ${result.username}! تم تسجيل الدخول بنجاح",
                                    Toast.LENGTH_LONG
                                ).show()
                                onNavigateToDashboard()
                            } else {
                                errorMessage = result.error ?: "فشل الاتصال بالمنصة"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_button"),
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (selectedTab == 0) "تسجيل الدخول" else "اختبار الاتصال والدخول",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Add Request Subscription Button & Registration Form Dialog
                var showRegistrationDialog by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { showRegistrationDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "طلب اشتراك جديد بالمنصة السحابية / المحلية",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (showRegistrationDialog) {
                    var regName by remember { mutableStateOf("") }
                    var regPhone by remember { mutableStateOf("") }
                    var regWhatsapp by remember { mutableStateOf("") }
                    var regEmail by remember { mutableStateOf("") }
                    var regCompany by remember { mutableStateOf("") }
                    var regUser by remember { mutableStateOf("") }
                    var regPass by remember { mutableStateOf("") }
                    var regDuration by remember { mutableStateOf("3_months") } // 3_months, 6_months, 1_year
                    var regError by remember { mutableStateOf<String?>(null) }
                    var regLoading by remember { mutableStateOf(false) }

                    AlertDialog(
                        onDismissRequest = { showRegistrationDialog = false },
                        title = {
                            Text(
                                text = "طلب اشتراك جديد في حذاري",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text(
                                        text = "املأ البيانات أدناه لمراجعتها والموافقة عليها من قبل الإدارة لتفعيل حسابك.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    if (regError != null) {
                                        Text(
                                            text = regError ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = regName,
                                        onValueChange = { regName = it },
                                        label = { Text("الاسم الكامل") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regPhone,
                                        onValueChange = { regPhone = it },
                                        label = { Text("الهاتف") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regWhatsapp,
                                        onValueChange = { regWhatsapp = it },
                                        label = { Text("واتساب") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regEmail,
                                        onValueChange = { regEmail = it },
                                        label = { Text("البريد الإلكتروني") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regCompany,
                                        onValueChange = { regCompany = it },
                                        label = { Text("اسم الشركة / المنشأة") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regUser,
                                        onValueChange = { regUser = it },
                                        label = { Text("اسم المستخدم المطلوب") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = regPass,
                                        onValueChange = { regPass = it },
                                        label = { Text("كلمة المرور") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                        )
                                }

                                item {
                                    Text(
                                        text = "اختر الباقة المطلوبة:",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    
                                    com.example.model.AppConfig.PLANS.forEach { plan ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { regDuration = plan.id }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = regDuration == plan.id,
                                                onClick = { regDuration = plan.id }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${plan.name} (${plan.price} ريال YER)",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = !regLoading,
                                onClick = {
                                    if (regName.trim().isEmpty() || regPhone.trim().isEmpty() || regUser.trim().isEmpty() || regPass.trim().isEmpty()) {
                                        regError = "يرجى ملء جميع الحقول الإلزامية (الاسم، الهاتف، اسم المستخدم، كلمة المرور)"
                                        return@Button
                                    }
                                    regLoading = true
                                    regError = null
                                    scope.launch {
                                        try {
                                            val database = com.example.model.OfflineDatabase.getDatabase(context)
                                            val newRequest = com.example.model.SubscriptionRequest(
                                                id = "req_" + java.util.UUID.randomUUID().toString(),
                                                name = regName.trim(),
                                                phone = regPhone.trim(),
                                                whatsapp = regWhatsapp.trim().ifEmpty { regPhone.trim() },
                                                email = regEmail.trim(),
                                                companyName = regCompany.trim(),
                                                username = regUser.trim(),
                                                password = regPass.trim(),
                                                duration = regDuration,
                                                status = "PENDING"
                                            )
                                            database.subscriptionRequestDao().insertRequest(newRequest)
                                            regLoading = false
                                            Toast.makeText(
                                                context,
                                                "تم تسجيل طلبك بنجاح! يرجى الانتظار لحين الموافقة من الإدارة",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            showRegistrationDialog = false
                                        } catch (e: Exception) {
                                            regLoading = false
                                            regError = "حدث خطأ أثناء إرسال الطلب: ${e.message}"
                                        }
                                    }
                                }
                            ) {
                                if (regLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("تقديم الطلب")
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRegistrationDialog = false }) {
                                Text("إلغاء")
                            }
                        }
                    )
                }
            }
        }
    }
}
