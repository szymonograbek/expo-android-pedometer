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
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.content.BroadcastReceiver

private const val TAG = "AndroidPedometerModule"

class PedometerError(message: String) : CodedException(message)

class AndroidPedometerModule : Module() {

    override fun definition() = ModuleDefinition {
        Name("AndroidPedometer")

        Events(PEDOMETER_UPDATE_EVENT)

        AsyncFunction("initialize") { promise: Promise ->
        }

        AsyncFunction("getStepsCountAsync") { timestamp: String?, promise: Promise ->
        }

        AsyncFunction("getStepsCountInRangeAsync") { startTimestamp: String, endTimestamp: String, promise: Promise ->
        }

        AsyncFunction("requestPermissions") { promise: Promise ->
        }

        AsyncFunction("requestNotificationPermissions") { promise: Promise ->
        }

        AsyncFunction("setupBackgroundUpdates") { config: NotificationConfigRecord?, promise: Promise ->
        }

        AsyncFunction("simulateMidnightReset") { promise: Promise ->
        }
    }
}
