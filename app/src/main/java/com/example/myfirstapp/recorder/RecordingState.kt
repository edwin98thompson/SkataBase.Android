package com.example.myfirstapp.recorder

import android.net.Uri

sealed interface RecordingState {
    object Idle : RecordingState
    data class Recording(
        val uri: Uri,
        val startTimeMs: Long,
        val sampleCount: Int = 0
    ) : RecordingState
    data class Completed(
        val uri: Uri,
        val totalSamples: Int,
        val durationMs: Long
    ) : RecordingState
    data class Error(val message: String) : RecordingState
}
