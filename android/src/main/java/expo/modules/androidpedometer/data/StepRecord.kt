package expo.modules.androidpedometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "step_records")
data class StepRecord(
    @PrimaryKey
    val timestamp: String,
    val steps: Int
) {
    companion object {
        fun create(steps: Int, timestamp: Instant = Instant.now()): StepRecord {
            return StepRecord(
                timestamp = timestamp.toString(),
                steps = steps
            )
        }
    }
} 