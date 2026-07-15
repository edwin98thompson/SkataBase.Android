package com.example.myfirstapp.screens

import BLEDeviceItem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.model.BLEDevice
import com.example.myfirstapp.BLEManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BLEScannerScreen(bleManager: BLEManager, onConnected: () -> Unit) {
    var devices by remember { mutableStateOf<List<BLEDevice>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        bleManager.setListener(object : BLEManager.BLEListener {
            override fun onDeviceFound(device: BLEDevice) {
                devices = devices.toMutableList().apply { add(device) }
            }

            override fun onConnected() {
                connectionStatus = "Connected"
                CoroutineScope(Dispatchers.Main).launch {
                    onConnected()
                }
            }

            override fun onDisconnected() {
                connectionStatus = "Disconnected"
            }

            override fun onError(message: String) {
                errorMessage = message
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(50.dp))
        Button(
            onClick = {
                devices = emptyList()
                bleManager.startScan()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan for BLE Devices")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (bleManager.isScanning.value) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
        }

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        connectionStatus?.let {
            Text(text = "Status: $it", color = MaterialTheme.colorScheme.primary)
        }

        LazyColumn {
            items(devices) { device ->
                BLEDeviceItem(device = device) {
                    bleManager.connect(device.device)
                }
                HorizontalDivider()
            }
        }
    }
}
