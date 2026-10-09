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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey val date: String,
    val steps: Long,
)

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

    @Query("SELECT * FROM daily_steps WHERE date >= :from ORDER BY date")
    abstract fun observeFrom(from: String): Flow<List<DailySteps>>

    @Query("SELECT * FROM daily_steps WHERE date >= :from ORDER BY date")
    abstract suspend fun loadFrom(from: String): List<DailySteps>

    @Query("SELECT * FROM daily_steps ORDER BY date")
    abstract fun observeAll(): Flow<List<DailySteps>>

}

@Database(entities = [DailySteps::class], version = 1, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {
    abstract fun dailySteps(): DailyStepsDao

    companion object {
        @Volatile
        private var instance: StepDatabase? = null

        fun get(context: Context): StepDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StepDatabase::class.java, "adimsayar.db")
                .build()
                .also { instance = it }
        }
    }
}
