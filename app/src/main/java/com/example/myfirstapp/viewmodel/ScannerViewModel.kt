package com.example.myfirstapp.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import com.example.myfirstapp.repository.BluetoothRepository

class ScannerViewModel(
    private val repository: BluetoothRepository
) : ViewModel() {

    val devices = repository.devices

    val connectionState = repository.connectionState

    val isScanning = repository.isScanning

    fun startScan() {
        repository.startScan()
    }

    fun connect(device: BluetoothDevice) {
        repository.connect(device)
    }
}