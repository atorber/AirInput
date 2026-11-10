package app.airinput.audio

/**
 * JNI wrapper around libopus encoder (48 kHz mono, 20 ms frames).
 */
class OpusEncoder(
    sampleRate: Int = 48_000,
    channels: Int = 1,
    bitrate: Int = 32_000,
) : AutoCloseable {
    private var nativeHandle: Long = nativeCreate(sampleRate, channels, bitrate)

    init {
        require(nativeHandle != 0L) { "failed to create Opus encoder" }
    }

    /** Encode one PCM s16le frame (960 samples for 20ms @ 48k). Returns Opus packet bytes. */
    fun encode(pcmS16le: ShortArray): ByteArray {
        check(nativeHandle != 0L) { "encoder closed" }
        return nativeEncode(nativeHandle, pcmS16le)
            ?: throw IllegalStateException("opus encode failed")
    }

    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int, bitrate: Int): Long
    private external fun nativeEncode(handle: Long, pcm: ShortArray): ByteArray?
    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("airinput_opus")
        }
    }
}
