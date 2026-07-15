package com.example.myfirstapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myfirstapp.ble.BLEManager
import com.example.myfirstapp.repository.BluetoothRepository
import com.example.myfirstapp.screens.BLEScannerScreen
import com.example.myfirstapp.screens.OrientationScreen
import com.example.myfirstapp.ui.theme.MyFirstAppTheme
import com.example.myfirstapp.viewmodel.ScannerViewModel
import com.example.myfirstapp.viewmodel.ScannerViewModelFactory
import com.example.myfirstapp.viewmodel.OrientationViewModel
import com.example.myfirstapp.viewmodel.OrientationViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BLEManager
    private lateinit var repository: BluetoothRepository
    private lateinit var scannerViewModel: ScannerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = BLEManager(applicationContext)
        repository = BluetoothRepository(bleManager)
        scannerViewModel = ScannerViewModel(repository)


        requestBluetoothPermissions()

        setContent {

            val navController = rememberNavController()

            MyFirstAppTheme {

                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {

                    NavHost(
                        navController = navController,
                        startDestination = "scanner"
                    ) {

                        composable("scanner") {

                            val scannerViewModel: ScannerViewModel =
                                viewModel(
                                    factory = ScannerViewModelFactory(repository)
                                )

                            BLEScannerScreen(
                                viewModel = scannerViewModel,
                                onConnected = {

                                    Log.d("NAV", "Navigating to orientation")

                                    navController.navigate("orientation")
                                }
                            )
                        }


                        composable("orientation") {

                            val orientationViewModel: OrientationViewModel =
                                viewModel(
                                    factory = OrientationViewModelFactory(repository)
                                )

                            OrientationScreen(
                                viewModel = orientationViewModel
                            )
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Optional but sometimes required on older Android versions

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val allGranted = result.values.all { it }
//            if (!allGranted) {
//                // You can handle permission denial more gracefully here
//                bleManager.setListener(object : BLEManager.BLEListener {
//                    override fun onDeviceFound(device: com.example.myfirstapp.model.BLEDevice) {}
//                    override fun onConnected() {}
//                    override fun onDisconnected() {}
//                    override fun onError(message: String) {
//                        // Optional: handle error display in UI if needed
//                    }
//                })
//            }
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}
