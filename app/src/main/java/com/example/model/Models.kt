package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Product(
    val id: String? = null,
    @Json(name = "item_name") val itemName: String,
    val barcode: String? = null,
    @Json(name = "expiry_date") val expiryDate: String, // YYYY-MM-DD
    val location: String? = null,
    val status: String? = null,
    val quantity: Int? = 1,
    val notes: String? = null,
    @Json(name = "days_remaining") val daysRemaining: Int? = null,
    @Json(name = "color_status") val colorStatus: String? = null, // "red", "yellow", "green"
    @Json(name = "created_at") val createdAt: String? = null,
    val imagePath: String? = null
)

@JsonClass(generateAdapter = true)
data class LocationItem(
    val id: String,
    val name: String,
    @Json(name = "last_sync_at") val lastSyncAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PlatformItem(
    val id: String? = null,
    val name: String? = null,
    @Json(name = "item_name") val rawItemName: String? = null,
    val barcode: String? = null,
    @Json(name = "expiry_date") val expiryDate: String? = null,
    @Json(name = "days_remaining") val daysRemaining: Int? = null,
    @Json(name = "color_status") val colorStatus: String? = null,
    val location: String? = null,
    val status: String? = null,
    val quantity: Int? = null
) {
    val itemName: String get() = name ?: rawItemName ?: ""
}

@JsonClass(generateAdapter = true)
data class ApiResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    @Json(name = "product_id") val productId: String? = null,
    @Json(name = "item_name") val itemName: String? = null,
    @Json(name = "expiry_date") val expiryDate: String? = null,
    @Json(name = "days_remaining") val daysRemaining: Int? = null,
    @Json(name = "color_status") val colorStatus: String? = null
)

@JsonClass(generateAdapter = true)
data class LocationsResponse(
    val success: Boolean,
    val locations: List<LocationItem>? = null,
    val username: String? = null,
    val role: String? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ItemsResponse(
    val success: Boolean,
    val items: List<PlatformItem>? = null
)

@JsonClass(generateAdapter = true)
data class ProductsResponse(
    val success: Boolean,
    val products: List<Product>? = null
)

@JsonClass(generateAdapter = true)
data class HistoryEntry(
    val id: String,
    val product: Product,
    val result: String, // "success" or "error"
    val errorMessage: String? = null,
    val timestamp: String
)

@JsonClass(generateAdapter = true)
data class CategoryItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @Json(name = "parent_id") val parentId: String? = null
)

@JsonClass(generateAdapter = true)
data class CategoriesResponse(
    val success: Boolean,
    val categories: List<CategoryItem>? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val success: Boolean,
    val username: String? = null,
    val email: String? = null,
    val role: String? = null,
    @Json(name = "api_token") val apiToken: String? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SubscriptionResponse(
    val success: Boolean,
    @Json(name = "plan_type") val planType: String? = null,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "days_remaining") val daysRemaining: Int? = null,
    val status: String? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ResetTokenResponse(
    val success: Boolean,
    @Json(name = "api_token") val apiToken: String? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val success: Boolean,
    val categories: List<CategoryItem>? = null,
    val items: List<PlatformItem>? = null,
    val profile: ProfileResponse? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val success: Boolean,
    @Json(name = "api_token") val apiToken: String? = null,
    val username: String? = null,
    val role: String? = null,
    val error: String? = null,
    val message: String? = null
)
