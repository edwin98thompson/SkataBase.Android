package com.example.myfirstapp.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.model.ConnectionState
import com.example.myfirstapp.viewmodel.OrientationViewModel

@Composable
fun OrientationScreen(
    viewModel: OrientationViewModel
) {

    val connectionState by viewModel.connectionState.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val history by viewModel.history.collectAsState()
    val magCalStatus by viewModel.magCalibrationStatus.collectAsState()

    Text("Connection State: ${connectionState.javaClass.simpleName}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),

        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Roll: %.2f".format(orientation.roll))
        Text("Pitch: %.2f".format(orientation.pitch))
        Text("Yaw: %.2f".format(orientation.yaw))

        IMUPlotter(
            data = history,
            modifier = Modifier.weight(1f)
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