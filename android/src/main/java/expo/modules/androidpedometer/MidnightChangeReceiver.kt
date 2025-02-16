package expo.modules.androidpedometer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "MidnightChangeReceiver"

class MidnightChangeReceiver(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val controller: StepCounterController,
    private val onMidnightChange: suspend () -> Unit
) : BroadcastReceiver() {
    private var currentDate = LocalDate.now()
    private var isRegistered = false

    override fun onReceive(context: Context?, intent: Intent?) {
        val today = LocalDate.now()
        if (today != currentDate) {
            currentDate = today
            coroutineScope.launch {
                controller.onDateChanged(today)
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
            Log.d(TAG, "Midnight receiver registered")
        }
    }

    fun unregister() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(this)
                isRegistered = false
                Log.d(TAG, "Midnight receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering midnight receiver: ${e.message}")
            }
        }
    }
} 