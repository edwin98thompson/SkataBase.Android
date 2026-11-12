package com.example.myfirstapp.screens

import BLEDeviceItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.model.ConnectionState
import com.example.myfirstapp.viewmodel.ScannerViewModel

@Composable
fun BLEScannerScreen(
    viewModel: ScannerViewModel,
    onConnected: () -> Unit
) {

    val devices = viewModel.devices.collectAsState().value

    // Observe the BLE connection state
    val connectionState by viewModel.connectionState.collectAsState()

    val isScanning by viewModel.isScanning

    // Navigate when the connection state changes
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            onConnected()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Spacer(modifier = Modifier.height(50.dp))

        Button(
            onClick = {
                viewModel.startScan()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan for BLE Devices")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning) {
            CircularProgressIndicator()
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (connectionState) {

                ConnectionState.Disconnected ->
                    "Status: Disconnected"

                ConnectionState.Connecting ->
                    "Status: Connecting..."

                ConnectionState.Connected ->
                    "Status: Connected"

                is ConnectionState.Error ->
                    "Status: ${(connectionState as ConnectionState.Error).message}"
            },
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {

            items(devices) { device ->

                BLEDeviceItem(device) {
                    viewModel.connect(device.device)
                }

                HorizontalDivider()
            }
        }
    }
}