package com.example.myfirstapp.model

import android.bluetooth.BluetoothDevice

data class BLEDevice(
    val name: String?,
    val address: String,
    val device: BluetoothDevice
)