package com.example.simple_progress

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

private fun getDefaultTargetHour(): Int {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MINUTE, 15) // Add 15 minutes to current time
    return calendar.get(Calendar.HOUR_OF_DAY)
}

private fun getDefaultTargetMinute(): Int {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MINUTE, 15) // Add 15 minutes to current time
    return calendar.get(Calendar.MINUTE)
}

data class TimerState(
    val timeRemaining: String = "00:00:00",
    val progress: Float = 0f,
    val percentage: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val hours: Int = 0,
    val minutes: Int = 0,
    val timerName: String = "",
    val timerMode: String = "duration", // "duration" or "target_time"
    val targetHour: Int = getDefaultTargetHour(),
    val targetMinute: Int = getDefaultTargetMinute()
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    private val sharedPreferences = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val _uiState = MutableStateFlow(TimerState())
    val uiState: StateFlow<TimerState> = _uiState.asStateFlow()
    
    private var timerJob: Job? = null
    
    init {
        createNotificationChannel()
        restoreTimerState()
    }
    
    fun updateHours(hours: Int) {
        if (hours in 0..24) {
            _uiState.value = _uiState.value.copy(hours = hours)
        }
    }
    
    fun updateMinutes(minutes: Int) {
        if (minutes in 0..59) {
            _uiState.value = _uiState.value.copy(minutes = minutes)
        }
    }
    
    fun updateTimerName(name: String) {
        _uiState.value = _uiState.value.copy(timerName = name)
        // Save the timer name to persistent storage
        sharedPreferences.edit()
            .putString(KEY_TIMER_NAME, name)
            .apply()
    }
    
    fun setTimerMode(mode: String) {
        val currentState = _uiState.value
        val newState = if (mode == "target_time" && currentState.timerMode != "target_time") {
            // When switching to target_time mode for the first time, set default target time
            currentState.copy(
                timerMode = mode,
                targetHour = getDefaultTargetHour(),
                targetMinute = getDefaultTargetMinute()
            )
        } else {
            currentState.copy(timerMode = mode)
        }
        
        _uiState.value = newState
        saveTimerSettings()
    }
    
    fun setTargetTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(targetHour = hour, targetMinute = minute)
        saveTimerSettings()
    }
    
    fun startTimer() {
        val totalTimeInMillis = if (_uiState.value.timerMode == "target_time") {
            calculateTimeToTarget()
        } else {
            calculateDurationTime()
        }
        
        if (totalTimeInMillis <= 0) return
        
        val endTime = System.currentTimeMillis() + totalTimeInMillis
        saveTimerState(endTime, totalTimeInMillis)
        startCountdown(totalTimeInMillis, totalTimeInMillis)
    }
    
    fun resetTimer() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            timeRemaining = "00:00:00",
            progress = 0f,
            percentage = 0,
            isRunning = false,
            isFinished = false,
            timerName = "", // Clear the timer name when resetting
            targetHour = getDefaultTargetHour(), // Reset target time to 15 minutes from current time
            targetMinute = getDefaultTargetMinute()
        )
        clearTimerState()
        notificationManager.cancel(TIMER_NOTIFICATION_ID)
        notificationManager.cancel(DONE_NOTIFICATION_ID)
    }
    
    private fun calculateDurationTime(): Long {
        val currentState = _uiState.value
        return (currentState.hours * 3600 + currentState.minutes * 60) * 1000L
    }
    
    private fun calculateTimeToTarget(): Long {
        val now = Calendar.getInstance()
        val targetTime = Calendar.getInstance()
        
        targetTime.set(Calendar.HOUR_OF_DAY, _uiState.value.targetHour)
        targetTime.set(Calendar.MINUTE, _uiState.value.targetMinute)
        targetTime.set(Calendar.SECOND, 0)
        targetTime.set(Calendar.MILLISECOND, 0)
        
        // If target time has already passed today, set it for tomorrow
        if (targetTime.before(now)) {
            targetTime.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return targetTime.timeInMillis - now.timeInMillis
    }
    
    private fun startCountdown(remainingTime: Long, totalTime: Long) {
        // Set initial progress to 0 immediately when starting
        _uiState.value = _uiState.value.copy(
            isRunning = true, 
            isFinished = false,
            progress = 0f,
            percentage = 0
        )
        
        timerJob = viewModelScope.launch {
            var timeLeft = remainingTime
            
            while (timeLeft > 0) {
                val timeString = formatTime(timeLeft)
                val progress = 1 - (timeLeft.toFloat() / totalTime)
                val percentage = (progress * 100).toInt()
                
                _uiState.value = _uiState.value.copy(
                    timeRemaining = timeString,
                    progress = progress,
                    percentage = percentage
                )
                
                showProgressNotification(timeString, percentage)
                delay(1000)
                timeLeft -= 1000
            }
            
            // Timer finished
            _uiState.value = _uiState.value.copy(
                timeRemaining = "Done!",
                progress = 1f,
                percentage = 100,
                isRunning = false,
                isFinished = true
            )
            
            clearTimerState()
            showCompletionNotification()
        }
    }
    
    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    private fun saveTimerState(endTime: Long, totalTime: Long) {
        sharedPreferences.edit()
            .putLong(KEY_END_TIME, endTime)
            .putLong(KEY_TOTAL_TIME, totalTime)
            .putString(KEY_TIMER_NAME, _uiState.value.timerName)
            .apply()
    }
    
    private fun saveTimerSettings() {
        with(_uiState.value) {
            sharedPreferences.edit()
                .putString(KEY_TIMER_MODE, timerMode)
                .putInt(KEY_TARGET_HOUR, targetHour)
                .putInt(KEY_TARGET_MINUTE, targetMinute)
                .apply()
        }
    }
    
    private fun clearTimerState() {
        sharedPreferences.edit()
            .remove(KEY_END_TIME)
            .remove(KEY_TOTAL_TIME)
            .remove(KEY_TIMER_NAME)
            .apply()
    }
    
    private fun restoreTimerState() {
        val endTime = sharedPreferences.getLong(KEY_END_TIME, 0)
        val totalTime = sharedPreferences.getLong(KEY_TOTAL_TIME, 0)
        val timerName = sharedPreferences.getString(KEY_TIMER_NAME, "") ?: ""
        val timerMode = sharedPreferences.getString(KEY_TIMER_MODE, "duration") ?: "duration"
        
        // Always use current time + 15 minutes as default target time
        val targetHour = getDefaultTargetHour()
        val targetMinute = getDefaultTargetMinute()
        
        _uiState.value = _uiState.value.copy(
            timerName = timerName,
            timerMode = timerMode,
            targetHour = targetHour,
            targetMinute = targetMinute
        )
        
        if (endTime > System.currentTimeMillis() && totalTime > 0) {
            val remainingTime = endTime - System.currentTimeMillis()
            startCountdown(remainingTime, totalTime)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows timer progress and completion"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showProgressNotification(timeString: String, percentage: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(_uiState.value.timerName.ifEmpty { "Timer Running" })
            .setContentText("$timeString remaining ($percentage%)")
            .setProgress(100, percentage, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
        
        notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
    }
    
    private fun showCompletionNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(_uiState.value.timerName.ifEmpty { "Timer Complete" })
            .setContentText("Time's up!")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(DONE_NOTIFICATION_ID, notification)
    }
    
    companion object {
        private const val CHANNEL_ID = "timer_channel"
        private const val TIMER_NOTIFICATION_ID = 1
        private const val DONE_NOTIFICATION_ID = 2
        private const val KEY_END_TIME = "end_time"
        private const val KEY_TOTAL_TIME = "total_time"
        private const val KEY_TIMER_NAME = "timer_name"
        private const val KEY_TIMER_MODE = "timer_mode"
        private const val KEY_TARGET_HOUR = "target_hour"
        private const val KEY_TARGET_MINUTE = "target_minute"
    }
}