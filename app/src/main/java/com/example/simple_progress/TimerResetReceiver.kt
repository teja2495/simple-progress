package com.example.simple_progress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // We use goAsync to keep the receiver alive while we launch a coroutine.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Post the reset event to our shared bus.
                TimerActionBus.postResetAction()
            } finally {
                // We must finish the pending result to tell the system we're done.
                pendingResult.finish()
            }
        }
    }
} 