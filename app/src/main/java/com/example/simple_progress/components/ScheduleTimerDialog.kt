package com.example.simple_progress.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimerDialog(
    onDismiss: () -> Unit,
    onSchedule: (Int, Int) -> Unit  // hour, minute (24-hour format)
) {
    val currentCalendar = Calendar.getInstance()
    val currentHour24 = currentCalendar.get(Calendar.HOUR_OF_DAY)
    val currentMinute = currentCalendar.get(Calendar.MINUTE)
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Initialize time picker state
    val timePickerState = rememberTimePickerState(
        initialHour = currentHour24,
        initialMinute = currentMinute,
        is24Hour = false // Use 12-hour format with AM/PM
    )
    
    // Calculate selected time in 24-hour format and validation
    val selectedHour24 = timePickerState.hour
    val selectedMinute = timePickerState.minute
    
    val startCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, selectedHour24)
        set(Calendar.MINUTE, selectedMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        
        // If selected time is before current time, schedule for tomorrow
        if (before(currentCalendar)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    
    val timeUntilStart = startCalendar.timeInMillis - currentCalendar.timeInMillis
    val hoursUntilStart = timeUntilStart / (1000 * 60 * 60)
    val minutesUntilStart = (timeUntilStart / (1000 * 60)) % 60
    
    // Check if within 24 hours
    val isValid = timeUntilStart > 0 && timeUntilStart <= 24 * 60 * 60 * 1000L
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle()
                Text(
                    "Schedule Timer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "Select when to start the timer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Native Material3 Time Picker with more space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = timePickerState
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Show calculated start time - smaller card
            Card(
                modifier = Modifier.wrapContentWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isValid) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isValid) "Starts in:" else "Invalid time",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isValid)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (isValid) {
                        Text(
                            String.format("%02d:%02d", hoursUntilStart, minutesUntilStart),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Action buttons - rounded Material 3 style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = { onSchedule(selectedHour24, selectedMinute) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    enabled = isValid
                ) {
                    Text("Schedule")
                }
            }
        }
    }
}
