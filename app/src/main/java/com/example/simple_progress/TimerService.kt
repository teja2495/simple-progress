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

                if (remainingTime > 0 && totalTime > 0) {
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

        timerJob = serviceScope.launch {
            var timeLeft = remainingTime
            
            while (timeLeft > 0 && isActive) {
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
        const val ACTION_TIMER_RESET = "com.example.simple_progress.TIMER_RESET"
        
        const val EXTRA_REMAINING_TIME = "remaining_time"
        const val EXTRA_TOTAL_TIME = "total_time"
        const val EXTRA_TIMER_NAME = "timer_name"
        
        private const val CHANNEL_ID = "timer_channel"
        private const val TIMER_NOTIFICATION_ID = 1
        private const val DONE_NOTIFICATION_ID = 2
    }
}



