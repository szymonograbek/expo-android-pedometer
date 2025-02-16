package expo.modules.androidpedometer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private const val TAG = "MidnightChangeReceiver"

class MidnightChangeReceiver(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val controller: StepCounterController,
    private val onMidnightChange: suspend () -> Unit
) : BroadcastReceiver() {
    private var lastProcessedDay = Instant.now().truncatedTo(ChronoUnit.DAYS)
    private var isRegistered = false

    override fun onReceive(context: Context?, intent: Intent?) {
        val now = Instant.now()
        val currentDay = now.truncatedTo(ChronoUnit.DAYS)
        if (currentDay != lastProcessedDay) {
            lastProcessedDay = currentDay
            coroutineScope.launch {
                controller.onDateChanged(now)
                onMidnightChange()
            }
        }
    }

    fun register() {
        if (!isRegistered) {
            val intentFilter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(this, intentFilter)
            isRegistered = true
        }
    }

    fun unregister() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(this)
                isRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister midnight receiver", e)
            }
        }
    }
} 