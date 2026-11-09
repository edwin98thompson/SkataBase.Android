package com.example.myfirstapp.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    val orientation by viewModel.orientation.collectAsState()
    val history by viewModel.history.collectAsState()
    val magCalStatus by viewModel.magCalibrationStatus.collectAsState()
    val betaValue by viewModel.betaValue.collectAsState()
    var sliderBetaValue by rememberSaveable {
        mutableFloatStateOf(betaValue.coerceIn(0f, 1f))
    }

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

        Text("Madgwick beta: %.2f".format(betaValue))

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

        // Show the status above the button
        Text(
            text = magCalStatus,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(
            enabled = connectionState == ConnectionState.Connected,
            onClick = {
                if(connectionState != ConnectionState.Connected)
                {
                    Log.d("OrientationViewModel","Device must be connected to start calibration")
                }
                else
                {
                    Log.d("OrientationScreen","Starting magnetic calibration")
                    viewModel.startMagnetometerCalibration();
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Magnetic Calibration")
        }
    }
}