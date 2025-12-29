package com.example.simple_progress

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_progress.utils.getDefaultTargetHour
import com.example.simple_progress.utils.getDefaultTargetMinute
import com.example.simple_progress.utils.getCurrentHour
import com.example.simple_progress.utils.getCurrentMinute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Calendar

// ============================================================================
// DATA CLASSES AND CONSTANTS
// ============================================================================

data class TimerState(
    val timeRemaining: String = "00:00:00",
    val progress: Float = 0f,
    val percentage: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val isPaused: Boolean = false,
    val hours: Int = 1,
    val minutes: Int = 30, // Default to 1 hour 30 minutes for duration mode
    val timerName: String = "",
    val timerMode: String = "duration", // "duration" or "target_time"
    val targetHour: Int = getCurrentHour(), // Default to current time for time mode
    val targetMinute: Int = getCurrentMinute(), // Default to current time for time mode
    val isScheduled: Boolean = false,
    val scheduledStartTime: Long = 0L, // Timestamp when timer should start
    val scheduledDuration: Long = 0L // Duration of the scheduled timer in milliseconds
)

// ============================================================================
// MAIN VIEW MODEL CLASS
// ============================================================================

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    private val sharedPreferences = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    private val _uiState = MutableStateFlow(TimerState())
    val uiState: StateFlow<TimerState> = _uiState.asStateFlow()
    
    private var timerService: TimerService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            serviceBound = true
            
            // Update UI state to show timer is running
            _uiState.value = _uiState.value.copy(
                isRunning = true,
                isFinished = false,
                isScheduled = false
            )
            
            // Set up callbacks
            timerService?.onTimerUpdate = { timeString, progress, percentage ->
                _uiState.value = _uiState.value.copy(
                    timeRemaining = timeString,
                    progress = progress,
                    percentage = percentage
                )
            }
            
            timerService?.onTimerComplete = {
                _uiState.value = _uiState.value.copy(
                    timeRemaining = "Done!",
                    progress = 1f,
                    percentage = 100,
                    isRunning = false,
                    isFinished = true
                )
                clearTimerState()
                unbindService()
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            serviceBound = false
        }
    }
    
    init {
        createNotificationChannel()
        restoreTimerState()
        observeTimerActions()
    }
    
    private fun observeTimerActions() {
        TimerActionBus.resetAction
            .onEach { resetTimer() }
            .launchIn(viewModelScope)
    }
    
    private fun bindService() {
        if (!serviceBound) {
            val intent = Intent(context, TimerService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    private fun unbindService() {
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // Service already unbound
            }
            serviceBound = false
            timerService = null
        }
    }
    
    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================
    
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
        saveTimerName(name)

        // If timer is running, update the service's notification
        if (_uiState.value.isRunning && serviceBound) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_UPDATE_TIMER_NAME
                putExtra(TimerService.EXTRA_TIMER_NAME, name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
    
    fun setTimerMode(mode: String) {
        val currentState = _uiState.value
        val newState = when (mode) {
            "target_time" -> {
                // When switching to target_time mode, set current time as default
                currentState.copy(
                    timerMode = mode,
                    targetHour = getCurrentHour(),
                    targetMinute = getCurrentMinute()
                )
            }
            "duration" -> {
                // When switching to duration mode, set 1 hour 30 minutes as default
                currentState.copy(
                    timerMode = mode,
                    hours = 1,
                    minutes = 30
                )
            }
            else -> currentState.copy(timerMode = mode)
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
        
        // Start the foreground service
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER
            putExtra(TimerService.EXTRA_REMAINING_TIME, totalTimeInMillis)
            putExtra(TimerService.EXTRA_TOTAL_TIME, totalTimeInMillis)
            putExtra(TimerService.EXTRA_TIMER_NAME, _uiState.value.timerName)
            putExtra(TimerService.EXTRA_TIMER_MODE, _uiState.value.timerMode)
        }
        
        ContextCompat.startForegroundService(context, intent)
        bindService()
        
        // Update UI state immediately
        _uiState.value = _uiState.value.copy(
            isRunning = true,
            isFinished = false,
            isPaused = false,
            progress = 0f,
            percentage = 0
        )
    }
    
    fun scheduleTimer(startHour: Int, startMinute: Int) {
        val totalTimeInMillis = if (_uiState.value.timerMode == "target_time") {
            calculateTimeToTarget()
        } else {
            calculateDurationTime()
        }
        
        if (totalTimeInMillis <= 0) return
        
        // Calculate the scheduled start time
        val scheduledStartTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If scheduled time is before current time, schedule for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
        
        // Validate that scheduled time is within 24 hours
        val timeUntilStart = scheduledStartTime - System.currentTimeMillis()
        if (timeUntilStart <= 0 || timeUntilStart > 24 * 60 * 60 * 1000L) {
            return
        }
        
        // Create the intent for the scheduled timer receiver
        val intent = Intent(context, ScheduledTimerReceiver::class.java).apply {
            putExtra(ScheduledTimerReceiver.EXTRA_REMAINING_TIME, totalTimeInMillis)
            putExtra(ScheduledTimerReceiver.EXTRA_TOTAL_TIME, totalTimeInMillis)
            putExtra(ScheduledTimerReceiver.EXTRA_TIMER_NAME, _uiState.value.timerName)
            putExtra(ScheduledTimerReceiver.EXTRA_TIMER_MODE, _uiState.value.timerMode)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SCHEDULED_TIMER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule the alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledStartTime,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledStartTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledStartTime,
                    pendingIntent
                )
            }
            
            // Save scheduled timer state
            saveScheduledTimerState(scheduledStartTime, totalTimeInMillis)
            
            // Update UI state
            _uiState.value = _uiState.value.copy(
                isScheduled = true,
                scheduledStartTime = scheduledStartTime,
                scheduledDuration = totalTimeInMillis
            )
        } catch (e: SecurityException) {
            // Permission not granted
            e.printStackTrace()
        }
    }
    
    fun cancelScheduledTimer() {
        val intent = Intent(context, ScheduledTimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SCHEDULED_TIMER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        clearScheduledTimerState()
        
        _uiState.value = _uiState.value.copy(
            isScheduled = false,
            scheduledStartTime = 0L,
            scheduledDuration = 0L
        )
    }
    
    fun pauseTimer() {
        if (_uiState.value.isRunning && !_uiState.value.isPaused && _uiState.value.timerMode == "duration") {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_PAUSE_TIMER
            }
            ContextCompat.startForegroundService(context, intent)

            _uiState.value = _uiState.value.copy(isPaused = true)
        }
    }

    fun resumeTimer() {
        if (_uiState.value.isRunning && _uiState.value.isPaused && _uiState.value.timerMode == "duration") {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_RESUME_TIMER
            }
            ContextCompat.startForegroundService(context, intent)

            _uiState.value = _uiState.value.copy(isPaused = false)
        }
    }

    fun resetTimer() {
        // Stop the service
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_RESET_TIMER
        }
        context.startService(intent)
        unbindService()

        // Cancel any scheduled timers
        if (_uiState.value.isScheduled) {
            cancelScheduledTimer()
        }

        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            timeRemaining = "00:00:00",
            progress = 0f,
            percentage = 0,
            isRunning = false,
            isFinished = false,
            isPaused = false,
            timerName = "",
            hours = if (currentState.timerMode == "duration") 1 else currentState.hours,
            minutes = if (currentState.timerMode == "duration") 30 else currentState.minutes,
            targetHour = if (currentState.timerMode == "target_time") getCurrentHour() else currentState.targetHour,
            targetMinute = if (currentState.timerMode == "target_time") getCurrentMinute() else currentState.targetMinute,
            isScheduled = false,
            scheduledStartTime = 0L,
            scheduledDuration = 0L
        )
        clearTimerState()
        clearNotifications()
    }
    
    /**
     * Check if a timer is running and bind to the service if needed.
     * This should be called when the app resumes to sync UI with running timer.
     */
    fun checkAndBindToRunningTimer() {
        val endTime = sharedPreferences.getLong(KEY_END_TIME, 0)
        val totalTime = sharedPreferences.getLong(KEY_TOTAL_TIME, 0)
        
        if (endTime > System.currentTimeMillis() && totalTime > 0 && !serviceBound) {
            // Timer is running but we're not bound, bind to service
            bindService()
            
            // Update UI state
            val timerName = sharedPreferences.getString(KEY_TIMER_NAME, "") ?: ""
            val remainingTime = endTime - System.currentTimeMillis()
            val progress = 1 - (remainingTime.toFloat() / totalTime)
            val percentage = (progress * 100).toInt()
            val timeString = formatTime(remainingTime)
            
            _uiState.value = _uiState.value.copy(
                isRunning = true,
                isFinished = false,
                isScheduled = false,
                timeRemaining = timeString,
                progress = progress,
                percentage = percentage,
                timerName = timerName
            )
        } else if (endTime <= System.currentTimeMillis() && endTime > 0) {
            // Timer has finished, clear state
            clearTimerState()
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                isFinished = true
            )
        }
    }
    
    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    // ============================================================================
    // TIMER CALCULATION METHODS
    // ============================================================================
    
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
    
    // ============================================================================
    // COUNTDOWN LOGIC (Now handled by TimerService)
    // ============================================================================
    
    // ============================================================================
    // UTILITY METHODS
    // ============================================================================
    
    // ============================================================================
    // PERSISTENCE METHODS
    // ============================================================================
    
    private fun saveTimerState(endTime: Long, totalTime: Long) {
        sharedPreferences.edit()
            .putLong(KEY_END_TIME, endTime)
            .putLong(KEY_TOTAL_TIME, totalTime)
            .putString(KEY_TIMER_NAME, _uiState.value.timerName)
            .putBoolean(KEY_IS_PAUSED, _uiState.value.isPaused)
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
    
    private fun saveTimerName(name: String) {
        sharedPreferences.edit()
            .putString(KEY_TIMER_NAME, name)
            .apply()
    }
    
    private fun saveScheduledTimerState(scheduledTime: Long, duration: Long) {
        with(_uiState.value) {
            sharedPreferences.edit()
                .putLong(KEY_SCHEDULED_TIME, scheduledTime)
                .putLong(KEY_SCHEDULED_DURATION, duration)
                .putString(KEY_SCHEDULED_TIMER_NAME, timerName)
                .putString(KEY_SCHEDULED_TIMER_MODE, timerMode)
                .apply()
        }
    }
    
    private fun clearScheduledTimerState() {
        sharedPreferences.edit()
            .remove(KEY_SCHEDULED_TIME)
            .remove(KEY_SCHEDULED_DURATION)
            .remove(KEY_SCHEDULED_TIMER_NAME)
            .remove(KEY_SCHEDULED_TIMER_MODE)
            .apply()
    }
    
    private fun clearTimerState() {
        sharedPreferences.edit()
            .remove(KEY_END_TIME)
            .remove(KEY_TOTAL_TIME)
            .remove(KEY_TIMER_NAME)
            .remove(KEY_IS_PAUSED)
            .apply()
    }
    
    private fun restoreTimerState() {
        val endTime = sharedPreferences.getLong(KEY_END_TIME, 0)
        val totalTime = sharedPreferences.getLong(KEY_TOTAL_TIME, 0)
        val timerName = sharedPreferences.getString(KEY_TIMER_NAME, "") ?: ""
        val timerMode = sharedPreferences.getString(KEY_TIMER_MODE, "duration") ?: "duration"
        val isPaused = sharedPreferences.getBoolean(KEY_IS_PAUSED, false)

        // Restore scheduled timer state
        val scheduledTime = sharedPreferences.getLong(KEY_SCHEDULED_TIME, 0)
        val scheduledDuration = sharedPreferences.getLong(KEY_SCHEDULED_DURATION, 0)
        val scheduledTimerName = sharedPreferences.getString(KEY_SCHEDULED_TIMER_NAME, "") ?: ""
        val scheduledTimerMode = sharedPreferences.getString(KEY_SCHEDULED_TIMER_MODE, "duration") ?: "duration"

        // Set defaults based on mode
        val targetHour = if (timerMode == "target_time") getCurrentHour() else getDefaultTargetHour()
        val targetMinute = if (timerMode == "target_time") getCurrentMinute() else getDefaultTargetMinute()
        val hours = if (timerMode == "duration") 1 else 0
        val minutes = if (timerMode == "duration") 30 else 0

        _uiState.value = _uiState.value.copy(
            timerName = timerName,
            timerMode = timerMode,
            hours = hours,
            minutes = minutes,
            targetHour = targetHour,
            targetMinute = targetMinute,
            isPaused = isPaused
        )

        // Check if there's a scheduled timer
        if (scheduledTime > System.currentTimeMillis()) {
            _uiState.value = _uiState.value.copy(
                isScheduled = true,
                scheduledStartTime = scheduledTime,
                scheduledDuration = scheduledDuration,
                timerName = scheduledTimerName
            )
        } else if (scheduledTime > 0) {
            // Scheduled time has passed, clear it
            clearScheduledTimerState()
        }

        if (endTime > System.currentTimeMillis() && totalTime > 0) {
            val remainingTime = endTime - System.currentTimeMillis()

            // Restart the service with remaining time
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
                putExtra(TimerService.EXTRA_REMAINING_TIME, remainingTime)
                putExtra(TimerService.EXTRA_TOTAL_TIME, totalTime)
                putExtra(TimerService.EXTRA_TIMER_NAME, timerName)
            }

            ContextCompat.startForegroundService(context, intent)
            bindService()

            _uiState.value = _uiState.value.copy(
                isRunning = true,
                isFinished = false,
                isPaused = isPaused
            )
        }
    }
    
    // ============================================================================
    // NOTIFICATION METHODS (Now handled by TimerService)
    // ============================================================================
    
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
    
    private fun clearNotifications() {
        notificationManager.cancel(TIMER_NOTIFICATION_ID)
        notificationManager.cancel(DONE_NOTIFICATION_ID)
    }
    
    override fun onCleared() {
        super.onCleared()
        unbindService()
        clearNotifications()
    }
    
    // ============================================================================
    // COMPANION OBJECT - CONSTANTS
    // ============================================================================
    
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
        private const val KEY_IS_PAUSED = "is_paused"
        private const val KEY_SCHEDULED_TIME = "scheduled_time"
        private const val KEY_SCHEDULED_DURATION = "scheduled_duration"
        private const val KEY_SCHEDULED_TIMER_NAME = "scheduled_timer_name"
        private const val KEY_SCHEDULED_TIMER_MODE = "scheduled_timer_mode"
        private const val SCHEDULED_TIMER_REQUEST_CODE = 1001
    }
}