package com.example.myfirstapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myfirstapp.ble.BLEManager
import com.example.myfirstapp.repository.BluetoothRepository
import com.example.myfirstapp.screens.AnimationScreen
import com.example.myfirstapp.screens.BLEScannerScreen
import com.example.myfirstapp.screens.CalibrationScreen
import com.example.myfirstapp.screens.EventReviewScreen
import com.example.myfirstapp.screens.OrientationScreen
import com.example.myfirstapp.screens.TrainingScreen
import com.example.myfirstapp.ui.theme.MyFirstAppTheme
import com.example.myfirstapp.viewmodel.CalibrationViewModel
import com.example.myfirstapp.viewmodel.CalibrationViewModelFactory
import com.example.myfirstapp.viewmodel.OrientationViewModel
import com.example.myfirstapp.viewmodel.OrientationViewModelFactory
import com.example.myfirstapp.viewmodel.ScannerViewModel
import com.example.myfirstapp.viewmodel.ScannerViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BLEManager
    private lateinit var repository: BluetoothRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep one BLE manager shared by the activity and background services.
        AppGraph.initialize(applicationContext)
        bleManager = AppGraph.bleManager
        repository = AppGraph.repository

        requestBluetoothPermissions()

        setContent {
            MyFirstAppTheme {
                val navController = rememberNavController()

                // Define the tabs
                val items = listOf(
                    Screen.Scanner,
                    Screen.Orientation,
                    Screen.Animation,
                    Screen.Calibration,
                    Screen.Training
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination

                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            // Pop up to the start destination of the graph to
                                            // avoid building up a large stack of destinations
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            // Avoid multiple copies of the same destination when
                                            // reselecting the same item
                                            launchSingleTop = true
                                            // Restore state when reselecting a previously selected item
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Scanner.route
                        ) {
                            composable(Screen.Scanner.route) {
                                val scannerViewModel: ScannerViewModel = viewModel(
                                    factory = ScannerViewModelFactory(repository)
                                )
                                BLEScannerScreen(
                                    viewModel = scannerViewModel,
                                    onConnected = {
                                        // Automatically switch to orientation tab on connect
//                                        navController.navigate(Screen.Orientation.route)
                                    }
                                )
                            }

                            composable(Screen.Orientation.route) {
                                val orientationViewModel: OrientationViewModel = viewModel(
                                    factory = OrientationViewModelFactory(repository)
                                )
                                OrientationScreen(viewModel = orientationViewModel)
                            }

                            composable(Screen.Animation.route) {
                                val orientationViewModel: OrientationViewModel = viewModel(
                                    factory = OrientationViewModelFactory(repository)
                                )
                                AnimationScreen(viewModel = orientationViewModel)
                            }

                            composable(Screen.Calibration.route) {
                                val calibrationViewModel: CalibrationViewModel = viewModel(
                                    factory = CalibrationViewModelFactory(repository)
                                )
                                CalibrationScreen(viewModel = calibrationViewModel)
                            }

                            composable(Screen.Training.route) {
                                TrainingScreen(
                                    onOpenReview = { navController.navigate(Screen.EventReview.route) }
                                )
                            }

                            composable(Screen.EventReview.route) {
                                EventReviewScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            val canScan = grants[Manifest.permission.BLUETOOTH_SCAN] == true
            val canConnect = grants[Manifest.permission.BLUETOOTH_CONNECT] == true
            if (canScan && canConnect) {
                bleManager.startAutoConnect()
            }
        }
        permissionLauncher.launch(permissions)
    }
}

// Helper class to manage screen routes and icons
sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Scanner : Screen("scanner", "Scanner", Icons.AutoMirrored.Filled.List)
    object Orientation : Screen("orientation", "Orientation", Icons.Default.Place)
    object Animation : Screen("animation", "Animation", Icons.Default.ShoppingCart)
    object Calibration : Screen("Calibration", "Calibration", Icons.Default.Build)
    object Training : Screen("training", "Training", Icons.Default.PlayArrow)
    object EventReview : Screen("event-review", "Event Review", Icons.Default.PlayArrow)
}