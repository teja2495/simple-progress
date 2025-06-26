package com.example.simple_progress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simple_progress.ui.theme.SimpleProgressTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for better keyboard handling
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            SimpleProgressTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { TimerScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(timerViewModel: TimerViewModel = viewModel()) {
    val time by timerViewModel.time.collectAsState()
    val progress by timerViewModel.progress.collectAsState()
    val isRunning by timerViewModel.isRunning.collectAsState()
    val hours by timerViewModel.hours.collectAsState()
    val minutes by timerViewModel.minutes.collectAsState()
    val timerName by timerViewModel.timerName.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val showDialog = rememberSaveable { mutableStateOf(false) }
    val tempTimerName = rememberSaveable { mutableStateOf("") }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    if (showDialog.value) {
        AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text("Name your timer (Optional)") },
                text = {
                    OutlinedTextField(
                            value = tempTimerName.value,
                            onValueChange = { tempTimerName.value = it },
                            label = { Text("Timer Name") })
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                timerViewModel.setTimerName(tempTimerName.value)
                                timerViewModel.startTimer()
                                showDialog.value = false
                            })
                    { Text("Start") }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                timerViewModel.startTimer()
                                showDialog.value = false
                            })
                    { Text("Skip") }
                }
        )
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Simple Progress") },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor = MaterialTheme.colorScheme.primary,
                                ),
                )
            }
    ) { innerPadding ->
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            if (isRunning && timerName.isNotEmpty()) {
                Text(text = timerName, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(16.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                val animatedProgress by animateFloatAsState(targetValue = progress, label = "")
                CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(250.dp),
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (time == "Done!") {
                        Text(text = time, fontSize = 32.sp)
                    } else {
                        Text(text = time, fontSize = 46.sp)
                    }
                    if (isRunning) {
                        Text(text = "${(progress * 100).toInt()}%", fontSize = 24.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!isRunning) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedTextField(
                            value = hours,
                            onValueChange = { timerViewModel.onHoursChanged(it) },
                            label = { Text("Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .width(100.dp)
                                .focusRequester(focusRequester)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                            value = minutes,
                            onValueChange = { timerViewModel.onMinutesChanged(it) },
                            label = { Text("Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                    onClick = {
                        if (isRunning) {
                            timerViewModel.resetTimer()
                        } else {
                            showDialog.value = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.5f)
            ) { Text(text = if (isRunning) "Reset" else "Start") }
        }
    }
}

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences =
            application.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
    private val notificationManager =
            application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _time = MutableStateFlow("00:00:00")
    val time: StateFlow<String> = _time

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _hours = MutableStateFlow("")
    val hours: StateFlow<String> = _hours

    private val _minutes = MutableStateFlow("")
    val minutes: StateFlow<String> = _minutes

    private val _timerName = MutableStateFlow("")
    val timerName: StateFlow<String> = _timerName

    private var timerJob: Job? = null

    init {
        createNotificationChannel()
        restoreTimerState()
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun onHoursChanged(newHours: String) {
        if (newHours.all { it.isDigit() }) {
            _hours.value = newHours
        }
    }

    fun onMinutesChanged(newMinutes: String) {
        if (newMinutes.all { it.isDigit() }) {
            _minutes.value = newMinutes
        }
    }

    fun setTimerName(name: String) {
        _timerName.value = name
        sharedPreferences.edit().putString("timer_name", name).apply()
    }

    fun startTimer() {
        val hoursValue = _hours.value.toIntOrNull() ?: 0
        val minutesValue = _minutes.value.toIntOrNull() ?: 0
        val totalTimeInMillis = (hoursValue * 3600 + minutesValue * 60) * 1000L

        if (totalTimeInMillis > 0) {
            val endTime = System.currentTimeMillis() + totalTimeInMillis
            sharedPreferences.edit().putLong("end_time", endTime).apply()
            sharedPreferences.edit().putLong("total_time", totalTimeInMillis).apply()
            startCountdown(totalTimeInMillis, totalTimeInMillis)
        }
    }

    private fun startCountdown(remainingTime: Long, totalTime: Long) {
        _isRunning.value = true
        timerJob =
                viewModelScope.launch {
                    var remainingTimeInMillis = remainingTime
                    while (remainingTimeInMillis > 0) {
                        _time.value = formatTime(remainingTimeInMillis)
                        _progress.value = 1 - remainingTimeInMillis.toFloat() / totalTime
                        showNotification(remainingTimeInMillis, totalTime)
                        delay(1000)
                        remainingTimeInMillis -= 1000
                    }
                    _time.value = "Done!"
                    _progress.value = 1f
                    _isRunning.value = false
                    showDoneNotification()
                    resetTimer()
                }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _time.value = "00:00:00"
        _progress.value = 0f
        _isRunning.value = false
        _hours.value = ""
        _minutes.value = ""
        _timerName.value = ""
        sharedPreferences.edit().remove("end_time").remove("total_time").remove("timer_name").apply()
        notificationManager.cancel(1)
    }

    private fun restoreTimerState() {
        val endTime = sharedPreferences.getLong("end_time", 0)
        val totalTime = sharedPreferences.getLong("total_time", 0)
        val name = sharedPreferences.getString("timer_name", "")
        if (endTime > System.currentTimeMillis()) {
            _timerName.value = name ?: ""
            val remainingTime = endTime - System.currentTimeMillis()
            startCountdown(remainingTime, totalTime)
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Timer Notifications"
            val descriptionText = "Shows the countdown timer progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("timer_channel", name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(remainingTime: Long, totalTime: Long) {
        val openAppIntent = Intent(getApplication(), MainActivity::class.java)
        val openAppPendingIntent =
                PendingIntent.getActivity(getApplication(), 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification =
                NotificationCompat.Builder(getApplication(), "timer_channel")
                        .setContentTitle(if (timerName.value.isNotEmpty()) timerName.value else "")
                        .setContentText("${formatTime(remainingTime)} - ${(progress.value * 100).toInt()}%")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setProgress(totalTime.toInt(), (totalTime - remainingTime).toInt(), false)
                        .setOngoing(true)
                        .setContentIntent(openAppPendingIntent)
                        .build()

        notificationManager.notify(1, notification)
    }

    private fun showDoneNotification() {
        val openAppIntent = Intent(getApplication(), MainActivity::class.java)
        val openAppPendingIntent =
                PendingIntent.getActivity(getApplication(), 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification =
                NotificationCompat.Builder(getApplication(), "timer_channel")
                        .setContentTitle(if (timerName.value.isNotEmpty()) timerName.value else "Simple Progress")
                        .setContentText("Timer is done!")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(openAppPendingIntent)
                        .setAutoCancel(true)
                        .build()

        notificationManager.notify(2, notification)
    }
}
