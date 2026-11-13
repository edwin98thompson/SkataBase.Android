package com.example.myfirstapp.training

data class TrainingConfig(
    val startThreshold: Float = 100f,
    val endThreshold: Float = 50f,
    val preEventSamples: Int = 50,
    val postEventSamples: Int = 50
)

sealed interface TrainingSessionState {
    data object Idle : TrainingSessionState
    data class Running(
        val sessionDirectory: String,
        val eventsSaved: Int,
        val activeEvent: Int?,
        val activeEventSamples: Int
    ) : TrainingSessionState
    data class Paused(
        val sessionDirectory: String,
        val eventsSaved: Int
    ) : TrainingSessionState
    data class Completed(
        val sessionDirectory: String,
        val eventsSaved: Int
    ) : TrainingSessionState
    data class Error(val message: String) : TrainingSessionState
}
