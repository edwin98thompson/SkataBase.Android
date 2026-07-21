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
            roll = message[0] / 100f,
            pitch = message[1] / 100f,
            yaw = normalizeYaw(message[2] / 100f),
            q0 = message[3] / 10000f,
            q1 = message[4] / 10000f,
            q2 = message[5] / 10000f,
            q3 = message[6] / 10000f
        )
    }

    private fun normalizeYaw(yaw: Float): Float {
        return when {
            yaw > 180f -> yaw - 360f
            yaw < -180f -> yaw + 360f
            else -> yaw
        }
    }
}