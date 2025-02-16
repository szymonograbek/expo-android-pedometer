package expo.modules.androidpedometer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import expo.modules.core.errors.ModuleDestroyedException
import expo.modules.interfaces.permissions.Permissions
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Record
import expo.modules.kotlin.records.Field
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import expo.modules.androidpedometer.Constants.PEDOMETER_UPDATE_EVENT
import expo.modules.androidpedometer.Constants.ACTION_START_SERVICE
import expo.modules.androidpedometer.Constants.ACTION_PAUSE_COUNTING
import expo.modules.androidpedometer.Constants.ACTION_RESUME_COUNTING
import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val TAG = "AndroidPedometerModule"

class PedometerError(message: String) : CodedException(message)

class NotificationConfigRecord : Record {
    @Field
    var title: String? = null

    @Field
    var contentTemplate: String? = null

    @Field
    var style: String? = null

    @Field
    var iconResourceName: String? = null
}

class AndroidPedometerModule : Module() {
    private var sensorManager: SensorManager? = null
    private var stepCountSensor: Sensor? = null
    private var isInitialized = false
    private val moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var controller: StepCounterController
    private lateinit var midnightReceiver: MidnightChangeReceiver
    private var currentTimestamp = Instant.now()

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val steps = event.values[0].toInt()
            controller.onStepCountChanged(steps, Instant.now())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun pauseServiceCounting() {
        val serviceIntent = Intent(appContext.reactContext, PedometerService::class.java).apply {
            action = ACTION_PAUSE_COUNTING
        }
        appContext.reactContext?.startService(serviceIntent)
    }

    private fun resumeServiceCounting() {
        val serviceIntent = Intent(appContext.reactContext, PedometerService::class.java).apply {
            action = ACTION_RESUME_COUNTING
        }
        appContext.reactContext?.startService(serviceIntent)
    }

