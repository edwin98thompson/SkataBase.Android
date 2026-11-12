package com.example.myfirstapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.model.CommandId
import com.example.myfirstapp.model.CommandPacket
import com.example.myfirstapp.model.Orientation
import com.example.myfirstapp.model.OrientationDataPoint
import com.example.myfirstapp.repository.BluetoothRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class OrientationViewModel(
    private val repository: BluetoothRepository
) : ViewModel() {

    val connectionState = repository.connectionState

    // 1. Calibration status state stream
    val magCalibrationStatus: StateFlow<String> = repository.imuPacketFlow
        .map { packet -> formatCalibrationStatus(packet.calibrationStatus) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "Ready"
        )

    // 2. Beta value state stream
    val betaValue: StateFlow<Float> = repository.imuPacketFlow
        .map { packet -> packet.betaValue }
//        .onEach { value -> Log.d("OrientationViewModel", "Received beta value from BLE flow: $value") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0f
        )

    // 3. History buffer and state flow
    private val historyBuffer = ArrayDeque<OrientationDataPoint>()
    private var time = 0f

    private val _history = MutableStateFlow<List<OrientationDataPoint>>(emptyList())
    val history: StateFlow<List<OrientationDataPoint>> = _history.asStateFlow()

    // 4. Conflated orientation stream with history side effect
    val orientation: StateFlow<Orientation> = repository.orientationFlow
        .conflate()
        .onEach { updateHistory(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Orientation(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    fun updateBetaValue(betaValue: Float) {
        Log.d("OrientationViewModel", "Sending beta value to BLE: $betaValue")

        val message = CommandPacket(
            protocolVersion = 1,
            commandId = CommandId.CMD_SET_BETA_VALUE,
            betaValue = betaValue
        )

        repository.sendMessage(message)
    }

    fun startGyroCalibration() {
        Log.d("OrientationViewModel", "Triggering gyro calibration")

        val message = CommandPacket(
            protocolVersion = 1,
            commandId = CommandId.CMD_START_GYROSCOPE_CALIBRATION,
            betaValue = 0f
        )

        repository.sendMessage(message)
    }

    fun toggleGyroscopeCalibration(apply: Boolean) {
        Log.d("OrientationViewModel", "Toggling gyro calibration: $apply")

        val command = if (apply) {
            CommandId.CMD_ENABLE_GYROSCOPE_CALIBRATION
        } else {
            CommandId.CMD_DISABLE_GYROSCOPE_CALIBRATION
        }

        val message = CommandPacket(
            protocolVersion = 1,
            commandId = command,
            betaValue = 0f
        )

        repository.sendMessage(message)
    }

    fun startAccelCalibration() {
        Log.d("OrientationViewModel", "Triggering accel calibration")

        val message = CommandPacket(
            protocolVersion = 1,
            commandId = CommandId.CMD_START_ACCELEROMETER_CALIBRATION,
            betaValue = 0f
        )

        repository.sendMessage(message)
    }

    fun toggleAccelCalibration(apply: Boolean) {
        Log.d("OrientationViewModel", "Toggling accel calibration: $apply")

        val command = if (apply) {
            CommandId.CMD_ENABLE_ACCELEROMETER_CALIBRATION
        } else {
            CommandId.CMD_DISABLE_ACCELEROMETER_CALIBRATION
        }

        val message = CommandPacket(
            protocolVersion = 1,
            commandId = command,
            betaValue = 0f
        )

        repository.sendMessage(message)
    }

    fun startMagnetometerCalibration() {
        Log.d("OrientationScreen", "Starting magnetic calibration")

        val message = CommandPacket(
            protocolVersion = 1,
            commandId = CommandId.CMD_START_MAGNETOMETER_CALIBRATION,
            betaValue = 0f
        )

        repository.sendMessage(message)
    }

    private fun updateHistory(orientation: Orientation) {
        time += 0.02f
        historyBuffer.add(
            OrientationDataPoint(
                time = time,
                roll = orientation.roll,
                pitch = orientation.pitch,
                yaw = orientation.yaw,
                q0 = orientation.q0,
                q1 = orientation.q1,
                q2 = orientation.q2,
                q3 = orientation.q3
            )
        )

        if (historyBuffer.size > 500) {
            historyBuffer.removeFirst()
        }

        _history.value = historyBuffer.toList()
    }

    private fun formatCalibrationStatus(status: Int): String {
        return when (status) {
            0 -> "Uncalibrated"
            1 -> "Calibration In Progress"
            2 -> "Calibration Complete"
            3 -> "Calibration Failed"
            else -> "Status: $status"
        }
    }
}