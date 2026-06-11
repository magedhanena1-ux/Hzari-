package com.example.model

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

object OfflineCacheManager {
    private const val STORES_CACHE_FILE = "cached_products.json"
    private const val EXPIRING_CACHE_FILE = "cached_expiring.json"
    private const val LOCATIONS_CACHE_FILE = "cached_locations.json"

    private val moshi = Moshi.Builder().build()

    fun saveMyProductsCache(context: Context, products: List<Product>) {
        try {
            val type = Types.newParameterizedType(List::class.java, Product::class.java)
            val adapter = moshi.adapter<List<Product>>(type)
            val json = adapter.toJson(products)
            val file = File(context.filesDir, STORES_CACHE_FILE)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMyProductsCache(context: Context): List<Product> {
        return try {
            val file = File(context.filesDir, STORES_CACHE_FILE)
            if (!file.exists()) return emptyList()
            val type = Types.newParameterizedType(List::class.java, Product::class.java)
            val adapter = moshi.adapter<List<Product>>(type)
            adapter.fromJson(file.readText()) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveExpiringItemsCache(context: Context, items: List<PlatformItem>) {
        try {
            val type = Types.newParameterizedType(List::class.java, PlatformItem::class.java)
            val adapter = moshi.adapter<List<PlatformItem>>(type)
            val json = adapter.toJson(items)
            val file = File(context.filesDir, EXPIRING_CACHE_FILE)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadExpiringItemsCache(context: Context): List<PlatformItem> {
        return try {
            val file = File(context.filesDir, EXPIRING_CACHE_FILE)
            if (!file.exists()) return emptyList()
            val type = Types.newParameterizedType(List::class.java, PlatformItem::class.java)
            val adapter = moshi.adapter<List<PlatformItem>>(type)
            adapter.fromJson(file.readText()) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveLocationsCache(context: Context, locations: List<String>) {
        try {
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(type)
            val json = adapter.toJson(locations)
            val file = File(context.filesDir, LOCATIONS_CACHE_FILE)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadLocationsCache(context: Context): List<String> {
        return try {
            val file = File(context.filesDir, LOCATIONS_CACHE_FILE)
            if (!file.exists()) return emptyList()
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(type)
            adapter.fromJson(file.readText()) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
