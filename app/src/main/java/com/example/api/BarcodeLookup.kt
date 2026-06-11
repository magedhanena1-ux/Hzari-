package com.example.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BarcodeLookup {
    private const val TAG = "BarcodeLookup"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Unified lookup:
     * 1. Query local API first to see if item is already known (matching items).
     * 2. Query Open Food Facts public database.
     * 3. Query Open Beauty Facts public database.
     * 4. Call Gemini API if GEMINI_API_KEY is configured and not default.
     */
    suspend fun lookupBarcode(context: Context, barcode: String): String? {
        val cleaned = barcode.trim()
        if (cleaned.isEmpty()) return null

        // 1. Try local Hathari API first
        try {
            val localItems = ApiClient.fetchItems(context, cleaned)
            val directMatch = localItems.find { it.barcode == cleaned }
            if (directMatch != null) {
                Log.d(TAG, "Barcode found in local Hathari API: ${directMatch.itemName}")
                return directMatch.itemName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Local Hathari API lookup failed: ${e.message}")
        }

        // 2. Query Open Food Facts API (Worldwide free, open food database)
        val offName = lookupOpenFoodFacts(cleaned)
        if (!offName.isNullOrEmpty()) {
            Log.d(TAG, "Barcode identified by Open Food Facts: $offName")
            return offName
        }

        // 3. Query Open Beauty Facts (as another free keyless database)
        val obfName = lookupOpenBeautyFacts(cleaned)
        if (!obfName.isNullOrEmpty()) {
            Log.d(TAG, "Barcode identified by Open Beauty Facts: $obfName")
            return obfName
        }

        // 4. Try Gemini AI lookup if API key is present and not the default template placeholder
        val geminiApiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (geminiApiKey.isNotEmpty() && geminiApiKey != "MY_GEMINI_API_KEY" && geminiApiKey != "YOUR_API_KEY") {
            val geminiName = lookupGeminiBarcode(geminiApiKey, cleaned)
            if (!geminiName.isNullOrEmpty()) {
                Log.d(TAG, "Barcode identified by Gemini AI: $geminiName")
                return geminiName
            }
        }

        return null
    }

    private suspend fun lookupOpenFoodFacts(barcode: String): String? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
        try {
            val request = Request.Builder()
                .url(url)
                // Set user-agent to comply with Open Food Facts policy
                .header("User-Agent", "HathariBarcodeScanner/1.0 (Android; Kotlin)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use null
                    val json = JSONObject(bodyStr)
                    val status = json.optInt("status", 0)
                    if (status == 1) {
                        val product = json.optJSONObject("product") ?: return@use null
                        
                        val nameAr = product.optString("product_name_ar")
                        val nameEn = product.optString("product_name")
                        val genericName = product.optString("generic_name_ar") ?: product.optString("generic_name")
                        val brands = product.optString("brands")

                        var finalName = ""
                        if (!nameAr.isNullOrEmpty() && nameAr != "null" && nameAr.isNotBlank()) {
                            finalName = nameAr
                        } else if (!nameEn.isNullOrEmpty() && nameEn != "null" && nameEn.isNotBlank()) {
                            finalName = nameEn
                        } else if (!genericName.isNullOrEmpty() && genericName != "null" && genericName.isNotBlank()) {
                            finalName = genericName
                        }

                        if (finalName.isNotEmpty()) {
                            if (!brands.isNullOrEmpty() && brands != "null" && brands.isNotBlank()) {
                                return@use "$finalName - $brands"
                            }
                            return@use finalName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Open Food Facts lookup failed: ${e.message}")
        }
        null
    }

    private suspend fun lookupOpenBeautyFacts(barcode: String): String? = withContext(Dispatchers.IO) {
        val url = "https://world.openbeautyfacts.org/api/v0/product/$barcode.json"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HathariBarcodeScanner/1.0 (Android; Kotlin)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use null
                    val json = JSONObject(bodyStr)
                    val status = json.optInt("status", 0)
                    if (status == 1) {
                        val product = json.optJSONObject("product") ?: return@use null
                        
                        val nameAr = product.optString("product_name_ar")
                        val nameEn = product.optString("product_name")
                        val brands = product.optString("brands")

                        var finalName = ""
                        if (!nameAr.isNullOrEmpty() && nameAr != "null" && nameAr.isNotBlank()) {
                            finalName = nameAr
                        } else if (!nameEn.isNullOrEmpty() && nameEn != "null" && nameEn.isNotBlank()) {
                            finalName = nameEn
                        }

                        if (finalName.isNotEmpty()) {
                            if (!brands.isNullOrEmpty() && brands != "null" && brands.isNotBlank()) {
                                return@use "$finalName - $brands"
                            }
                            return@use finalName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Open Beauty Facts lookup failed: ${e.message}")
        }
        null
    }

    private suspend fun lookupGeminiBarcode(apiKey: String, barcode: String): String? = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        try {
            val requestObj = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            
            val prompt = """
                Identify the product associated with global barcode (EAN / UPC / Code 128): $barcode.
                Provide only the product name and brand as a single clean line in Arabic, like: "بسكويت أوريو بالشوكولاتة - كرافت".
                If you are entirely unsure or can't identify the product, respond exactly with "UNKNOWN". Do not apologize or add extra words.
            """.trimIndent()

            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestObj.put("contents", contentsArray)

            val mediaType = "application/json".toMediaType()
            val requestBody = requestObj.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use null
                    val json = JSONObject(bodyStr)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text").trim()
                                if (text.isNotEmpty() && !text.equals("UNKNOWN", ignoreCase = true)) {
                                    return@use text
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini barcode lookup failed: ${e.message}")
        }
        null
    }

    fun calculateDaysRemaining(expiryDateStr: String?): Int? {
        if (expiryDateStr.isNullOrEmpty()) return null
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val expiry = format.parse(expiryDateStr) ?: return null
            val today = format.parse(format.format(java.util.Date())) ?: return null
            val diffMs = expiry.time - today.time
            (diffMs / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            null
        }
    }

    fun checkScannedProductExpiry(expiryDateStr: String?): ExpiryWarningMessage? {
        val days = calculateDaysRemaining(expiryDateStr) ?: return null
        return when {
            days < 0 -> ExpiryWarningMessage(
                days = days,
                warningType = "EXPIRED",
                title = "منتهي الصلاحية بالفعل 🚨",
                message = "لقد منتهت صلاحية هذا الصنف منذ ${kotlin.math.abs(days)} يوم! يرجى الحذر والتخلص منه.",
                colorHex = "EF4444"
            )
            days <= 10 -> ExpiryWarningMessage(
                days = days,
                warningType = "CRITICAL",
                title = "تنبيه حرج جداً (المرحلة الثالثة) 🚨",
                message = "متبقي $days أيام فقط على انتهاء صلاحية هذا الصنف! دخل في مرحلة الخطر (العشرة أيام الأخيرة).",
                colorHex = "EF4444"
            )
            days <= 20 -> ExpiryWarningMessage(
                days = days,
                warningType = "WARNING_MEDIUM",
                title = "تنبيه متوسط (المرحلة الثانية) ⚠️",
                message = "متبقي $days يوماً على انتهاء الصلاحية. الصنف في مرحلة التنبيه المتوسط (العشرين يوماً).",
                colorHex = "F59E0B"
            )
            days <= 30 -> ExpiryWarningMessage(
                days = days,
                warningType = "WARNING_SOON",
                title = "تنبيه مبكر (المرحلة الأولى) 🔵",
                message = "متبقي $days يوماً على انتهاء الصلاحية. يقترب الصنف من حد الثلاثين يوماً.",
                colorHex = "3B82F6"
            )
            else -> null
        }
    }

    data class ExpiryWarningMessage(
        val days: Int,
        val warningType: String,
        val title: String,
        val message: String,
        val colorHex: String
    )
}
