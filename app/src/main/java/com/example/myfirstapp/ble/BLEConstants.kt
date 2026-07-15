package com.example.myfirstapp.ble

import java.util.UUID

object BLEConstants {

    val SERVICE_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-1234567890ab")

    val IMU_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")

    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val SCAN_DURATION_MS = 10_000L

    const val EXPECTED_PACKET_SIZE = 20
}