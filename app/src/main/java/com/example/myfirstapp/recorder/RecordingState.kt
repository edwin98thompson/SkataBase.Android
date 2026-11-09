package com.example.myfirstapp.recorder

import java.io.File

sealed interface RecordingState {
    object Idle : RecordingState
    data class Recording(
        val file: File,
        val startTimeMs: Long,
        val sampleCount: Int = 0
    ) : RecordingState
    data class Completed(
        val file: File,
        val totalSamples: Int,
        val durationMs: Long
    ) : RecordingState
    data class Error(val message: String) : RecordingState
}