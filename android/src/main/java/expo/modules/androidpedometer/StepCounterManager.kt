package expo.modules.androidpedometer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

object StepCounterManager {
    private var controller: StepCounterController? = null

    @Synchronized
    fun getController(context: Context, coroutineScope: CoroutineScope): StepCounterController {
        if (controller == null) {
            val stepsDataStore = StepsDataStore(context.applicationContext)
            controller = StepCounterController(stepsDataStore, coroutineScope)
        }
        return controller!!
    }

    fun getState(): StateFlow<StepCounterState>? = controller?.state

    @Synchronized
    fun reset() {
        controller = null
    }
} 