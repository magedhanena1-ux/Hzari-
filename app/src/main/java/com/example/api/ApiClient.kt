package com.example.api

import android.content.Context
import android.util.Base64
import com.example.model.*
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

interface HathariApiService {
    @GET("locations")
    suspend fun getLocations(
        @Header("Authorization") authHeader: String
    ): Response<LocationsResponse>

    @GET("items")
    suspend fun getItems(
        @Header("Authorization") authHeader: String,
        @Query("location") location: String? = null,
        @Query("category_id") categoryId: String? = null,
        @Query("days") days: Int? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ItemsResponse>

    @GET(".")
    suspend fun getMyProducts(
        @Header("Authorization") authHeader: String,
        @Query("location") location: String? = null,
        @Query("category_id") categoryId: String? = null,
        @Query("search") search: String? = null,
        @Query("archived") archived: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): Response<okhttp3.ResponseBody>

    @POST(".")
    suspend fun uploadProduct(
        @Header("Authorization") authHeader: String,
        @Body productBody: Map<String, @JvmSuppressWildcards Any>
    ): Response<okhttp3.ResponseBody>

    @POST("products/bulk")
    suspend fun bulkUploadProducts(
        @Header("Authorization") authHeader: String,
        @Body products: List<Map<String, @JvmSuppressWildcards Any>>
    ): Response<ApiResponse>

    @PUT("products/{id}")
    suspend fun updateProduct(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String,
        @Body productBody: Map<String, @JvmSuppressWildcards Any>
    ): Response<ApiResponse>

    @DELETE("products/{id}")
    suspend fun archiveProduct(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    ): Response<ApiResponse>

    @POST("login")
    suspend fun login(
        @Body loginBody: Map<String, String>
    ): Response<LoginResponse>

    @GET("sync")
    suspend fun getSync(
        @Header("Authorization") authHeader: String,
        @Query("days") days: Int? = null
    ): Response<SyncResponse>

    @GET("categories")
    suspend fun getCategories(
        @Header("Authorization") authHeader: String
    ): Response<CategoriesResponse>

    @POST("categories")
    suspend fun addCategory(
        @Header("Authorization") authHeader: String,
        @Body categoryBody: Map<String, @JvmSuppressWildcards Any>
    ): Response<ApiResponse>

    @POST("categories/bulk")
    suspend fun bulkUploadCategories(
        @Header("Authorization") authHeader: String,
        @Body categories: List<Map<String, @JvmSuppressWildcards Any>>
    ): Response<ApiResponse>

    @PUT("categories/{id}")
    suspend fun updateCategory(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String,
        @Body categoryBody: Map<String, @JvmSuppressWildcards Any>
    ): Response<ApiResponse>

    @DELETE("categories/{id}")
    suspend fun deleteCategory(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    ): Response<ApiResponse>

    @GET("profile")
    suspend fun getProfile(
        @Header("Authorization") authHeader: String
    ): Response<ProfileResponse>

    @GET("subscription")
    suspend fun getSubscription(
        @Header("Authorization") authHeader: String
    ): Response<SubscriptionResponse>

    @POST("request-renewal")
    suspend fun requestRenewal(
        @Header("Authorization") authHeader: String,
        @Body renewalBody: Map<String, String>
    ): Response<ApiResponse>

    @POST("reset-token")
    suspend fun resetToken(
        @Header("Authorization") authHeader: String
    ): Response<ResetTokenResponse>
}

object ApiClient {
    private const val API_BASE = "https://thfadnleudohykztjrct.supabase.co/functions/v1/external-api/"
    private const val PREFS_NAME = "hathari_prefs"
    private const val KEY_TOKEN = "hathari_token"
    private const val KEY_USER = "hathari_user"
    private const val KEY_ROLE = "hathari_role"

    // حقل مخصص لـ apikey أو Authorization لتمكين إدخال مفتاح أمان Supabase لاحقاً إذا تطلب الأمر
    private const val SUPABASE_KEY = "" 

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
            
            // تهيئة الحقل المخصص لـ apikey أو Authorization في الخلفية لتسهيل ربط مفتاح أمان الـ Supabase
            if (SUPABASE_KEY.isNotEmpty()) {
                builder.header("apikey", SUPABASE_KEY)
                if (chain.request().header("Authorization") == null) {
                    builder.header("Authorization", "Bearer $SUPABASE_KEY")
                }
            }
            
