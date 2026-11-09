package com.example.myfirstapp.recorder

import android.content.Context
import com.example.myfirstapp.model.IMUPacket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MotionRecorder(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var recordingJob: Job? = null
    private var writer: BufferedWriter? = null

    companion object {
        private const val CSV_HEADER =
            "timestamp_ms,sequence,roll,pitch,yaw,accel_x,accel_y,accel_z," +
                    "gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,q0,q1,q2,q3"
    }

    fun startRecording(packetFlow: Flow<IMUPacket>) {
        if (_recordingState.value is RecordingState.Recording) return

        recordingJob = coroutineScope.launch {
            try {
                val file = createOutputFile()
                val bufferedWriter = BufferedWriter(FileWriter(file), 8192) // 8KB buffer
                writer = bufferedWriter

                // Write CSV Header
                bufferedWriter.write(CSV_HEADER)
                bufferedWriter.newLine()

                val startTime = System.currentTimeMillis()
                var sampleCount = 0
                _recordingState.value = RecordingState.Recording(file, startTime, sampleCount)

                packetFlow.collect { packet ->
                    val line = packetToCsv(packet)
                    bufferedWriter.write(line)
                    bufferedWriter.newLine()

                    sampleCount++
                    // Flush every 50 samples to prevent data loss without throttling I/O
                    if (sampleCount % 50 == 0) {
                        bufferedWriter.flush()
                        _recordingState.value = RecordingState.Recording(file, startTime, sampleCount)
                    }
                }
            } catch (e: CancellationException) {
                // Expected when stopping
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(e.message ?: "Failed to write file")
            } finally {
                closeWriter()
            }
        }
    }

    fun stopRecording() {
        val currentState = _recordingState.value
        recordingJob?.cancel()
        recordingJob = null
        closeWriter()

        if (currentState is RecordingState.Recording) {
            val duration = System.currentTimeMillis() - currentState.startTimeMs
            _recordingState.value = RecordingState.Completed(
                file = currentState.file,
                totalSamples = currentState.sampleCount,
                durationMs = duration
            )
        } else {
            _recordingState.value = RecordingState.Idle
        }
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {}
        writer = null
    }

    private fun createOutputFile(): File {
        // App-specific external storage (No permissions needed on Android 10+ / API 29+)
        val dir = context.getExternalFilesDir("recordings") ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "imu_motion_$timeStamp.csv")
    }

    private fun packetToCsv(p: IMUPacket): String {
        return "${p.timestampMs},${p.sequence}," +
                "${p.roll},${p.pitch},${p.yaw}," +
                "${p.accelX},${p.accelY},${p.accelZ}," +
                "${p.gyroX},${p.gyroY},${p.gyroZ}," +
                "${p.magX},${p.magY},${p.magZ}," +
                "${p.q0},${p.q1},${p.q2},${p.q3}"
    }
}