package app.airinput.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures 48 kHz mono PCM 16-bit in 20 ms (960 sample) frames.
 */
class AudioRecorder(
    private val onFrame: (ShortArray) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val tag = "AirInputAudio"
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            running.set(false)
            onError("AudioRecord.getMinBufferSize failed: $minBuf")
            return
        }
        val bufferSize = maxOf(minBuf, FRAME_SAMPLES * 2 * 4)
        // MIC：比 VOICE_COMMUNICATION 少做回声消除/自动门限，远程验听更不容易整段静音。
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            running.set(false)
            rec.release()
            onError("AudioRecord not initialized")
            return
        }
        record = rec
        rec.startRecording()
        thread = Thread({
            val frame = ShortArray(FRAME_SAMPLES)
            while (running.get()) {
                var filled = 0
                while (filled < FRAME_SAMPLES && running.get()) {
                    val n = rec.read(frame, filled, FRAME_SAMPLES - filled)
                    if (n < 0) {
                        onError("AudioRecord.read error: $n")
                        running.set(false)
                        break
                    }
                    filled += n
                }
                if (filled == FRAME_SAMPLES && running.get()) {
                    onFrame(frame.copyOf())
                }
            }
            Log.i(tag, "capture loop exit")
        }, "airinput-audio-capture").also { it.start() }
    }

    fun stop() {
        running.set(false)
        try {
            thread?.join(1_000)
        } catch (_: InterruptedException) {
        }
        thread = null
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        record?.release()
        record = null
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
    }
}
