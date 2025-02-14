package expo.modules.androidpedometer

import java.time.LocalDate

data class StepCounterState(
    val date: LocalDate,
    val steps: Int,
    val timestamp: Long
) 