package expo.modules.androidpedometer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import kotlinx.coroutines.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import expo.modules.androidpedometer.Constants.ACTION_PAUSE_COUNTING
import expo.modules.androidpedometer.Constants.ACTION_RESUME_COUNTING

class PedometerService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var controller: StepCounterController
    private var isListening = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastProcessedTimestamp = Instant.now()
    private lateinit var midnightReceiver: MidnightChangeReceiver

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "pedometer_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "PedometerService"
        private var notificationConfig = NotificationConfig(
            title = "Step Counter Active",
            contentTemplate = null
        )
        @Volatile
        var instance: PedometerService? = null
            private set

        fun setNotificationConfig(config: NotificationConfig) {
            notificationConfig = NotificationConfig(
                title = config.title ?: notificationConfig.title,
                contentTemplate = config.contentTemplate,
                style = config.style,
                iconResourceName = config.iconResourceName
            )
            // Update notification immediately if service is running
            instance?.updateCurrentNotification()
        }

        private fun getIconResourceId(context: Context, resourceName: String?): Int {
            if (resourceName == null) return R.drawable.footprint
            return try {
                val resources = context.resources
                val packageName = context.packageName
                resources.getIdentifier(resourceName, "drawable", packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get icon resource: ${e.message}")
                R.drawable.footprint
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            controller = StepCounterManager.getController(applicationContext, serviceScope)
            midnightReceiver = MidnightChangeReceiver(
                applicationContext,
                serviceScope,
                controller
            ) {}
            midnightReceiver.register()
            startForeground(NOTIFICATION_ID, createNotification(controller.state.value.steps).build())
            serviceScope.launch {
                controller.state.collect { state ->
                    updateNotification(state.steps)
                }
            }
            registerStepCounter()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_PAUSE_COUNTING -> {
                    if (isListening) {
                        sensorManager.unregisterListener(this)
                        isListening = false
                        Log.d(TAG, "Step counter paused")
                    }
                }
                ACTION_RESUME_COUNTING -> {
                    if (!isListening) {
                        registerStepCounter()
                        Log.d(TAG, "Step counter resumed")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    private fun registerStepCounter(): Boolean {
        val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        return stepCounterSensor?.let { sensor ->
            try {
                val result = sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                if (result) {
                    isListening = true
                } else {
                    Log.e(TAG, "Failed to register step counter sensor")
                    stopSelf()
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register step counter sensor", e)
                stopSelf()
                false
            }
        } ?: run {
            Log.e(TAG, "Step counter sensor not available")
            stopSelf()
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val steps = it.values[0].toInt()
            controller.onStepCountChanged(steps, Instant.now())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                description = "Shows your current step count"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(steps: Int): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(notificationConfig.title)
            .setSmallIcon(getIconResourceId(this, notificationConfig.iconResourceName))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(createPendingIntent())

        when (notificationConfig.style) {
            "bigText" -> {
                notificationConfig.contentTemplate?.let { template ->
                    val contentText = String.format(template, steps)
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                }
            }
            else -> {
                // Only set content text if template is provided
                notificationConfig.contentTemplate?.let { template ->
                    val contentText = String.format(template, steps)
                    builder.setContentText(contentText)
                }
            }
        }

        return builder
    }

    private fun createPendingIntent(): PendingIntent {
        val packageManager = applicationContext.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(this, 0, launchIntent, flags)
    }

    private fun updateNotification(steps: Int) {
        try {
            val notification = createNotification(steps).build()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun updateCurrentNotification() {
        try {
            updateNotification(controller.state.value.steps)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating current notification", e)
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            if (isListening) {
                sensorManager.unregisterListener(this)
                isListening = false
            }
            midnightReceiver.unregister()
            synchronized(PedometerService::class.java) {
                instance = null
            }
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy service", e)
        }
    }
} 