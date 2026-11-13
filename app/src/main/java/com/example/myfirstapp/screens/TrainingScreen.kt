package com.example.myfirstapp.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myfirstapp.training.TrainingService
import com.example.myfirstapp.training.TrainingSessionState

@Composable
fun TrainingScreen(onOpenReview: () -> Unit) {
    val context = LocalContext.current
    val state by TrainingService.sessionState.collectAsState()
    var startThresholdText by rememberSaveable { mutableStateOf("100") }
    var endThresholdText by rememberSaveable { mutableStateOf("50") }
    var preSamplesText by rememberSaveable { mutableStateOf("25") }
    var postSamplesText by rememberSaveable { mutableStateOf("25") }

    val startThreshold = startThresholdText.toFloatOrNull()
    val endThreshold = endThresholdText.toFloatOrNull()
    val preSamples = preSamplesText.toIntOrNull()
    val postSamples = postSamplesText.toIntOrNull()
    val configIsValid = startThreshold != null && endThreshold != null &&
            startThreshold.isFinite() && endThreshold.isFinite() &&
            startThreshold > endThreshold && endThreshold >= 0f &&
            preSamples != null && preSamples in 0..5_000 &&
            postSamples != null && postSamples in 0..5_000
    val hasBluetoothPermissions =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

    val startSession = remember(
        configIsValid,
        startThreshold,
        endThreshold,
        preSamples,
        postSamples
    ) {
        {
            if (configIsValid) {
                val intent = Intent(context, TrainingService::class.java)
                    .setAction(TrainingService.ACTION_START)
                    .putExtra(TrainingService.EXTRA_START_THRESHOLD, startThreshold!!)
                    .putExtra(TrainingService.EXTRA_END_THRESHOLD, endThreshold!!)
                    .putExtra(TrainingService.EXTRA_PRE_SAMPLES, preSamples!!)
                    .putExtra(TrainingService.EXTRA_POST_SAMPLES, postSamples!!)
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startSession() }

    fun beginTraining() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startSession()
        }
    }

    fun sendAction(action: String) {
        context.startService(
            Intent(context, TrainingService::class.java).setAction(action)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Data training", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = onOpenReview, modifier = Modifier.fillMaxWidth()) {
            Text("Review recorded sessions")
        }
        Text(
            "The gyro magnitude is √(x² + y² + z²) in the units sent by the device. " +
                    "An event starts at the upper threshold and ends below the lower threshold."
        )

        OutlinedTextField(
            value = startThresholdText,
            onValueChange = { startThresholdText = it },
            label = { Text("Event start threshold") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is TrainingSessionState.Running &&
                    state !is TrainingSessionState.Paused
        )
        OutlinedTextField(
            value = endThresholdText,
            onValueChange = { endThresholdText = it },
            label = { Text("Event end threshold") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is TrainingSessionState.Running &&
                    state !is TrainingSessionState.Paused
        )
        OutlinedTextField(
            value = preSamplesText,
            onValueChange = { preSamplesText = it },
            label = { Text("Pre-event samples") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is TrainingSessionState.Running &&
                    state !is TrainingSessionState.Paused
        )
        OutlinedTextField(
            value = postSamplesText,
            onValueChange = { postSamplesText = it },
            label = { Text("Post-event samples") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is TrainingSessionState.Running &&
                    state !is TrainingSessionState.Paused
        )

        if (!configIsValid) {
            Text(
                "Enter valid values. The start threshold must be greater than the end threshold.",
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!hasBluetoothPermissions) {
            Text(
                "Bluetooth scan and connect permissions are required to collect training data.",
                color = MaterialTheme.colorScheme.error
            )
        }

        when (val current = state) {
            TrainingSessionState.Idle,
            is TrainingSessionState.Completed,
            is TrainingSessionState.Error -> {
                Button(
                    onClick = ::beginTraining,
                    enabled = configIsValid && hasBluetoothPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Training")
                }
            }

            is TrainingSessionState.Running -> {
                Text(
                    if (current.activeEvent == null) {
                        "Training active · ${current.eventsSaved} events saved · waiting for movement"
                    } else {
                        "Recording event ${current.activeEvent} · ${current.activeEventSamples} samples"
                    }
                )
                Text("Session folder: ${current.sessionDirectory}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sendAction(TrainingService.ACTION_PAUSE) }) {
                        Text("Pause")
                    }
                    Button(onClick = { sendAction(TrainingService.ACTION_STOP) }) {
                        Text("Stop")
                    }
                }
            }

            is TrainingSessionState.Paused -> {
                Text("Training paused · ${current.eventsSaved} events saved")
                Text("Session folder: ${current.sessionDirectory}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sendAction(TrainingService.ACTION_RESUME) }) {
                        Text("Resume")
                    }
                    Button(onClick = { sendAction(TrainingService.ACTION_STOP) }) {
                        Text("Stop")
                    }
                }
            }
        }

        when (val current = state) {
            is TrainingSessionState.Completed ->
                Text("Training complete · ${current.eventsSaved} events saved in ${current.sessionDirectory}")
            is TrainingSessionState.Error ->
                Text("Training error: ${current.message}", color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }
}
