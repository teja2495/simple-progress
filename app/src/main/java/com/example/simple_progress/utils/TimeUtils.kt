package com.example.simple_progress.utils

import java.text.SimpleDateFormat
import java.util.*

// ============================================================================
// TIME UTILITY FUNCTIONS
// ============================================================================

fun formatTime12Hour(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return format.format(calendar.time)
}

fun getDefaultTargetHour(): Int {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MINUTE, 15) // Add 15 minutes to current time
    return calendar.get(Calendar.HOUR_OF_DAY)
}

fun getDefaultTargetMinute(): Int {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MINUTE, 15) // Add 15 minutes to current time
    return calendar.get(Calendar.MINUTE)
}

// New functions for current time (for time mode default)
fun getCurrentHour(): Int {
    return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}

fun getCurrentMinute(): Int {
    return Calendar.getInstance().get(Calendar.MINUTE)
} 