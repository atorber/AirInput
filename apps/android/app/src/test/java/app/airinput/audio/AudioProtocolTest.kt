package app.airinput.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioProtocolTest {
    @Test
    fun emptyPingMatchesCrossLanguageVector() {
        val emptyPing = AudioProtocol.encodeFrame(PacketType.PING, 0, 0L)
        assertEquals(
            "414901040000000000000000000000000000",
            AudioProtocol.toHex(emptyPing),
        )
    }

    @Test
    fun startFrameMatchesCrossLanguageVector() {
        val startPayload = AudioProtocol.encodeStartPayload(48_000, 1, 20, 32_000)
        val start = AudioProtocol.encodeFrame(PacketType.START, 1, 1_710_000_000_000L, startPayload)
        assertEquals(
            "41490101000000010000018e23f14c00000b0000bb80011400007d0001",
            AudioProtocol.toHex(start),
        )
    }

    @Test
    fun audioTinyMatchesCrossLanguageVector() {
        val audio = AudioProtocol.encodeFrame(
            PacketType.AUDIO,
            42,
            1_710_000_000_020L,
            byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()),
        )
        assertEquals(
            "414901020000002a0000018e23f14c140004deadbeef",
            AudioProtocol.toHex(audio),
        )
    }
}
