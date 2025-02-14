package expo.modules.androidpedometer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import kotlinx.coroutines.*
import java.time.LocalDate

class PedometerService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var controller: StepCounterController
    private var isListening = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "pedometer_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "PedometerService"
        private var notificationTitle = "Step Counter Active"
        private var notificationTemplate = "Steps today: %d"
        private var notificationIcon = R.drawable.footprint 

        fun setNotificationContent(title: String?, template: String?) {
            title?.let { notificationTitle = it }
            template?.let { notificationTemplate = it }
        }

        fun setNotificationIcon(iconResourceId: Int) {
            notificationIcon = iconResourceId
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate started")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            controller = StepCounterManager.getController(applicationContext, serviceScope)

            // Start foreground service with initial notification
            startForeground(NOTIFICATION_ID, createNotification(controller.state.value.steps))

            // Monitor state for notification updates
            serviceScope.launch {
                controller.state.collect { state ->
                    updateNotification(state.steps)
                }
            }

            registerStepCounter()
            Log.d(TAG, "Service onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            stopSelf()
        }
    }

    private fun registerStepCounter() {
        val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepCounterSensor?.let {
            val result = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            isListening = result
            Log.d(TAG, "Step counter registered, success=$result")
        } ?: run {
            Log.e(TAG, "Step counter sensor not available")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val steps = it.values[0].toInt()
            controller.onStepCountChanged(steps, LocalDate.now())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(steps: Int) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle(notificationTitle)
        .setContentText(notificationTemplate.format(steps))
        .setSmallIcon(notificationIcon)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(createPendingIntent())
        .build()

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
            val notification = createNotification(steps)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
} 