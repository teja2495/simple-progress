package com.example.simple_progress

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simple_progress.ui.theme.SimpleProgressTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission handled gracefully */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkNotificationPermission()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            SimpleProgressTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TimerScreen()
                }
            }
        }
    }
    
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(viewModel: TimerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val animatedProgress by animateFloatAsState(targetValue = uiState.progress, label = "progress")
    
    var showNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        Text(
                            "Simple Progress",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                
                // Modern Tab Row at the top
                if (!uiState.isRunning && !uiState.isFinished) {
                    ModernTimerModeSelector(
                        selectedMode = uiState.timerMode,
                        onModeChanged = viewModel::setTimerMode
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { },
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount > 50) {
                                    // Swipe right - switch to duration
                                    if (uiState.timerMode == "target_time") {
                                        viewModel.setTimerMode("duration")
                                    }
                                } else if (dragAmount < -50) {
                                    // Swipe left - switch to target time
                                    if (uiState.timerMode == "duration") {
                                        viewModel.setTimerMode("target_time")
                                    }
                                }
                            }
                        )
                    }
            ) {
                if (uiState.isRunning || uiState.isFinished) {
                    // Center the timer display when running or finished
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TimerDisplay(
                            timeText = uiState.timeRemaining,
                            progress = animatedProgress,
                            percentage = uiState.percentage,
                            isFinished = uiState.isFinished,
                            isRunning = uiState.isRunning,
                            timerName = uiState.timerName,
                            onAddNameClick = { showNameDialog = true }
                        )
                    }
                } else {
                    // Normal layout when not running
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Main Timer Display
                        TimerDisplay(
                            timeText = uiState.timeRemaining,
                            progress = animatedProgress,
                            percentage = uiState.percentage,
                            isFinished = uiState.isFinished,
                            isRunning = uiState.isRunning,
                            timerName = uiState.timerName,
                            onAddNameClick = null
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Timer Controls
                        UnifiedTimeInput(
                            timerMode = uiState.timerMode,
                            hours = uiState.hours,
                            minutes = uiState.minutes,
                            targetHour = uiState.targetHour,
                            targetMinute = uiState.targetMinute,
                            onHoursChanged = viewModel::updateHours,
                            onMinutesChanged = viewModel::updateMinutes,
                            onTargetTimeChanged = viewModel::setTargetTime
                        )
                        
                        // Add space for bottom buttons
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
            
            // Bottom buttons
            BottomButtons(
                isRunning = uiState.isRunning,
                isFinished = uiState.isFinished,
                onStart = { viewModel.startTimer() },
                onReset = viewModel::resetTimer,
                modifier = Modifier.align(Alignment.BottomCenter),
                hours = uiState.hours,
                minutes = uiState.minutes,
                timerMode = uiState.timerMode
            )
        }
    }
    
    // Name Dialog
    if (showNameDialog) {
        TimerNameDialog(
            currentName = tempName,
            onNameChanged = { tempName = it },
            onConfirm = {
                viewModel.updateTimerName(tempName)
                showNameDialog = false
                tempName = ""
            },
            onDismiss = {
                showNameDialog = false
                tempName = ""
            }
        )
    }
}

@Composable
fun TimerDisplay(
    timeText: String,
    progress: Float,
    percentage: Int,
    isFinished: Boolean,
    isRunning: Boolean = false,
    timerName: String = "",
    modifier: Modifier = Modifier,
    onAddNameClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Show timer name when running
        if (isRunning && timerName.isNotEmpty()) {
            Text(
                text = timerName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(65.dp))
        }
        
        // Show "Add Name" pill when running and no name is set
        if (isRunning && timerName.isEmpty() && onAddNameClick != null) {
            Card(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .clickable { onAddNameClick() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Add Name",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 16.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                
                if (!isFinished && percentage > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "$percentage%",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernTimerModeSelector(
    selectedMode: String,
    onModeChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ModernTab(
                text = "Duration",
                isSelected = selectedMode == "duration",
                onClick = { onModeChanged("duration") },
                modifier = Modifier.weight(1f),
                isLeft = true
            )
            
            ModernTab(
                text = "Time",
                isSelected = selectedMode == "target_time",
                onClick = { onModeChanged("target_time") },
                modifier = Modifier.weight(1f),
                isLeft = false
            )
        }
    }
}

@Composable
fun ModernTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLeft: Boolean = false
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = if (isLeft) 24.dp else 0.dp,
                    bottomStart = if (isLeft) 24.dp else 0.dp,
                    topEnd = if (!isLeft) 24.dp else 0.dp,
                    bottomEnd = if (!isLeft) 24.dp else 0.dp
                )
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun UnifiedTimeInput(
    timerMode: String,
    hours: Int,
    minutes: Int,
    targetHour: Int,
    targetMinute: Int,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onTargetTimeChanged: (Int, Int) -> Unit
) {
    // Convert 24-hour to 12-hour for display in target time mode
    val displayHour = if (timerMode == "target_time") {
        when (targetHour) {
            0 -> 12
            in 1..11 -> targetHour
            in 13..23 -> targetHour - 12
            else -> 12
        }
    } else {
        hours
    }
    
    val isAM = if (timerMode == "target_time") {
        targetHour < 12
    } else {
        true // Not used in duration mode
    }
    
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
                    value = displayHour,
                    range = if (timerMode == "duration") 0..24 else 1..12,
                    onValueChanged = { newValue ->
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
                    },
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                )
                
                // Compact AM/PM selector for target time mode
                if (timerMode == "target_time") {
                    Column(
                        modifier = Modifier.weight(0.5f),
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
                                // AM section
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isAM) MaterialTheme.colorScheme.primaryContainer 
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                        )
                                        .clickable {
                                            val currentHour12 = when (targetHour) {
                                                0 -> 12
                                                in 1..11 -> targetHour
                                                in 13..23 -> targetHour - 12
                                                else -> 12
                                            }
                                            onTargetTimeChanged(currentHour12, targetMinute)
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "AM",
                                        fontSize = 8.sp,
                                        fontWeight = if (isAM) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isAM) MaterialTheme.colorScheme.onPrimaryContainer 
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                
                                // Divider
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                                
                                // PM section
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (!isAM) MaterialTheme.colorScheme.primaryContainer 
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                        )
                                        .clickable {
                                            val currentHour12 = when (targetHour) {
                                                0 -> 12
                                                in 1..11 -> targetHour
                                                in 13..23 -> targetHour - 12
                                                else -> 12
                                            }
                                            onTargetTimeChanged(currentHour12 + 12, targetMinute)
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "PM",
                                        fontSize = 8.sp,
                                        fontWeight = if (!isAM) FontWeight.Bold else FontWeight.Normal,
                                        color = if (!isAM) MaterialTheme.colorScheme.onPrimaryContainer 
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}

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
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Reset", fontSize = 16.sp)
                }
            }
            isRunning -> {
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset", fontSize = 16.sp)
                }
            }
            else -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    enabled = timerMode == "target_time" || hours > 0 || minutes > 0
                ) {
                    Text("Start", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun TimeInputCard(
    label: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
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
        
        // Number display
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Compact slider for quick navigation
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.count() - 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TimerNameDialog(
    currentName: String,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name Your Timer") },
        text = {
            OutlinedTextField(
                value = currentName,
                onValueChange = onNameChanged,
                label = { Text("Timer Name (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}



private fun formatTime12Hour(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return format.format(calendar.time)
}