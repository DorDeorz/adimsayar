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

@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey val date: String,
    val kg: Double,
)

@Entity(tableName = "hourly_steps")
data class HourlySteps(
    @PrimaryKey val hour: String,
    val steps: Long,
)

@Entity(tableName = "water")
data class WaterDay(
    @PrimaryKey val date: String,
    val ml: Int,
)

@Entity(tableName = "streak_freezes")
data class StreakFreeze(
    @PrimaryKey val date: String,
)

@Entity(tableName = "medals")
data class KeptMedal(
    @PrimaryKey val id: String,
    val date: String,
)

@Dao
interface WaterDao {

    @Query("SELECT * FROM water ORDER BY date")
    fun observeAll(): Flow<List<WaterDay>>

    @Query("SELECT * FROM water WHERE date = :date")
    suspend fun get(date: String): WaterDay?

    @Upsert
    suspend fun upsert(day: WaterDay)
}

@Dao
interface KeepDao {

    @Query("SELECT * FROM streak_freezes")
    fun observeFreezes(): Flow<List<StreakFreeze>>

    @Query("SELECT * FROM streak_freezes")
    suspend fun loadFreezes(): List<StreakFreeze>

    @Upsert
    suspend fun addFreeze(freeze: StreakFreeze)

    @Query("DELETE FROM streak_freezes WHERE date = :date")
    suspend fun removeFreeze(date: String)

    @Query("SELECT * FROM medals")
    fun observeMedals(): Flow<List<KeptMedal>>

    @Query("SELECT * FROM medals")
    suspend fun loadMedals(): List<KeptMedal>

    @Upsert
    suspend fun keepMedals(medals: List<KeptMedal>)
}

@Dao
interface WeightDao {

    @Query("SELECT * FROM weights ORDER BY date")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WeightEntry?

    @Upsert
    suspend fun upsert(entry: WeightEntry)

    @Query("DELETE FROM weights WHERE date = :date")
    suspend fun delete(date: String)
}

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

    @Query("SELECT * FROM daily_steps WHERE date >= :from")
    abstract suspend fun loadPhoneFrom(from: String): List<DailySteps>

    @Query("SELECT * FROM hourly_steps WHERE hour >= :from AND hour < :to ORDER BY hour")
    abstract suspend fun loadHours(from: String, to: String): List<HourlySteps>

    @Query("SELECT * FROM hourly_steps WHERE hour = :hour")
    abstract suspend fun getHour(hour: String): HourlySteps?

    @Upsert
    abstract suspend fun upsertHour(hour: HourlySteps)

    @Transaction
    open suspend fun addHours(hours: Map<String, Long>) {
        for ((hour, steps) in hours) {
            val current = getHour(hour)?.steps ?: 0L
            upsertHour(HourlySteps(hour, current + steps))
        }
    }

    @Upsert
    abstract suspend fun upsertBand(days: List<BandSteps>)

    @Query("DELETE FROM band_steps")
    abstract suspend fun clearBand()
}

@Database(
    entities = [
        DailySteps::class,
        BandSteps::class,
        WeightEntry::class,
        HourlySteps::class,
        WaterDay::class,
        StreakFreeze::class,
        KeptMedal::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class StepDatabase : RoomDatabase() {
    abstract fun dailySteps(): DailyStepsDao

    abstract fun weights(): WeightDao

    abstract fun water(): WaterDao

    abstract fun keep(): KeepDao

    companion object {
        @Volatile
        private var instance: StepDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `band_steps` (`date` TEXT NOT NULL, `steps` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `weights` (`date` TEXT NOT NULL, `kg` REAL NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_steps` (`hour` TEXT NOT NULL, `steps` INTEGER NOT NULL, PRIMARY KEY(`hour`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `water` (`date` TEXT NOT NULL, `ml` INTEGER NOT NULL, PRIMARY KEY(`date`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `streak_freezes` (`date` TEXT NOT NULL, PRIMARY KEY(`date`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `medals` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun get(context: Context): StepDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StepDatabase::class.java, "adimsayar.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
