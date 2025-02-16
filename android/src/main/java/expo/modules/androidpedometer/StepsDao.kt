package expo.modules.androidpedometer

import androidx.room.*
import java.time.Instant

@Dao
interface StepsDao {
    @Query("SELECT * FROM steps WHERE timestamp = :timestamp")
    suspend fun getStepsForTimestamp(timestamp: String): StepsEntity?

    @Query("SELECT * FROM steps WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp")
    suspend fun getStepsInRange(startTimestamp: String, endTimestamp: String): List<StepsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stepsEntity: StepsEntity)

    @Query("SELECT SUM(steps) FROM steps WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp")
    suspend fun getTotalStepsInRange(startTimestamp: String, endTimestamp: String): Int?
}

@Entity(tableName = "steps")
data class StepsEntity(
    @PrimaryKey val timestamp: String, // ISO timestamp with UTC timezone
    val steps: Int // Now represents incremental steps in this minute
) 