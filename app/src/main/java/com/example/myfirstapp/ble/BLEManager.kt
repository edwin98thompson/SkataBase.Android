package com.example.myfirstapp.ble

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
import com.example.myfirstapp.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BLEManager(private val context: Context) {
    var isScanning = mutableStateOf(false)
        private set

    private val SERVICE_UUID = java.util.UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val CHARACTERISTIC_UUID = java.util.UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableMapOf<String, BLEDevice>()

    private val _incomingMessages =
        MutableStateFlow<IntArray?>(null)

    val incomingMessages =
        _incomingMessages.asStateFlow()

    private val _connectionState =
        MutableStateFlow<ConnectionState>(
            ConnectionState.Disconnected
        )

    val connectionState =
        _connectionState.asStateFlow()

    private val _devices =
        MutableStateFlow<List<BLEDevice>>(emptyList())

    val devices =
        _devices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            if (!scanResults.containsKey(address)) {
                val bleDevice = BLEDevice(device.name, address, device)
                scanResults[address] = bleDevice
                _devices.value = scanResults.values.toList()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning.value = false
            _connectionState.value = ConnectionState.Error("Scan failed with code $errorCode")
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

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        isScanning.value = false
        Log.d("BLEManager", "Scanning stopped")
    }

    fun connect(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            _connectionState.value = ConnectionState.Connecting
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
                _connectionState.value = ConnectionState.Connected
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            if (service != null) {
                val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)

                    // Required to get notifications on some devices
                    val descriptor = characteristic.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(it)
                    }

                    Log.d("BLEManager", "Notification enabled on characteristic")
                } else {
                    _connectionState.value = ConnectionState.Error("Characteristic not found")

                }
            } else {
                _connectionState.value = ConnectionState.Error("Service not found")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val data = characteristic.value

                if (data.size >= 20) {
                    val buffer = ByteBuffer.wrap(data)
                    buffer.order(ByteOrder.LITTLE_ENDIAN)  // ESP32 sends in little-endian

                    val values = IntArray(10) { buffer.short.toInt() }  // short = int16

                    Log.d("BLEManager", "Received IMU data: ${values.joinToString(", ")}")

                    _incomingMessages.value = values

                } else {
                    Log.e("BLEManager", "Invalid data length: ${data.size}")
                }
            }
        }
    }

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            gatt?.close()
            gatt = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }
}
