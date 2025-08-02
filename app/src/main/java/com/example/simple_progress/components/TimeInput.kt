package com.example.simple_progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// TIME INPUT COMPONENTS
// ============================================================================

@Composable
fun UnifiedTimeInput(
    timerMode: String,
    hours: Int,
    minutes: Int,
    targetHour: Int,
    targetMinute: Int,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onTargetTimeChanged: (Int, Int) -> Unit,
    isRunning: Boolean = false
) {
    val timeDisplayData = getTimeDisplayData(timerMode, targetHour, targetMinute)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimeInputCard(
                label = "Hours",
                value = if (timerMode == "duration") hours else timeDisplayData.displayHour,
                range = if (timerMode == "duration") 0..24 else 1..12,
                onValueChanged = { newValue ->
                    handleHoursChange(
                        timerMode = timerMode,
                        newValue = newValue,
                        isAM = timeDisplayData.isAM,
                        targetHour = targetHour,
                        targetMinute = targetMinute,
                        onHoursChanged = onHoursChanged,
                        onTargetTimeChanged = onTargetTimeChanged
                    )
                },
                modifier = Modifier.weight(1f),
                isRunning = isRunning
            )
            
            TimeInputCard(
                label = "Minutes",
                value = if (timerMode == "duration") minutes else targetMinute,
                range = 0..59,
                onValueChanged = { newValue ->
                    if (timerMode == "duration") {
                        onMinutesChanged(newValue)
                    } else {
                        onTargetTimeChanged(targetHour, newValue)
                    }
                },
                modifier = Modifier.weight(1f),
                isRunning = isRunning
            )
            
            // Compact AM/PM selector for target time mode
            if (timerMode == "target_time") {
                AmPmSelector(
                    isAM = timeDisplayData.isAM,
                    targetHour = targetHour,
                    targetMinute = targetMinute,
                    onTargetTimeChanged = onTargetTimeChanged,
                    modifier = Modifier.weight(0.5f)
                )
            }
        }
    }
}

@Composable
fun AmPmSelector(
    isAM: Boolean,
    targetHour: Int,
    targetMinute: Int,
    onTargetTimeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        Card(
            modifier = Modifier.width(40.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AmPmOption(
                    text = "AM",
                    isSelected = isAM,
                    onClick = {
                        val currentHour12 = getCurrentHour12(targetHour)
                        onTargetTimeChanged(currentHour12, targetMinute)
                    },
                    isTop = true
                )
                
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                
                AmPmOption(
                    text = "PM",
                    isSelected = !isAM,
                    onClick = {
                        val currentHour12 = getCurrentHour12(targetHour)
                        onTargetTimeChanged(currentHour12 + 12, targetMinute)
                    },
                    isTop = false
                )
            }
        }
    }
}

@Composable
fun AmPmOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isTop: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = if (isTop) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                       else RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 8.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

private data class TimeDisplayData(
    val displayHour: Int,
    val isAM: Boolean
)

private fun getTimeDisplayData(timerMode: String, targetHour: Int, targetMinute: Int): TimeDisplayData {
    return if (timerMode == "target_time") {
        val displayHour = when (targetHour) {
            0 -> 12
            in 1..11 -> targetHour
            in 13..23 -> targetHour - 12
            else -> 12
        }
        val isAM = targetHour < 12
        TimeDisplayData(displayHour, isAM)
    } else {
        TimeDisplayData(0, true) // Not used in duration mode
    }
}

private fun handleHoursChange(
    timerMode: String,
    newValue: Int,
    isAM: Boolean,
    targetHour: Int,
    targetMinute: Int,
    onHoursChanged: (Int) -> Unit,
    onTargetTimeChanged: (Int, Int) -> Unit
) {
    if (timerMode == "duration") {
        onHoursChanged(newValue)
    } else {
        // Convert 12-hour to 24-hour format
        val adjustedHour = when {
            newValue == 12 && isAM -> 0
            newValue == 12 && !isAM -> 12
            !isAM -> newValue + 12
            else -> newValue
        }
        onTargetTimeChanged(adjustedHour, targetMinute)
    }
}

private fun getCurrentHour12(targetHour: Int): Int {
    return when (targetHour) {
        0 -> 12
        in 1..11 -> targetHour
        in 13..23 -> targetHour - 12
        else -> 12
    }
} 