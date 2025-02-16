package expo.modules.androidpedometer

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object TimeUtils {
    private val systemZone = ZoneId.systemDefault()

    fun truncateToMinute(timestamp: Instant): Instant =
        timestamp.truncatedTo(ChronoUnit.MINUTES)

    fun getStartOfDay(timestamp: Instant): Instant =
        timestamp.atZone(systemZone)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()

    fun getEndOfDay(timestamp: Instant): Instant =
        getStartOfDay(timestamp).plus(1, ChronoUnit.DAYS)
} 