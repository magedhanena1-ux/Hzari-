package com.example.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LanguageManager {
    private const val PREFS_NAME = "hathari_prefs"
    private const val KEY_LANG = "app_language"

    private val _currentLanguage = MutableStateFlow("ar")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANG, "ar") ?: "ar"
        _currentLanguage.value = saved
    }

    fun setLanguage(context: Context, lang: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang).apply()
        _currentLanguage.value = lang
    }

    fun isArabic(): Boolean {
        return _currentLanguage.value == "ar"
    }

    fun getString(key: String, fallbackArabic: String = ""): String {
        val lang = _currentLanguage.value
        val map = stringsMap[key] ?: return fallbackArabic.ifEmpty { key }
        return map[lang] ?: map["ar"] ?: fallbackArabic.ifEmpty { key }
    }

    private val stringsMap = mapOf(
        "app_name" to mapOf(
            "ar" to "حذاري للرقابة والمخزون",
            "en" to "Hathari Monitor & Inventory"
        ),
        "app_tagline" to mapOf(
            "ar" to "النظام الذكي لمراقبة تواريخ ونظم الباركود السحابية",
            "en" to "Smart cloud system for tracking barcode & expiration dates"
        ),
        "dashboard" to mapOf(
            "ar" to "الرئيسية",
            "en" to "Dashboard"
        ),
        "add_product" to mapOf(
            "ar" to "إضافة صنف",
            "en" to "Add Product"
        ),
        "barcode_scanner" to mapOf(
            "ar" to "مسح باركود",
            "en" to "Scan Barcode"
        ),
        "batch_upload" to mapOf(
            "ar" to "رفع Excel",
            "en" to "Excel Batch Upload"
        ),
        "sync_local" to mapOf(
            "ar" to "المزامنة اليدوية",
            "en" to "Manual Sync"
        ),
        "history" to mapOf(
            "ar" to "السجل وبطاقات العمليات",
            "en" to "Operations History"
        ),
        "support" to mapOf(
            "ar" to "الدعم الفني",
            "en" to "Technical Support"
        ),
        "settings" to mapOf(
            "ar" to "الإعدادات واللغة",
            "en" to "Settings & Language"
        ),
        "connected" to mapOf(
            "ar" to "متصل بالمنصة السحابية",
            "en" to "Connected to Cloud Platform"
        ),
        "disconnected" to mapOf(
            "ar" to "غير متصل بالمنصة السحابية",
            "en" to "Disconnected from Cloud"
        ),
        "offline_banner_title" to mapOf(
            "ar" to "وضع التشغيل دون اتصال (أوفلاين)",
            "en" to "Offline Mode Activated"
        ),
        "offline_banner_desc" to mapOf(
            "ar" to "البيانات المعروضة مخزنة محلياً. ستصلك التنبيهات للأصناف منتهية الصلاحية حتى في هذا الوضع.",
            "en" to "The displayed data is cached locally. Expiration notifications will still function offline."
        ),
        "search_hint" to mapOf(
            "ar" to "بحث بالاسم أو الباركود...",
            "en" to "Search by name or barcode..."
        ),
        "expiring_soon_title" to mapOf(
            "ar" to "منتجات تقترب من الانتهاء (30 يوم)",
            "en" to "Products Expiring Soon (30 Days)"
        ),
        "expired" to mapOf(
            "ar" to "منتهي الصلاحية ❌",
            "en" to "Expired ❌"
        ),
        "days_left" to mapOf(
            "ar" to "يوم متبقي",
            "en" to "days left"
        ),
        "no_expiring" to mapOf(
            "ar" to "لا توجد منتجات تقترب من الانتهاء حالياً 🎉",
            "en" to "No expiring products found 🎉"
        ),
        "no_items_match" to mapOf(
            "ar" to "لا توجد منتجات مطابقة في هذا التصنيف أو البحث.",
            "en" to "No products match this category or search."
        ),
        "lang_settings_title" to mapOf(
            "ar" to "تغيير لغة التطبيق والواجهة",
            "en" to "Change App Language & UI Style"
        ),
        "lang_switch_btn" to mapOf(
            "ar" to "English (تفعيل الإنجليزية)",
            "en" to "العربية (Switch to Arabic)"
        ),
        "current_lang_desc" to mapOf(
            "ar" to "اللغة الحالية للمنصة والتطبيق: العربية",
            "en" to "Current App & Platform Language: English"
        ),
        "whatsapp_support_title" to mapOf(
            "ar" to "مركز الدعم والتطوير المباشر",
            "en" to "Direct Tech Support & Dev Center"
        ),
        "whatsapp_support_desc" to mapOf(
            "ar" to "يمكنك التواصل مباشرة مع مركز إدارة وتطوير نظام حذاري عبر تطبيق واتساب لطلب تعديلات أو إرسال استفسارات برمجية فورية.",
            "en" to "You can directly message Hathari system management and development team on WhatsApp for instant software upgrades or inquiries."
        ),
        "whatsapp_btn" to mapOf(
            "ar" to "تواصل فوري عبر الواتساب (+967 737 007 979)",
            "en" to "Instant Chat on WhatsApp (+967 737 007 979)"
        ),
        "sync_offline_notice" to mapOf(
            "ar" to "النظام يدعم الآن تخزين المزامنة والعمل بدون إنترنت بالكامل.",
            "en" to "The system now fully supports offline operations and local data caching."
        ),
        "sync_platform_btn" to mapOf(
            "ar" to "مزامنة ورفع البيانات الفورية",
            "en" to "Sync & Upload to Platform"
        ),
        "logout" to mapOf(
            "ar" to "تسجيل الخروج",
            "en" to "Log Out"
        ),
        "menu" to mapOf(
            "ar" to "القائمة الجانبية",
            "en" to "Navigation Menu"
        )
    )
}
