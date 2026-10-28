package com.example.myfirstapp.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

object IMUPacketDecoder {

    const val PACKET_SIZE = 76

    fun decode(data: ByteArray): IMUPacket? {
        if (data.size != PACKET_SIZE) {
            return null
        }

        return try {
            val buffer = ByteBuffer
                .wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)

            val protocolVersion = buffer.get().toInt() and 0xFF
            val sequence = buffer.short.toInt() and 0xFFFF
            val timestampMs = buffer.int.toLong() and 0xFFFFFFFFL

            val roll = buffer.float
            val pitch = buffer.float
            val yaw = buffer.float

            val accelX = buffer.float
            val accelY = buffer.float
            val accelZ = buffer.float

            val gyroX = buffer.float
            val gyroY = buffer.float
            val gyroZ = buffer.float

            val magX = buffer.float
            val magY = buffer.float
            val magZ = buffer.float

            val q0 = buffer.float
            val q1 = buffer.float
            val q2 = buffer.float
            val q3 = buffer.float

            val temperatureC = buffer.float
            val batteryPercent = buffer.get().toInt() and 0xFF

            IMUPacket(
                protocolVersion = protocolVersion,
                sequence = sequence,
                timestampMs = timestampMs,
                roll = roll,
                pitch = pitch,
                yaw = yaw,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                gyroX = gyroX,
                gyroY = gyroY,
                gyroZ = gyroZ,
                magX = magX,
                magY = magY,
                magZ = magZ,
                q0 = q0,
                q1 = q1,
                q2 = q2,
                q3 = q3,
                temperatureC = temperatureC,
                batteryPercent = batteryPercent
            )
        } catch (e: Exception) {
            null
        }
    }
}