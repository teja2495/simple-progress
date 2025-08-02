package com.example.simple_progress

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A singleton object to act as an event bus for timer actions, allowing
 * background components to safely communicate with the ViewModel.
 */
object TimerActionBus {
    private val _resetAction = MutableSharedFlow<Unit>()
    val resetAction = _resetAction.asSharedFlow()

    suspend fun postResetAction() {
        _resetAction.emit(Unit)
    }
} 