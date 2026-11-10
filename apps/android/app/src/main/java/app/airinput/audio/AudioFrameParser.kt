package app.airinput.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedFrame(
    val type: PacketType,
    val sequence: Int,
    val timestampMs: Long,
    val payload: ByteArray,
)

/**
 * Streaming parser for RFCOMM byte streams (partial frames + magic resync).
 */
class AudioFrameParser(private val maxBufferBytes: Int = 256 * 1024) {
    private var buffer = ByteArray(0)

    fun reset() {
        buffer = ByteArray(0)
    }

    fun push(chunk: ByteArray): List<DecodedFrame> {
        if (chunk.isEmpty()) return emptyList()
        buffer += chunk
        if (buffer.size > maxBufferBytes) {
            reset()
            throw IllegalStateException("parser buffer overflow")
        }

        val frames = ArrayList<DecodedFrame>()
        var offset = 0
        while (buffer.size - offset >= AudioProtocol.HEADER_SIZE) {
            val header = tryParseHeader(buffer, offset)
            if (header == null) {
                offset += 1
                continue
            }
            val (type, seq, ts, payloadLen) = header
            val total = AudioProtocol.HEADER_SIZE + payloadLen
            if (buffer.size - offset < total) break
            val payload = buffer.copyOfRange(offset + AudioProtocol.HEADER_SIZE, offset + total)
            frames += DecodedFrame(type, seq, ts, payload)
            offset += total
        }
        if (offset > 0) {
            buffer = buffer.copyOfRange(offset, buffer.size)
        }
        return frames
    }

    private data class Header(val type: PacketType, val seq: Int, val ts: Long, val payloadLen: Int)

    private fun tryParseHeader(buf: ByteArray, offset: Int): Header? {
        val view = ByteBuffer.wrap(buf, offset, AudioProtocol.HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        val magic = view.short.toInt() and 0xffff
        if (magic != AudioProtocol.MAGIC) return null
        val version = view.get().toInt() and 0xff
        if (version != AudioProtocol.VERSION) return null
        val typeId = view.get().toInt() and 0xff
        val type = PacketType.values().firstOrNull { it.id == typeId } ?: return null
        val seq = view.int
        val ts = view.long
        val payloadLen = view.short.toInt() and 0xffff
        if (payloadLen > AudioProtocol.MAX_PAYLOAD) return null
        return Header(type, seq, ts, payloadLen)
    }
}
