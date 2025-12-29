package com.example.simple_progress.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduledTimerDisplay(
    scheduledStartTime: Long,
    scheduledDuration: Long,
    timerName: String,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Update current time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }
    
    val timeUntilStart = scheduledStartTime - currentTime
    val hoursUntilStart = timeUntilStart / (1000 * 60 * 60)
    val minutesUntilStart = (timeUntilStart / (1000 * 60)) % 60
    val secondsUntilStart = (timeUntilStart / 1000) % 60
    
    // Format the scheduled start time
    val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val startTimeString = dateFormat.format(Date(scheduledStartTime))
    
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = alpha)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "⏰ Timer Scheduled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (timerName.isNotEmpty()) {
                Text(
                    timerName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Text(
                "Starts at: $startTimeString",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Starts in: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                
                Text(
                    String.format("%02d:%02d:%02d", hoursUntilStart, minutesUntilStart, secondsUntilStart),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val durationHours = scheduledDuration / (1000 * 60 * 60)
            val durationMinutes = (scheduledDuration / (1000 * 60)) % 60
            
            Text(
                "Duration: ${durationHours}h ${durationMinutes}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}
