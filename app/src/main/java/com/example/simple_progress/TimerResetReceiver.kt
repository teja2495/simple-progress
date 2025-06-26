package com.example.simple_progress

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.simple_progress.RESET_TIMER") {
            val sharedPreferences = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
        }
    }
} 