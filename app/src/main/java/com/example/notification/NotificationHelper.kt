package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.model.Product
import com.example.model.PlatformItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object NotificationHelper {
    private const val CHANNEL_ID = "hathari_expiry_alerts"
    private const val CHANNEL_NAME = "تنبيهاث انتهاء الصلاحية"
    private const val CHANNEL_DESC = "تنبيهات الأصناف والمنتجات قاربت على انتهاء صلاحيتها"

    private const val PREFS_NAME = "hathari_prefs"
    private const val KEY_NOTIFS_ENABLED = "expiry_notifications_enabled"

    // Help create notification channel
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Checking if notification is enabled in user settings
    fun areNotificationsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIFS_ENABLED, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NOTIFS_ENABLED, enabled).apply()
    }

    private fun calculateDaysRemaining(expiryDateStr: String?): Int? {
        if (expiryDateStr.isNullOrEmpty()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val expiryDate = sdf.parse(expiryDateStr) ?: return null
            
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val expiryCal = Calendar.getInstance().apply {
                time = expiryDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val diffInMs = expiryCal.timeInMillis - todayCal.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffInMs).toInt()
        } catch (e: Exception) {
            null
        }
    }

    // Unified evaluation for any product list loaded
    fun evaluateAndNotify(context: Context, products: List<Product>) {
        if (!areNotificationsEnabled(context)) return
        createNotificationChannel(context)

        // Categorize into 4 phases:
        // Phase 1: Under 30 days remaining (21..30 days)
        // Phase 2: Under 20 days remaining (11..20 days)
        // Phase 3: Under 10 days remaining (8..10 days)
        // Phase 7 Days Urgent Expiration Alert: (0..7 days)
        val phase1Items = mutableListOf<Product>()
        val phase2Items = mutableListOf<Product>()
        val phase3Items = mutableListOf<Product>()
        val phase7DaysItems = mutableListOf<Product>()

        for (p in products) {
            val days = p.daysRemaining ?: calculateDaysRemaining(p.expiryDate) ?: continue
            if (days < 0) {
                // Also classify expired into urgent Phase 3
                phase3Items.add(p)
            } else if (days <= 7) {
                phase7DaysItems.add(p)
            } else if (days <= 10) {
                phase3Items.add(p)
            } else if (days <= 20) {
                phase2Items.add(p)
            } else if (days <= 30) {
                phase1Items.add(p)
            }
        }

        // Send Phase 7 Days (Highly Urgent!) - <= 7 days
        if (phase7DaysItems.isNotEmpty()) {
            val names = phase7DaysItems.joinToString(", ") { it.itemName }
            val text = "أصناف ستنتهي صلاحيتها خلال 7 أيام أو أقل ⚠️: $names"
            sendNotification(
                context = context,
                notificationId = 150,
                title = "تنبيه انتهاء الصلاحية الوشيك (7 أيام) ⚠️",
                content = text
            )
        }

        // Send Phase 3 (Urgent!) - <= 10 days
        if (phase3Items.isNotEmpty()) {
            val names = phase3Items.joinToString(", ") { it.itemName }
            val text = "أصناف دخلت مرحلة الخطر (10 أيام أو أقل) ⚠️: $names"
            sendNotification(
                context = context,
                notificationId = 300,
                title = "المرحلة الثالثة: تنبيه حرج جداً 🚨",
                content = text
            )
        }

        // Send Phase 2 - <= 20 days
        if (phase2Items.isNotEmpty()) {
            val names = phase2Items.joinToString(", ") { it.itemName }
            val text = "أصناف متبقي عليها 20 يوماً أو أقل 🟡: $names"
            sendNotification(
                context = context,
                notificationId = 200,
                title = "المرحلة الثانية: تنبيه متوسط ⚠️",
                content = text
            )
        }

        // Send Phase 1 - <= 30 days
        if (phase1Items.isNotEmpty()) {
            val names = phase1Items.joinToString(", ") { it.itemName }
            val text = "أصناف متبقي عليها 30 يوماً أو أقل 🔵: $names"
            sendNotification(
                context = context,
                notificationId = 100,
                title = "المرحلة الأولى: تنبيه مبكر ℹ️",
                content = text
            )
        }
    }

    // Trigger instant mock notifications for user testing or demo
    fun triggerInstantDemoNotifications(context: Context) {
        createNotificationChannel(context)
        
        // Notification 1
        sendNotification(
            context = context,
            notificationId = 11,
            title = "المرحلة الأولى: تنبيه مبكر (30 يوم) 🔵",
            content = "صنف [عصير تفاح طبيعي] و [حليب مكثف] تبقت على صلاحيتها أقل من 30 يوماً."
        )

        // Notification 2
        sendNotification(
            context = context,
            notificationId = 12,
            title = "المرحلة الثانية: تنبيه متوسط (20 يوم) 🟡",
            content = "صنف [زبادي طازج] و [شيبس جبنة] تبقت على صلاحيتها أقل من 20 يوماً."
        )

        // Notification 3
        sendNotification(
            context = context,
            notificationId = 13,
            title = "المرحلة الثالثة: تنبيه خطر وحرج (10 أيام) 🚨",
            content = "صنف [خبز بر] و [عصير برتقال طازج] تبقت على صلاحيتها أقل من 10 أيام أو منتهية!"
        )

        // Notification 4 - 7 Days Alert
        sendNotification(
            context = context,
            notificationId = 14,
            title = "تنبيه غاية في الأهمية: المتبقي 7 أيام أو أقل ⚠️",
            content = "صنف [حليب طازج] و [جبنة بيضاء] ستنتهي صلاحيتها خلال أقل من 7 أيام!"
        )
    }

    fun notifyOfflineMode(context: Context) {
        if (!areNotificationsEnabled(context)) return
        createNotificationChannel(context)
        sendNotification(
            context = context,
            notificationId = 400,
            title = "وضع التشغيل دون اتصال (أوفلاين) 📶",
            content = "تطبيق حذاري يعمل الآن في وضع دون اتصال بالشبكة. يتم تحميل بياناتك المخزنة محلياً ويمكنك استعراض منتجاتك بأمان."
        )
    }

    private fun sendNotification(context: Context, notificationId: Int, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // Default Android notification icon
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, builder.build())
        } catch (securityException: SecurityException) {
            // Android 13+ permission not granted yet - can be skipped gracefully or caught
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
