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

    suspend fun storeSensorValue(timestamp: Instant, sensorValue: Int) {
        withContext(Dispatchers.IO) {
            try {
                val truncatedTimestamp = TimeUtils.truncateToMinute(timestamp)
                val timestampStr = truncatedTimestamp.toString()
                
                // Check if this is our first ever reading
                val firstEntry = dao.getFirstEntry()
                if (firstEntry == null) {
                    Log.d(TAG, "First ever sensor reading: $sensorValue")
                } else {
                    val lastEntry = dao.getLastEntry()
                    if (lastEntry != null) {
                        val diff = sensorValue - lastEntry.steps
                        Log.d(TAG, "Sensor increment since last reading: $diff (current: $sensorValue, last: ${lastEntry.steps})")
                    }
                }
                
                Log.d(TAG, "Storing sensor value: $sensorValue for minute $timestampStr")
                dao.insert(StepsEntity(timestampStr, sensorValue))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store sensor value", e)
                throw e
            }
        }
    }

    suspend fun getStepsInRange(startTimestamp: Instant, endTimestamp: Instant): Map<Instant, Int> = 
        withContext(Dispatchers.IO) {
            try {
                val entries = dao.getStepsInRange(
                    startTimestamp.toString(),
                    endTimestamp.toString()
                )
                
                if (entries.isEmpty()) return@withContext emptyMap()
                
                // Get the last value before our range to properly calculate first increment
                val lastBeforeRange = dao.getLastEntryBefore(startTimestamp.toString())
                val initialValue = lastBeforeRange?.steps ?: entries.first().steps
                
                // Convert raw sensor values to step increments
                val result = mutableMapOf<Instant, Int>()
                var lastValue = initialValue
                
                for (entry in entries) {
                    val currentValue = entry.steps
                    val increment = if (currentValue >= lastValue) currentValue - lastValue else 0
                    if (increment > 0) {
                        result[Instant.parse(entry.timestamp)] = increment
                    }
                    lastValue = currentValue
                }
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get steps in range", e)
                emptyMap()
            }
        }

    suspend fun getTotalStepsInRange(startTimestamp: Instant, endTimestamp: Instant): Int =
        withContext(Dispatchers.IO) {
            try {
                val steps = dao.getTotalStepsInRange(
                    startTimestamp.toString(),
                    endTimestamp.toString()
                )
                Log.d(TAG, "Got steps in range $startTimestamp to $endTimestamp: $steps")
                steps
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get total steps in range", e)
                0
            }
        }

    override fun close() {
        database.close()
    }
} 