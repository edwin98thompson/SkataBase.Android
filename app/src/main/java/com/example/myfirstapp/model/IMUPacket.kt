package com.example.myfirstapp.model

data class IMUPacket(
    val protocolVersion: Int,
    val sequence: Int,
    val timestampMs: Long,

    val roll: Float,
    val pitch: Float,
    val yaw: Float,

    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,

    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,

    val magX: Float,
    val magY: Float,
    val magZ: Float,

    val q0: Float,
    val q1: Float,
    val q2: Float,
    val q3: Float,

    val temperatureC: Float,
    val batteryPercent: Int,
    val calibrationStatus: Int,

    val betaValue: Float
)