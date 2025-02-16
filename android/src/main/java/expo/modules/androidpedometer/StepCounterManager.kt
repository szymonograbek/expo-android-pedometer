package expo.modules.androidpedometer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference

object StepCounterManager {
    private val controllerRef = AtomicReference<StepCounterController?>(null)

    @Synchronized
    fun getController(context: Context, coroutineScope: CoroutineScope): StepCounterController {
        return controllerRef.get() ?: run {
            val stepsDataStore = StepsDataStore(context.applicationContext)
            val newController = StepCounterController(stepsDataStore, coroutineScope)
            if (controllerRef.compareAndSet(null, newController)) {
                newController
            } else {
                controllerRef.get()!!
            }
        }
    }

    fun getState(): StateFlow<StepCounterState>? = controllerRef.get()?.state

    @Synchronized
    fun reset() {
        val oldController = controllerRef.get()
        if (oldController != null) {
            // Perform cleanup if needed
            controllerRef.compareAndSet(oldController, null)
        }
    }
} 