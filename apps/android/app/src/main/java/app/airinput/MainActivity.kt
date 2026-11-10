package app.airinput

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import app.airinput.ui.ConnectionViewModel
import app.airinput.ui.HomeScreen
import app.airinput.ui.KeyboardScreen
import app.airinput.ui.TouchpadScreen
import app.airinput.ui.theme.AirInputTheme
import app.airinput.ui.theme.AirColors

class MainActivity : ComponentActivity() {
    private val vm: ConnectionViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.refreshPermissionsAndAdapter()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()
        setContent {
            AirInputTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = AirColors.Ink) {
                    val state by vm.state.collectAsState()
                    var page by remember { mutableStateOf("home") }
                    when (page) {
                        "touchpad" -> TouchpadScreen(
                            onBack = { page = "home" },
                            onMove = vm::mouseMove,
                            onWheel = vm::mouseWheel,
                            onLeftDown = vm::setLeftButton,
                            onRightDown = vm::setRightButton,
                            onTap = vm::tap,
                            onDoubleTap = vm::doubleTap,
                            onDragStart = vm::beginDrag,
                        )
                        "keyboard" -> KeyboardScreen(
                            onBack = { page = "home" },
                            onKeyDown = vm::keyDown,
                            onKeyUp = vm::keyUp,
                        )
                        else -> HomeScreen(
                            state = state,
                            onRefresh = vm::refreshBondedDevices,
                            onSelect = vm::selectDevice,
                            onConnect = vm::connectSelected,
                            onDisconnect = vm::disconnectAll,
                            onMicDown = vm::startMic,
                            onMicUp = vm::stopMic,
                            onSensitivity = vm::setSensitivity,
                            onKeyDown = vm::keyDown,
                            onKeyUp = vm::keyUp,
                            onOpenTouchpad = { page = "touchpad" },
                            onOpenKeyboard = { page = "keyboard" },
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        vm.onAppBackground()
    }

    override fun onResume() {
        super.onResume()
        vm.onAppForeground()
        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) needed += Manifest.permission.BLUETOOTH_CONNECT
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)) needed += Manifest.permission.BLUETOOTH_SCAN
        }
        if (!granted(Manifest.permission.RECORD_AUDIO)) needed += Manifest.permission.RECORD_AUDIO
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            vm.refreshPermissionsAndAdapter()
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
