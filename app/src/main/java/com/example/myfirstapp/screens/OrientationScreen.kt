package com.example.myfirstapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.myfirstapp.viewmodel.OrientationViewModel

@Composable
fun OrientationScreen(
    viewModel: OrientationViewModel
) {

    val orientation by viewModel.orientation.collectAsState(null)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Roll: ${orientation?.roll}")

        Text("Pitch: ${orientation?.pitch}")

        Text("Yaw: ${orientation?.yaw}")
    }
}