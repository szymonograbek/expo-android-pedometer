package expo.modules.androidpedometer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.ChronoUnit

private const val TAG = "StepCounterController"

class StepCounterController(
    private val stepsDataStore: StepsDataStore,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(StepCounterState(Instant.now(), 0))
    val state: StateFlow<StepCounterState> = _state.asStateFlow()

    init {
        coroutineScope.launch {
            try {
                val now = Instant.now()
                val startOfDay = TimeUtils.getStartOfDay(now)
                val todaySteps = stepsDataStore.getTotalStepsInRange(startOfDay, now)
                Log.d(TAG, "Initial step count loaded: $todaySteps")
                updateState(now, todaySteps)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load initial state", e)
            }
        }
    }

    fun onStepCountChanged(sensorValue: Int, eventTimestamp: Instant) {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Received sensor value: $sensorValue at $eventTimestamp")
                
                // Store raw sensor value
                stepsDataStore.storeSensorValue(eventTimestamp, sensorValue)
                
                // Update state with calculated steps
                val startOfDay = TimeUtils.getStartOfDay(eventTimestamp)
                val todaySteps = stepsDataStore.getTotalStepsInRange(startOfDay, eventTimestamp)
                Log.d(TAG, "Calculated steps for today: $todaySteps")
                updateState(eventTimestamp, todaySteps)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process step count change", e)
            }
        }
    }

    private fun updateState(timestamp: Instant, steps: Int) {
        _state.value = StepCounterState(timestamp, steps)
    }

    suspend fun getHistoricalSteps(timestamp: Instant): Int {
        val now = Instant.now()
        return stepsDataStore.getTotalStepsInRange(timestamp, now)
    }

    suspend fun getStepsInRange(startTimestamp: Instant, endTimestamp: Instant): Map<Instant, Int> {
        return stepsDataStore.getStepsInRange(startTimestamp, endTimestamp)
    }

    suspend fun onDateChanged(newTimestamp: Instant) {
        val startOfDay = TimeUtils.getStartOfDay(newTimestamp)
        val todaySteps = stepsDataStore.getTotalStepsInRange(startOfDay, newTimestamp)
        updateState(newTimestamp, todaySteps)
    }
} 