    override fun definition() = ModuleDefinition {
        Name("AndroidPedometer")

        Events(PEDOMETER_UPDATE_EVENT)

        AsyncFunction("initialize") { promise: Promise ->
            try {
                if (!isInitialized) {
                    controller = StepCounterManager.getController(appContext.reactContext!!, moduleScope)
                    
                    // Initialize and register midnight receiver
                    midnightReceiver = MidnightChangeReceiver(
                        appContext.reactContext!!,
                        moduleScope,
                        controller
                    ) {
                        val currentSteps = controller.state.value.steps
                        Log.d(TAG, "Midnight change detected, steps: $currentSteps")
                        sendEvent(PEDOMETER_UPDATE_EVENT, mapOf(
                            "steps" to currentSteps,
                            "timestamp" to controller.state.value.timestamp.toString()
                        ))
                    }
                    midnightReceiver.register()
                    
                    // Only register sensor listener if service is not running
                    if (PedometerService.instance == null) {
                        sensorManager = appContext.reactContext?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                        stepCountSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

                        if (stepCountSensor == null) {
                            throw PedometerError("Step counter sensor not available on this device")
                        }

                        val result = sensorManager?.registerListener(
                            sensorEventListener,
                            stepCountSensor,
                            SensorManager.SENSOR_DELAY_NORMAL
                        ) ?: false

                        if (!result) {
                            throw PedometerError("Failed to register sensor listener")
                        }
                    }

                    // Start collecting state changes
                    moduleScope.launch {
                        controller.state.collect { state ->
                            try {
                                sendEvent(PEDOMETER_UPDATE_EVENT, mapOf(
                                    "steps" to state.steps,
                                    "timestamp" to state.timestamp.toString()
                                ))
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send step update event", e)
                            }
                        }
                    }

                    // Get initial steps immediately after initialization
                    moduleScope.launch {
                        try {
                            val initialSteps = controller.state.value.steps
                            sendEvent(PEDOMETER_UPDATE_EVENT, mapOf(
                                "steps" to initialSteps,
                                "timestamp" to controller.state.value.timestamp.toString()
                            ))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to get initial steps", e)
                        }
                    }

                    isInitialized = true
                }
                // Always resolve with true if initialization is successful or already initialized
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to initialize pedometer: ${e.message}"))
            }
        }

        AsyncFunction("getStepsCountAsync") { timestamp: String?, promise: Promise ->
            try {
                if (!isInitialized) {
                    throw PedometerError("Pedometer not initialized. Call initialize() first")
                }
                
                val targetTimestamp = if (timestamp != null) {
                    Instant.parse(timestamp)
                } else {
                    Instant.now()
                }

                moduleScope.launch {
                    try {
                        val steps = controller.getHistoricalSteps(targetTimestamp)
                        Log.d(TAG, "Retrieved steps for ${targetTimestamp}: $steps")
                        promise.resolve(steps)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting steps for ${targetTimestamp}: ${e.message}")
                        promise.reject(PedometerError("Failed to get steps count: ${e.message}"))
                    }
                }
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to get steps count: ${e.message}"))
            }
        }

        AsyncFunction("getStepsCountInRangeAsync") { startTimestamp: String, endTimestamp: String, promise: Promise ->
            try {
                if (!isInitialized) {
                    throw PedometerError("Pedometer not initialized. Call initialize() first")
                }
                
                val start = Instant.parse(startTimestamp)
                val end = Instant.parse(endTimestamp)

                moduleScope.launch {
                    try {
                        val stepsMap = controller.getStepsInRange(start, end)
                        Log.d(TAG, "Retrieved steps for range ${start} to ${end}: $stepsMap")
                        // Convert Instant keys to ISO strings for JavaScript
                        val result = stepsMap.mapKeys { it.key.toString() }
                        promise.resolve(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting steps for range ${start} to ${end}: ${e.message}")
                        promise.reject(PedometerError("Failed to get steps count in range: ${e.message}"))
                    }
                }
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to get steps count in range: ${e.message}"))
            }
        }

        AsyncFunction("requestPermissions") { promise: Promise ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Permissions.askForPermissionsWithPermissionsManager(
                    appContext.permissions,
                    promise,
                    Manifest.permission.ACTIVITY_RECOGNITION
                )
            } else {
                // Permissions not needed for Android versions below Q
                promise.resolve(mapOf(
                    "status" to "granted",
                    "expires" to "never",
                    "granted" to true
                ))
            }
        }

        AsyncFunction("requestNotificationPermissions") { promise: Promise ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Permissions.askForPermissionsWithPermissionsManager(
                    appContext.permissions,
                    promise,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                // Permissions not needed for Android versions below 13
                promise.resolve(mapOf(
                    "status" to "granted",
                    "expires" to "never",
                    "granted" to true
                ))
            }
        }

        AsyncFunction("setupBackgroundUpdates") { config: NotificationConfigRecord?, promise: Promise ->
            try {
                if (!isInitialized) {
                    throw PedometerError("Pedometer not initialized. Call initialize() first")
                }

                // Check for activity recognition permission
                val activityRecognitionPermission = appContext.permissions?.hasGrantedPermissions(
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) ?: false

                if (!activityRecognitionPermission) {
                    throw PedometerError("Activity recognition permission not granted. Call requestPermissions() first")
                }

                // Check for notification permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermission = appContext.permissions?.hasGrantedPermissions(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) ?: false
                    
                    if (!notificationPermission) {
                        throw PedometerError("Notification permission not granted. Call requestNotificationPermissions() first")
                    }
                }

                // Set notification config if provided
                if (config != null) {
                    val notificationConfig = NotificationConfig(
                        title = config.title,
                        contentTemplate = config.contentTemplate,
                        style = config.style,
                        iconResourceName = config.iconResourceName
                    )
                    PedometerService.setNotificationConfig(notificationConfig)
                }

                // Start foreground service for background tracking
                try {
                    val serviceIntent = Intent(appContext.reactContext, PedometerService::class.java)
                    Log.d(TAG, "Starting PedometerService")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.reactContext?.startForegroundService(serviceIntent)
                    } else {
                        appContext.reactContext?.startService(serviceIntent)
                    }
                    Log.d(TAG, "PedometerService started successfully")
                    promise.resolve(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start PedometerService: ${e.message}", e)
                    throw PedometerError("Failed to start background service: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in setupBackgroundUpdates: ${e.message}", e)
                promise.reject(PedometerError("Failed to setup background updates: ${e.message}"))
            }
        }

        AsyncFunction("simulateMidnightReset") { promise: Promise ->
            try {
                if (!isInitialized) {
                    throw PedometerError("Pedometer not initialized. Call initialize() first")
                }

                moduleScope.launch {
                    try {
                        // Get today's midnight timestamp in UTC
                        val todayMidnight = Instant.now()
                            .truncatedTo(ChronoUnit.DAYS)
                        controller.onDateChanged(todayMidnight)
                        
                        // Emit event with updated steps
                        val currentSteps = controller.state.value.steps
                        Log.d(TAG, "Emitting steps update after midnight reset: $currentSteps")
                        sendEvent(PEDOMETER_UPDATE_EVENT, mapOf(
                            "steps" to currentSteps,
                            "timestamp" to controller.state.value.timestamp.toString()
                        ))
                        
                        promise.resolve(true)
                    } catch (e: Exception) {
                        promise.reject(PedometerError("Failed to simulate midnight reset: ${e.message}"))
                    }
                }
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to simulate midnight reset: ${e.message}"))
            }
        }

        OnDestroy {
            sensorManager?.unregisterListener(sensorEventListener)
            midnightReceiver.unregister()
            resumeServiceCounting() // Resume service counting when module is destroyed
            isInitialized = false
            StepCounterManager.reset()
        }
    }
}
