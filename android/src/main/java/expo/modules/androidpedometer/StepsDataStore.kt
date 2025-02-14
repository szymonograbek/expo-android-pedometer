package expo.modules.androidpedometer

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StepsDataStore(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        StepsDatabase::class.java,
        "steps_database"
    ).build()

    private val dao = database.stepsDao()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val _stepsFlow = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    val stepsFlow: StateFlow<Map<LocalDate, Int>> = _stepsFlow.asStateFlow()

    suspend fun loadStepsData(date: LocalDate): Int = withContext(Dispatchers.IO) {
        val dateStr = date.format(dateFormatter)
        dao.getStepsForDate(dateStr)?.steps ?: 0
    }

    suspend fun incrementSteps(date: LocalDate, increment: Int) {
        if (increment <= 0) return

        withContext(Dispatchers.IO) {
            val dateStr = date.format(dateFormatter)
            val currentEntity = dao.getStepsForDate(dateStr)
            
            if (currentEntity == null) {
                // First entry for this date
                dao.insert(StepsEntity(
                    date = dateStr,
                    steps = increment,
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                dao.incrementSteps(dateStr, increment)
            }

            // Update flow
            val updatedSteps = dao.getStepsForDate(dateStr)?.steps ?: 0
            _stepsFlow.value = _stepsFlow.value + (date to updatedSteps)
        }
    }

    suspend fun getStepsInRange(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Int> = 
        withContext(Dispatchers.IO) {
            dao.getStepsInRange(
                startDate.format(dateFormatter),
                endDate.format(dateFormatter)
            ).associate { entity ->
                LocalDate.parse(entity.date, dateFormatter) to entity.steps
            }
        }
} 