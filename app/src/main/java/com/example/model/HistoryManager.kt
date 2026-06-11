package com.example.model

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.*

object HistoryManager {
    private const val PREFS_NAME = "hathari_prefs"
    private const val KEY_HISTORY = "hathari_history"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val listType = Types.newParameterizedType(List::class.java, HistoryEntry::class.java)
    private val jsonAdapter = moshi.adapter<List<HistoryEntry>>(listType)

    fun getHistory(context: Context): List<HistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            jsonAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistory(context: Context, product: Product, success: Boolean, errorMsg: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = getHistory(context).toMutableList()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            product = product,
            result = if (success) "success" else "error",
            errorMessage = errorMsg,
            timestamp = timestamp
        )

        currentList.add(0, entry) // Insert at top

        // Trim to 200 items according to guidelines
        if (currentList.size > 200) {
            currentList.removeAt(currentList.lastIndex)
        }

        try {
            val json = jsonAdapter.toJson(currentList)
            prefs.edit().putString(KEY_HISTORY, json).apply()
        } catch (e: Exception) {
            // Ignore write errors
        }
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun exportHistoryJson(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HISTORY, "[]") ?: "[]"
    }

    fun importHistoryJson(context: Context, json: String): Boolean {
        return try {
            val list = jsonAdapter.fromJson(json)
            if (list != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_HISTORY, json).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
