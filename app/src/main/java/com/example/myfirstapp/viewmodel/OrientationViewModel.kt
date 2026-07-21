package com.example.myfirstapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.model.Orientation
import com.example.myfirstapp.model.OrientationDataPoint
import com.example.myfirstapp.repository.BluetoothRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrientationViewModel(
    repository: BluetoothRepository
) : ViewModel() {

    val connectionState = repository.connectionState

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