package expo.modules.androidpedometer

import java.time.Instant

data class StepCounterState(
    val timestamp: Instant,
    val steps: Int
) 