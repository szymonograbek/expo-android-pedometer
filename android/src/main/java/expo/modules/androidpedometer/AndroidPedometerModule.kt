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
import java.time.LocalDate
import android.content.Intent
import android.util.Log
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val PEDOMETER_UPDATE_EVENT = "AndroidPedometer.pedometerUpdate"
private const val TAG = "AndroidPedometerModule"
private const val ACTION_PAUSE_COUNTING = "expo.modules.androidpedometer.PAUSE_COUNTING"
private const val ACTION_RESUME_COUNTING = "expo.modules.androidpedometer.RESUME_COUNTING"

class PedometerError(message: String) : CodedException(message)

class AndroidPedometerModule : Module() {
    private var sensorManager: SensorManager? = null
    private var stepCountSensor: Sensor? = null
    private var isInitialized = false
    private val moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private lateinit var controller: StepCounterController

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val steps = event.values[0].toInt()
            controller.onStepCountChanged(steps, LocalDate.now())
            
            moduleScope.launch {
                try {
                    val currentSteps = controller.state.value.steps
                    Log.d(TAG, "Current steps: $currentSteps")
                    sendEvent(PEDOMETER_UPDATE_EVENT, mapOf(
                        "steps" to currentSteps,
                        "timestamp" to System.currentTimeMillis()
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating steps: ${e.message}")
                }
            }
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

                    // Get initial steps immediately after initialization
                    moduleScope.launch {
                        try {
                            val initialSteps = controller.state.value.steps
                            Log.d(TAG, "Initial steps: $initialSteps")
                            sendEvent(PEDOMETER_UPDATE_EVENT, mapOf(
                                "steps" to initialSteps,
                                "timestamp" to System.currentTimeMillis()
                            ))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting initial steps: ${e.message}")
                        }
                    }

                    isInitialized = true
                    promise.resolve(true)
                } else {
                    promise.resolve(false)
                }
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to initialize pedometer: ${e.message}"))
            }
        }

        AsyncFunction("getStepsCountAsync") { date: String?, promise: Promise ->
            try {
                if (!isInitialized) {
                    throw PedometerError("Pedometer not initialized. Call initialize() first")
                }
                
                val targetDate = if (date != null) {
                    LocalDate.parse(date, dateFormatter)
                } else {
                    LocalDate.now()
                }

                moduleScope.launch {
                    try {
                        val steps = if (targetDate == LocalDate.now()) {
                            // For today, use the current state from controller
                            controller.state.value.steps
                        } else {
                            // For historical data, get it from the database
                            controller.getHistoricalSteps(targetDate)
                        }
                        Log.d(TAG, "Retrieved steps for ${targetDate}: $steps")
                        promise.resolve(steps)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting steps for ${targetDate}: ${e.message}")
                        promise.reject(PedometerError("Failed to get steps count: ${e.message}"))
                    }
                }
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to get steps count: ${e.message}"))
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

        AsyncFunction("setupBackgroundUpdates") { notificationTitle: String?, notificationTemplate: String?, promise: Promise ->
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

                // Set notification content if provided
                if (notificationTitle != null || notificationTemplate != null) {
                    PedometerService.setNotificationContent(notificationTitle, notificationTemplate)
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

        AsyncFunction("customizeNotification") { title: String?, textTemplate: String?, promise: Promise ->
            try {
                PedometerService.setNotificationContent(title, textTemplate)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to customize notification: ${e.message}"))
            }
        }

        AsyncFunction("setNotificationIcon") { iconResourceId: Int, promise: Promise ->
            try {
                PedometerService.setNotificationIcon(iconResourceId)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject(PedometerError("Failed to set notification icon: ${e.message}"))
            }
        }

        OnDestroy {
            sensorManager?.unregisterListener(sensorEventListener)
            resumeServiceCounting() // Resume service counting when module is destroyed
            isInitialized = false
            StepCounterManager.reset()
        }
    }
}
