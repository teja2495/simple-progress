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
    timerMode: String = "duration"
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
                StartButton(
                    onStart = onStart,
                    isEnabled = timerMode == "target_time" || hours > 0 || minutes > 0
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
        
        TimeDisplayCard(value = value)
        
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