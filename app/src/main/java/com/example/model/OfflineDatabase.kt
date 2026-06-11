package com.example.model

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_products")
data class OfflineProduct(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val itemName: String,
    val barcode: String? = null,
    val expiryDate: String, // YYYY-MM-DD
    val location: String? = null,
    val status: String? = null,
    val quantity: Int? = 1,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface OfflineProductDao {
    @Query("SELECT * FROM offline_products ORDER BY createdAt DESC")
    fun getAllOfflineProducts(): Flow<List<OfflineProduct>>

    @Query("SELECT * FROM offline_products ORDER BY createdAt DESC")
    suspend fun getAllOfflineProductsList(): List<OfflineProduct>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineProduct(product: OfflineProduct): Long

    @Delete
    suspend fun deleteOfflineProduct(product: OfflineProduct)

    @Query("DELETE FROM offline_products WHERE localId = :id")
    suspend fun deleteById(id: Int)
    
    @Query("DELETE FROM offline_products")
    suspend fun deleteAll()
}

@Entity(tableName = "local_products")
data class LocalProduct(
    @PrimaryKey val id: String,
    val name: String,
    val barcode: String? = null,
    val expiryDate: String, // YYYY-MM-DD
    val location: String? = null,
    val status: String? = null,
    val stockQuantity: Int = 1,
    val notes: String? = null,
    val daysRemaining: Int? = null,
    val colorStatus: String? = null,
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface LocalProductDao {
    @Query("SELECT * FROM local_products ORDER BY createdAt DESC")
    fun getAllLocalProducts(): Flow<List<LocalProduct>>

    @Query("SELECT * FROM local_products ORDER BY createdAt DESC")
    suspend fun getAllLocalProductsList(): List<LocalProduct>

    @Query("SELECT * FROM local_products WHERE isSynced = 0 ORDER BY createdAt DESC")
    suspend fun getUnsyncedProducts(): List<LocalProduct>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: LocalProduct)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<LocalProduct>)

    @Query("DELETE FROM local_products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_products WHERE isSynced = 1")
    suspend fun deleteSyncedProducts()

    @Query("DELETE FROM local_products")
    suspend fun deleteAll()
}

@Database(entities = [OfflineProduct::class, LocalProduct::class], version = 2, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineProductDao(): OfflineProductDao
    abstract fun localProductDao(): LocalProductDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        fun getDatabase(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "offline_products_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
