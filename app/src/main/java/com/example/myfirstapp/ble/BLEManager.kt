package com.example.myfirstapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import com.example.myfirstapp.model.BLEDevice
import com.example.myfirstapp.model.ConnectionState
import com.example.myfirstapp.model.IMUPacket
import com.example.myfirstapp.model.IMUPacketDecoder
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.contains

class BLEManager(private val context: Context) {
    var isScanning = mutableStateOf(false)
        private set

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableMapOf<String, BLEDevice>()

//    private val _incomingMessages =
//        MutableStateFlow<IMUPacket?>(null)
//
//    val incomingMessages =
//        _incomingMessages.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IMUPacket>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages = _incomingMessages.asSharedFlow()

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

            // Get the name, defaulting to "Unknown" if null
            val deviceName = device.name ?: "Unknown"

            // 2. Check if the name contains your specific string (case-insensitive)
            if (deviceName.contains("skate", ignoreCase = true)) {
                if (!scanResults.containsKey(address)) {
                    Log.d("BLEManager", "Found matching device: $deviceName ($address)")

                    val bleDevice = BLEDevice(deviceName, address, device)
                    scanResults[address] = bleDevice

                    // Update the StateFlow list for the UI
                    _devices.value = scanResults.values.toList()
                }
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
//                this@BLEManager.gatt = gatt
//                gatt.requestMtu(247)
//                gatt.discoverServices()
//                _connectionState.value = ConnectionState.Connected

                this@BLEManager.gatt = gatt
                Log.d("BLEManager", "Connected, requesting MTU...")
                gatt.requestMtu(247)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(BLEConstants.SERVICE_UUID)
            if (service != null) {
                val characteristic = service.getCharacteristic(BLEConstants.IMU_CHARACTERISTIC_UUID)
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

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(
            gatt: BluetoothGatt,
            mtu: Int,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                Log.d("BLEManager", "MTU negotiated: $mtu bytes")

            } else {

                Log.w("BLEManager", "MTU request failed, using default")
            }

            // Continue with normal connection
            gatt.discoverServices()

            _connectionState.value = ConnectionState.Connected
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BLEConstants.IMU_CHARACTERISTIC_UUID) {

                val data = characteristic.value

                val packet = IMUPacketDecoder.decode(data)

                if (packet == null)
                {
                    Log.e(
                        "BLEManager",
                        "Failed to decode IMU packet: ${data.size} bytes"
                    )
                    return
                }

                Log.d(
                    "BLEManager",
                    "Seq=${packet.sequence}, " +
                            "timestamp=${packet.timestampMs}ms, " +
                            "Roll=${packet.roll}, " +
                            "Pitch=${packet.pitch}, " +
                            "Yaw=${packet.yaw}, " +
                            "Accel=(${packet.accelX}, ${packet.accelY}, ${packet.accelZ}), " +
                            "Gyro=(${packet.gyroX}, ${packet.gyroY}, ${packet.gyroZ})," +
                            "Magx=(${packet.magX}, Magy=${packet.magY}, Magz=${packet.magZ}" +
                            "calibrationStatus=(${packet.calibrationStatus}"
                )

                _incomingMessages.tryEmit(packet)
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

    @SuppressLint("MissingPermission")
    fun sendMessage(message: String) {
        val currentGatt = gatt ?: run {
            Log.w("BLEManager", "Gatt is null, cannot send message")
            return
        }

        val service = currentGatt.getService(BLEConstants.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BLEConstants.IMU_CHARACTERISTIC_UUID)

        if (characteristic != null) {
            val bytes = message.encodeToByteArray()

            // Use the appropriate write type for your device (DEFAULT or NO_RESPONSE)
            val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(characteristic, bytes, writeType)
            } else {
                // Legacy support for older Android versions
                characteristic.value = bytes
                characteristic.writeType = writeType
                currentGatt.writeCharacteristic(characteristic)
            }
            Log.d("BLEManager", "Sent message: $message")
        } else {
            Log.e("BLEManager", "Write characteristic not found")
        }
    }
}

fun debugImuPacket(data: ByteArray) {
    if (data.size != 77) {
        Log.e("IMU_BLE", "Invalid packet size: ${data.size}")
        return
    }

    val buffer = ByteBuffer
        .wrap(data)
        .order(ByteOrder.LITTLE_ENDIAN)

    val version = buffer.get().toInt() and 0xFF
    val sequence = buffer.short.toInt() and 0xFFFF
    val timestamp = buffer.int.toLong() and 0xFFFFFFFFL

    val roll = buffer.float
    val pitch = buffer.float
    val yaw = buffer.float

    Log.d(
        "IMU_BLE",
        "Version=$version, Seq=$sequence, Time=${timestamp}ms, " +
                "Roll=%.2f°, Pitch=%.2f°, Yaw=%.2f°".format(
                    roll, pitch, yaw
                )
    )
}
