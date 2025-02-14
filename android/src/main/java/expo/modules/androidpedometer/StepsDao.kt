package expo.modules.androidpedometer

import androidx.room.*
import java.time.LocalDate

@Dao
interface StepsDao {
    @Query("SELECT * FROM steps WHERE date = :date")
    suspend fun getStepsForDate(date: String): StepsEntity?

    @Query("SELECT * FROM steps WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getStepsInRange(startDate: String, endDate: String): List<StepsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stepsEntity: StepsEntity)

    @Query("UPDATE steps SET steps = steps + :increment WHERE date = :date")
    suspend fun incrementSteps(date: String, increment: Int)
}

@Entity(tableName = "steps")
data class StepsEntity(
    @PrimaryKey val date: String,
    val steps: Int,
    val timestamp: Long
) 