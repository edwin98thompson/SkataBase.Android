package com.example.myfirstapp.repository

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.MutableState
import com.example.myfirstapp.ble.BLEManager
import com.example.myfirstapp.model.BLEDevice
import com.example.myfirstapp.model.ConnectionState
import com.example.myfirstapp.model.IMUPacket
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

    fun sendMessage(command: String): Unit =
        bluetoothManager.sendMessage(command)

    fun disconnect() =
        bluetoothManager.disconnect()

    val imuPacketFlow: Flow<IMUPacket> = bluetoothManager.incomingMessages

    val orientationFlow: Flow<Orientation> =
        bluetoothManager.incomingMessages
            .filterNotNull()
            .map(::parseOrientation)

    private fun parseOrientation(message: IMUPacket): Orientation {
        return Orientation(
            roll = message.roll,
            pitch = message.pitch,
            yaw = normalizeYaw(message.yaw),
            q0 = message.q0,
            q1 = message.q1,
            q2 = message.q2,
            q3 = message.q3
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