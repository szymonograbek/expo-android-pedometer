package expo.modules.androidpedometer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val NOTIFICATION_CHANNEL_ID = "pedometer_channel"
private const val NOTIFICATION_ID = 1001
private const val TAG = "PedometerService"
private const val ACTION_PAUSE_COUNTING = "expo.modules.androidpedometer.PAUSE_COUNTING"
private const val ACTION_RESUME_COUNTING = "expo.modules.androidpedometer.RESUME_COUNTING"

class PedometerService : Service() {
    companion object {
        private var notificationTitle = "Step Counter Active"
        private var notificationTextTemplate = "Today's steps: %d"
        private var notificationIcon = R.drawable.footprint

        fun setNotificationContent(title: String?, textTemplate: String?) {
            title?.let { notificationTitle = it }
            textTemplate?.let { notificationTextTemplate = it }
        }

        fun setNotificationIcon(iconResourceId: Int) {
            notificationIcon = iconResourceId
        }
    }

    private var sensorManager: SensorManager? = null
    private var stepCountSensor: Sensor? = null
    private var stepsAtTheBeginning: Int? = null
    private var lastKnownSteps = 0
    private var currentDaySteps = 0
    private var lastUpdateDate = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private lateinit var stepsDataStore: StepsDataStore
    private var isListening = false

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val totalSteps = event.values[0].toInt()
            Log.d(TAG, "Sensor event received: totalSteps=$totalSteps")
            
            if (stepsAtTheBeginning == null) {
                stepsAtTheBeginning = totalSteps
                lastKnownSteps = totalSteps
                currentDaySteps = stepsDataStore.loadStepsData(LocalDate.now())
                Log.d(TAG, "Initial steps set: totalSteps=$totalSteps, currentDaySteps=$currentDaySteps")
                updateNotification()
                return
            }

            val currentDate = LocalDate.now()
            if (currentDate != lastUpdateDate) {
                Log.d(TAG, "New day detected, saving previous day steps and resetting")
                stepsDataStore.saveStepsData(lastUpdateDate, currentDaySteps, System.currentTimeMillis())
                stepsAtTheBeginning = totalSteps
                currentDaySteps = 0
                lastUpdateDate = currentDate
            }

            val stepsSinceReboot = totalSteps - (stepsAtTheBeginning ?: totalSteps)
            currentDaySteps = stepsSinceReboot
            lastKnownSteps = totalSteps
            
            stepsDataStore.saveStepsData(currentDate, currentDaySteps, System.currentTimeMillis())
            
            Log.d(TAG, "Steps updated: currentDaySteps=$currentDaySteps")
            updateNotification()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.d(TAG, "Sensor accuracy changed: $accuracy")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate started")
        
        try {
            stepsDataStore = StepsDataStore(applicationContext)
            initializeSensor()
            createNotificationChannel()
            startForegroundService()
            Log.d(TAG, "Service onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            stopSelf()
        }
    }

    private fun initializeSensor() {
        Log.d(TAG, "Initializing sensor")
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            stepCountSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            if (stepCountSensor == null) {
                Log.e(TAG, "Step counter sensor not available")
                stopSelf()
                return
            }

            val result = sensorManager?.registerListener(
                sensorEventListener,
                stepCountSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            ) ?: false

            if (!result) {
                Log.e(TAG, "Failed to register sensor listener")
                stopSelf()
                return
            }

            Log.d(TAG, "Step counter sensor initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing sensor: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_PAUSE_COUNTING -> pauseCounting()
            ACTION_RESUME_COUNTING -> resumeCounting()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "Creating notification channel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Step Counter",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Tracks your daily steps"
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                }
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel: ${e.message}", e)
                throw e  // Rethrow to handle in onCreate
            }
        }
    }

    private fun startForegroundService() {
        Log.d(TAG, "Starting foreground service")
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Service started in foreground successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            throw e  // Rethrow to handle in onCreate
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle(notificationTitle)
        .setContentText(notificationTextTemplate.format(currentDaySteps))
        .setSmallIcon(notificationIcon)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(createPendingIntent())
        .setAutoCancel(false)
        .build()
        .also { Log.d(TAG, "Notification created with steps: $currentDaySteps") }

    private fun createPendingIntent(): PendingIntent {
        val packageManager = applicationContext.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            flags
        )
    }

    private fun updateNotification() {
        Log.d(TAG, "Updating notification")
        try {
            val notification = createNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        sensorManager?.unregisterListener(sensorEventListener)
    }

    private fun pauseCounting() {
        if (isListening) {
            sensorManager?.unregisterListener(sensorEventListener)
            isListening = false
            Log.d(TAG, "Paused step counting in service")
        }
    }

    private fun resumeCounting() {
        if (!isListening) {
            val result = sensorManager?.registerListener(
                sensorEventListener,
                stepCountSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            ) ?: false

            isListening = result
            Log.d(TAG, "Resumed step counting in service, success=$result")
        }
    }
} 