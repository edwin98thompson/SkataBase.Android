package com.example.myfirstapp.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.training.TrickLabelCatalog
import com.example.myfirstapp.training.TrickOutcome
import com.example.myfirstapp.viewmodel.EventReviewViewModel
import com.example.myfirstapp.viewmodel.PlaybackFrame

@Composable
fun EventReviewScreen(
    onBack: () -> Unit,
    viewModel: EventReviewViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var eventMenuExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var pendingEventIndex by remember { mutableIntStateOf(-1) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { folderUri ->
        folderUri?.let {
            viewModel.loadSession(
                treeUri = it,
                grantedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.pausePlayback() }
    }

    fun requestEvent(index: Int) {
        if (index !in state.events.indices) return
        if (state.hasDraftChanges) {
            pendingEventIndex = index
            showDiscardDialog = true
        } else {
            viewModel.selectEvent(index)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹  Training") }
            Text("Review events", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(1.dp))
        }

        if (state.sessionUri == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Choose a training session", style = MaterialTheme.typography.titleMedium)
                    Text("Select the timestamped session folder in Downloads/recordings.")
                    Button(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select session folder")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.sessionName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${state.events.count { it.annotation != null }} of ${state.events.size} events classified",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    enabled = !state.isSaving && !state.isLoading
                ) {
                    Text("Change")
                }
            }

            if (state.isLoading && state.currentEvent == null) {
                Text("Loading session…")
            } else if (state.events.isNotEmpty()) {
                Box {
                    OutlinedButton(
                        onClick = { eventMenuExpanded = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val current = state.currentEvent
                        Text(
                            if (state.isComplete) {
                                "Session reviewed"
                            } else {
                                "Event ${state.currentIndex + 1} of ${state.events.size}" +
                                        (current?.annotation?.classificationKey?.let { " · $it" } ?: "")
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = eventMenuExpanded,
                        onDismissRequest = { eventMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        state.events.forEachIndexed { index, event ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${index + 1}. ${event.fileName}" +
                                                (event.annotation?.let {
                                                    " · ${it.classificationKey} · ${it.outcome.title}"
                                                } ?: " · Unclassified")
                                    )
                                },
                                onClick = {
                                    eventMenuExpanded = false
                                    requestEvent(index)
                                }
                            )
                        }
                    }
                }

                if (state.isComplete) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Session review complete", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.events.count { it.annotation != null }} events classified; " +
                                        "${state.events.count { it.annotation == null }} skipped or remaining."
                            )
                            Button(
                                onClick = { viewModel.selectEvent(state.currentIndex) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Review last event")
                            }
                        }
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            PlaybackScene(
                                frame = state.currentFrame,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (state.isLoading) {
                                Text(
                                    "Loading event…",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }

                    val event = state.currentEvent
                    Text(
                        event?.let {
                            val duration = (it.durationMs ?: 0L) / 1000f
                            "${it.fileName} · %.2f seconds".format(duration)
                        } ?: "Loading event…",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = viewModel::togglePlayback,
                            enabled = event != null && !state.isLoading
                        ) {
                            Text(if (state.isPlaying) "Pause" else "Play")
                        }
                        Box {
                            OutlinedButton(onClick = { speedMenuExpanded = true }) {
                                Text("${state.playbackSpeed}×")
                            }
                            DropdownMenu(
                                expanded = speedMenuExpanded,
                                onDismissRequest = { speedMenuExpanded = false }
                            ) {
                                EventReviewViewModel.PLAYBACK_SPEEDS.forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}×") },
                                        onClick = {
                                            speedMenuExpanded = false
                                            viewModel.setPlaybackSpeed(speed)
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            if (state.frameCount == 0) "—" else
                                "Frame ${state.frameIndex + 1} / ${state.frameCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text("Trick type", style = MaterialTheme.typography.titleSmall)
                    if (TrickLabelCatalog.labels.isEmpty()) {
                        Text(
                            "Add your code-defined trick labels to TrickLabels.kt to enable classification.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TrickLabelCatalog.labels.forEach { label ->
                                FilterChip(
                                    selected = state.selectedClassification == label.key,
                                    onClick = { viewModel.selectClassification(label.key) },
                                    label = { Text(label.title) }
                                )
                            }
                        }
                    }

                    Text("Outcome", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrickOutcome.entries.forEach { outcome ->
                            FilterChip(
                                selected = state.selectedOutcome == outcome,
                                onClick = { viewModel.selectOutcome(outcome) },
                                label = { Text(outcome.title) }
                            )
                        }
                    }

                    if (state.errorMessage != null) {
                        Text(
                            state.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { requestEvent(state.currentIndex - 1) },
                            enabled = state.currentIndex > 0 && !state.isSaving && !state.isLoading
                        ) {
                            Text("Previous")
                        }
                        TextButton(
                            onClick = viewModel::skipToNextEvent,
                            enabled = !state.isSaving && !state.isLoading
                        ) {
                            Text("Skip")
                        }
                        Button(
                            onClick = viewModel::saveAndNext,
                            enabled = state.selectedClassification != null &&
                                    state.selectedOutcome != null &&
                                    !state.isSaving &&
                                    !state.isLoading &&
                                    TrickLabelCatalog.labels.isNotEmpty()
                        ) {
                            Text(if (state.isSaving) "Saving…" else "Save & next")
                        }
                    }
                }
            }

            if (state.errorMessage != null && state.currentEvent == null) {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { folderPicker.launch(null) }) {
                    Text("Choose another folder")
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard unsaved choices?") },
            text = { Text("Your current trick type and outcome have not been saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.selectEvent(pendingEventIndex)
                    }
                ) {
                    Text("Discard and open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
    }
}

@Composable
private fun PlaybackScene(
    frame: PlaybackFrame?,
    modifier: Modifier = Modifier
) {
    val engine = io.github.sceneview.rememberEngine()
    val modelLoader = io.github.sceneview.rememberModelLoader(engine)
    var modelNode by remember {
        androidx.compose.runtime.mutableStateOf<io.github.sceneview.node.ModelNode?>(null)
    }

    io.github.sceneview.Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        childNodes = listOfNotNull(modelNode),
        cameraManipulator = null,
        onViewCreated = {
            modelNode = io.github.sceneview.node.ModelNode(
                modelInstance = modelLoader.createModelInstance(
                    assetFileLocation = "models/skateboard.glb"
                )
            ).apply {
                scale = io.github.sceneview.math.Scale(0.2f)
                position = io.github.sceneview.math.Position(0f, 0f, -2f)
            }
        }
    )

    androidx.compose.runtime.LaunchedEffect(frame, modelNode) {
        if (frame != null) {
            modelNode?.quaternion = dev.romainguy.kotlin.math.Quaternion(
                frame.q1,
                frame.q2,
                frame.q3,
                frame.q0
            )
        }
    }
}
