package com.example.simple_progress

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simple_progress.components.*
import com.example.simple_progress.ui.theme.SimpleProgressTheme

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

// ============================================================================
// MAIN SCREEN COMPOSABLE
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(viewModel: TimerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val animatedProgress by animateFloatAsState(targetValue = uiState.progress, label = "progress")
    
    var showNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TimerTopBar(
                isRunning = uiState.isRunning,
                timerMode = uiState.timerMode,
                onModeChanged = viewModel::setTimerMode
            )
        }
    ) { paddingValues ->
        TimerContent(
            uiState = uiState,
            animatedProgress = animatedProgress,
            paddingValues = paddingValues,
            onStart = { viewModel.startTimer() },
            onReset = viewModel::resetTimer,
            onModeChanged = viewModel::setTimerMode,
            onHoursChanged = viewModel::updateHours,
            onMinutesChanged = viewModel::updateMinutes,
            onTargetTimeChanged = viewModel::setTargetTime,
            onShowNameDialog = { showNameDialog = true },
            onUpdateTimerName = { viewModel.updateTimerName(it) }
        )
    }
    
    // Name Dialog
    if (showNameDialog) {
        TimerNameDialog(
            currentName = if (tempName.isEmpty()) uiState.timerName else tempName,
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

// ============================================================================
// TOP BAR COMPONENTS
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerTopBar(
    isRunning: Boolean,
    timerMode: String,
    onModeChanged: (String) -> Unit
) {
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
        
        // Modern Tab Row at the top - hide when running
        if (!isRunning) {
            ModernTimerModeSelector(
                selectedMode = timerMode,
                onModeChanged = onModeChanged
            )
        }
    }
}

// ============================================================================
// MAIN CONTENT COMPONENTS
// ============================================================================

@Composable
fun TimerContent(
    uiState: TimerState,
    animatedProgress: Float,
    paddingValues: PaddingValues,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onModeChanged: (String) -> Unit,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onTargetTimeChanged: (Int, Int) -> Unit,
    onShowNameDialog: () -> Unit,
    onUpdateTimerName: (String) -> Unit
) {
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
                .pointerInput(uiState.timerMode) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            val dragThreshold = 50f
                            if (totalDrag > dragThreshold) {
                                // Swiped right: Switch to duration mode
                                if (uiState.timerMode == "target_time") {
                                    onModeChanged("duration")
                                }
                            } else if (totalDrag < -dragThreshold) {
                                // Swiped left: Switch to target time mode
                                if (uiState.timerMode == "duration") {
                                    onModeChanged("target_time")
                                }
                            }
                        }
                    )
                }
        ) {
            TimerMainContent(
                uiState = uiState,
                animatedProgress = animatedProgress,
                onHoursChanged = onHoursChanged,
                onMinutesChanged = onMinutesChanged,
                onTargetTimeChanged = onTargetTimeChanged,
                onShowNameDialog = onShowNameDialog
            )
        }
        
        // Bottom buttons
        BottomButtons(
            isRunning = uiState.isRunning,
            isFinished = uiState.isFinished,
            onStart = onStart,
            onReset = onReset,
            modifier = Modifier.align(Alignment.BottomCenter),
            hours = uiState.hours,
            minutes = uiState.minutes,
            timerMode = uiState.timerMode,
            targetHour = uiState.targetHour,
            targetMinute = uiState.targetMinute
        )
    }
}

@Composable
fun TimerMainContent(
    uiState: TimerState,
    animatedProgress: Float,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onTargetTimeChanged: (Int, Int) -> Unit,
    onShowNameDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        TimerNameSection(
            timerName = uiState.timerName,
            isRunning = uiState.isRunning,
            onShowNameDialog = onShowNameDialog
        )
        
        // Main Timer Display
        TimerDisplay(
            timeText = uiState.timeRemaining,
            progress = animatedProgress,
            percentage = uiState.percentage,
            isFinished = uiState.isFinished,
            isRunning = uiState.isRunning
        )
    
        Spacer(modifier = Modifier.height(24.dp))
        
        // Timer Controls - always show, but sliders hidden when running
        UnifiedTimeInput(
            timerMode = uiState.timerMode,
            hours = uiState.hours,
            minutes = uiState.minutes,
            targetHour = uiState.targetHour,
            targetMinute = uiState.targetMinute,
            onHoursChanged = onHoursChanged,
            onMinutesChanged = onMinutesChanged,
            onTargetTimeChanged = onTargetTimeChanged,
            isRunning = uiState.isRunning
        )
        
        // Add space for bottom buttons
        Spacer(modifier = Modifier.height(80.dp))
    }
}