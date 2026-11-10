package app.airinput.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.airinput.audio.AudioRecorder
import app.airinput.audio.AudioStreamSender
import app.airinput.bluetooth.HidDeviceController
import app.airinput.bluetooth.RfcommClient
import app.airinput.hid.KeyboardReport
import app.airinput.hid.MouseReport
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val apiSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
    val bluetoothEnabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val devices: List<BluetoothDevice> = emptyList(),
    val selectedAddress: String? = null,
    val hidProxyReady: Boolean = false,
    val hidRegistered: Boolean = false,
    val hidConnected: Boolean = false,
    val rfcommState: String = "Idle",
    val micActive: Boolean = false,
    val statusMessage: String = "",
    val sensitivity: Float = 1.2f,
)

class ConnectionViewModel(app: Application) : AndroidViewModel(app),
    HidDeviceController.Listener,
    RfcommClient.Listener {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val adapter: BluetoothAdapter? =
        (app.getSystemService(BluetoothManager::class.java))?.adapter

    private var hid: HidDeviceController? = null
    private var rfcomm: RfcommClient? = null
    private var recorder: AudioRecorder? = null
    private var sender: AudioStreamSender? = null
    private var mouseButtons: Int = 0
    private var dragging: Boolean = false

    init {
        refreshPermissionsAndAdapter()
    }

    fun refreshPermissionsAndAdapter() {
        val granted = hasBluetoothPermission()
        val enabled = adapter?.isEnabled == true
        _state.update {
            it.copy(
                permissionGranted = granted,
                bluetoothEnabled = enabled,
                apiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            )
        }
        if (granted && enabled) {
            ensureControllers()
            refreshBondedDevices()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        val ctx = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasMicPermission(): Boolean {
        val ctx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureControllers() {
        val ad = adapter ?: return
        if (hid == null) {
            hid = HidDeviceController(getApplication(), ad, this).also { it.start() }
        }
        if (rfcomm == null) {
            rfcomm = RfcommClient(ad, this)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        if (!hasBluetoothPermission()) return
        val bonded = adapter?.bondedDevices?.toList().orEmpty()
        _state.update { it.copy(devices = bonded) }
    }

    fun selectDevice(address: String) {
        _state.update { it.copy(selectedAddress = address) }
    }

    @SuppressLint("MissingPermission")
    fun connectSelected() {
        val address = _state.value.selectedAddress ?: run {
            _state.update { it.copy(statusMessage = "请先选择电脑设备") }
            return
        }
        val device = _state.value.devices.firstOrNull { it.address == address } ?: return
        viewModelScope.launch {
            // 桌面认证依赖 RFCOMM：先建 RFCOMM，成功后再连 HID，避免抢链路。
            _state.update { it.copy(statusMessage = "正在连接 RFCOMM（请确认电脑已「开启服务」）…") }
            rfcomm?.connect(device)
            // 给 SDP/认证一点时间；即便 RFCOMM 仍在重试，也随后尝试 HID。
            kotlinx.coroutines.delay(1500)
            _state.update {
                it.copy(
                    statusMessage = if (rfcomm?.isAuthenticated() == true) {
                        "RFCOMM 已认证，正在连接 HID…"
                    } else if (rfcomm?.isConnected() == true) {
                        "RFCOMM 已连接，等待认证 / 连接 HID…"
                    } else {
                        "RFCOMM 未完成，仍尝试连接 HID…"
                    },
                )
            }
            hid?.connect(device)
        }
    }

    fun disconnectAll() {
        stopMic()
        hid?.disconnect()
        rfcomm?.disconnect()
        hid?.releaseAllInputs()
    }

    fun onAppBackground() {
        stopMic()
        hid?.onEnterBackground()
    }

    fun onAppForeground() {
        hid?.onEnterForeground()
        refreshPermissionsAndAdapter()
    }

    fun setSensitivity(value: Float) {
        _state.update { it.copy(sensitivity = value.coerceIn(0.2f, 4f)) }
    }

    fun mouseMove(dx: Float, dy: Float) {
        val s = _state.value.sensitivity
        val report = MouseReport(
            buttons = mouseButtons,
            dx = (dx * s).roundToInt(),
            dy = (dy * s).roundToInt(),
            wheel = 0,
        )
        hid?.sendMouse(report)
    }

    fun mouseWheel(delta: Int) {
        hid?.sendMouse(MouseReport(buttons = mouseButtons, wheel = delta.coerceIn(-127, 127)))
    }

    fun setLeftButton(down: Boolean) {
        mouseButtons = if (down) mouseButtons or MouseReport.BUTTON_LEFT
        else mouseButtons and MouseReport.BUTTON_LEFT.inv()
        hid?.sendMouse(MouseReport(buttons = mouseButtons))
        if (!down) dragging = false
    }

    fun setRightButton(down: Boolean) {
        mouseButtons = if (down) mouseButtons or MouseReport.BUTTON_RIGHT
        else mouseButtons and MouseReport.BUTTON_RIGHT.inv()
        hid?.sendMouse(MouseReport(buttons = mouseButtons))
    }

    fun beginDrag() {
        dragging = true
        setLeftButton(true)
    }

    fun tap() {
        setLeftButton(true)
        setLeftButton(false)
    }

    fun doubleTap() {
        tap()
        tap()
    }

    fun keyDown(code: Int, modifiers: Int = 0) {
        hid?.sendKeyboard(KeyboardReport(modifiers = modifiers, keyCodes = listOf(code)))
    }

    fun keyUp() {
        hid?.sendKeyboard(KeyboardReport.release())
    }

    fun toggleMic() {
        if (_state.value.micActive) stopMic() else startMic()
    }

    fun startMic() {
        if (_state.value.micActive) return
        if (!hasMicPermission()) {
            _state.update { it.copy(statusMessage = "需要麦克风权限") }
            return
        }
        if (rfcomm?.isConnected() != true) {
            _state.update { it.copy(statusMessage = "RFCOMM 未连接，无法发送音频") }
            return
        }
        if (rfcomm?.isAuthenticated() != true) {
            _state.update { it.copy(statusMessage = "等待桌面 Challenge/HMAC 认证完成后再开麦") }
            return
        }
        val client = rfcomm ?: return
        val stream = AudioStreamSender(client)
        sender = stream
        stream.start()
        val rec = AudioRecorder(
            onFrame = { pcm -> stream.onPcmFrame(pcm) },
            onError = { msg -> _state.update { it.copy(statusMessage = msg, micActive = false) } },
        )
        recorder = rec
        rec.start()
        _state.update { it.copy(micActive = true, statusMessage = "按住说话中…") }
    }

    fun stopMic() {
        val wasActive = _state.value.micActive
        recorder?.stop()
        recorder = null
        sender?.stop()
        sender = null
        _state.update {
            it.copy(
                micActive = false,
                statusMessage = if (wasActive) "已松开，语音发送结束" else it.statusMessage,
            )
        }
    }

    override fun onCleared() {
        stopMic()
        hid?.close()
        rfcomm?.close()
        super.onCleared()
    }

    override fun onHidProxyReady(ready: Boolean) {
        _state.update { it.copy(hidProxyReady = ready) }
    }

    override fun onHidAppRegistered(registered: Boolean) {
        _state.update { it.copy(hidRegistered = registered, statusMessage = if (registered) "HID 已注册，请在电脑上连接本手机" else "HID 未注册") }
    }

    override fun onHidConnectionState(device: BluetoothDevice?, state: Int) {
        val connected = state == BluetoothProfile.STATE_CONNECTED
        _state.update { it.copy(hidConnected = connected) }
        if (!connected) {
            stopMic()
            hid?.releaseAllInputs()
        }
    }

    override fun onHidError(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    override fun onRfcommState(state: RfcommClient.State, detail: String) {
        _state.update { it.copy(rfcommState = state.name + if (detail.isNotEmpty()) " ($detail)" else "") }
        if (state == RfcommClient.State.Disconnected || state == RfcommClient.State.Error) {
            stopMic()
        }
        if (state == RfcommClient.State.Authenticated) {
            _state.update { it.copy(statusMessage = "RFCOMM 已认证，可以开麦") }
        }
    }

    override fun onRfcommError(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    override fun onRfcommAuthenticated() {
        _state.update { it.copy(statusMessage = "设备认证成功") }
    }

    override fun onRfcommAuthFailed(detail: String) {
        stopMic()
        _state.update { it.copy(statusMessage = "设备认证失败：$detail") }
    }
}
