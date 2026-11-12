package com.example.myfirstapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myfirstapp.repository.BluetoothRepository

class CalibrationViewModelFactory (
    private val repository: BluetoothRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(CalibrationViewModel::class.java)) {
            return CalibrationViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}