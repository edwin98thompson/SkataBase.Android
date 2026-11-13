package com.example.myfirstapp.training

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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MotionTrainingRecorder(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<TrainingSessionState>(TrainingSessionState.Idle)
    val state: StateFlow<TrainingSessionState> = _state.asStateFlow()

    private var packetFlow: Flow<IMUPacket>? = null
    private var collectionJob: Job? = null
    private var config = TrainingConfig()
    private var sessionDirectory: String? = null
    private var eventNumber = 0
    private var eventsSaved = 0
    private var activeEventUri: Uri? = null
    private var writer: BufferedWriter? = null
    private var activeEventSamples = 0
    private var samplesUntilEventEnd: Int? = null
    private val rollingBuffer = ArrayDeque<IMUPacket>()

    companion object {
        private const val CSV_HEADER =
            "timestamp_ms,sequence,roll,pitch,yaw,accel_x,accel_y,accel_z," +
                    "gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,q0,q1,q2,q3"
    }

    suspend fun start(flow: Flow<IMUPacket>, trainingConfig: TrainingConfig) {
        require(trainingConfig.startThreshold.isFinite() && trainingConfig.endThreshold.isFinite()) {
            "Thresholds must be finite numbers"
        }
        require(trainingConfig.startThreshold > trainingConfig.endThreshold) {
            "Start threshold must be greater than end threshold"
        }
        require(trainingConfig.endThreshold >= 0f)
        require(trainingConfig.preEventSamples in 0..5_000)
        require(trainingConfig.postEventSamples in 0..5_000)

        collectionJob?.cancelAndJoin()
        config = trainingConfig
        packetFlow = flow
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        sessionDirectory =
            Environment.DIRECTORY_DOWNLOADS + "/recordings/training_$timestamp/"
        writeSessionMetadata(timestamp, trainingConfig)
        eventNumber = 0
        eventsSaved = 0
        rollingBuffer.clear()
        startCollecting()
    }

    suspend fun pause() {
        collectionJob?.cancelAndJoin()
        collectionJob = null
        closeActiveEvent()
        rollingBuffer.clear()
        sessionDirectory?.let { _state.value = TrainingSessionState.Paused(it, eventsSaved) }
    }

    suspend fun resume() {
        if (_state.value !is TrainingSessionState.Paused) return
        startCollecting()
    }

    suspend fun stop() {
        collectionJob?.cancelAndJoin()
        collectionJob = null
        closeActiveEvent()
        rollingBuffer.clear()
        val directory = sessionDirectory
        if (directory != null) {
            _state.value = TrainingSessionState.Completed(directory, eventsSaved)
        } else {
            _state.value = TrainingSessionState.Idle
        }
    }

    private fun startCollecting() {
        val flow = packetFlow ?: return
        val directory = sessionDirectory ?: return
        _state.value = TrainingSessionState.Running(directory, eventsSaved, null, 0)
        collectionJob = scope.launch(Dispatchers.IO) {
            try {
                flow.collect(::processPacket)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                runCatching { closeActiveEvent() }
                _state.value = TrainingSessionState.Error(
                    failure.message ?: "Training data collection failed"
                )
            }
        }
    }

    private fun processPacket(packet: IMUPacket) {
        val magnitude = sqrt(
            packet.gyroX * packet.gyroX +
                    packet.gyroY * packet.gyroY +
                    packet.gyroZ * packet.gyroZ
        )

        if (writer == null) {
            if (magnitude >= config.startThreshold) {
                beginEvent(packet)
            } else {
                rollingBuffer.addLast(packet)
                while (rollingBuffer.size > config.preEventSamples) {
                    rollingBuffer.removeFirst()
                }
            }
            return
        }

        writePacket(packet)
        val remaining = samplesUntilEventEnd
        if (remaining == null) {
            if (magnitude <= config.endThreshold) {
                samplesUntilEventEnd = config.postEventSamples
                if (config.postEventSamples == 0) closeActiveEvent()
            }
        } else if (magnitude > config.endThreshold) {
            samplesUntilEventEnd = null
        } else {
            val next = remaining - 1
            samplesUntilEventEnd = next
            if (next <= 0) closeActiveEvent()
        }
    }

    private fun beginEvent(triggerPacket: IMUPacket) {
        eventNumber++
        val uri = createEventUri(eventNumber)
        activeEventUri = uri
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: run {
                context.contentResolver.delete(uri, null, null)
                activeEventUri = null
                error("Unable to open event $eventNumber")
            }
        writer = BufferedWriter(OutputStreamWriter(output), 8192)
        writer?.write(CSV_HEADER)
        writer?.newLine()

        rollingBuffer.forEach(::writePacket)
        rollingBuffer.clear()
        writePacket(triggerPacket)
        samplesUntilEventEnd = null
        publishRunningState()
    }

    private fun writePacket(packet: IMUPacket) {
        val currentWriter = writer ?: return
        currentWriter.write(packetToCsv(packet))
        currentWriter.newLine()
        activeEventSamples++
        if (activeEventSamples % 50 == 0) {
            currentWriter.flush()
            publishRunningState()
        }
    }

    private fun closeActiveEvent() {
        val currentUri = activeEventUri ?: return
        try {
            writer?.flush()
            writer?.close()
        } catch (failure: Exception) {
            context.contentResolver.delete(currentUri, null, null)
            throw failure
        } finally {
            writer = null
            activeEventUri = null
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(currentUri, values, null, null)
        eventsSaved++
        activeEventSamples = 0
        samplesUntilEventEnd = null
        publishRunningState()
    }

    private fun publishRunningState() {
        val directory = sessionDirectory ?: return
        _state.value = TrainingSessionState.Running(
            sessionDirectory = directory,
            eventsSaved = eventsSaved,
            activeEvent = if (writer == null) null else eventNumber,
            activeEventSamples = activeEventSamples
        )
    }

    private fun writeSessionMetadata(timestamp: String, trainingConfig: TrainingConfig) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "session_info.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, sessionDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Unable to create training session folder")

        try {
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: error("Unable to write training session metadata")
            output.bufferedWriter().use { metadataWriter ->
                metadataWriter.write("session_started,start_threshold,end_threshold,pre_event_samples,post_event_samples")
                metadataWriter.newLine()
                metadataWriter.write(
                    "$timestamp,${trainingConfig.startThreshold},${trainingConfig.endThreshold}," +
                            "${trainingConfig.preEventSamples},${trainingConfig.postEventSamples}"
                )
                metadataWriter.newLine()
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        } catch (failure: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun createEventUri(number: Int): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "event_%04d.csv".format(number))
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, sessionDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Unable to create training event file")
    }

    private fun packetToCsv(packet: IMUPacket): String =
        "${packet.timestampMs},${packet.sequence}," +
                "${packet.roll},${packet.pitch},${packet.yaw}," +
                "${packet.accelX},${packet.accelY},${packet.accelZ}," +
                "${packet.gyroX},${packet.gyroY},${packet.gyroZ}," +
                "${packet.magX},${packet.magY},${packet.magZ}," +
                "${packet.q0},${packet.q1},${packet.q2},${packet.q3}"
}
