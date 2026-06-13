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
    val createdAt: Long = System.currentTimeMillis(),
    val imagePath: String? = null
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
    val warehouseName: String? = null,
    val isDamaged: Int = 0, // 0 = standard, 1 = damaged/archived
    val damagedDate: String? = null,
    val damagedQuantity: Int = 0,
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val imagePath: String? = null
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

@Entity(tableName = "subscription_requests")
data class SubscriptionRequest(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val whatsapp: String,
    val email: String,
    val companyName: String,
    val username: String,
    val password: String,
    val duration: String, // e.g. "3 Months", "6 Months", "1 Year"
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SubscriptionRequestDao {
    @Query("SELECT * FROM subscription_requests ORDER BY createdAt DESC")
    fun getAllRequests(): Flow<List<SubscriptionRequest>>

    @Query("SELECT * FROM subscription_requests ORDER BY createdAt DESC")
    suspend fun getAllRequestsList(): List<SubscriptionRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: SubscriptionRequest)

    @Query("UPDATE subscription_requests SET status = :status WHERE id = :id")
    suspend fun updateRequestStatus(id: String, status: String)

    @Query("DELETE FROM subscription_requests WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Entity(tableName = "user_accounts")
data class UserAccount(
    @PrimaryKey val username: String,
    val password: String,
    val name: String,
    val companyName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    val subscriptionExpiry: Long, // expiry timestamp in millis
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts ORDER BY createdAt DESC")
    fun getAllUsersFlow(): Flow<List<UserAccount>>

    @Query("SELECT * FROM user_accounts ORDER BY createdAt DESC")
    suspend fun getAllUsers(): List<UserAccount>

    @Query("SELECT * FROM user_accounts WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccount)

    @Query("DELETE FROM user_accounts WHERE username = :username")
    suspend fun deleteUser(username: String)
}

@Database(entities = [OfflineProduct::class, LocalProduct::class, SubscriptionRequest::class, UserAccount::class], version = 4, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineProductDao(): OfflineProductDao
    abstract fun localProductDao(): LocalProductDao
    abstract fun subscriptionRequestDao(): SubscriptionRequestDao
    abstract fun userAccountDao(): UserAccountDao

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
