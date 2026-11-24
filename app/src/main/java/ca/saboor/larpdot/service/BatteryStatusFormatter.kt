package ca.saboor.larpdot.service

import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure formatting and classification helpers for smart charging & battery alerts. */
object BatteryStatusFormatter {

    /**
     * Estimates charging power in Watts from [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW]
     * and battery voltage in millivolts. Handles OEM sign/unit differences gracefully.
     */
    fun estimateChargingWatts(currentNow: Int, voltageMilliVolts: Int): Float? {
        if (currentNow == 0 || currentNow == Int.MIN_VALUE || voltageMilliVolts <= 0) return null
        val absCurrent = abs(currentNow).toFloat()
        // Most modern Android devices report microamps (e.g. 2_500_000 uA = 2.5A),
        // while a few older HALs report milliamps (e.g. 2500 mA).
        val amps = if (absCurrent >= 10_000f) absCurrent / 1_000_000f else absCurrent / 1_000f
        val volts = voltageMilliVolts / 1_000f
        val watts = amps * volts
        return watts.takeIf { it in 1.0f..240.0f }
    }

    fun classifyChargingTitle(watts: Float?): String = when {
        watts != null && watts >= 25f -> "Super Fast Charging"
        watts != null && watts >= 14f -> "Fast Charging"
        else -> "Charging"
    }

    fun formatTimeRemaining(millis: Long): String? {
        if (millis <= 0L) return null
        val totalMinutes = (millis / 60_000L).toInt()
        if (totalMinutes < 1) return "< 1m left"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m left"
            hours > 0 -> "${hours}h left"
            else -> "${minutes}m left"
        }
    }

    fun buildChargingSubtitle(
        percent: Int,
        watts: Float?,
        timeRemainingMs: Long,
    ): String {
        val parts = buildList {
            if (percent > 0) add("$percent%")
            if (watts != null) add("${watts.roundToInt()}W")
            formatTimeRemaining(timeRemainingMs)?.let(::add)
        }
        return if (parts.isEmpty()) "Connected to power" else parts.joinToString(" • ")
    }

    fun shouldTriggerChargeLimit(
        isCharging: Boolean,
        percent: Int,
        limitPercent: Int,
        alreadyNotified: Boolean,
    ): Boolean = isCharging && !alreadyNotified && percent >= limitPercent.coerceIn(50, 100)
}