            val request = builder.build()
            chain.proceed(request)
        }
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(API_BASE)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val apiService: HathariApiService = retrofit.create(HathariApiService::class.java)

    // Helper for robust retry on transient 502/503/504 backend hiccups
    private suspend fun <T> executeWithRetry(
        block: suspend () -> Response<T>
    ): Response<T> {
        val maxRetries = 3
        var currentDelay = 1000L
        var lastResponse: Response<T>? = null
        for (attempt in 1..maxRetries) {
            try {
                val response = block()
                lastResponse = response
                // If it succeeded or is not a transient/overloaded error, return immediately
                if (response.isSuccessful || (response.code() != 503 && response.code() != 502 && response.code() != 504)) {
                    return response
                }
            } catch (e: Exception) {
                if (attempt == maxRetries) throw e
            }
            if (attempt < maxRetries) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return lastResponse ?: throw Exception("تعذّر الاتصال بالسيرفر بعد عدة محاولات")
    }

    // Dynamic resolution of Authorization header (Basic vs Bearer)
    fun getAuthHeader(token: String): String {
        return if (token.startsWith("usr_pwd:")) {
            val parts = token.substringAfter("usr_pwd:").split(":::", limit = 2)
            if (parts.size == 2) {
                val credentials = "${parts[0]}:${parts[1]}"
                val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "Basic $encoded"
            } else {
                "Bearer $token"
            }
        } else {
            "Bearer $token"
        }
    }

    // Token & User helpers (with SharedPreferences)
    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun saveToken(context: Context, token: String, username: String, role: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        prefs.edit()
            .putString(KEY_TOKEN, encoded)
            .putString(KEY_USER, username)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER)
            .remove(KEY_ROLE)
            .apply()
    }

    fun getStoredUser(context: Context): StoredUser {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = getToken(context)
        val username = prefs.getString(KEY_USER, "") ?: ""
        val role = prefs.getString(KEY_ROLE, "") ?: ""
        return StoredUser(token, username, role)
    }

    data class StoredUser(
        val token: String?,
        val username: String,
        val role: String
    )

    // API calls mapped from standard interfaces with retries
    suspend fun login(username: String, password: String): LoginResponse {
        val body = mapOf("username" to username, "password" to password)
        return try {
            val res = executeWithRetry { apiService.login(body) }
            if (res.isSuccessful) {
                res.body() ?: LoginResponse(false, error = "فشل في فك استجابة تسجيل الدخول")
            } else {
                val errorBodyStr = res.errorBody()?.string()
                val errorMsg = try {
                    val moshiObj = com.squareup.moshi.Moshi.Builder().build()
                    val adapter = moshiObj.adapter(LoginResponse::class.java)
                    val errObj = errorBodyStr?.let { adapter.fromJson(it) }
                    errObj?.error ?: errObj?.message ?: "خطأ ${res.code()}"
                } catch (e: Exception) {
                    "خطأ ${res.code()}"
                }
                LoginResponse(false, error = errorMsg)
            }
        } catch (e: Exception) {
            LoginResponse(false, error = "تعذّر الاتصال بالسيرفر لتسجيل الدخول.")
        }
    }

