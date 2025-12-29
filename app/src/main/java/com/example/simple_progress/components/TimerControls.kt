package com.example.simple_progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ============================================================================
// BOTTOM BUTTONS COMPONENTS
// ============================================================================

@Composable
fun BottomButtons(
    isRunning: Boolean,
    isFinished: Boolean,
    isScheduled: Boolean,
    isPaused: Boolean = false,
    onStart: () -> Unit,
    onSchedule: () -> Unit,
    onReset: () -> Unit,
    onCancelScheduled: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    modifier: Modifier = Modifier,
    hours: Int = 0,
    minutes: Int = 0,
    timerMode: String = "duration",
    targetHour: Int = 0,
    targetMinute: Int = 0
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        when {
            isFinished -> {
                ResetButton(onReset = onReset)
            }
            isRunning -> {
                if (timerMode == "duration") {
                    // Show pause/resume and reset buttons for duration timers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isPaused) {
                            Button(
                                onClick = onResume,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("Resume")
                            }
                        } else {
                            Button(
                                onClick = onPause,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("Pause")
                            }
                        }
                        Button(
                            onClick = onReset,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("Reset")
                        }
                    }
                } else {
                    // Show only reset button for time tab timers
                    ResetButton(
                        onReset = onReset,
                        isError = true
                    )
                }
            }
            else -> {
                val isEnabled = when (timerMode) {
                    "duration" -> true // Always enabled for duration mode (15 min default)
                    "target_time" -> {
                        // For time mode, check if time has been changed from current time
                        val currentHour = com.example.simple_progress.utils.getCurrentHour()
                        val currentMinute = com.example.simple_progress.utils.getCurrentMinute()
                        targetHour != currentHour || targetMinute != currentMinute
                    }
                    else -> hours > 0 || minutes > 0
                }
                
                StartButton(
                    onStart = onStart,
                    onLongPress = onSchedule,
                    isEnabled = isEnabled
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StartButton(
    onStart: () -> Unit,
    onLongPress: () -> Unit,
    isEnabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .combinedClickable(
                enabled = isEnabled,
                onClick = onStart,
                onLongClick = onLongPress,
                interactionSource = interactionSource,
                indication = null
            ),
        shape = RoundedCornerShape(28.dp),
        color = if (isEnabled)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (isEnabled)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Start",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ResetButton(
    onReset: () -> Unit,
    isError: Boolean = false
) {
    Button(
        onClick = onReset,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = if (isError) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        Text("Reset", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================================
// TIME INPUT CARD COMPONENTS
// ============================================================================

@Composable
fun TimeInputCard(
    label: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isRunning: Boolean = false,
    isFinished: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Box to wrap the display card and apply the swipe gesture
        Box(
            modifier = Modifier.pointerInput(isRunning, value, range) {
                // Gestures are only enabled if the timer is not running
                if (isRunning) return@pointerInput

                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        val threshold = 50f // Defines how far the user must swipe
                        
                        // Swipe Up (increment value)
                        if (totalDrag < -threshold) {
                            if (value < range.last) {
                                onValueChanged(value + 1)
                            }
                        } 
                        // Swipe Down (decrement value)
                        else if (totalDrag > threshold) {
                            if (value > range.first) {
                                onValueChanged(value - 1)
                            }
                        }
                    }
                )
            }
        ) {
            TimeDisplayCard(value = value)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Compact slider for quick navigation - hide when running or finished
        if (!isRunning && !isFinished) {
            TimeSlider(
                value = value.toFloat(),
                onValueChange = { onValueChanged(it.toInt()) },
                range = range
            )
        }
    }
}

@Composable
fun TimeDisplayCard(value: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", value),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun TimeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: IntRange
) {
    val valueRange = range.first.toFloat()..range.last.toFloat()
    
    // Enhanced touch area wrapper
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp) // Increased padding for better touch area
            .pointerInput(value) {
                // Add additional touch handling for edge cases
                detectTapGestures(
                    onTap = { offset ->
                        // Calculate the position and update value accordingly
                        val width = size.width
                        val position = offset.x / width
                        val newValue = range.first + (position * (range.last - range.first))
                        onValueChange(newValue.coerceIn(range.first.toFloat(), range.last.toFloat()))
                    }
                )
            }
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = range.count() - 2,
            modifier = Modifier.fillMaxWidth(),
            // Enhanced visual feedback for better touch experience
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            )
        )
    }
} 