package expo.modules.androidpedometer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import expo.modules.androidpedometer.data.StepDatabase
import expo.modules.androidpedometer.service.StepCounterService
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class AndroidPedometerModule : Module() {
    private val context: Context
        get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val stepDatabase by lazy { StepDatabase.getInstance(context) }
    private var isInitialized = false

    companion object {
        private const val PEDOMETER_UPDATE_EVENT = "AndroidPedometer.pedometerUpdate"
        private var moduleInstance: AndroidPedometerModule? = null

        fun emitStepUpdate(steps: Int, timestamp: String) {
            moduleInstance?.sendEvent(
                PEDOMETER_UPDATE_EVENT,
                mapOf(
                    "steps" to steps,
                    "timestamp" to timestamp
                )
            )
        }
    }

    override fun definition() = ModuleDefinition {
        Name("AndroidPedometer")
        Events(PEDOMETER_UPDATE_EVENT)

        OnCreate {
            moduleInstance = this@AndroidPedometerModule
        }

        AsyncFunction("initialize") { promise: Promise ->
            try {
                if (isInitialized) {
                    promise.resolve(true)
                    return@AsyncFunction
                }

                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

                if (stepSensor == null) {
                    Log.e("AndroidPedometerModule", "Step counter sensor not available on this device")
                    isInitialized = false
                    promise.resolve(false)
                    return@AsyncFunction
                }

                isInitialized = true
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e("AndroidPedometerModule", "Error initializing pedometer", e)
                isInitialized = false
                promise.resolve(false)
            }
        }

        AsyncFunction("getStepsCountAsync") { date: String?, promise: Promise ->
            try {
                checkInitialized()
                coroutineScope.launch {
                    val targetDate = if (date != null) {
                        Log.d("AndroidPedometerModule", "Input date string: $date")
                        // Parse the instant and convert to the device's timezone
                        val instant = Instant.parse(date)
                        val zoneId = ZoneOffset.systemDefault()
                        instant.atZone(zoneId).toLocalDate()
                    } else {
                        LocalDate.now()
                    }

                    Log.d("AndroidPedometerModule", "Getting steps for date: $targetDate")

                    // Convert back to UTC for database queries
                    val startInstant = targetDate.atStartOfDay(ZoneOffset.systemDefault())
                        .toInstant()
                    val endInstant = targetDate.plusDays(1).atStartOfDay(ZoneOffset.systemDefault())
                        .toInstant()

                    Log.d("AndroidPedometerModule", "Start instant: $startInstant")
                    Log.d("AndroidPedometerModule", "End instant: $endInstant")

                    // First get all records to check what we have
                    val records = stepDatabase.stepRecordDao()
                        .getRecordsInRange(startInstant.toString(), endInstant.toString())
                    Log.d("AndroidPedometerModule", "Found ${records.size} records for the day")
                    records.forEach { record ->
                        Log.d("AndroidPedometerModule", "Record - Time: ${record.timestamp}, Steps: ${record.steps}")
                    }

                    val totalSteps = stepDatabase.stepRecordDao()
                        .getTotalStepsInRange(startInstant.toString(), endInstant.toString())

                    Log.d("AndroidPedometerModule", "Total steps from DB: $totalSteps")
                    promise.resolve(totalSteps)
                }
            } catch (e: Exception) {
                Log.e("AndroidPedometerModule", "Error getting steps count", e)
                Log.e("AndroidPedometerModule", "Stack trace: ${e.stackTrace.joinToString("\n")}")
                promise.reject("ERR_PEDOMETER", e.message, e)
            }
        }

        AsyncFunction("getStepsCountInRangeAsync") { startTimestamp: String, endTimestamp: String, promise: Promise ->
            try {
                checkInitialized()
                coroutineScope.launch {
                    val records = stepDatabase.stepRecordDao()
                        .getRecordsInRange(startTimestamp, endTimestamp)

                    val result = records.associate { record ->
                        record.timestamp to record.steps
                    }

                    promise.resolve(result)
                }
            } catch (e: Exception) {
                promise.reject("ERR_PEDOMETER", e.message, e)
            }
        }

        AsyncFunction("requestPermissions") { promise: Promise ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val activity = appContext.activityProvider?.currentActivity
                        ?: throw IllegalStateException("Activity not found")

                    val permissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
                    
                    if (Build.VERSION.SDK_INT >= 34) { // Android 14 (API 34)
                        permissions.add("android.permission.FOREGROUND_SERVICE_HEALTH")
                    }

                    val allGranted = permissions.all { permission ->
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        promise.resolve(createPermissionResponse(true))
                        return@AsyncFunction
                    }

                    ActivityCompat.requestPermissions(
                        activity,
                        permissions.toTypedArray(),
                        0
                    )
                    
                    val granted = permissions.all { permission ->
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }
                    promise.resolve(createPermissionResponse(granted))
                } else {
                    promise.resolve(createPermissionResponse(true))
                }
            } catch (e: Exception) {
                promise.reject("ERR_PERMISSIONS", e.message, e)
            }
        }

        AsyncFunction("requestNotificationPermissions") { promise: Promise ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val activity = appContext.activityProvider?.currentActivity
                        ?: throw IllegalStateException("Activity not found")

                    val permission = Manifest.permission.POST_NOTIFICATIONS
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        promise.resolve(createPermissionResponse(true))
                        return@AsyncFunction
                    }

                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(permission),
                        1
                    )
                    promise.resolve(createPermissionResponse(
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    ))
                } else {
                    promise.resolve(createPermissionResponse(true))
                }
            } catch (e: Exception) {
                promise.reject("ERR_PERMISSIONS", e.message, e)
            }
        }

        AsyncFunction("setupBackgroundUpdates") { config: Map<String, Any>?, promise: Promise ->
            try {
                checkInitialized()
                if (StepCounterService.isServiceRunning()) {
                    // Update notification config even if service is running
                    StepCounterService.setNotificationConfig(config)
                    promise.resolve(true)
                    return@AsyncFunction
                }

                // Set notification config before starting the service
                StepCounterService.setNotificationConfig(config)
                
                val serviceIntent = Intent(context, StepCounterService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("ERR_BACKGROUND", e.message, e)
            }
        }

        AsyncFunction("simulateMidnightReset") { promise: Promise ->
            try {
                checkInitialized()
                val intent = Intent(Intent.ACTION_TIME_CHANGED)
                context.sendBroadcast(intent)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("ERR_PEDOMETER", e.message, e)
            }
        }
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Pedometer is not initialized. Call initialize() first.")
        }
    }

    private fun createPermissionResponse(granted: Boolean): Map<String, Any> {
        return mapOf(
            "status" to if (granted) "granted" else "denied",
            "granted" to granted,
            "expires" to "never"
        )
    }
}
