package app.airinput.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import app.airinput.audio.AudioFrameParser
import app.airinput.audio.AudioProtocol
import app.airinput.audio.DecodedFrame
import app.airinput.audio.PacketType
import app.airinput.security.DeviceAuth
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bluetooth Classic RFCOMM client. All I/O runs off the main thread.
 */
class RfcommClient(
    private val adapter: BluetoothAdapter,
    private val listener: Listener,
) {
    interface Listener {
        fun onRfcommState(state: State, detail: String = "")
        fun onRfcommError(message: String)
        fun onRfcommAuthenticated() {}
        fun onRfcommAuthFailed(detail: String) {}
        fun onRfcommFrame(frame: DecodedFrame) {}
    }

    enum class State { Idle, Connecting, Connected, Authenticated, Disconnected, Error }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8d53dc1d-1db7-4cd3-868b-8a527460aa84")
        /** Must match Helper default pairing key. */
        val DEFAULT_PAIRING_KEY: ByteArray =
            "AirInput-Default-Pairing-Key-v1!!".toByteArray(Charsets.UTF_8)
    }

    private val tag = "AirInputRFCOMM"
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "airinput-rfcomm").apply { isDaemon = true }
    }
    private val connected = AtomicBoolean(false)
    private val authenticated = AtomicBoolean(false)
    private val session = AtomicInteger(0)
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var input: InputStream? = null
    private var readThread: Thread? = null
    private val parser = AudioFrameParser()
    private var outSeq = 1

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        io.execute {
            listener.onRfcommState(State.Connecting)
            // Invalidate any previous read loop before closing.
            val mySession = session.incrementAndGet()
            connected.set(false)
            authenticated.set(false)
            closeSocketQuietly()

            try {
                runCatching { adapter.cancelDiscovery() }
                // Refresh remote SDP cache; Mac may have just published the service.
                runCatching {
                    device.fetchUuidsWithSdp()
                    Thread.sleep(700)
                }

                val sock = openSocketWithFallback(device)
                socket = sock
                output = sock.outputStream
                input = sock.inputStream
                connected.set(true)
                authenticated.set(false)
                parser.reset()
                listener.onRfcommState(State.Connected, device.name ?: "device")
                startReadLoop(mySession)
            } catch (e: IOException) {
                Log.e(tag, "connect failed", e)
                connected.set(false)
                closeSocketQuietly()
                val msg = humanizeConnectError(e)
                listener.onRfcommState(State.Error, msg)
                listener.onRfcommError(msg)
            } catch (t: Throwable) {
                Log.e(tag, "connect failed", t)
                connected.set(false)
                closeSocketQuietly()
                listener.onRfcommState(State.Error, t.message ?: "connect failed")
                listener.onRfcommError(t.message ?: "RFCOMM connect failed")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSocketWithFallback(device: BluetoothDevice): BluetoothSocket {
        val attempts = listOf(
            "secure-uuid" to {
                device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            },
            "insecure-uuid" to {
                device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
            },
            "reflect-channel-1" to {
                createReflectionSocket(device, 1)
            },
            "reflect-channel-3" to {
                createReflectionSocket(device, 3)
            },
            "reflect-channel-5" to {
                createReflectionSocket(device, 5)
            },
        )

        var last: IOException? = null
        for ((name, factory) in attempts) {
            var sock: BluetoothSocket? = null
            try {
                runCatching { adapter.cancelDiscovery() }
                sock = factory()
                Log.i(tag, "trying RFCOMM via $name ...")
                sock.connect()
                Log.i(tag, "RFCOMM connected via $name")
                return sock
            } catch (e: IOException) {
                Log.w(tag, "RFCOMM $name failed: ${e.message}")
                last = e
                try {
                    sock?.close()
                } catch (_: Throwable) {
                }
                try {
                    Thread.sleep(350)
                } catch (_: InterruptedException) {
                }
            } catch (t: Throwable) {
                Log.w(tag, "RFCOMM $name unavailable: ${t.message}")
                try {
                    sock?.close()
                } catch (_: Throwable) {
                }
            }
        }
        throw last ?: IOException("RFCOMM connect failed (all strategies)")
    }

    @SuppressLint("MissingPermission")
    private fun createReflectionSocket(device: BluetoothDevice, channel: Int): BluetoothSocket {
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return m.invoke(device, channel) as BluetoothSocket
    }

    private fun humanizeConnectError(e: IOException): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("read failed", ignoreCase = true) ||
                raw.contains("socket might closed", ignoreCase = true) ->
                "RFCOMM 建链失败（常见：电脑 Helper 未 listening / 与 HID 抢链路）。请先开桌面「开启服务」，再重连。详情: $raw"
            raw.contains("timeout", ignoreCase = true) ->
                "RFCOMM 超时。确认已配对且 Mac Helper 正在 listening。详情: $raw"
            else -> "RFCOMM 连接失败: ${raw.ifEmpty { e.javaClass.simpleName }}"
        }
    }

    fun send(bytes: ByteArray): Boolean {
        if (!connected.get()) return false
        return try {
            val out = output ?: return false
            val latchOk = AtomicBoolean(false)
            val done = Object()
            var finished = false
            io.execute {
                try {
                    out.write(bytes)
                    out.flush()
                    latchOk.set(true)
                } catch (e: IOException) {
                    Log.e(tag, "write failed", e)
                    connected.set(false)
                    listener.onRfcommState(State.Error, e.message ?: "write failed")
                    listener.onRfcommError(e.message ?: "RFCOMM write failed")
                    closeSocketQuietly()
                } finally {
                    synchronized(done) {
                        finished = true
                        done.notifyAll()
                    }
                }
            }
            synchronized(done) {
                while (!finished) done.wait(1_000)
            }
            latchOk.get()
        } catch (t: Throwable) {
            listener.onRfcommError(t.message ?: "send failed")
            false
        }
    }

    fun sendAsync(bytes: ByteArray) {
        if (!connected.get()) return
        io.execute {
            try {
                val out = output ?: return@execute
                out.write(bytes)
            } catch (e: IOException) {
                Log.e(tag, "async write failed", e)
                connected.set(false)
                listener.onRfcommState(State.Disconnected, e.message ?: "write failed")
                closeSocketQuietly()
            }
        }
    }

    fun isConnected(): Boolean = connected.get()
    fun isAuthenticated(): Boolean = authenticated.get()

    fun disconnect() {
        io.execute {
            session.incrementAndGet()
            connected.set(false)
            authenticated.set(false)
            closeSocketQuietly()
            listener.onRfcommState(State.Disconnected)
        }
    }

    fun close() {
        disconnect()
        io.shutdownNow()
    }

    private fun startReadLoop(expectedSession: Int) {
        readThread?.interrupt()
        val inp = input ?: return
        readThread = Thread({
            val buf = ByteArray(4096)
            try {
                while (
                    session.get() == expectedSession &&
                    connected.get() &&
                    !Thread.currentThread().isInterrupted
                ) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    handleIncoming(buf.copyOf(n))
                }
            } catch (e: IOException) {
                if (session.get() == expectedSession && connected.get()) {
                    Log.w(tag, "read ended", e)
                }
            } finally {
                if (session.get() == expectedSession && connected.get()) {
                    connected.set(false)
                    authenticated.set(false)
                    listener.onRfcommState(State.Disconnected, "read closed")
                }
            }
        }, "airinput-rfcomm-read").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun handleIncoming(chunk: ByteArray) {
        val frames = try {
            parser.push(chunk)
        } catch (t: Throwable) {
            listener.onRfcommError(t.message ?: "parse error")
            return
        }
        for (frame in frames) {
            when (frame.type) {
                PacketType.CHALLENGE -> respondChallenge(frame.payload)
                PacketType.AUTH_OK -> {
                    authenticated.set(true)
                    listener.onRfcommState(State.Authenticated)
                    listener.onRfcommAuthenticated()
                }
                PacketType.AUTH_FAILED -> {
                    authenticated.set(false)
                    listener.onRfcommAuthFailed("HMAC mismatch")
                    listener.onRfcommState(State.Error, "auth failed")
                }
                PacketType.PONG, PacketType.PING -> { /* ignore */ }
                else -> listener.onRfcommFrame(frame)
            }
        }
    }

    private fun respondChallenge(challenge: ByteArray) {
        val mac = DeviceAuth.hmacSha256(DEFAULT_PAIRING_KEY, challenge)
        val seq = outSeq++
        val frame = AudioProtocol.encodeFrame(
            PacketType.AUTH_RESPONSE,
            seq,
            System.currentTimeMillis(),
            mac,
        )
        sendAsync(frame)
    }

    private fun closeSocketQuietly() {
        try {
            readThread?.interrupt()
        } catch (_: Throwable) {
        }
        readThread = null
        try {
            input?.close()
        } catch (_: Throwable) {
        }
        try {
            output?.close()
        } catch (_: Throwable) {
        }
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        input = null
        output = null
        socket = null
        parser.reset()
    }
}
