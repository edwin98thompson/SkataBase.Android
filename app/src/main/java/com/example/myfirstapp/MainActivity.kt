package com.example.myfirstapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.myfirstapp.screens.BLEScannerScreen
import com.example.myfirstapp.ui.theme.MyFirstAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BLEManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLEManager with application context
        bleManager = BLEManager(applicationContext)

        // Ask for necessary permissions at runtime
        requestBluetoothPermissions()

        setContent {
            MyFirstAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BLEScannerScreen(bleManager = bleManager)
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Optional but sometimes required on older Android versions
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.BLUETOOTH)
        }

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val allGranted = result.values.all { it }
            if (!allGranted) {
                // You can handle permission denial more gracefully here
                bleManager.setListener(object : BLEManager.BLEListener {
                    override fun onDeviceFound(device: com.example.myfirstapp.model.BLEDevice) {}
                    override fun onConnected() {}
                    override fun onDisconnected() {}
                    override fun onError(message: String) {
                        // Optional: handle error display in UI if needed
                    }
                })
            }
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}
