package com.example.myfirstapp.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

object CommandPacketEncoder {

    const val PACKET_SIZE = 6

    fun encode(packet: CommandPacket): ByteArray {
        require(packet.protocolVersion in 0..0xFF) {
            "protocolVersion must fit in one unsigned byte (0..255); got ${packet.protocolVersion}"
        }
        require(packet.commandId.value in 0..0xFF) {
            "commandId must fit in one unsigned byte (0..255); got ${packet.commandId.value}"
        }
        require(packet.betaValue.isFinite()) {
            "betaValue must be a finite Float; got ${packet.betaValue}"
        }
        require(packet.betaValue in 0f..1f) {
            "betaValue must be in the range 0.0..1.0; got ${packet.betaValue}"
        }

        return try {
            ByteBuffer.allocate(PACKET_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(packet.protocolVersion.toByte())
                .put(packet.commandId.value.toByte())
                 .putFloat(packet.betaValue)
                .array()
                .also { bytes ->
                    check(bytes.size == PACKET_SIZE) {
                        "Encoded packet has ${bytes.size} bytes; expected $PACKET_SIZE"
                    }
                }
        } catch (exception: Exception) {
            throw IllegalArgumentException(
                "Could not encode command packet $packet",
                exception
            )
        }
    }
}