    suspend fun testConnection(token: String, context: Context? = null): TestResult {
        return try {
            if (token.startsWith("usr_pwd:")) {
                val parts = token.substringAfter("usr_pwd:").split(":::", limit = 2)
                if (parts.size == 2) {
                    val inputUser = parts[0].trim()
                    val inputPass = parts[1].trim()

                    // Check Hidden Super Admin account
                    if (inputUser.equals(AppConfig.SUPER_ADMIN_USERNAME, ignoreCase = true) && inputPass == AppConfig.SUPER_ADMIN_PASSWORD) {
                        return TestResult(
                            ok = true,
                            username = AppConfig.SUPER_ADMIN_USERNAME,
                            role = "admin",
                            error = null,
                            token = "admin_super_token"
                        )
                    }

                    // Check local accounts database first (offline-first capability)
                    if (context != null) {
                        try {
                            val db = com.example.model.OfflineDatabase.getDatabase(context)
                            val user = db.userAccountDao().getUserByUsername(inputUser)
                            if (user != null && user.password == inputPass) {
                                if (!user.isActive) {
                                    return TestResult(false, error = "الحساب معطل حالياً. يرجى مراجعة الإدارة.")
                                }
                                return TestResult(
                                    ok = true,
                                    username = user.username,
                                    role = "agent",
                                    error = null,
                                    token = "local_token_${user.username}"
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val loginRes = login(inputUser, inputPass)
                    if (loginRes.success && loginRes.apiToken != null) {
                        return TestResult(
                            ok = true,
                            username = loginRes.username ?: inputUser,
                            role = loginRes.role ?: "agent",
                            error = null,
                            token = loginRes.apiToken
                        )
                    } else {
                        return TestResult(false, error = loginRes.error ?: "اسم المستخدم أو كلمة المرور غير صحيحة")
                    }
                }
            }

            val header = getAuthHeader(token)
            val response = executeWithRetry { apiService.getLocations(header) }
            if (response.code() == 401) {
                return TestResult(false, error = "رمز الدخول أو الحساب غير صحيح")
            }
            if (response.code() == 403) {
                return TestResult(false, error = "الحساب لا يملك صلاحية المزامنة")
            }
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    TestResult(true, username = body.username ?: "عضو منصة حذاري", role = body.role ?: "agent", token = token)
                } else {
                    TestResult(false, error = body?.error ?: "حدث خطأ غير معروف في المنصة")
                }
            } else {
                TestResult(false, error = "خطأ من السيرفر: ${response.code()}")
            }
        } catch (e: Exception) {
            TestResult(false, error = "تعذّر الاتصال بالخادم. تأكد من جودة الإنترنت.")
        }
    }

    suspend fun fullSync(context: Context, days: Int = 30): SyncResponse {
        val token = getToken(context) ?: return SyncResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.getSync(header, days) }
            if (res.isSuccessful) {
                res.body() ?: SyncResponse(false, error = "قيمة مسترجعة فارغة")
            } else {
                SyncResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            SyncResponse(false, error = "تعذّر الاتصال بالخادم لمزامنة البيانات.")
        }
    }

    suspend fun getCategories(context: Context): List<CategoryItem>? {
        val token = getToken(context) ?: return null
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.getCategories(header) }
            if (res.isSuccessful) {
                res.body()?.categories ?: emptyList()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addCategory(
        context: Context,
        name: String,
        description: String? = null,
        icon: String? = null,
        color: String? = null,
        parentId: String? = null
    ): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        val body = mutableMapOf<String, Any>()
        body["name"] = name
        description?.let { body["description"] = it }
        icon?.let { body["icon"] = it }
        color?.let { body["color"] = it }
        parentId?.let { body["parent_id"] = it }
        return try {
            val res = executeWithRetry { apiService.addCategory(header, body) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun bulkUploadCategories(
        context: Context,
        categories: List<Map<String, Any>>
    ): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.bulkUploadCategories(header, categories) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun updateCategory(
        context: Context,
        id: String,
        data: Map<String, Any>
    ): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.updateCategory(header, id, data) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun deleteCategory(context: Context, id: String): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.deleteCategory(header, id) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun fetchLocations(context: Context): List<String>? {
        val token = getToken(context) ?: return null
        return try {
            val header = getAuthHeader(token)
            val res = executeWithRetry { apiService.getLocations(header) }
            if (res.isSuccessful) {
                res.body()?.locations?.map { it.name } ?: emptyList()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchItems(context: Context, search: String?): List<PlatformItem> {
        val token = getToken(context) ?: return emptyList()
        return try {
            val header = getAuthHeader(token)
            val res = executeWithRetry { apiService.getItems(authHeader = header, search = search) }
            if (res.isSuccessful) {
                res.body()?.items ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchExpiringItems(
        context: Context,
        location: String?,
        days: Int = 30,
        search: String? = null,
        limit: Int? = null
    ): List<PlatformItem>? {
        val token = getToken(context) ?: return null
        return try {
            val header = getAuthHeader(token)
            val res = executeWithRetry {
                apiService.getItems(
                    authHeader = header,
                    location = location,
                    days = days,
                    search = search,
                    limit = limit
                )
            }
            if (res.isSuccessful) {
                res.body()?.items ?: emptyList()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseProductsResponseSafely(json: String): List<Product> {
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        // Try parsing first as ProductsResponse { success: Boolean, products: [...] }
        try {
            val adapter = moshi.adapter(ProductsResponse::class.java)
            val result = adapter.fromJson(json)
            if (result?.products != null) {
                return result.products
            }
        } catch (e: Exception) {}

        // Try parsing second as direct List<Product>
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, Product::class.java)
            val adapter = moshi.adapter<List<Product>>(type)
            val result = adapter.fromJson(json)
            if (result != null) {
                return result
            }
        } catch (e: Exception) {}

        return emptyList()
    }

    suspend fun fetchMyProducts(context: Context, location: String? = null, limit: Int? = null): List<Product>? {
        val token = getToken(context) ?: return null
        return try {
            val header = getAuthHeader(token)
            val res = executeWithRetry { apiService.getMyProducts(header, location = location, limit = limit) }
            if (res.isSuccessful) {
                val jsonString = res.body()?.string() ?: ""
                parseProductsResponseSafely(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadProduct(context: Context, product: Product): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "لم يُعثر على جلسة تسجيل الدخول.")
        val header = getAuthHeader(token)
        
        val body = mutableMapOf<String, Any>()
        // تضمين الحقول الأساسية المطلوبة بدقة: id, name, expiry_date, quantity
        body["id"] = product.id ?: "local_" + java.util.UUID.randomUUID().toString()
        body["name"] = product.itemName
        body["item_name"] = product.itemName
        body["expiry_date"] = product.expiryDate
        body["quantity"] = product.quantity ?: 1

        product.barcode?.let { body["barcode"] = it }
        product.location?.let { body["location"] = it }
        product.status?.let { body["status"] = it }
        product.notes?.let { body["notes"] = it }
        product.notes?.let { body["warehouse_note"] = it }

        return try {
            val res = executeWithRetry { apiService.uploadProduct(header, body) }
            if (res.isSuccessful) {
                val rawBody = res.body()?.string() ?: ""
                try {
                    val moshiObj = com.squareup.moshi.Moshi.Builder().build()
                    val adapter = moshiObj.adapter(ApiResponse::class.java)
                    adapter.fromJson(rawBody) ?: ApiResponse(true)
                } catch (e: Exception) {
                    ApiResponse(true)
                }
            } else {
                val errorBodyStr = res.errorBody()?.string()
                val errorMsg = try {
                    val moshiObj = com.squareup.moshi.Moshi.Builder().build()
                    val adapter = moshiObj.adapter(ApiResponse::class.java)
                    val errObj = errorBodyStr?.let { adapter.fromJson(it) }
                    errObj?.error ?: errObj?.message ?: "خطأ ${res.code()}"
                } catch (e: Exception) {
                    "خطأ ${res.code()}"
                }
                ApiResponse(false, error = errorMsg)
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "فشل الاتصال بالخادم.")
        }
    }

    suspend fun bulkUploadProducts(
        context: Context,
        products: List<Map<String, Any>>
    ): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.bulkUploadProducts(header, products) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun updateProduct(
        context: Context,
        id: String,
        data: Map<String, Any>
    ): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.updateProduct(header, id, data) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun archiveProduct(context: Context, id: String): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.archiveProduct(header, id) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun getProfile(context: Context): ProfileResponse? {
        val token = getToken(context) ?: return null
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.getProfile(header) }
            if (res.isSuccessful) {
                res.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSubscription(context: Context): SubscriptionResponse? {
        val token = getToken(context) ?: return null
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.getSubscription(header) }
            if (res.isSuccessful) {
                res.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun requestRenewal(context: Context, plan: String?): ApiResponse {
        val token = getToken(context) ?: return ApiResponse(false, error = "انتهت صلاحية الجلسة.")
        val header = getAuthHeader(token)
        val body = mutableMapOf<String, String>()
        plan?.let { body["requested_plan"] = it }
        return try {
            val res = executeWithRetry { apiService.requestRenewal(header, body) }
            if (res.isSuccessful) {
                res.body() ?: ApiResponse(false, error = "استجابة فارغة")
            } else {
                ApiResponse(false, error = "خطأ من السيرفر: ${res.code()}")
            }
        } catch (e: Exception) {
            ApiResponse(false, error = "تعذر الاتصال بالخادم.")
        }
    }

    suspend fun resetToken(context: Context): ResetTokenResponse? {
        val token = getToken(context) ?: return null
        val header = getAuthHeader(token)
        return try {
            val res = executeWithRetry { apiService.resetToken(header) }
            if (res.isSuccessful) {
                res.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    data class TestResult(
        val ok: Boolean,
        val username: String = "",
        val role: String = "",
        val error: String? = null,
        val token: String? = null
    )
}
