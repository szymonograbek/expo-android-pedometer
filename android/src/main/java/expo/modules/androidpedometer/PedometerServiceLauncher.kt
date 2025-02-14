package expo.modules.androidpedometer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PedometerServiceLauncher : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.run {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED && hasPermissions(context)) {
                val serviceIntent = Intent(applicationContext, PedometerService::class.java)
                ContextCompat.startForegroundService(applicationContext, serviceIntent)
            }
        }
    }

    private fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
                return false
            }
        }
        return true
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        val status = ContextCompat.checkSelfPermission(context, permission)
        return status == PackageManager.PERMISSION_GRANTED
    }
} 