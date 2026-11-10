package app.airinput.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import app.airinput.hid.HidReportDescriptor
import app.airinput.hid.KeyboardReport
import app.airinput.hid.MouseReport
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers this phone as a Bluetooth HID Device (mouse + keyboard).
 * Success is determined by [BluetoothHidDevice.Callback.onAppStatusChanged], not registerApp()'s boolean.
 */
class HidDeviceController(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val listener: Listener,
) {
    interface Listener {
        fun onHidProxyReady(ready: Boolean)
        fun onHidAppRegistered(registered: Boolean)
        fun onHidConnectionState(device: BluetoothDevice?, state: Int)
        fun onHidError(message: String)
    }

    private val tag = "AirInputHID"
    private var hidDevice: BluetoothHidDevice? = null
    private val proxyReady = AtomicBoolean(false)
    private val appRegistered = AtomicBoolean(false)
    private var hostDevice: BluetoothDevice? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE || proxy !is BluetoothHidDevice) return
            hidDevice = proxy
            proxyReady.set(true)
            listener.onHidProxyReady(true)
            registerAppInternal(proxy)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            proxyReady.set(false)
            appRegistered.set(false)
            hidDevice = null
            hostDevice = null
            listener.onHidProxyReady(false)
            listener.onHidAppRegistered(false)
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(tag, "onAppStatusChanged registered=$registered plugged=${pluggedDevice?.addressMasked()}")
            appRegistered.set(registered)
            listener.onHidAppRegistered(registered)
            if (registered && pluggedDevice != null) {
                hostDevice = pluggedDevice
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.i(tag, "onConnectionStateChanged state=$state device=${device?.addressMasked()}")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                releaseAllInputs()
                if (hostDevice?.address == device?.address) {
                    hostDevice = null
                }
            }
            listener.onHidConnectionState(device, state)
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            listener.onHidError("Bluetooth HID Device requires API 28+")
            return
        }
        val ok = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) {
            listener.onHidError("getProfileProxy(HID_DEVICE) failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerAppInternal(proxy: BluetoothHidDevice) {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "AirInput",
            "AirInput HID Mouse/Keyboard",
            "AirInput",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidReportDescriptor.BYTES,
        )
        val inQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX,
        )
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX,
        )
        // Do not trust the boolean alone; wait for onAppStatusChanged(true).
        val accepted = proxy.registerApp(sdp, inQos, outQos, Runnable::run, hidCallback)
        Log.i(tag, "registerApp accepted=$accepted (await onAppStatusChanged)")
        if (!accepted) {
            listener.onHidError("registerApp rejected by stack")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        val hid = hidDevice
        if (hid == null || !appRegistered.get()) {
            listener.onHidError("HID app not registered yet")
            return false
        }
        return hid.connect(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnect(): Boolean {
        val hid = hidDevice ?: return false
        val device = hostDevice ?: return false
        releaseAllInputs()
        return hid.disconnect(device)
    }

    fun onEnterBackground() {
        // Many OEMs unregister HID when the app backgrounds. Release keys first.
        releaseAllInputs()
        unregisterApp()
    }

    fun onEnterForeground() {
        val hid = hidDevice
        if (hid != null && proxyReady.get() && !appRegistered.get()) {
            registerAppInternal(hid)
        } else if (!proxyReady.get()) {
            start()
        }
    }

    @SuppressLint("MissingPermission")
    fun unregisterApp() {
        try {
            hidDevice?.unregisterApp()
        } catch (t: Throwable) {
            Log.w(tag, "unregisterApp", t)
        }
        appRegistered.set(false)
        listener.onHidAppRegistered(false)
    }

    fun close() {
        releaseAllInputs()
        unregisterApp()
        try {
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Throwable) {
        }
        hidDevice = null
        proxyReady.set(false)
    }

    @SuppressLint("MissingPermission")
    fun sendMouse(report: MouseReport): Boolean {
        val hid = hidDevice ?: return false
        val device = hostDevice ?: return false
        return hid.sendReport(device, MouseReport.REPORT_ID.toInt(), report.toBytes())
    }

    @SuppressLint("MissingPermission")
    fun sendKeyboard(report: KeyboardReport): Boolean {
        val hid = hidDevice ?: return false
        val device = hostDevice ?: return false
        return hid.sendReport(device, KeyboardReport.REPORT_ID.toInt(), report.toBytes())
    }

    fun releaseAllInputs() {
        sendMouse(MouseReport())
        sendKeyboard(KeyboardReport.release())
    }

    fun isAppRegistered(): Boolean = appRegistered.get()
    fun connectedDevice(): BluetoothDevice? = hostDevice
}

private fun BluetoothDevice.addressMasked(): String {
    val a = address ?: return "?"
    return if (a.length >= 5) "xx:xx:xx:xx:${a.takeLast(5)}" else "xx"
}
