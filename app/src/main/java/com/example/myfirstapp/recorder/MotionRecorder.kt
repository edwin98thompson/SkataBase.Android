package com.example.myfirstapp.recorder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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
import java.io.OutputStreamWriter
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
    private var outputUri: Uri? = null
    @Volatile private var samplesWritten = 0

    companion object {
        private const val CSV_HEADER =
            "timestamp_ms,sequence,roll,pitch,yaw,accel_x,accel_y,accel_z," +
                    "gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,q0,q1,q2,q3"
    }

    fun startRecording(packetFlow: Flow<IMUPacket>) {
        if (_recordingState.value is RecordingState.Recording) return

        recordingJob = coroutineScope.launch {
            var insertedUri: Uri? = null
            try {
                val uri = createOutputUri()
                insertedUri = uri
                outputUri = uri

                val outputStream = context.contentResolver.openOutputStream(uri, "w")
                    ?: error("Unable to open the recording file")
                val bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream), 8192)
                writer = bufferedWriter

                bufferedWriter.write(CSV_HEADER)
                bufferedWriter.newLine()

                val startTime = System.currentTimeMillis()
                samplesWritten = 0
                _recordingState.value = RecordingState.Recording(uri, startTime, samplesWritten)

                packetFlow.collect { packet ->
                    bufferedWriter.write(packetToCsv(packet))
                    bufferedWriter.newLine()

                    samplesWritten++
                    if (samplesWritten % 50 == 0) {
                        bufferedWriter.flush()
                        _recordingState.value = RecordingState.Recording(uri, startTime, samplesWritten)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                insertedUri?.let { context.contentResolver.delete(it, null, null) }
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
        publishOutput()

        if (currentState is RecordingState.Recording) {
            val duration = System.currentTimeMillis() - currentState.startTimeMs
            _recordingState.value = RecordingState.Completed(
                uri = currentState.uri,
                totalSamples = samplesWritten,
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
        } catch (_: Exception) {
        }
        writer = null
    }

    private fun publishOutput() {
        outputUri?.let { uri ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
        outputUri = null
    }

    private fun createOutputUri(): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "imu_motion_$timestamp.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/recordings"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Unable to create the recording file")
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
