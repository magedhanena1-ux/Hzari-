package com.example.model

import android.content.Context
import com.example.api.ApiClient

object AppConfig {
    // 1. Technical Support Contacts
    const val SUPPORT_WHATSAPP = "+967737007979"
    const val SUPPORT_PHONE = "+967737007979"
    const val SUPPORT_EMAIL = "m.hdigital.ye@gmail.com"

    // 2. Subscription Plans
    val PLANS = listOf(
        SubscriptionPlan("3_months", "اشتراك 3 أشهر", 5000, 90),
        SubscriptionPlan("6_months", "اشتراك 6 أشهر", 8500, 180),
        SubscriptionPlan("1_year", "اشتراك سنة كاملة", 15000, 365)
    )

    // 3. Hidden Super Admin Account
    const val SUPER_ADMIN_USERNAME = "Admin"
    const val SUPER_ADMIN_PASSWORD = "777230792"

    data class SubscriptionPlan(
        val id: String,
        val name: String,
        val price: Int, // YER
        val days: Int
    )

    /**
     * Checks if the currently logged-in user's subscription has expired.
     * Note: "Admin" super admin is always active!
     */
    suspend fun isSubscriptionExpired(context: Context): Boolean {
        val currentUser = ApiClient.getStoredUser(context).username.ifEmpty { null } ?: return false
        if (currentUser.equals(SUPER_ADMIN_USERNAME, ignoreCase = true)) {
            return false // Admin has absolute lifetime access
        }

        // Check against local database user accounts
        val db = OfflineDatabase.getDatabase(context)
        val user = db.userAccountDao().getUserByUsername(currentUser)
        if (user != null) {
            return System.currentTimeMillis() > user.subscriptionExpiry
        }

        // Check if there is cached data in profile or preferences for subscription remaining days
        val remainingDays = loadSubscriptionRemainingDays(context)
        if (remainingDays != null) {
            return remainingDays <= 0
        }

        return false // Safe default to avoid locking out users incorrectly during network lag
    }

    /**
     * Gets the active subscription expiry timestamp or remaining days count.
     */
    suspend fun getRemainingSubscriptionDays(context: Context): Int {
        val currentUser = ApiClient.getStoredUser(context).username.ifEmpty { null } ?: return 365
        if (currentUser.equals(SUPER_ADMIN_USERNAME, ignoreCase = true)) {
            return 9999 // Super admin lifetime
        }

        val db = OfflineDatabase.getDatabase(context)
        val user = db.userAccountDao().getUserByUsername(currentUser)
        if (user != null) {
            val diffMs = user.subscriptionExpiry - System.currentTimeMillis()
            return if (diffMs <= 0) 0 else (diffMs / (1000 * 60 * 60 * 24)).toInt()
        }

        return loadSubscriptionRemainingDays(context) ?: 30 // Default fallback
    }

    private fun loadSubscriptionRemainingDays(context: Context): Int? {
        val prefs = context.getSharedPreferences("hathari_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("sub_remaining_days")) {
            return prefs.getInt("sub_remaining_days", 30)
        }
        return null
    }

    fun saveSubscriptionRemainingDays(context: Context, days: Int) {
        val prefs = context.getSharedPreferences("hathari_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("sub_remaining_days", days).apply()
    }
}
