package expo.modules.androidpedometer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "StepCounterController"

class StepCounterController(
    private val stepsDataStore: StepsDataStore,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(StepCounterState(LocalDate.now(), 0, System.currentTimeMillis()))
    val state: StateFlow<StepCounterState> = _state.asStateFlow()

    private val rawStepSensorReadings = MutableStateFlow(StepCounterEvent(0, LocalDate.MIN))
    private var previousStepCount: Int? = null
    private var baseStepCount: Int? = null
    private var lastProcessedDate = LocalDate.now()
    private var todaySteps = 0

    init {
        rawStepSensorReadings.drop(1).onEach { event ->
            try {
                processStepCount(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing step count: ${e.message}")
            }
        }.launchIn(coroutineScope)

        // Load initial state
        coroutineScope.launch {
            try {
                todaySteps = stepsDataStore.loadStepsData(LocalDate.now())
                Log.d(TAG, "Loaded initial steps: $todaySteps")
                updateState(LocalDate.now())
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial state: ${e.message}")
            }
        }
    }

    private suspend fun processStepCount(event: StepCounterEvent) {
        val currentDate = event.eventDate
        val totalSteps = event.stepCount
        
        // Handle date change
        if (currentDate != lastProcessedDate) {
            Log.d(TAG, "Date changed from $lastProcessedDate to $currentDate")
            // Save current day's steps before resetting
            stepsDataStore.incrementSteps(lastProcessedDate, todaySteps)
            // Reset for new day
            baseStepCount = totalSteps
            previousStepCount = totalSteps
            lastProcessedDate = currentDate
            todaySteps = 0
            updateState(currentDate)
            return
        }

        // Initialize base count if not set
        if (baseStepCount == null) {
            Log.d(TAG, "Initializing base step count: $totalSteps")
            baseStepCount = totalSteps
            previousStepCount = totalSteps
            return
        }

        // Calculate steps since the start of the day
        val stepsSinceBase = totalSteps - (baseStepCount ?: totalSteps)
        Log.d(TAG, "Steps since base: $stepsSinceBase (total: $totalSteps, base: $baseStepCount)")

        // Calculate increment since last reading
        val stepIncrement = totalSteps - (previousStepCount ?: totalSteps)
        if (stepIncrement > 0) {
            Log.d(TAG, "Incrementing steps by: $stepIncrement")
            previousStepCount = totalSteps
            todaySteps += stepIncrement
            // Update the database and state
            stepsDataStore.incrementSteps(currentDate, stepIncrement)
            updateState(currentDate)
        }
    }

    private suspend fun updateState(date: LocalDate) {
        val steps = if (date == LocalDate.now()) todaySteps else stepsDataStore.loadStepsData(date)
        Log.d(TAG, "Updating state for $date with steps: $steps")
        _state.value = StepCounterState(
            date = date,
            steps = steps,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun getHistoricalSteps(date: LocalDate): Int {
        return stepsDataStore.loadStepsData(date)
    }

    suspend fun getStepsInRange(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Int> {
        return stepsDataStore.getStepsInRange(startDate, endDate)
    }

    fun onStepCountChanged(newStepCount: Int, eventDate: LocalDate) {
        Log.d(TAG, "Step count changed: $newStepCount on $eventDate")
        rawStepSensorReadings.value = StepCounterEvent(newStepCount, eventDate)
    }

    suspend fun onDateChanged(newDate: LocalDate) {
        Log.d(TAG, "Date changed to: $newDate")
        // Save current day's steps before resetting
        stepsDataStore.incrementSteps(lastProcessedDate, todaySteps)
        // Reset for new day
        baseStepCount = null
        previousStepCount = null
        lastProcessedDate = newDate
        todaySteps = 0
        updateState(newDate)
    }
} 