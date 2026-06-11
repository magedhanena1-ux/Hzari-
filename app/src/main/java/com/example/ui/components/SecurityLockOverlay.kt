package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Fingerprint
import com.example.utils.BiometricHelper
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
import kotlinx.coroutines.delay

@Composable
fun SecurityLockOverlay(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hathari_prefs", Context.MODE_PRIVATE) }
    val correctPin = remember { prefs.getString("security_passcode", "1234") ?: "1234" }
    
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(false) }

    val isBiometricEnabledInPrefs = remember { prefs.getBoolean("security_biometric_enabled", true) }
    val isBiometricAvailable = remember { BiometricHelper.isBiometricAvailable(context) && isBiometricEnabledInPrefs }

    LaunchedEffect(Unit) {
        if (isBiometricAvailable) {
            BiometricHelper.showBiometricPrompt(
                context = context,
                title = "فتح قفل حذاري",
                subtitle = "يرجى تأكيد هويتك عبر بصمة الإصبع",
                onSuccess = {
                    isUnlocked = true
                    onUnlockSuccess()
                },
                onError = { err ->
                    // Gracefully allow fallback to PIN entry
                }
            )
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            if (enteredPin == correctPin) {
                isUnlocked = true
                isError = false
                delay(300)
                onUnlockSuccess()
            } else {
                isError = true
                enteredPin = ""
                Toast.makeText(context, "رمز المرور خاطئ! يرجى إعادة المحاولة.", Toast.LENGTH_SHORT).show()
                delay(1000)
                isError = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {}, // Prevent clicks behind the overlay
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) Color(0xFF10B981).copy(alpha = 0.15f)
                        else if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = "قفل الأمان",
                    tint = if (isUnlocked) Color(0xFF10B981)
                    else if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Headers
            Text(
                text = "رمز حماية التطبيق",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "الرجاء إدخال رمز المرور PIN المكون من 4 أرقام لمنع التلاعب بالبيانات والتأكد من هويتك.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Pin Entry Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) {
                                    if (isUnlocked) Color(0xFF10B981)
                                    else if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Numpad Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val row1 = listOf("1", "2", "3")
                val row2 = listOf("4", "5", "6")
                val row3 = listOf("7", "8", "9")

                listOf(row1, row2, row3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        row.forEach { num ->
                            NumpadButton(text = num, onClick = {
                                if (enteredPin.length < 4) {
                                    enteredPin += num
                                }
                            })
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biometric fingerprint button / Empty spacer button for alignment
                    if (isBiometricAvailable) {
                        IconButton(
                            onClick = {
                                BiometricHelper.showBiometricPrompt(
                                    context = context,
                                    title = "فتح قفل حذاري",
                                    subtitle = "يرجى تأكيد هويتك عبر بصمة الإصبع",
                                    onSuccess = {
                                        isUnlocked = true
                                        onUnlockSuccess()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .testTag("biometric_trigger_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "فتح بالبصمة",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(72.dp))
                    }

                    NumpadButton(text = "0", onClick = {
                        if (enteredPin.length < 4) {
                            enteredPin += "0"
                        }
                    })

                    // Backspace Button
                    IconButton(
                        onClick = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .testTag("numpad_backspace")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "حذف الرقم الأخير",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NumpadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .testTag("numpad_btn_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
