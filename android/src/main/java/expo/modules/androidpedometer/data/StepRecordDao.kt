package expo.modules.androidpedometer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StepRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: StepRecord)

    @Query("SELECT * FROM step_records WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp ORDER BY timestamp ASC")
    suspend fun getRecordsInRange(startTimestamp: String, endTimestamp: String): List<StepRecord>

    @Query("SELECT COALESCE(SUM(steps), 0) FROM step_records WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp")
    suspend fun getTotalStepsInRange(startTimestamp: String, endTimestamp: String): Int

    @Query("SELECT * FROM step_records WHERE timestamp >= :startTimestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecordSince(startTimestamp: String): StepRecord?

    @Query("DELETE FROM step_records WHERE timestamp < :timestamp")
    suspend fun deleteRecordsBefore(timestamp: String)
} 