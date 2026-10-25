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

@Entity(tableName = "band_hours", primaryKeys = ["hour", "file"])
data class BandHour(
    val hour: String,
    val file: Long,
    val steps: Long,
)

data class HourTotal(val hour: String, val steps: Long)

@Entity(tableName = "band_samples")
data class BandSample(
    @PrimaryKey val minute: Long,
    val heart: Int,
    val spo2: Int,
    val stress: Int,
)

@Entity(tableName = "band_sleep")
data class BandSleep(
    @PrimaryKey val bedtime: Long,
    val wake: Long,
    val minutes: Int,
    val deep: Int,
    val light: Int,
    val rem: Int,
    val awake: Int,
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
interface BandHealthDao {
    @Upsert
    suspend fun upsertSamples(samples: List<BandSample>)

    @Query("SELECT * FROM band_samples WHERE minute >= :from AND minute < :to ORDER BY minute")
    suspend fun samples(from: Long, to: Long): List<BandSample>

    @Query("SELECT * FROM band_samples WHERE heart > 0 ORDER BY minute DESC LIMIT 1")
    suspend fun latestHeart(): BandSample?

    @Upsert
    suspend fun upsertSleep(sleep: BandSleep)

    @Query("SELECT * FROM band_sleep WHERE wake >= :from AND wake < :to ORDER BY wake")
    suspend fun sleep(from: Long, to: Long): List<BandSleep>
}

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

    @Upsert
    abstract suspend fun upsertBandHours(hours: List<BandHour>)

    @Query("SELECT hour, SUM(steps) AS steps FROM band_hours WHERE hour >= :from GROUP BY hour")
    abstract suspend fun loadBandHours(from: String): List<HourTotal>
}

@Database(
    entities = [
        DailySteps::class,
        BandSteps::class,
        BandHour::class,
        BandSample::class,
        BandSleep::class,
        WeightEntry::class,
        HourlySteps::class,
        WaterDay::class,
        StreakFreeze::class,
        KeptMedal::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class StepDatabase : RoomDatabase() {
    abstract fun dailySteps(): DailyStepsDao

    abstract fun weights(): WeightDao

    abstract fun water(): WaterDao

    abstract fun keep(): KeepDao

    abstract fun bandHealth(): BandHealthDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `band_hours` (`hour` TEXT NOT NULL, `file` INTEGER NOT NULL, `steps` INTEGER NOT NULL, PRIMARY KEY(`hour`, `file`))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `band_samples` (`minute` INTEGER NOT NULL, `heart` INTEGER NOT NULL, `spo2` INTEGER NOT NULL, `stress` INTEGER NOT NULL, PRIMARY KEY(`minute`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `band_sleep` (`bedtime` INTEGER NOT NULL, `wake` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `deep` INTEGER NOT NULL, `light` INTEGER NOT NULL, `rem` INTEGER NOT NULL, `awake` INTEGER NOT NULL, PRIMARY KEY(`bedtime`))")
            }
        }

        fun get(context: Context): StepDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StepDatabase::class.java, "adimsayar.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                .also { instance = it }
        }
    }
}
