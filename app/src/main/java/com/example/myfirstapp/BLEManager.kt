package com.example.myfirstapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import com.example.myfirstapp.model.BLEDevice

class BLEManager(private val context: Context) {

    interface BLEListener {
        fun onDeviceFound(device: BLEDevice)
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    private var listener: BLEListener = object : BLEListener {
        override fun onDeviceFound(device: BLEDevice) {}
        override fun onConnected() {}
        override fun onDisconnected() {}
        override fun onError(message: String) {}
    }

    fun setListener(newListener: BLEListener) {
        listener = newListener
    }

    var isScanning = mutableStateOf(false)
        private set

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableMapOf<String, BLEDevice>()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            if (!scanResults.containsKey(address)) {
                val bleDevice = BLEDevice(device.name, address, device)
                scanResults[address] = bleDevice
                listener.onDeviceFound(bleDevice)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning.value = false
            listener.onError("Scan failed with code $errorCode")
        }
    }

    fun startScan() {
        if (isScanning.value) {
            Log.d("BLEManager", "Already scanning, aborting")
            return
        }

        isScanning.value = true
        scanResults.clear()
        Log.d("BLEManager", "Scanning started")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            scanner?.startScan(null, settings, scanCallback)
            handler.postDelayed({ stopScan() }, 10000) // stop after 10s
        } else {
            Log.d("BLEManager", "Permission issue with scan")
            isScanning.value = false
        }
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        isScanning.value = false
        Log.d("BLEManager", "Scanning stopped")
    }

    fun connect(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            gatt = device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            ) {
                this@BLEManager.gatt = gatt
                gatt.discoverServices()
                listener.onConnected()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener.onDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Handle services here if needed
        }
    }

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            gatt?.close()
            gatt = null
        }
    }
}
