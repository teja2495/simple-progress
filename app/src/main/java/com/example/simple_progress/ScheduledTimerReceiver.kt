package com.example.simple_progress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ScheduledTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remainingTime = intent.getLongExtra(EXTRA_REMAINING_TIME, 0)
        val totalTime = intent.getLongExtra(EXTRA_TOTAL_TIME, 0)
        val timerName = intent.getStringExtra(EXTRA_TIMER_NAME) ?: ""
        
        if (remainingTime > 0 && totalTime > 0) {
            // Calculate end time for timer state
            val endTime = System.currentTimeMillis() + remainingTime
            
            // Save timer state so ViewModel can detect it
            val sharedPreferences = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit()
                .putLong(KEY_END_TIME, endTime)
                .putLong(KEY_TOTAL_TIME, totalTime)
                .putString(KEY_TIMER_NAME, timerName)
                .apply()
            
            // Start the timer service
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
                putExtra(TimerService.EXTRA_REMAINING_TIME, remainingTime)
                putExtra(TimerService.EXTRA_TOTAL_TIME, totalTime)
                putExtra(TimerService.EXTRA_TIMER_NAME, timerName)
            }
            
            ContextCompat.startForegroundService(context, serviceIntent)
            
            // Clear the scheduled timer state
            sharedPreferences.edit()
                .remove(KEY_SCHEDULED_TIME)
                .remove(KEY_SCHEDULED_DURATION)
                .remove(KEY_SCHEDULED_TIMER_NAME)
                .apply()
        }
    }
    
    companion object {
        const val EXTRA_REMAINING_TIME = "remaining_time"
        const val EXTRA_TOTAL_TIME = "total_time"
        const val EXTRA_TIMER_NAME = "timer_name"
        
        private const val KEY_END_TIME = "end_time"
        private const val KEY_TOTAL_TIME = "total_time"
        private const val KEY_TIMER_NAME = "timer_name"
        private const val KEY_SCHEDULED_TIME = "scheduled_time"
        private const val KEY_SCHEDULED_DURATION = "scheduled_duration"
        private const val KEY_SCHEDULED_TIMER_NAME = "scheduled_timer_name"
    }
}
