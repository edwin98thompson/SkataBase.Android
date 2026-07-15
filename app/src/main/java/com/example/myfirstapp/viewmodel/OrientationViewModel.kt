package com.example.myfirstapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.model.Orientation
import com.example.myfirstapp.repository.BluetoothRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrientationViewModel(
    repository: BluetoothRepository
) : ViewModel() {

    val connectionState = repository.connectionState

    private val _orientation =
        MutableStateFlow(
            Orientation(
                roll = 0,
                pitch = 0,
                yaw = 0
            )
        )

    val orientation = _orientation.asStateFlow()


    init {
        viewModelScope.launch {

            repository.orientationFlow.collect { value ->

                _orientation.value = value

            }
        }
    }
}