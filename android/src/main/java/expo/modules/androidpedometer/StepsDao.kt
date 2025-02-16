package expo.modules.androidpedometer

import androidx.room.*
import java.time.Instant

@Dao
interface StepsDao {
    @Query("SELECT * FROM steps WHERE timestamp = :timestamp")
    suspend fun getStepsForTimestamp(timestamp: String): StepsEntity?

    @Query("SELECT * FROM steps WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp ORDER BY timestamp ASC")
    suspend fun getStepsInRange(startTimestamp: String, endTimestamp: String): List<StepsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stepsEntity: StepsEntity)

    @Query("SELECT * FROM steps WHERE timestamp <= :endTimestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEntryBefore(endTimestamp: String): StepsEntity?

    @Query("""
        SELECT COALESCE(
            (
                SELECT steps FROM steps 
                WHERE timestamp <= :endTimestamp 
                ORDER BY timestamp DESC 
                LIMIT 1
            ) - (
                SELECT COALESCE(
                    (
                        SELECT steps FROM steps 
                        WHERE timestamp < :startTimestamp 
                        ORDER BY timestamp DESC 
                        LIMIT 1
                    ),
                    (
                        SELECT steps FROM steps 
                        WHERE timestamp >= :startTimestamp 
                        ORDER BY timestamp ASC 
                        LIMIT 1
                    )
                )
            ),
            0
        ) as steps
    """)
    suspend fun getTotalStepsInRange(startTimestamp: String, endTimestamp: String): Int

    @Query("SELECT * FROM steps ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstEntry(): StepsEntity?

    @Query("SELECT * FROM steps ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEntry(): StepsEntity?
}

@Entity(tableName = "steps")
data class StepsEntity(
    @PrimaryKey val timestamp: String, // ISO timestamp with UTC timezone
    val steps: Int // Raw sensor value
) 