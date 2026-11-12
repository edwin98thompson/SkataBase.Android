package com.example.myfirstapp.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.model.ConnectionState
import com.example.myfirstapp.viewmodel.OrientationViewModel

@Composable
fun CalibrationScreen(
    viewModel: OrientationViewModel
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val magCalStatus by viewModel.magCalibrationStatus.collectAsState()
    val betaValue by viewModel.betaValue.collectAsState()
    var sliderBetaValue by rememberSaveable {
        mutableFloatStateOf(betaValue.coerceIn(0f, 1f))
    }
    var applyGyroCalibration by rememberSaveable { mutableStateOf(false) }
    var applyAccelCalibration by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(betaValue) {
        Log.d("CalibrationScreen", "Displaying beta value received over BLE: $betaValue")
    }

    Text("Connection State: ${connectionState.javaClass.simpleName}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),

        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {



        Text(
            text = "Madgwick beta: %.2f".format(betaValue),
            modifier = Modifier.padding(top = 16.dp)
        )

        Slider(
            value = sliderBetaValue,
            onValueChange = { sliderBetaValue = it },
            onValueChangeFinished = {
                Log.d(
                    "CalibrationScreen",
                    "Slider released at beta=$sliderBetaValue; connection=$connectionState"
                )
                if (connectionState == ConnectionState.Connected) {
                    viewModel.updateBetaValue(sliderBetaValue)
                } else {
                    Log.w("CalibrationScreen", "Beta value was not sent because BLE is disconnected")
                }
            },
            valueRange = 0f..1f,
            enabled = connectionState == ConnectionState.Connected,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Gyroscopic Calibration",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                enabled = connectionState == ConnectionState.Connected,
                onClick = {
                    Log.d("CalibrationScreen", "Run gyroscopic calibration clicked")
                    viewModel.startGyroCalibration()
                }
            ) {
                Text("Run Gyro Calibration")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Apply ")
                Switch(
                    checked = applyGyroCalibration,
                    enabled = connectionState == ConnectionState.Connected,
                    onCheckedChange = {
                        applyGyroCalibration = it
                        viewModel.toggleGyroscopeCalibration(applyGyroCalibration)
                    }
                )
            }
        }

        Text(
            text = "Accelerometer Calibration",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                enabled = connectionState == ConnectionState.Connected,
                onClick = {
                    Log.d("CalibrationScreen", "Run accelerometer calibration clicked")
                    viewModel.startAccelCalibration()
                }
            ) {
                Text("Run Accel Calibration")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Apply ")
                Switch(
                    checked = applyAccelCalibration,
                    enabled = connectionState == ConnectionState.Connected,
                    onCheckedChange = {
                        applyAccelCalibration = it
                        viewModel.toggleAccelCalibration(applyAccelCalibration)
                    }
                )
            }
        }

        // Show the status above the button
        Text(
            text = magCalStatus,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(
            enabled = connectionState == ConnectionState.Connected,
            onClick = {
                Log.d("CalibrationScreen","Starting magnetic calibration")
                viewModel.startMagnetometerCalibration()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Magnetic Calibration")
        }

        // At some point I may add toggle for mag calibration just like the other bits
    }
}