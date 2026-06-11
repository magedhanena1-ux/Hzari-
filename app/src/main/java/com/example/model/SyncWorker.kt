package com.example.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.api.ApiClient
import java.util.UUID

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val applicationContext = applicationContext
        
        try {
            val db = OfflineDatabase.getDatabase(applicationContext)
            val offlineDao = db.offlineProductDao()
            val localDao = db.localProductDao()
            
            // 1. Process legacy OfflineProducts
            val oldOfflineProducts = offlineDao.getAllOfflineProductsList()
            for (p in oldOfflineProducts) {
                val prod = Product(
                    itemName = p.itemName,
                    expiryDate = p.expiryDate,
                    barcode = p.barcode,
                    location = p.location,
                    status = p.status,
                    quantity = p.quantity,
                    notes = p.notes
                )
                val response = ApiClient.uploadProduct(applicationContext, prod)
                if (response.success) {
                    offlineDao.deleteOfflineProduct(p)
                    // Save history of success
                    try {
                        HistoryManager.addHistory(applicationContext, prod, success = true, errorMsg = "تم الرفع من قاعدة البيانات")
                    } catch (e: Exception) {}
                }
            }

            // 2. Process unsynced LocalProducts
            val unsyncedLocal = localDao.getUnsyncedProducts()
            for (p in unsyncedLocal) {
                val prod = Product(
                    itemName = p.name,
                    expiryDate = p.expiryDate,
                    barcode = p.barcode,
                    location = p.location,
                    status = p.status,
                    quantity = p.stockQuantity,
                    notes = p.notes
                )
                val response = ApiClient.uploadProduct(applicationContext, prod)
                if (response.success) {
                    localDao.deleteById(p.id) // Delete temporary offline copy
                    try {
                        HistoryManager.addHistory(applicationContext, prod, success = true, errorMsg = "تم رفع التعديل/الإضافة لـ " + p.name)
                    } catch (e: Exception) {}
                }
            }

            // 3. Fetch Locations
            val fetchedLocations = ApiClient.fetchLocations(applicationContext)
            if (fetchedLocations != null) {
                OfflineCacheManager.saveLocationsCache(applicationContext, fetchedLocations)
            }

            // 4. Fetch Products and update local Database
            val fetchedProducts = ApiClient.fetchMyProducts(applicationContext)
            if (fetchedProducts != null) {
                // Remove existing synced products
                localDao.deleteSyncedProducts()
                
                // Map to room records
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
                
                // Bulk insert
                localDao.insertProducts(dbRecords)
                
                // Update JSON file cache as well
                OfflineCacheManager.saveMyProductsCache(applicationContext, fetchedProducts)
                
                // Dynamic alerts trigger
                try {
                    com.example.notification.NotificationHelper.evaluateAndNotify(applicationContext, fetchedProducts)
                } catch (e: Exception) {}
            }

            // 5. Fetch expiring list
            val fetchedExpiring = ApiClient.fetchExpiringItems(applicationContext, location = null, days = 30)
            if (fetchedExpiring != null) {
                OfflineCacheManager.saveExpiringItemsCache(applicationContext, fetchedExpiring)
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Retrying ensures WorkManager runs this again when network conditions improve
            return Result.retry()
        }
    }
}
