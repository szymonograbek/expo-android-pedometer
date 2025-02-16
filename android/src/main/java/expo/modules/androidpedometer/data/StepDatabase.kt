package expo.modules.androidpedometer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StepRecord::class], version = 1)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepRecordDao(): StepRecordDao

    companion object {
        private const val DATABASE_NAME = "step_database"

        @Volatile
        private var INSTANCE: StepDatabase? = null

        fun getInstance(context: Context): StepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 