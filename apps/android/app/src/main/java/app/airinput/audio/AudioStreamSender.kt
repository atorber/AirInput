package app.airinput.audio

import app.airinput.bluetooth.RfcommClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AudioStreamSender(
    private val rfcomm: RfcommClient,
) : AutoCloseable {
    private val active = AtomicBoolean(false)
    private val sequence = AtomicInteger(0)
    private val framesSent = AtomicLong(0)
    private var encoder: OpusEncoder? = null

    fun start() {
        if (!active.compareAndSet(false, true)) return
        encoder = OpusEncoder()
        val startPayload = AudioProtocol.encodeStartPayload(
            sampleRate = AudioRecorder.SAMPLE_RATE,
            channels = 1,
            frameMs = AudioRecorder.FRAME_MS,
            bitrate = 32_000,
        )
        val frame = AudioProtocol.encodeFrame(
            PacketType.START,
            sequence.getAndIncrement(),
            System.currentTimeMillis(),
            startPayload,
        )
        rfcomm.sendAsync(frame)
    }

    fun onPcmFrame(pcm: ShortArray) {
        if (!active.get()) return
        val enc = encoder ?: return
        try {
            val opus = enc.encode(pcm)
            val packet = AudioProtocol.encodeFrame(
                PacketType.AUDIO,
                sequence.getAndIncrement(),
                System.currentTimeMillis(),
                opus,
            )
            rfcomm.sendAsync(packet)
            framesSent.incrementAndGet()
        } catch (t: Throwable) {
            // Keep capturing; surface via stop on fatal disconnect elsewhere.
        }
    }

    fun stop() {
        if (!active.compareAndSet(true, false)) return
        val stop = AudioProtocol.encodeFrame(
            PacketType.STOP,
            sequence.getAndIncrement(),
            System.currentTimeMillis(),
        )
        rfcomm.sendAsync(stop)
        encoder?.close()
        encoder = null
    }

    fun framesSent(): Long = framesSent.get()

    override fun close() {
        stop()
    }
}
