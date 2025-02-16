package expo.modules.androidpedometer

import android.util.Log

object Logger {
    private const val DEFAULT_TAG = "AndroidPedometer"

    fun debug(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
    }

    fun error(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    fun info(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
    }

    fun warn(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
    }
} 