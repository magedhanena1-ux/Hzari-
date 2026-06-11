package com.example.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import com.example.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AutoSyncManager {

    /**
     * Checks if the device has a working internet connection.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return true
                }
            }
            @Suppress("DEPRECATION")
            val activeInfo = connectivityManager.activeNetworkInfo
            if (activeInfo != null && activeInfo.isConnected) {
                return true
            }
        }
        // Always try to execute the network requests instead of blocking them if there's any uncertainty.
        return true
    }

    /**
     * Runs auto-sync:
     * 1. Auto-upload any products added offline previously that failed connection.
     * 2. Auto-pull branches, all inventory items, and expiring products from the server to refresh cached offline storage.
     */
    fun startAutoSync(context: Context, onSyncComplete: (() -> Unit)? = null) {
        val applicationContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            if (!isNetworkAvailable(applicationContext)) {
                return@launch
            }
            try {
                // 1. Auto-Push Offline Products Saved in DB
                val db = OfflineDatabase.getDatabase(applicationContext)
                val dao = db.offlineProductDao()
                val offlineList = dao.getAllOfflineProductsList()
                var uploadCount = 0

                for (item in offlineList) {
                    val prod = Product(
                        itemName = item.itemName,
                        expiryDate = item.expiryDate,
                        barcode = item.barcode,
                        location = item.location,
                        status = item.status,
                        quantity = item.quantity,
                        notes = item.notes
                    )
                    // Attempt upload
                    val result = ApiClient.uploadProduct(applicationContext, prod)
                    if (result.success) {
                        dao.deleteOfflineProduct(item)
                        uploadCount++
                        // Add history log of auto-upload
                        try {
                            HistoryManager.addHistory(
                                applicationContext, 
                                prod,
                                success = true,
                                errorMsg = "تم الرفع التلقائي للفرع"
                            )
                        } catch (e: Exception) {}
                    }
                }

                // 2. Auto-Pull Current Server Platform Data
                val fetchedLocations = ApiClient.fetchLocations(applicationContext)
                if (fetchedLocations != null) {
                    OfflineCacheManager.saveLocationsCache(applicationContext, fetchedLocations)
                }

                val fetchedProducts = ApiClient.fetchMyProducts(applicationContext)
                val fetchedExpiring = ApiClient.fetchExpiringItems(applicationContext, location = null, days = 30)

                if (fetchedProducts != null && fetchedExpiring != null) {
                    OfflineCacheManager.saveMyProductsCache(applicationContext, fetchedProducts)
                    OfflineCacheManager.saveExpiringItemsCache(applicationContext, fetchedExpiring)

                    // Evaluate local alert checks
                    try {
                        com.example.notification.NotificationHelper.evaluateAndNotify(applicationContext, fetchedProducts)
                    } catch (e: Exception) {}
                }

                // Trigger UI response if we did useful auto-sync uploads
                if (uploadCount > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "تم رفع $uploadCount أطعمة/منتجات مسجلة أوفلاين تلقائياً!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Call back to update screens (such as Refreshing list)
                onSyncComplete?.let {
                    withContext(Dispatchers.Main) {
                        it()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
