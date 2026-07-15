package com.example.myfirstapp.repository

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.MutableState
import com.example.myfirstapp.ble.BLEManager
import com.example.myfirstapp.model.BLEDevice
import com.example.myfirstapp.model.ConnectionState
import com.example.myfirstapp.model.Orientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class BluetoothRepository(
    private val bluetoothManager: BLEManager
) {
    val devices: StateFlow<List<BLEDevice>> =
        bluetoothManager.devices

    val connectionState: StateFlow<ConnectionState> =
        bluetoothManager.connectionState

    val isScanning: MutableState<Boolean> =
        bluetoothManager.isScanning

    fun startScan() =
        bluetoothManager.startScan()

    fun connect(device: BluetoothDevice) =
        bluetoothManager.connect(device)

    fun disconnect() =
        bluetoothManager.disconnect()

    val orientationFlow: Flow<Orientation> =
        bluetoothManager.incomingMessages
            .filterNotNull()
            .map(::parseOrientation)

    private fun parseOrientation(message: IntArray): Orientation {
        return Orientation(
            roll = message[0],
            pitch = message[1],
            yaw = message[2]
        )
    }
}