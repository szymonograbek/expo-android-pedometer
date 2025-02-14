package expo.modules.androidpedometer

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StepsEntity::class],
    version = 1,
    exportSchema = true
)
abstract class StepsDatabase : RoomDatabase() {
    abstract fun stepsDao(): StepsDao
} 