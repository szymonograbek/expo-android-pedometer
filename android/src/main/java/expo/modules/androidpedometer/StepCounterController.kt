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

    private var sensorState = SensorState()

    private data class SensorState(
        val baseStepCount: Int? = null,
        val previousStepCount: Int? = null,
        val lastProcessedTimestamp: Instant = Instant.now()
    )

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

    private suspend fun processStepCount(totalSteps: Int, currentTimestamp: Instant) {
        val currentState = sensorState
        
        if (currentState.baseStepCount == null) {
            Log.d(TAG, "Initializing step counter with base count: $totalSteps")
            sensorState = currentState.copy(
                baseStepCount = totalSteps,
                previousStepCount = totalSteps
            )
            return
        }

        val stepIncrement = totalSteps - (currentState.previousStepCount ?: totalSteps)
        if (stepIncrement > 0) {
            Log.d(TAG, "Step count increased by: $stepIncrement")
            sensorState = currentState.copy(
                previousStepCount = totalSteps,
                lastProcessedTimestamp = currentTimestamp
            )
            stepsDataStore.incrementSteps(currentTimestamp, stepIncrement)
            updateState(currentTimestamp)
        }
    }

    private suspend fun updateState(timestamp: Instant, totalSteps: Int? = null) {
        val steps = totalSteps ?: run {
            val startOfDay = TimeUtils.getStartOfDay(timestamp)
            stepsDataStore.getTotalStepsInRange(startOfDay, timestamp)
        }
        
        // Only update state if steps count has changed
        if (steps != _state.value.steps) {
            _state.value = StepCounterState(timestamp, steps)
        }
    }

    suspend fun getHistoricalSteps(timestamp: Instant): Int {
        val now = Instant.now()
        return stepsDataStore.getTotalStepsInRange(timestamp, now)
    }

    suspend fun getStepsInRange(startTimestamp: Instant, endTimestamp: Instant): Map<Instant, Int> {
        return stepsDataStore.getStepsInRange(startTimestamp, endTimestamp)
    }

    fun onStepCountChanged(newStepCount: Int, eventTimestamp: Instant) {
        coroutineScope.launch {
            processStepCount(newStepCount, eventTimestamp)
        }
    }

    suspend fun onDateChanged(newTimestamp: Instant) {
        sensorState = SensorState(lastProcessedTimestamp = newTimestamp)
        updateState(newTimestamp)
    }
} 