package com.example.myfirstapp.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.training.EventAnnotation
import com.example.myfirstapp.training.EventAnnotationStore
import com.example.myfirstapp.training.TrickLabelCatalog
import com.example.myfirstapp.training.TrickOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.max

data class ReviewEvent(
    val id: String,
    val fileName: String,
    val uri: Uri,
    val annotation: EventAnnotation? = null,
    val startTimestampMs: Long? = null,
    val durationMs: Long? = null
)

data class PlaybackFrame(
    val q0: Float,
    val q1: Float,
    val q2: Float,
    val q3: Float
)

data class EventReviewUiState(
    val sessionUri: Uri? = null,
    val sessionName: String? = null,
    val events: List<ReviewEvent> = emptyList(),
    val currentIndex: Int = 0,
    val currentEvent: ReviewEvent? = null,
    val currentFrame: PlaybackFrame? = null,
    val frameIndex: Int = 0,
    val frameCount: Int = 0,
    val selectedClassification: String? = null,
    val selectedOutcome: TrickOutcome? = null,
    val playbackSpeed: Float = 1f,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
) {
    val hasDraftChanges: Boolean
        get() = currentEvent?.let {
            selectedClassification != it.annotation?.classificationKey ||
                    selectedOutcome != it.annotation?.outcome
        } ?: false
}

class EventReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val annotationStore = EventAnnotationStore(application)
    private val _uiState = MutableStateFlow(EventReviewUiState())
    val uiState: StateFlow<EventReviewUiState> = _uiState.asStateFlow()

    private var currentSamples: List<TimedSample> = emptyList()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var playbackJob: Job? = null

    private data class TimedSample(
        val timestampMs: Long,
        val frame: PlaybackFrame
    )

    fun loadSession(treeUri: Uri, grantedFlags: Int) {
        if (_uiState.value.isSaving) return
        loadJob?.cancel()
        stopPlayback()
        loadJob = viewModelScope.launch {
            _uiState.value = EventReviewUiState(isLoading = true)
            try {
                val persistableFlags = grantedFlags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (persistableFlags != 0) {
                    runCatching {
                        resolver.takePersistableUriPermission(treeUri, persistableFlags)
                    }
                }

                val session = withContext(Dispatchers.IO) {
                    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                    val sessionName = rootId.substringAfterLast('/').substringAfterLast(':')
                    val annotations = annotationStore.annotationsForSession(treeUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri,
                        rootId
                    )
                    val events = mutableListOf<ReviewEvent>()
                    resolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID
                        )
                        val nameColumn = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        )
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(nameColumn) ?: continue
                            if (!EVENT_FILE_PATTERN.matches(name)) continue
                            val documentId = cursor.getString(idColumn) ?: continue
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                documentId
                            )
                            events += ReviewEvent(
                                id = "${treeUri}#${name}",
                                fileName = name,
                                uri = documentUri,
                                annotation = annotations[name]
                            )
                        }
                    } ?: error("Could not read the selected session folder")

                    SessionFiles(
                        sessionName = sessionName.ifBlank { "Training session" },
                        events = events.sortedBy { it.fileName }
                    )
                }

                if (session.events.isEmpty()) {
                    error("No event CSV files were found in this folder.")
                }

                _uiState.value = EventReviewUiState(
                    sessionUri = treeUri,
                    sessionName = session.sessionName,
                    events = session.events
                )
                loadEventContents(0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = EventReviewUiState(
                    isLoading = false,
                    errorMessage = failure.message ?: "Could not open this session folder."
                )
            }
        }
    }

    fun selectEvent(index: Int) {
        val state = _uiState.value
        if (state.isSaving || index !in state.events.indices) return
        if (index == state.currentIndex && !state.isComplete) return
        loadEvent(index)
    }

    private fun loadEvent(index: Int) {
        loadJob?.cancel()
        stopPlayback()
        loadJob = viewModelScope.launch {
            loadEventContents(index)
        }
    }

    private suspend fun loadEventContents(index: Int) {
        val before = _uiState.value
        val event = before.events.getOrNull(index) ?: return
        _uiState.value = before.copy(
            currentIndex = index,
            currentEvent = event,
            currentFrame = null,
            frameIndex = 0,
            frameCount = 0,
            selectedClassification = event.annotation?.classificationKey,
            selectedOutcome = event.annotation?.outcome,
            isPlaying = false,
            isLoading = true,
            isComplete = false,
            errorMessage = null
        )

        try {
            val samples = withContext(Dispatchers.IO) { readEvent(event.uri) }
            if (samples.isEmpty()) error("This event contains no readable IMU samples.")

            currentSamples = samples
            val startTime = samples.first().timestampMs
            val duration = sampleDuration(samples)
            val updatedEvent = event.copy(
                startTimestampMs = startTime,
                durationMs = duration
            )
            val events = _uiState.value.events.toMutableList().apply {
                this[index] = updatedEvent
            }
            _uiState.value = _uiState.value.copy(
                events = events,
                currentEvent = updatedEvent,
                frameCount = samples.size,
                isLoading = false
            )
            startPlayback()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isPlaying = false,
                errorMessage = failure.message ?: "Could not read this event."
            )
        }
    }

    fun selectClassification(key: String) {
        if (TrickLabelCatalog.labels.none { it.key == key }) return
        _uiState.value = _uiState.value.copy(selectedClassification = key)
    }

    fun selectOutcome(outcome: TrickOutcome) {
        _uiState.value = _uiState.value.copy(selectedOutcome = outcome)
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) stopPlayback() else startPlayback()
    }

    fun setPlaybackSpeed(speed: Float) {
        if (speed !in PLAYBACK_SPEEDS) return
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun previousEvent() {
        if (_uiState.value.isSaving) return
        val previousIndex = if (_uiState.value.isComplete) {
            _uiState.value.events.lastIndex
        } else {
            _uiState.value.currentIndex - 1
        }
        if (previousIndex >= 0) loadEvent(previousIndex)
    }

    fun skipToNextEvent() {
        if (_uiState.value.isSaving) return
        val nextIndex = _uiState.value.currentIndex + 1
        if (nextIndex < _uiState.value.events.size) {
            loadEvent(nextIndex)
        } else {
            stopPlayback()
            _uiState.value = _uiState.value.copy(
                isComplete = true,
                currentFrame = null
            )
        }
    }

    fun saveAndNext() {
        val state = _uiState.value
        val event = state.currentEvent ?: return
        val classification = state.selectedClassification ?: return
        val outcome = state.selectedOutcome ?: return
        if (state.isSaving || state.isLoading) return
        if (TrickLabelCatalog.labels.none { it.key == classification }) {
            _uiState.value = state.copy(errorMessage = "Select an available trick type before saving.")
            return
        }
        val sessionUri = state.sessionUri ?: return
        val sessionName = state.sessionName ?: "session"
        val samples = currentSamples.toList()

        saveJob?.cancel()
        stopPlayback()
        saveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val annotation = withContext(Dispatchers.IO) {
                    saveAnnotation(
                        event = event,
                        classificationKey = classification,
                        outcome = outcome,
                        sessionUri = sessionUri,
                        sessionName = sessionName,
                        samples = samples
                    )
                }
                val latest = _uiState.value
                val events = latest.events.toMutableList().apply {
                    this[latest.currentIndex] =
                        this[latest.currentIndex].copy(annotation = annotation)
                }
                _uiState.value = latest.copy(events = events, isSaving = false)
                skipToNextEvent()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = failure.message ?: "Could not save this annotation."
                )
                if (currentSamples.isNotEmpty()) startPlayback()
            }
        }
    }

    private fun saveAnnotation(
        event: ReviewEvent,
        classificationKey: String,
        outcome: TrickOutcome,
        sessionUri: Uri,
        sessionName: String,
        samples: List<TimedSample>
    ): EventAnnotation {
        val oldAnnotation = annotationStore.annotationForEvent(event.id)
        val existingCopyUri = oldAnnotation?.classifiedCopyUri?.let(Uri::parse)
        val canReuseCopy = oldAnnotation?.classificationKey == classificationKey &&
                existingCopyUri != null && canOpen(existingCopyUri)

        val copyUri = if (canReuseCopy) {
            existingCopyUri!!
        } else {
            copyIntoClassificationFolder(
                sourceUri = event.uri,
                sessionName = sessionName,
                eventFileName = event.fileName,
                classificationKey = classificationKey
            )
        }

        val annotation = EventAnnotation(
            eventId = event.id,
            sessionUri = sessionUri.toString(),
            sourceFileName = event.fileName,
            sourceUri = event.uri.toString(),
            startTimestampMs = event.startTimestampMs ?: samples.firstOrNull()?.timestampMs ?: 0L,
            durationMs = event.durationMs ?: sampleDuration(samples),
            classificationKey = classificationKey,
            outcome = outcome,
            classifiedCopyUri = copyUri.toString(),
            updatedAtMs = System.currentTimeMillis()
        )

        if (!annotationStore.save(annotation)) {
            if (!canReuseCopy) resolver.delete(copyUri, null, null)
            error("The annotation metadata could not be saved.")
        }

        if (oldAnnotation != null &&
            oldAnnotation.classifiedCopyUri != copyUri.toString()
        ) {
            resolver.delete(Uri.parse(oldAnnotation.classifiedCopyUri), null, null)
        }
        return annotation
    }

    private fun copyIntoClassificationFolder(
        sourceUri: Uri,
        sessionName: String,
        eventFileName: String,
        classificationKey: String
    ): Uri {
        val folderKey = classificationKey
            .lowercase(Locale.ROOT)
            .replace(UNSAFE_FOLDER_CHARS, "_")
            .trim('_')
            .ifBlank { error("The classification label has an invalid folder key.") }
        val safeSessionName = sessionName
            .replace(UNSAFE_FILE_CHARS, "_")
            .trim('_')
            .ifBlank { "session" }
        val safeEventName = eventFileName
            .removeSuffix(".csv")
            .replace(UNSAFE_FILE_CHARS, "_")
            .trim('_')

        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "${safeSessionName}_${safeEventName}_${System.currentTimeMillis()}.csv"
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/recordings/classified/$folderKey/"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create a classified event file.")

        try {
            val input = resolver.openInputStream(sourceUri)
                ?: error("Could not read the original event CSV.")
            val output = resolver.openOutputStream(destination, "w")
                ?: error("Could not open the classified event copy.")
            input.use { source ->
                output.use { target -> source.copyTo(target) }
            }
            val published = resolver.update(
                destination,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            if (published == 0) error("The classified event copy could not be published.")
            return destination
        } catch (failure: Exception) {
            resolver.delete(destination, null, null)
            throw failure
        }
    }

    private fun canOpen(uri: Uri): Boolean =
        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { }
                ?: return false
            true
        }.getOrDefault(false)

    private fun readEvent(uri: Uri): List<TimedSample> {
        val input = resolver.openInputStream(uri)
            ?: error("Could not open the event CSV.")
        val samples = mutableListOf<TimedSample>()
        input.bufferedReader().use { reader: BufferedReader ->
            reader.readLine() // CSV header
            while (true) {
                val line = reader.readLine() ?: break
                val values = line.split(',')
                if (values.size < 18) continue

                val timestamp = values[0].toLongOrNull() ?: continue
                val quaternion = values.subList(14, 18).map { it.toFloatOrNull() }
                if (quaternion.any { it == null || !it.isFinite() }) continue
                samples += TimedSample(
                    timestampMs = timestamp,
                    frame = PlaybackFrame(
                        q0 = quaternion[0]!!,
                        q1 = quaternion[1]!!,
                        q2 = quaternion[2]!!,
                        q3 = quaternion[3]!!
                    )
                )
            }
        }
        return samples
    }

    private fun sampleDuration(samples: List<TimedSample>): Long {
        if (samples.size < 2) return 0L
        val first = samples.first().timestampMs
        val last = samples.last().timestampMs
        return if (last >= first) {
            last - first
        } else {
            UINT32_RANGE - first + last
        }
    }

    private fun startPlayback() {
        if (currentSamples.isEmpty()) return
        playbackJob?.cancel()
        val startIndex = _uiState.value.frameIndex.coerceIn(0, currentSamples.lastIndex)
        _uiState.value = _uiState.value.copy(isPlaying = true)
        playbackJob = viewModelScope.launch {
            var index = startIndex
            while (true) {
                val sample = currentSamples[index]
                _uiState.value = _uiState.value.copy(
                    currentFrame = sample.frame,
                    frameIndex = index,
                    isPlaying = true
                )
                val nextIndex = (index + 1) % currentSamples.size
                val delta = timestampDelta(
                    sample.timestampMs,
                    currentSamples[nextIndex].timestampMs
                )
                val speed = _uiState.value.playbackSpeed
                delay((delta / speed).toLong().coerceIn(1L, MAX_FRAME_DELAY_MS))
                index = nextIndex
            }
        }
    }

    fun pausePlayback() {
        stopPlayback()
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        if (_uiState.value.isPlaying) {
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }
    }

    private fun timestampDelta(current: Long, next: Long): Long {
        val delta = if (next >= current) next - current else UINT32_RANGE - current + next
        return max(1L, delta)
    }

    override fun onCleared() {
        stopPlayback()
        annotationStore.close()
        super.onCleared()
    }

    private data class SessionFiles(
        val sessionName: String,
        val events: List<ReviewEvent>
    )

    companion object {
        val PLAYBACK_SPEEDS = listOf(0.5f, 1f, 2f)
        private val EVENT_FILE_PATTERN = Regex("event_\\d+\\.csv", RegexOption.IGNORE_CASE)
        private val UNSAFE_FOLDER_CHARS = Regex("[^a-z0-9_-]")
        private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9_-]")
        private const val UINT32_RANGE = 4_294_967_296L
        private const val MAX_FRAME_DELAY_MS = 5_000L
    }
}
