package com.example.myfirstapp.model

data class OrientationDataPoint (
    val time: Float,
    val roll: Float,
    val pitch: Float,
    val yaw: Float,
    val q0: Float,
    val q1: Float,
    val q2: Float,
    val q3: Float
)