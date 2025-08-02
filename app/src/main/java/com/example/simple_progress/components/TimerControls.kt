package com.example.simple_progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// BOTTOM BUTTONS COMPONENTS
// ============================================================================

@Composable
fun BottomButtons(
    isRunning: Boolean,
    isFinished: Boolean,
    onStart: () -> Unit,
    onReset: () -> Unit,
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
                ResetButton(
                    onReset = onReset,
                    isError = true
                )
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
                    isEnabled = isEnabled
                )
            }
        }
    }
}

@Composable
fun StartButton(
    onStart: () -> Unit,
    isEnabled: Boolean
) {
    Button(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        enabled = isEnabled
    ) {
        Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    isRunning: Boolean = false
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
        
        // Compact slider for quick navigation - hide when running
        if (!isRunning) {
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
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.count() - 2,
        modifier = Modifier.fillMaxWidth()
    )
} 