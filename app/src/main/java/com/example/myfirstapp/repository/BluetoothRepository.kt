package com.example.myfirstapp.repository

import com.example.myfirstapp.BLEManager
import com.example.myfirstapp.model.Orientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

class BluetoothRepository(
    private val bluetoothManager: BLEManager
) {

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