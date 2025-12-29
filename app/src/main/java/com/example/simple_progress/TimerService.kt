package com.example.simple_progress

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class TimerService : Service() {
    
    private val binder = TimerBinder()
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTimerName: String = ""
    private var currentTimerMode: String = "duration"
    private var isPaused: Boolean = false
    private var pausedTimeRemaining: Long = 0L
    
    private val sharedPreferences by lazy {
        getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
    }
    
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    var onTimerUpdate: ((String, Float, Int) -> Unit)? = null
    var onTimerComplete: (() -> Unit)? = null
    
    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val remainingTime = intent.getLongExtra(EXTRA_REMAINING_TIME, 0)
                val totalTime = intent.getLongExtra(EXTRA_TOTAL_TIME, 0)
                val timerName = intent.getStringExtra(EXTRA_TIMER_NAME) ?: ""
                val timerMode = intent.getStringExtra(EXTRA_TIMER_MODE) ?: "duration"

                if (remainingTime > 0 && totalTime > 0) {
                    currentTimerMode = timerMode
                    startForeground(TIMER_NOTIFICATION_ID, createInitialNotification(timerName))
                    startCountdown(remainingTime, totalTime, timerName)
                }
            }
            ACTION_UPDATE_TIMER_NAME -> {
                val newTimerName = intent.getStringExtra(EXTRA_TIMER_NAME) ?: ""
                updateTimerName(newTimerName)
            }
            ACTION_STOP_TIMER -> {
                stopTimer()
            }
            ACTION_RESET_TIMER -> {
                stopTimer()
                broadcastTimerReset()
            }
            ACTION_PAUSE_TIMER -> {
                pauseTimer()
            }
            ACTION_RESUME_TIMER -> {
                resumeTimer()
            }
        }

        return START_STICKY
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SimpleProgress::TimerWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun startCountdown(remainingTime: Long, totalTime: Long, timerName: String) {
        timerJob?.cancel()
        currentTimerName = timerName
        isPaused = false

        timerJob = serviceScope.launch {
            var timeLeft = remainingTime

            while (timeLeft > 0 && isActive) {
                // Wait for resume if paused
                while (isPaused && isActive) {
                    updatePausedNotification()
                    delay(500) // Update paused notification every 500ms
                }

                if (!isActive) break

                val timeString = formatTime(timeLeft)
                val progress = 1 - (timeLeft.toFloat() / totalTime)
                val percentage = (progress * 100).toInt()

                // Update notification
                updateProgressNotification(timeString, percentage)

                // Notify listeners (ViewModel)
                onTimerUpdate?.invoke(timeString, progress, percentage)

                delay(1000)
                timeLeft -= 1000
            }

            if (isActive) {
                // Timer completed
                onTimerComplete?.invoke()
                showCompletionNotification(timerName)
                stopTimer()
            }
        }
    }

    private fun pauseTimer() {
        isPaused = true
        // Store the current remaining time for resume
        val endTime = sharedPreferences.getLong("end_time", 0)
        val currentTime = System.currentTimeMillis()
        if (endTime > currentTime) {
            pausedTimeRemaining = endTime - currentTime
        }
        updatePausedNotification()
    }

    private fun resumeTimer() {
        if (isPaused && pausedTimeRemaining > 0) {
            isPaused = false
            // Calculate new end time based on paused remaining time
            val newEndTime = System.currentTimeMillis() + pausedTimeRemaining
            val totalTime = sharedPreferences.getLong("total_time", 0)

            sharedPreferences.edit()
                .putLong("end_time", newEndTime)
                .apply()

            // Update notification to show running state
            val timeString = formatTime(pausedTimeRemaining)
            val progress = 1 - (pausedTimeRemaining.toFloat() / totalTime)
            val percentage = (progress * 100).toInt()
            updateProgressNotification(timeString, percentage)
        }
    }

    private fun updatePausedNotification() {
        val totalTime = sharedPreferences.getLong("total_time", 0)

        if (totalTime > 0 && pausedTimeRemaining > 0) {
            val timeString = formatTime(pausedTimeRemaining)
            val progress = 1 - (pausedTimeRemaining.toFloat() / totalTime)
            val percentage = (progress * 100).toInt()

            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )

            val resetIntent = Intent(this, TimerResetReceiver::class.java)
            val resetPendingIntent = PendingIntent.getBroadcast(
                this, 1, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val resumeIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_RESUME_TIMER
            }
            val resumePendingIntent = PendingIntent.getService(
                this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(currentTimerName.ifEmpty { "Timer Paused" })
                .setContentText("$timeString - $percentage% (Paused)")
                .setProgress(100, percentage, false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .addAction(
                    R.drawable.ic_launcher_foreground,
                    "Resume",
                    resumePendingIntent
                )
                .addAction(
                    R.drawable.ic_launcher_foreground,
                    "Reset",
                    resetPendingIntent
                )
                .build()

            notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
        }
    }

    private fun updateTimerName(newName: String) {
        currentTimerName = newName
        // Update the notification with the new name immediately
        if (timerJob?.isActive == true) {
            updateNotificationWithNewName()
        }
    }

    private fun updateNotificationWithNewName() {
        // Get current timer state from shared preferences
        val endTime = sharedPreferences.getLong("end_time", 0)
        val totalTime = sharedPreferences.getLong("total_time", 0)

        if (endTime > 0 && totalTime > 0) {
            val remainingTime = endTime - System.currentTimeMillis()
            if (remainingTime > 0) {
                val timeString = formatTime(remainingTime)
                val progress = 1 - (remainingTime.toFloat() / totalTime)
                val percentage = (progress * 100).toInt()

                val intent = Intent(this, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )

                val resetIntent = Intent(this, TimerResetReceiver::class.java)
                val resetPendingIntent = PendingIntent.getBroadcast(
                    this, 1, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(currentTimerName.ifEmpty { "Timer Running" })
                    .setContentText("$timeString - $percentage%")
                    .setProgress(100, percentage, false)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
                    .setOnlyAlertOnce(true)
                    .addAction(
                        R.drawable.ic_launcher_foreground,
                        "Reset",
                        resetPendingIntent
                    )
                    .build()

                notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
    
    private fun createInitialNotification(timerName: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val resetIntent = Intent(this, TimerResetReceiver::class.java)
        val resetPendingIntent = PendingIntent.getBroadcast(
            this, 1, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(timerName.ifEmpty { "Timer Running" })
            .setContentText("Starting timer...")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Reset",
                resetPendingIntent
            )
            .build()
    }
    
    private fun updateProgressNotification(timeString: String, percentage: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = Intent(this, TimerResetReceiver::class.java)
        val resetPendingIntent = PendingIntent.getBroadcast(
            this, 1, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(currentTimerName.ifEmpty { "Timer Running" })
            .setContentText("$timeString - $percentage%")
            .setProgress(100, percentage, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)

        // Only add pause action for duration timers
        if (currentTimerMode == "duration") {
            val pauseIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_PAUSE_TIMER
            }
            val pausePendingIntent = PendingIntent.getService(
                this, 3, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                R.drawable.ic_launcher_foreground,
                "Pause",
                pausePendingIntent
            )
        }

        notificationBuilder.addAction(
            R.drawable.ic_launcher_foreground,
            "Reset",
            resetPendingIntent
        )

        val notification = notificationBuilder.build()
        notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
    }
    
    private fun showCompletionNotification(timerName: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val resetIntent = Intent(this, TimerResetReceiver::class.java)
        val resetPendingIntent = PendingIntent.getBroadcast(
            this, 2, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(timerName.ifEmpty { "Progress Complete" })
            .setContentText("Time's up!")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Reset",
                resetPendingIntent
            )
            .build()
        
        notificationManager.notify(DONE_NOTIFICATION_ID, notification)
    }
    
    private fun broadcastTimerReset() {
        // Broadcast to ViewModel via the event bus
        val intent = Intent(ACTION_TIMER_RESET)
        sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        notificationManager.cancel(TIMER_NOTIFICATION_ID)
    }
    
    companion object {
        const val ACTION_START_TIMER = "com.example.simple_progress.START_TIMER"
        const val ACTION_UPDATE_TIMER_NAME = "com.example.simple_progress.UPDATE_TIMER_NAME"
        const val ACTION_STOP_TIMER = "com.example.simple_progress.STOP_TIMER"
        const val ACTION_RESET_TIMER = "com.example.simple_progress.RESET_TIMER"
        const val ACTION_PAUSE_TIMER = "com.example.simple_progress.PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.example.simple_progress.RESUME_TIMER"
        const val ACTION_TIMER_RESET = "com.example.simple_progress.TIMER_RESET"
        
        const val EXTRA_REMAINING_TIME = "remaining_time"
        const val EXTRA_TOTAL_TIME = "total_time"
        const val EXTRA_TIMER_NAME = "timer_name"
        const val EXTRA_TIMER_MODE = "timer_mode"
        
        private const val CHANNEL_ID = "timer_channel"
        private const val TIMER_NOTIFICATION_ID = 1
        private const val DONE_NOTIFICATION_ID = 2
    }
}



