package com.example.myfirstapp

import android.content.Context
import com.example.myfirstapp.ble.BLEManager
import com.example.myfirstapp.repository.BluetoothRepository

object AppGraph {
    private lateinit var manager: BLEManager
    private lateinit var bluetoothRepository: BluetoothRepository

    val bleManager: BLEManager
        get() = manager

    val repository: BluetoothRepository
        get() = bluetoothRepository

    @Synchronized
    fun initialize(context: Context) {
        if (!::manager.isInitialized) {
            manager = BLEManager(context.applicationContext)
            bluetoothRepository = BluetoothRepository(manager)
        }
    }
}
