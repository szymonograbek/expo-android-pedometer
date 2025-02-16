package expo.modules.androidpedometer

import android.content.Context
import androidx.room.Room
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.io.Closeable

private const val TAG = "StepsDataStore"

class StepsDataStore(context: Context) : Closeable {
    private val database = Room.databaseBuilder(
        context,
        StepsDatabase::class.java,
        "steps_database"
    ).fallbackToDestructiveMigration()
        .build()

    private val dao = database.stepsDao()

    suspend fun loadStepsForTimestamp(timestamp: Instant): Int = withContext(Dispatchers.IO) {
        try {
            val truncatedTimestamp = TimeUtils.truncateToMinute(timestamp)
            dao.getStepsForTimestamp(truncatedTimestamp.toString())?.steps ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load steps for timestamp", e)
            0
        }
    }

    suspend fun loadStepsForDate(date: LocalDate): Int = withContext(Dispatchers.IO) {
        try {
            val startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant()
            val endOfDay = TimeUtils.getEndOfDay(startOfDay)
            dao.getTotalStepsInRange(startOfDay.toString(), endOfDay.toString()) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load steps for date", e)
            0
        }
    }

    suspend fun incrementSteps(timestamp: Instant, increment: Int) {
        if (increment <= 0) return
        withContext(Dispatchers.IO) {
            try {
                val truncatedTimestamp = TimeUtils.truncateToMinute(timestamp)
                val timestampStr = truncatedTimestamp.toString()
                
                val currentSteps = dao.getStepsForTimestamp(timestampStr)?.steps ?: 0
                val newSteps = currentSteps + increment
                
                dao.insert(StepsEntity(timestampStr, newSteps))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to increment steps", e)
                throw e
            }
        }
    }

    suspend fun getStepsInRange(startTimestamp: Instant, endTimestamp: Instant): Map<Instant, Int> = 
        withContext(Dispatchers.IO) {
            try {
                dao.getStepsInRange(
                    startTimestamp.toString(),
                    endTimestamp.toString()
                ).associate { entity ->
                    Instant.parse(entity.timestamp) to entity.steps
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get steps in range", e)
                emptyMap()
            }
        }

    suspend fun getTotalStepsInRange(startTimestamp: Instant, endTimestamp: Instant): Int =
        withContext(Dispatchers.IO) {
            try {
                dao.getTotalStepsInRange(
                    startTimestamp.toString(),
                    endTimestamp.toString()
                ) ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get total steps in range", e)
                0
            }
        }

    override fun close() {
        database.close()
    }
} 