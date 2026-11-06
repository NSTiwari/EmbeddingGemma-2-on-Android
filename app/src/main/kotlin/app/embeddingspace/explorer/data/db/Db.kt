package app.embeddingspace.explorer.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** One point on the map. The raw 256-d vector is stored as a float32 blob. */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val source: String,
    val type: String,
    val title: String,
    val text: String? = null,
    val mediaPath: String? = null,
    val thumbPath: String? = null,
    val creator: String? = null,
    val license: String? = null,
    val licenseUrl: String? = null,
    val sourceUrl: String? = null,
    val concept: String? = null,
    val durationMs: Long = 0L,
    val createdAt: Long = 0L,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val vec: ByteArray,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val clusterId: Int = -1,
    val tentative: Boolean = false,
)

@Entity(tableName = "clusters")
data class ClusterEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val guess: Boolean,
    val colorIndex: Int,
    val words: String = "",
)

@Entity(tableName = "pending")
data class PendingEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val source: String,
    val title: String,
    val stage: String,
    val error: String? = null,
    val attempts: Int = 0,
    val createdAt: Long = 0L,
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt, id")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM items WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("UPDATE items SET x = :x, y = :y, z = :z, clusterId = :cluster, tentative = :tentative WHERE id = :id")
    suspend fun updatePlacement(id: String, x: Float, y: Float, z: Float, cluster: Int, tentative: Boolean)

    @Transaction
    suspend fun updatePlacements(list: List<ItemEntity>) {
        for (e in list) updatePlacement(e.id, e.x, e.y, e.z, e.clusterId, e.tentative)
    }
}

@Dao
interface ClusterDao {
    @Query("SELECT * FROM clusters ORDER BY id")
    suspend fun all(): List<ClusterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ClusterEntity>)

    @Query("DELETE FROM clusters")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(list: List<ClusterEntity>) {
        deleteAll()
        insertAll(list)
    }
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending ORDER BY createdAt")
    suspend fun all(): List<PendingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PendingEntity)

    @Query("DELETE FROM pending WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending")
    suspend fun deleteAll()
}

/** Every schema change gets a real migration here. User data is never wiped on an update. */
object Migrations {
    /** Version 2 dropped collections (and the lasso that made them). Items, clusters and the queue are untouched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `collection_items`")
            db.execSQL("DROP TABLE IF EXISTS `collections`")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}

@Database(
    entities = [ItemEntity::class, ClusterEntity::class, PendingEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ExplorerDb : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun clusters(): ClusterDao
    abstract fun pending(): PendingDao

    companion object {
        fun open(context: Context): ExplorerDb =
            Room.databaseBuilder(context.applicationContext, ExplorerDb::class.java, "explorer.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
