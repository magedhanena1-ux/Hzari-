package com.example.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ApiClient
import com.example.model.HistoryManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLang by com.example.model.LanguageManager.currentLanguage.collectAsState()

    val PREFS_NAME = "hathari_prefs"
    val KEY_SOUND = "alert_sound_enabled"
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // State bindings
    var showToken by remember { mutableStateOf(false) }
    var alertSoundEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_SOUND, true)) }
    var expiryNotificationsEnabled by remember { mutableStateOf(com.example.notification.NotificationHelper.areNotificationsEnabled(context)) }
    var testResultMsg by remember { mutableStateOf<String?>(null) }
    var testResultOk by remember { mutableStateOf(false) }
    var testLoading by remember { mutableStateOf(false) }

    // JSON backup state
    var backupJson by remember { mutableStateOf(HistoryManager.exportHistoryJson(context)) }
    var importJsonInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    // Security Lock state
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("security_lock_enabled", false)) }
    var currentPasscode by remember { mutableStateOf(prefs.getString("security_passcode", "1234") ?: "1234") }
    var changePinDialogVisible by remember { mutableStateOf(false) }
    var pinValueInput by remember { mutableStateOf("") }

    val storedUser = ApiClient.getStoredUser(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_root")
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper Title
        Text(
            text = com.example.model.LanguageManager.getString("settings", "إعدادات التطبيق"),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        // Language Selector Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("language_selector_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "اللغات",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = com.example.model.LanguageManager.getString("lang_settings_title", "لغة التطبيق (Language)"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = com.example.model.LanguageManager.getString("current_lang_desc", "اللغة الحالية للمنصة والتطبيق: العربية"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            com.example.model.LanguageManager.setLanguage(context, "ar")
                            Toast.makeText(context, "تم تحويل التطبيق للغة العربية 🇸🇦", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("lang_arabic_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentLang == "ar") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentLang == "ar") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("العربية (Arabic)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            com.example.model.LanguageManager.setLanguage(context, "en")
                            Toast.makeText(context, "App switched to English successfully 🇬🇧", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("lang_english_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentLang == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentLang == "en") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("English (إنجليزية)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Theme Configuration Card (Light / Dark / System)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "المظهر",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "مظهر التطبيق (فاتح / داكن)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val currentThemeMode = com.example.ui.theme.ThemeConfig.themeMode.value
                val options = listOf(
                    Triple("system", "تلقائي (حسب إعدادات النظام)", Icons.Default.SettingsSuggest),
                    Triple("light", "المظهر الفاتح (Light Mode)", Icons.Default.LightMode),
                    Triple("dark", "المظهر الداكن (Dark Mode)", Icons.Default.DarkMode)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { (mode, label, icon) ->
                        val isSelected = currentThemeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    com.example.ui.theme.ThemeConfig.saveTheme(context, mode)
                                    Toast.makeText(context, "تم تغيير مظهر التطبيق بنجاح", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    com.example.ui.theme.ThemeConfig.saveTheme(context, mode)
                                    Toast.makeText(context, "تم تغيير مظهر التطبيق بنجاح", Toast.LENGTH_SHORT).show()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. General Account Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                    Text(text = "الحساب والاتصال الحالي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // User details
                Column {
                    Text(text = "اسم المستخدم المسجل:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(text = storedUser.username.ifEmpty { "[غير معروف]" }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }

                Column {
                    Text(text = "الصلاحيات / الدور:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(text = if (storedUser.role == "agent") "مندوب الحساب (Agent)" else storedUser.role.ifEmpty { "[غير محدد]" }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }

                // API Token masking field
                Column {
                    Text(text = "رمز API المحفوظ:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = storedUser.token ?: "",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        )
                    }
                }

                // Check connection state button
                Button(
                    onClick = {
                        val token = storedUser.token
                        if (token.isNullOrEmpty()) {
                            testResultMsg = "لا توجد شهادة اتصال مخزنة."
                            testResultOk = false
                            return@Button
                        }

                        testLoading = true
                        testResultMsg = null

                        scope.launch {
                            val res = ApiClient.testConnection(token)
                            testLoading = false
                            if (res.ok) {
                                testResultMsg = "تم الاتصال بالمنصة بنجاح! اسم الحساب: ${res.username}"
                                testResultOk = true
                                ApiClient.saveToken(context, res.token ?: token, res.username, res.role)
                            } else {
                                testResultMsg = res.error ?: "تعذر التحقق من رمز الـ API"
                                testResultOk = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !testLoading
                ) {
                    if (testLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("اختبار الاتصال الحالي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                if (testResultMsg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (testResultOk) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = testResultMsg ?: "",
                            color = if (testResultOk) Color(0xFF047857) else Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2.5 Security Lock PIN Preferences Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("security_lock_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                    Text(text = "حماية التطبيق وأمن البيانات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "تفعيل قفل التطبيق بـ PIN", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "يطلب رمز المرور (PIN) عند تشغيل التطبيق لمنع التلاعب وحذف المنتجات.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = { isChecked ->
                            lockEnabled = isChecked
                            prefs.edit().putBoolean("security_lock_enabled", isChecked).apply()
                            Toast.makeText(context, if (isChecked) "تم تفعيل حماية التطبيق بالرمز" else "تم إيقاف قفل التطبيق", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (lockEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "رمز المرور الحالي (PIN):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = currentPasscode, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = {
                                pinValueInput = ""
                                changePinDialogVisible = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(imageVector = Icons.Default.LockReset, contentDescription = "")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تغيير رمز PIN")
                        }
                    }

                    // Biometric Option toggle
                    val isFingerprintSupported = remember { com.example.utils.BiometricHelper.isBiometricAvailable(context) }
                    if (isFingerprintSupported) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        var fingerprintEnabled by remember {
                            mutableStateOf(prefs.getBoolean("security_biometric_enabled", true))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "الدخول ببصمة الإصبع",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "استخدام البصمة لفتح قفل حذاري السريع عوضاً عن PIN تلقائياً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Switch(
                                checked = fingerprintEnabled,
                                onCheckedChange = { isChecked ->
                                    fingerprintEnabled = isChecked
                                    prefs.edit().putBoolean("security_biometric_enabled", isChecked).apply()
                                    Toast.makeText(
                                        context,
                                        if (isChecked) "بصمة الإصبع مفعّلة الآن للفتح السريع" else "تم إيقاف تفعيل البصمة لفتح القفل",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2.75 In-App System Updates Card
        val updateState by com.example.model.AppUpdateManager.updateState.collectAsState()
        val currentVersion = remember(updateState) { com.example.model.AppUpdateManager.getCurrentVersion(context) }
        var autoUpdateEnabled by remember { mutableStateOf(com.example.model.AppUpdateManager.isAutoUpdateEnabled(context)) }

        LaunchedEffect(Unit) {
            com.example.model.AppUpdateManager.checkForUpdates(context)
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("app_updates_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "تحديثات النظام وتنزيل الموارد",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Version Tag
                    SuggestionChip(
                        onClick = {},
                        label = { Text("النسخة: $currentVersion") },
                        shape = RoundedCornerShape(8.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Auto-Download Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "تنزيل وتحديث الأدوات تلقائياً", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "يتيح تثبيت أدوات وموارد الدعم والمسح الجديدة المرفوعة من الإدارة على الفور دون متجر.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = { isChecked ->
                            autoUpdateEnabled = isChecked
                            com.example.model.AppUpdateManager.setAutoUpdateEnabled(context, isChecked)
                            Toast.makeText(context, if (isChecked) "تم تفعيل التنزيل والتحديث التلقائي بنجاح!" else "تم تعطيل التحديث التلقائي للموارد", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Update Progress and States
                when (val state = updateState) {
                    is com.example.model.AppUpdateManager.UpdateState.Idle -> {
                        val devInfo = com.example.model.AppUpdateManager.loadDeveloperUpdateConfig(context)
                        val targetVersion = devInfo?.version ?: "v3.2.0"
                        if (currentVersion != targetVersion) {
                            // Version needs upgrade, show options
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "",
                                    tint = Color(0xFFD97706)
                                )
                                Column {
                                    Text(
                                        text = "يتوفر حزمة أدوات وموارد جديدة $targetVersion",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = "تحتوي الحزمة على الأدوات الجديدة والمزايا التي تم برمجتها وتصميمها للتو عبر المحادثة.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    com.example.model.AppUpdateManager.runUpdateFlow(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = "")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تنزيل وإعادة التشغيل لتثبيت التحديث المباشر", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Already latest
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "",
                                    tint = Color(0xFF16A34A)
                                )
                                Column {
                                    Text(
                                        text = "نظام الموارد محدث بنجاح!",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF14532D)
                                    )
                                    Text(
                                        text = "أدوات وميزات منصة التطوير نشطة وتعمل بكفاءة ممتازة بالتزامن.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "المتغيرات المبنية والمعدلة تلقائياً في هذا التحديث:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                val vars = listOf(
                                    "إصدار التطبيق (versionName)" to "3.3.0 (محدث بنجاح)",
                                    "رمز البناء (versionCode)" to "4 (محدث ومرفوع)",
                                    "صيغة الاتصال الأمنة (Authorization)" to "نظام ثنائي مدمج Basic + Bearer",
                                    "الرفع والتحقق من المنتجات" to "أوتوماتيكي بالكامل (أونلاين/أوفلاين)",
                                    "مستكشف الفروع والمخازن" to "ديناميكي تفاعلي نشط",
                                    "جودة الكاش والاتصال" to "إصدار v3 مشفر تلقائي (Room DB)"
                                )

                                vars.forEach { (name, value) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            // Optional reset button for test demonstration
                            OutlinedButton(
                                onClick = {
                                    com.example.model.AppUpdateManager.resetToDefault(context)
                                    Toast.makeText(context, "تم إعادة تعيين سجل الموارد لمحاكاة التحديث مجدداً!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = "")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إعادة تعيين إصدار الموارد (للمحاكاة مجدداً)")
                            }
                        }
                    }

                    is com.example.model.AppUpdateManager.UpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("جاري التحقق من الخادم لترقية الأدوات...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    is com.example.model.AppUpdateManager.UpdateState.UpdateAvailable -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "يتوفر تحديث رئيسي جديد لتنزيل الأدوات: ${state.version}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            state.changelog.forEach { log ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    Text(text = log, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(
                                onClick = { com.example.model.AppUpdateManager.runUpdateFlow(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("تحميل وإعادة تشغيل لتثبيت ${state.version}")
                            }
                        }
                    }

                    is com.example.model.AppUpdateManager.UpdateState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تحميل: ${state.currentTool}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    is com.example.model.AppUpdateManager.UpdateState.Installing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("جاري تشفير وتوليف الأدوات في واجهات التطبيق...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    is com.example.model.AppUpdateManager.UpdateState.Success -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "", tint = Color(0xFF16A34A))
                            Text(
                                text = "تم التنزيل والترقية بنجاح إلى النسخة ${state.version}!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF14532D)
                            )
                        }
                    }
                }
            }
        }

        // 3. Toggles Alerts Preferences Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                    Text(text = "إشعارات وصوتيات التنبيه", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // sound toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "تفعيل صوت التنبيه عند المسح", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "يقوم الهاتف بإصدار رنة تنبيه خفيفة عند قراءة الباركود بنجاح.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }

                    Switch(
                        checked = alertSoundEnabled,
                        onCheckedChange = {
                            alertSoundEnabled = it
                            prefs.edit().putBoolean(KEY_SOUND, it).apply()
                            Toast.makeText(context, "تم حفظ تفضيلات الصوت بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Expiry notifications toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "تنبيهات تاريخ انتهاء الصلاحية", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "تنبهك لوحة الإعلانات بالهاتف عن الأصناف المقربة للانتهاء بحسب المراحل الثلاث للحد (الأول < 30 يوم، الثاني < 20 يوم، الثالث < 10 أيام).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = expiryNotificationsEnabled,
                        onCheckedChange = {
                            expiryNotificationsEnabled = it
                            com.example.notification.NotificationHelper.setNotificationsEnabled(context, it)
                            Toast.makeText(context, "تم خفظ إعدادات إشعارات التنبيه", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Button to trigger immediate mock evaluation or test notifications
                Button(
                    onClick = {
                        if (!expiryNotificationsEnabled) {
                            Toast.makeText(context, "يرجى تفعيل خيار تنبيهات تاريخ انتهاء الصلاحية أولاً", Toast.LENGTH_LONG).show()
                        } else {
                            com.example.notification.NotificationHelper.triggerInstantDemoNotifications(context)
                            Toast.makeText(context, "تم إرسال 3 إشعارات تجريبية (للمراحل الثلاث) بنجاح!", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "جرب إرسال الإشعارات الثلاثة الآن", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 4. Data backups integration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Backup, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                    Text(text = "النسخ الاحتياطي للسجل المحلي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "تتيح لك هذه الميزة الحفاظ على تاريخ الرفع المسجل على جهازك أو نقله لجوال آخر.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Export Backup Trigger
                Button(
                    onClick = {
                        val historyStr = HistoryManager.exportHistoryJson(context)
                        backupJson = historyStr
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, historyStr)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(intent, "تصدير النسخة الاحتياطية"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تصدير نسخة احتياطية (JSON)", color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Import Backup trigger
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.SystemUpdateAlt, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استيراد سجل عمليات سابق", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. WhatsApp Official Support Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("whatsapp_support_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "الدعم الفني",
                        tint = Color(0xFF25D366) // WhatsApp green
                    )
                    Text(
                        text = "مركز الدعم والتطوير المباشر",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "يمكنك التواصل مباشرة مع مركز إدارة وتطوير نظام حذاري عبر تطبيق واتساب لطلب تعديلات أو إرسال استفسارات برمجية فورية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Button(
                    onClick = {
                        val messageUrl = "https://api.whatsapp.com/send?phone=967737007979&text=" + Uri.encode("أهلاً بالدعم الفني لتطبيق حذاري، أود الاستفسار والتنسيق حول التحديثات ولوازم البرمجة.")
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(messageUrl)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "تطبيق واتساب غير مثبت على هذا الهاتف!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("whatsapp_support_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), // Elegant WhatsApp Green
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Chat, contentDescription = "واتساب", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تواصل فوري عبر الواتساب (+967 737 007 979)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Change PIN Dialog
    if (changePinDialogVisible) {
        AlertDialog(
            onDismissRequest = { changePinDialogVisible = false },
            title = { Text("تعيين رمز PIN الجديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الرجاء تعبئة رمز أمان مكون من 4 أرقام:")
                    OutlinedTextField(
                        value = pinValueInput,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                pinValueInput = input
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("new_pin_input"),
                        placeholder = { Text("أدخل 4 أرقام فقط (مثال: 1234)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinValueInput.length == 4) {
                            currentPasscode = pinValueInput
                            prefs.edit().putString("security_passcode", pinValueInput).apply()
                            Toast.makeText(context, "تم تحديث رمز PIN الخاص بك بنجاح!", Toast.LENGTH_SHORT).show()
                            changePinDialogVisible = false
                        } else {
                            Toast.makeText(context, "يجب إدخال 4 أرقام بالتمام!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("حفظ التغيير", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { changePinDialogVisible = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Import Dialog overlay
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("استيراد سجل عمليات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الصق محتوى ملف النسخ الاحتياطي (JSON) أدناه لاستعادته بالكامل:")
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                        placeholder = { Text("[الصق كود JSON]") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonInput.trim().isEmpty()) return@Button
                        val ok = HistoryManager.importHistoryJson(context, importJsonInput.trim())
                        if (ok) {
                            Toast.makeText(context, "تم استيراد نسخة السجل بنجاح!", Toast.LENGTH_LONG).show()
                            showImportDialog = false
                            importJsonInput = ""
                        } else {
                            Toast.makeText(context, "صيغة JSON غير صحيحة أو تالفة!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("استيراد", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
