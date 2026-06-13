package com.example.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.work.*
import com.example.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

object AutoSyncManager {

    /**
     * Set up WorkManager for background synchronization.
     */
    fun setupWorkManager(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // One-time sync triggered to push changes immediately
        val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(oneTimeRequest)

        // Periodic sync to check updates every 1 hour
        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "HathariSystemPeriodicSync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

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
        return false
    }

    /**
     * Runs auto-sync:
     * 1. Queue WorkManager for bulletproof background execution.
     * 2. Perform safe, non-blocking foreground cache updates/pushes if network is online.
     */
    fun startAutoSync(context: Context, onSyncComplete: (() -> Unit)? = null) {
        val applicationContext = context.applicationContext
        
        // Always trigger WorkManager registration
        try {
            setupWorkManager(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (!isNetworkAvailable(applicationContext)) {
                withContext(Dispatchers.Main) {
                    onSyncComplete?.invoke()
                }
                return@launch
            }
            try {
                val db = OfflineDatabase.getDatabase(applicationContext)
                val offlineDao = db.offlineProductDao()
                val localDao = db.localProductDao()

                // A. Upload unsynced legacies
                val legacyProducts = offlineDao.getAllOfflineProductsList()
                var uploadCount = 0

                for (item in legacyProducts) {
                    val prod = Product(
                        itemName = item.itemName,
                        expiryDate = item.expiryDate,
                        barcode = item.barcode,
                        location = item.location,
                        status = item.status,
                        quantity = item.quantity,
                        notes = item.notes,
                        imagePath = item.imagePath
                    )
                    val result = ApiClient.uploadProduct(applicationContext, prod)
                    if (result.success) {
                        offlineDao.deleteOfflineProduct(item)
                        uploadCount++
                        
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

                // B. Upload unsynced LocalProducts
                val unsyncedLocal = localDao.getUnsyncedProducts()
                for (item in unsyncedLocal) {
                    val prod = Product(
                        itemName = item.name,
                        expiryDate = item.expiryDate,
                        barcode = item.barcode,
                        location = item.location,
                        status = item.status,
                        quantity = item.stockQuantity,
                        notes = item.notes,
                        imagePath = item.imagePath
                    )
                    val result = ApiClient.uploadProduct(applicationContext, prod)
                    if (result.success) {
                        localDao.deleteById(item.id)
                        uploadCount++

                        try {
                            HistoryManager.addHistory(
                                applicationContext,
                                prod,
                                success = true,
                                errorMsg = "تم الرفع التلقائي لمنتج محلي"
                            )
                        } catch (e: Exception) {}
                    }
                }

                // C. Pull Current Server Platform Data
                val fetchedLocations = ApiClient.fetchLocations(applicationContext)
                if (fetchedLocations != null) {
                    OfflineCacheManager.saveLocationsCache(applicationContext, fetchedLocations)
                }

                val fetchedProducts = ApiClient.fetchMyProducts(applicationContext)
                val fetchedExpiring = ApiClient.fetchExpiringItems(applicationContext, location = null, days = 30)

                if (fetchedProducts != null) {
                    // Update database Cache
                    localDao.deleteSyncedProducts()
                    val dbRecords = fetchedProducts.map { p ->
                        LocalProduct(
                            id = p.id ?: UUID.randomUUID().toString(),
                            name = p.itemName,
                            barcode = p.barcode,
                            expiryDate = p.expiryDate,
                            location = p.location,
                            status = p.status,
                            stockQuantity = p.quantity ?: 1,
                            notes = p.notes,
                            daysRemaining = p.daysRemaining,
                            colorStatus = p.colorStatus,
                            isSynced = true
                        )
                    }
                    localDao.insertProducts(dbRecords)

                    OfflineCacheManager.saveMyProductsCache(applicationContext, fetchedProducts)
                }

                if (fetchedExpiring != null) {
                    OfflineCacheManager.saveExpiringItemsCache(applicationContext, fetchedExpiring)
                }

                if (fetchedProducts != null) {
                    try {
                        com.example.notification.NotificationHelper.evaluateAndNotify(applicationContext, fetchedProducts)
                    } catch (e: Exception) {}
                }

                // Trigger UI response if useful auto-sync uploads completed
                if (uploadCount > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "تم رفع $uploadCount أطعمة/منتجات مسجلة أوفلاين تلقائياً بنجاح!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Call back to update screens (such as refreshing list)
                onSyncComplete?.let {
                    withContext(Dispatchers.Main) {
                        it()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onSyncComplete?.invoke()
                }
            }
        }
    }
}
