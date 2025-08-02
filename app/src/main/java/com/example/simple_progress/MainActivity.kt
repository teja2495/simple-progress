package com.example.simple_progress

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Simple Progress",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: Add settings */ }) {
                        Icon(Icons.Default.MoreVert, "More options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Main Timer Display
                TimerDisplay(
                    timeText = uiState.timeRemaining,
                    progress = animatedProgress,
                    percentage = uiState.percentage,
                    isFinished = uiState.isFinished
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Timer Controls
                if (!uiState.isRunning && !uiState.isFinished) {
                    TimerModeSelector(
                        selectedMode = uiState.timerMode,
                        onModeChanged = viewModel::setTimerMode
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    when (uiState.timerMode) {
                        "duration" -> {
                            DurationControls(
                                hours = uiState.hours,
                                minutes = uiState.minutes,
                                onHoursChanged = viewModel::updateHours,
                                onMinutesChanged = viewModel::updateMinutes
                            )
                        }
                        "target_time" -> {
                            TargetTimeDisplay(
                                targetHour = uiState.targetHour,
                                targetMinute = uiState.targetMinute,
                                onEditTime = { showTimePickerDialog = true }
                            )
                        }
                    }
                }
                
                // Add space for bottom buttons
                Spacer(modifier = Modifier.height(120.dp))
            }
            
            // Bottom buttons
            BottomButtons(
                isRunning = uiState.isRunning,
                isFinished = uiState.isFinished,
                onStart = { showNameDialog = true },
                onReset = viewModel::resetTimer,
                modifier = Modifier.align(Alignment.BottomCenter)
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
                viewModel.startTimer()
                showNameDialog = false
                tempName = ""
            },
            onDismiss = {
                viewModel.startTimer()
                showNameDialog = false
                tempName = ""
            }
        )
    }
    
    // Time Picker Dialog
    if (showTimePickerDialog) {
        TimePickerDialog(
            initialHour = uiState.targetHour,
            initialMinute = uiState.targetMinute,
            onTimeSelected = { hour, minute ->
                viewModel.setTargetTime(hour, minute)
                showTimePickerDialog = false
            },
            onDismiss = { showTimePickerDialog = false }
        )
    }
}

@Composable
fun TimerDisplay(
    timeText: String,
    progress: Float,
    percentage: Int,
    isFinished: Boolean
) {
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
            
            if (!isFinished) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TimerModeSelector(
    selectedMode: String,
    onModeChanged: (String) -> Unit
) {
    TabRow(
        selectedTabIndex = if (selectedMode == "duration") 0 else 1,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Tab(
            selected = selectedMode == "duration",
            onClick = { onModeChanged("duration") },
            text = { Text("Duration") }
        )
        Tab(
            selected = selectedMode == "target_time",
            onClick = { onModeChanged("target_time") },
            text = { Text("Target Time") }
        )
    }
}

@Composable
fun DurationControls(
    hours: Int,
    minutes: Int,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TimeInputCard(
            label = "Hours",
            value = hours,
            range = 0..24,
            onValueChanged = onHoursChanged,
            modifier = Modifier.weight(1f)
        )
        
        TimeInputCard(
            label = "Minutes", 
            value = minutes,
            range = 0..59,
            onValueChanged = onMinutesChanged,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TargetTimeDisplay(
    targetHour: Int,
    targetMinute: Int,
    onEditTime: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Target Time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = formatTime12Hour(targetHour, targetMinute),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onEditTime,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Time")
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Start", fontSize = 16.sp)
                    }
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChanged(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.count() - 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
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

@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Target Time") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatTime12Hour(selectedHour, selectedMinute),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Hour")
                Slider(
                    value = selectedHour.toFloat(),
                    onValueChange = { selectedHour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Minute")
                Slider(
                    value = selectedMinute.toFloat(),
                    onValueChange = { selectedMinute = it.toInt() },
                    valueRange = 0f..59f,
                    steps = 58,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onTimeSelected(selectedHour, selectedMinute) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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