package expo.modules.androidpedometer

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

class StepsDataStore(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "PedometerPrefs"
        private const val STEPS_DATA_KEY = "stepsData"
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    private fun getSharedPreferences(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadStepsData(date: LocalDate): Int {
        val prefs = getSharedPreferences()
        val stepsDataStr = prefs.getString(STEPS_DATA_KEY, "[]")
        val stepsDataArray = JSONArray(stepsDataStr)

        for (i in 0 until stepsDataArray.length()) {
            val entry = stepsDataArray.getJSONObject(i)
            if (entry.getString("date") == date.format(dateFormatter)) {
                return entry.getInt("steps")
            }
        }
        return 0
    }

    fun saveStepsData(date: LocalDate, steps: Int, timestamp: Long) {
        val prefs = getSharedPreferences()
        val stepsDataStr = prefs.getString(STEPS_DATA_KEY, "[]")
        val stepsDataArray = JSONArray(stepsDataStr)
        
        var found = false
        for (i in 0 until stepsDataArray.length()) {
            val entry = stepsDataArray.getJSONObject(i)
            if (entry.getString("date") == date.format(dateFormatter)) {
                entry.put("steps", steps)
                entry.put("timestamp", timestamp)
                found = true
                break
            }
        }

        if (!found) {
            val newEntry = JSONObject().apply {
                put("date", date.format(dateFormatter))
                put("steps", steps)
                put("timestamp", timestamp)
            }
            stepsDataArray.put(newEntry)
        }

        prefs.edit().putString(STEPS_DATA_KEY, stepsDataArray.toString()).apply()
    }
} 