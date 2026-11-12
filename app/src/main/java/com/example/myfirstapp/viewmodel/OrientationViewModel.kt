package com.example.myfirstapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.model.Orientation
import com.example.myfirstapp.model.OrientationDataPoint
import com.example.myfirstapp.repository.BluetoothRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class OrientationViewModel(
    private val repository: BluetoothRepository
) : ViewModel() {

    val connectionState = repository.connectionState

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
}