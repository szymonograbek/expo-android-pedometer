package expo.modules.androidpedometer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import expo.modules.androidpedometer.AndroidPedometerModule
import expo.modules.androidpedometer.data.StepDatabase
import expo.modules.androidpedometer.data.StepRecord
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import android.content.pm.ServiceInfo
import java.util.concurrent.atomic.AtomicInteger

class StepCounterService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var stepDatabase: StepDatabase
    private var previousStepCount: Int? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val bufferedSteps = AtomicInteger(0)
    private var aggregationJob: Job? = null

    companion object {
        private const val TAG = "StepCounterService"
        private const val NOTIFICATION_CHANNEL_ID = "step_counter_channel"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_TITLE = "Step Counter"
        private const val NOTIFICATION_TEXT = "Today's steps: %d"
        private const val DEFAULT_ICON_NAME = "footprint"
        private const val AGGREGATION_INTERVAL_MS = 5000L // 5 seconds
        private var isRunning = false
        private var notificationConfig: Map<String, Any>? = null
        private var serviceInstance: StepCounterService? = null

        fun isServiceRunning(): Boolean = isRunning

        fun setNotificationConfig(config: Map<String, Any>?) {
            notificationConfig = config
            // Update notification if service is running
            serviceInstance?.let { service ->
                service.serviceScope.launch {
                    val todaySteps = service.getTodaySteps()
                    service.updateNotification(todaySteps)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        serviceInstance = this
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepDatabase = StepDatabase.getInstance(this)
        setupStepSensor()
        startStepAggregation()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        
        serviceScope.launch {
            val todaySteps = getTodaySteps()
            startForegroundWithNotification(todaySteps)
        }
    }

    private fun startStepAggregation() {
        aggregationJob = serviceScope.launch {
            while (isActive) {
                delay(AGGREGATION_INTERVAL_MS)
                val steps = bufferedSteps.getAndSet(0)
                if (steps > 0) {
                    val now = Instant.now()
                    val record = StepRecord.create(steps, now)
                    stepDatabase.stepRecordDao().insertRecord(record)
                    AndroidPedometerModule.emitStepUpdate(steps, now.toString())
                    Log.d(TAG, "Aggregated and saved step update - Steps: $steps, Time: $now")
                }
            }
        }
    }

    private fun startForegroundWithNotification(steps: Int) {
        val notification = createNotification(steps)
        when {
            Build.VERSION.SDK_INT >= 34 -> { // Android 14
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            }
            else -> {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun setupStepSensor() {
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            Log.e(TAG, "No step counter sensor found")
            return
        }
        val registered = sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Step sensor registration result: $registered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val totalSteps = it.values[0].toInt()
                Log.d(TAG, "Sensor event - Total steps: $totalSteps, Previous: $previousStepCount")
                
                if (previousStepCount == null) {
                    previousStepCount = totalSteps
                    Log.d(TAG, "First step count reading: $totalSteps")
                    return
                }

                val stepDiff = totalSteps - previousStepCount!!
                Log.d(TAG, "Step difference: $stepDiff")
                
                if (stepDiff > 0) {
                    bufferedSteps.addAndGet(stepDiff)
                    Log.d(TAG, "Added $stepDiff steps to buffer. Current buffer: ${bufferedSteps.get()}")
                    
                    // Update notification with current steps plus buffered steps
                    serviceScope.launch {
                        val currentSteps = getTodaySteps()
                        val bufferedStepsCount = bufferedSteps.get()
                        Log.d(TAG, "Updating notification - DB steps: $currentSteps, Buffered steps: $bufferedStepsCount")
                        updateNotification(currentSteps + bufferedStepsCount)
                    }
                }
                previousStepCount = totalSteps
            }
        }
    }

    private suspend fun getTodaySteps(): Int {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startInstant = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        val endInstant = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return stepDatabase.stepRecordDao().getTotalStepsInRange(startInstant.toString(), endInstant.toString())
    }

    private suspend fun updateNotificationWithTodaySteps() {
        val todaySteps = getTodaySteps()
        Log.d(TAG, "Updating notification with today's steps: $todaySteps")
        updateNotification(todaySteps)
    }

    private fun createNotification(steps: Int): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Get icon resource name from config or use default
        val iconName = notificationConfig?.get("iconResourceName") as? String ?: DEFAULT_ICON_NAME
        
        // Try to get the resource ID for the provided icon name
        var iconResourceId = resources.getIdentifier(iconName, "drawable", packageName)
        if (iconResourceId == 0) {
            // If custom icon not found, try to use default icon
            Log.w(TAG, "Custom icon resource '$iconName' not found, falling back to default")
            iconResourceId = resources.getIdentifier(DEFAULT_ICON_NAME, "drawable", packageName)
            if (iconResourceId == 0) {
                // If default icon also not found, use system icon as last resort
                Log.e(TAG, "Default icon 'footprint' not found, using system icon")
                iconResourceId = android.R.drawable.ic_menu_compass
            }
        }

        val title = notificationConfig?.get("title") as? String ?: NOTIFICATION_TITLE
        val contentTemplate = notificationConfig?.get("contentTemplate") as? String ?: NOTIFICATION_TEXT

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(String.format(contentTemplate, steps))
            .setSmallIcon(iconResourceId)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .build()
    }

    private fun updateNotification(steps: Int) {
        val notification = createNotification(steps)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Step Counter Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your steps in the background"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: $accuracy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        sensorManager.unregisterListener(this)
        aggregationJob?.cancel()
        serviceScope.cancel()
    }
} 