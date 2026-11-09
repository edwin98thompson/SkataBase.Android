package com.example.myfirstapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.model.Orientation
import com.example.myfirstapp.model.OrientationDataPoint
import com.example.myfirstapp.repository.BluetoothRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections.emptyList

class OrientationViewModel(
    private val repository: BluetoothRepository
) : ViewModel() {

    val connectionState = repository.connectionState

    private val _magCalibrationStatus = MutableStateFlow("Ready (0)")
    val magCalibrationStatus = _magCalibrationStatus.asStateFlow()

    init {

        // Collect IMU packets for calibration status
        viewModelScope.launch {
            repository.imuPacketFlow.collect { packet ->
                _magCalibrationStatus.value = formatCalibrationStatus(packet.calibrationStatus)
            }
        }
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

    private val _orientation =
        MutableStateFlow(
            Orientation(
                roll = 0f,
                pitch = 0f,
                yaw = 0f,
                q0 = 0f,
                q1 = 0f,
                q2 = 0f,
                q3 = 0f
            )
        )

    val orientation = _orientation.asStateFlow()

    fun startMagnetometerCalibration() {
        Log.d("OrientationScreen","Starting magnetic calibration")

        repository.sendMessage("CALIBRATE_MAG")
    }

    private val historyBuffer = ArrayDeque<OrientationDataPoint>()

    private val _history =
        MutableStateFlow<List<OrientationDataPoint>>(emptyList())

    val history = _history.asStateFlow()


    private var time = 0f


    init {
        viewModelScope.launch {

            repository.orientationFlow.collect { value ->

                _orientation.value = value
                updateHistory(value)
            }
        }
    }

    private fun updateHistory(orientation: Orientation)
    {
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

        // Keep last 5 seconds
        if (historyBuffer.size > 500) {
            historyBuffer.removeFirst()
        }

        _history.value = historyBuffer.toList()
    }
}