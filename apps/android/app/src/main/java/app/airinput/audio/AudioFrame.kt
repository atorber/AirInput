package app.airinput.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PacketType(val id: Int) {
    START(1),
    AUDIO(2),
    STOP(3),
    PING(4),
    PONG(5),
    CHALLENGE(6),
    AUTH_RESPONSE(7),
    AUTH_OK(8),
    AUTH_FAILED(9),
}

object AudioProtocol {
    const val MAGIC: Int = 0x4149
    const val VERSION: Int = 1
    const val HEADER_SIZE: Int = 18
    const val MAX_PAYLOAD: Int = 4096
    const val CODEC_OPUS: Int = 1

    fun encodeFrame(
        type: PacketType,
        sequence: Int,
        timestampMs: Long,
        payload: ByteArray = ByteArray(0),
    ): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too large" }
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(MAGIC.toShort())
        buf.put(VERSION.toByte())
        buf.put(type.id.toByte())
        buf.putInt(sequence)
        buf.putLong(timestampMs)
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        return buf.array()
    }

    fun encodeStartPayload(
        sampleRate: Int,
        channels: Int,
        frameMs: Int,
        bitrate: Int,
        codec: Int = CODEC_OPUS,
    ): ByteArray {
        val buf = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sampleRate)
        buf.put(channels.toByte())
        buf.put(frameMs.toByte())
        buf.putInt(bitrate)
        buf.put(codec.toByte())
        return buf.array()
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { b -> "%02x".format(b) }
}
