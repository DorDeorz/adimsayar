package com.dordeorz.adimsayar.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey val date: String,
    val steps: Long,
)

@Entity(tableName = "band_steps")
data class BandSteps(
    @PrimaryKey val date: String,
    val steps: Long,
)

private const val MERGED = "SELECT date, steps FROM band_steps UNION ALL " +
    "SELECT date, steps FROM daily_steps WHERE date NOT IN (SELECT date FROM band_steps)"

@Dao
abstract class DailyStepsDao {

    @Query("SELECT * FROM daily_steps WHERE date = :date")
    abstract suspend fun get(date: String): DailySteps?

    @Upsert
    abstract suspend fun upsert(day: DailySteps)

    @Transaction
    open suspend fun add(days: Map<String, Long>) {
        for ((date, steps) in days) {
            val current = get(date)?.steps ?: 0L
            upsert(DailySteps(date, current + steps))
        }
    }

    @Transaction
    open suspend fun replace(days: Map<String, Long>) {
        for ((date, steps) in days) upsert(DailySteps(date, steps))
    }

    @Query("SELECT * FROM ($MERGED) WHERE date = :date")
    abstract suspend fun getMerged(date: String): DailySteps?

    @Query("SELECT * FROM ($MERGED) WHERE date >= :from ORDER BY date")
    abstract fun observeFrom(from: String): Flow<List<DailySteps>>

    @Query("SELECT * FROM ($MERGED) WHERE date >= :from ORDER BY date")
    abstract suspend fun loadFrom(from: String): List<DailySteps>

    @Query("SELECT * FROM ($MERGED) ORDER BY date")
    abstract fun observeAll(): Flow<List<DailySteps>>

    @Query("SELECT * FROM ($MERGED) ORDER BY date")
    abstract suspend fun loadAll(): List<DailySteps>

    @Upsert
    abstract suspend fun upsertBand(days: List<BandSteps>)

    @Query("DELETE FROM band_steps")
    abstract suspend fun clearBand()
}

@Database(entities = [DailySteps::class, BandSteps::class], version = 2, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {
    abstract fun dailySteps(): DailyStepsDao

    companion object {
        @Volatile
        private var instance: StepDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `band_steps` (`date` TEXT NOT NULL, `steps` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        fun get(context: Context): StepDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StepDatabase::class.java, "adimsayar.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
