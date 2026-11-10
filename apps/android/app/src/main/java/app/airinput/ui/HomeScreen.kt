package app.airinput.ui

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.airinput.hid.KeyboardReport
import app.airinput.ui.components.AirPanel
import app.airinput.ui.components.AirScaffold
import app.airinput.ui.components.GhostButton
import app.airinput.ui.components.PressKey
import app.airinput.ui.components.PrimaryButton
import app.airinput.ui.components.SectionLabel
import app.airinput.ui.components.StatusPill
import app.airinput.ui.components.StatusTone
import app.airinput.ui.components.performPressHaptic
import app.airinput.ui.theme.AirColors

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onSensitivity: (Float) -> Unit,
    onKeyDown: (Int, Int) -> Unit,
    onKeyUp: () -> Unit,
    onOpenTouchpad: () -> Unit,
    onOpenKeyboard: () -> Unit,
) {
    // 默认收起连接与蓝牙设备，点击入口再展开
    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedName = remember(state.devices, state.selectedAddress) {
        state.devices.firstOrNull { it.address == state.selectedAddress }?.name ?: "未选电脑"
    }
    val linkTone = when {
        state.hidConnected && state.rfcommState.contains("Authenticated", ignoreCase = true) -> StatusTone.Ok
        state.hidConnected || state.rfcommState.contains("Connected", ignoreCase = true) -> StatusTone.Active
        state.rfcommState.contains("Error", ignoreCase = true) -> StatusTone.Danger
        state.rfcommState.contains("Connecting", ignoreCase = true) -> StatusTone.Warn
        else -> StatusTone.Idle
    }
    val linkLabel = when {
        state.hidConnected && state.rfcommState.contains("Authenticated", ignoreCase = true) -> "已就绪"
        state.hidConnected -> "键鼠已连"
        state.rfcommState.contains("Connecting", ignoreCase = true) -> "连接中"
        state.rfcommState.contains("Error", ignoreCase = true) -> "异常 · 点此设置"
        else -> "未连接 · 点此设置"
    }

    fun down(code: Int, mod: Int = 0) = onKeyDown(code, mod)

    AirScaffold(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部紧凑入口：默认收起所有蓝牙/设备项，点此打开设置浮层
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(AirColors.Panel.copy(alpha = 0.9f))
                    .border(1.dp, AirColors.Line, RoundedCornerShape(999.dp))
                    .clickable { showSettingsSheet = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "AirInput · $selectedName",
                        style = MaterialTheme.typography.titleMedium,
                        color = AirColors.Mist,
                    )
                    Text(
                        state.statusMessage.ifEmpty { "点击选择电脑 / 连接 / 灵敏度设置" },
                        style = MaterialTheme.typography.bodySmall,
                        color = AirColors.Mute,
                        maxLines = 1,
                    )
                }
                StatusPill(linkLabel, linkTone)
            }

            // 主体：遥控器机身
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(34.dp),
                color = Color(0xFF141F29),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, AirColors.Line, RoundedCornerShape(34.dp))
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Row 1: 返回 (Esc) · 主页/隐藏 (⌘H) · 菜单 (⌘Space)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PressKey(
                            label = "返回",
                            sublabel = "Esc",
                            onDown = { down(KeyboardReport.KEY_ESC) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 54.dp,
                        )
                        PressKey(
                            label = "桌面",
                            sublabel = "⌘H",
                            onDown = { down(KeyboardReport.KEY_H, KeyboardReport.MOD_LMETA) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 54.dp,
                        )
                        PressKey(
                            label = "菜单",
                            sublabel = "⌘Space",
                            onDown = { down(KeyboardReport.KEY_SPACE, KeyboardReport.MOD_LMETA) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 54.dp,
                        )
                    }

                    // 圆盘四向键 + 中心确认 OK
                    RemoteDpad(
                        onUpKey = { down(KeyboardReport.KEY_UP) },
                        onDownKey = { down(KeyboardReport.KEY_DOWN) },
                        onLeftKey = { down(KeyboardReport.KEY_LEFT) },
                        onRightKey = { down(KeyboardReport.KEY_RIGHT) },
                        onOkKey = { down(KeyboardReport.KEY_ENTER) },
                        onRelease = onKeyUp,
                    )

                    // 常用功能键：退格 · 删除 · 确认
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PressKey(
                            label = "退格",
                            sublabel = "⌫ Back",
                            onDown = { down(KeyboardReport.KEY_BACKSPACE) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 58.dp,
                        )
                        PressKey(
                            label = "删除",
                            sublabel = "⌦ Del",
                            onDown = { down(KeyboardReport.KEY_DELETE) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 58.dp,
                        )
                        PressKey(
                            label = "确认",
                            sublabel = "Return",
                            onDown = { down(KeyboardReport.KEY_ENTER) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 58.dp,
                            emphasized = true,
                        )
                    }

                    // 遥控常用组合：切换窗口 · 播放/空格 · 撤销
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PressKey(
                            label = "切换窗口",
                            sublabel = "⌘Tab",
                            onDown = { down(KeyboardReport.KEY_TAB, KeyboardReport.MOD_LMETA) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 54.dp,
                        )
                        PressKey(
                            label = "播放/空格",
                            sublabel = "Space",
                            onDown = { down(KeyboardReport.KEY_SPACE) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 54.dp,
                        )
                        PressKey(
                            label = "撤销",
                            sublabel = "⌘Z",
                            onDown = { down(KeyboardReport.KEY_Z, KeyboardReport.MOD_LMETA) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 54.dp,
                        )
                    }

                    // 翻页/焦点
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PressKey(
                            label = "上一页",
                            sublabel = "PgUp",
                            onDown = { down(KeyboardReport.KEY_PAGE_UP) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 50.dp,
                        )
                        PressKey(
                            label = "下一页",
                            sublabel = "PgDn",
                            onDown = { down(KeyboardReport.KEY_PAGE_DOWN) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 50.dp,
                        )
                        PressKey(
                            label = "切焦点",
                            sublabel = "Tab",
                            onDown = { down(KeyboardReport.KEY_TAB) },
                            onUp = onKeyUp,
                            modifier = Modifier.weight(1f),
                            height = 50.dp,
                        )
                    }

                    // 模式与设置入口
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GhostButton(
                            text = "触控板",
                            onClick = onOpenTouchpad,
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = "组合键",
                            onClick = onOpenKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = "连接设置",
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 最底部：按住说话
                    MicRemoteButton(
                        active = state.micActive,
                        onDown = onMicDown,
                        onUp = onMicUp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }

        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                sheetState = sheetState,
                containerColor = AirColors.Panel,
                contentColor = AirColors.Mist,
            ) {
                SettingsSheetContent(
                    state = state,
                    onRefresh = onRefresh,
                    onSelect = onSelect,
                    onConnect = {
                        onConnect()
                        showSettingsSheet = false
                    },
                    onDisconnect = onDisconnect,
                    onSensitivity = onSensitivity,
                    onClose = { showSettingsSheet = false },
                )
            }
        }
    }
}

@Composable
private fun MicRemoteButton(
    active: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val holding = active || pressed

    LaunchedEffect(src) {
        src.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    performPressHaptic(view, true)
                    onDown()
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    performPressHaptic(view, false)
                    onUp()
                }
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (holding) 0.96f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "micScale",
    )
    val pulse = rememberInfiniteTransition(label = "micPulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micGlow",
    )

    val bg by animateColorAsState(
        targetValue = if (holding) AirColors.Teal else AirColors.Teal.copy(alpha = 0.16f),
        label = "micBg",
    )
    val border = if (holding) AirColors.Mist.copy(alpha = glowAlpha) else AirColors.Teal
    val fg = if (holding) AirColors.Ink else AirColors.Teal

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (holding) 2.dp.toPx() else 0f
            }
            .height(68.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(if (holding) 2.5.dp else 1.5.dp, border, RoundedCornerShape(999.dp))
            .clickable(interactionSource = src, indication = null, onClick = {})
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(if (holding) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (holding) AirColors.Danger else AirColors.Teal),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "按住说话",
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                )
                Text(
                    when {
                        active -> "正在收音发送 · 松开立即结束"
                        pressed -> "启动语音中 · 松开取消"
                        else -> "长按开启语音，松手立即停止"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (holding) AirColors.Ink.copy(alpha = 0.78f) else AirColors.Mute,
                )
            }
        }
    }
}

@Composable
private fun RemoteDpad(
    onUpKey: () -> Unit,
    onDownKey: () -> Unit,
    onLeftKey: () -> Unit,
    onRightKey: () -> Unit,
    onOkKey: () -> Unit,
    onRelease: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(248.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF223344), Color(0xFF172330), Color(0xFF101922)),
                ),
            )
            .border(1.5.dp, AirColors.Line, CircleShape)
            .padding(14.dp),
    ) {
        DpadArm(
            label = "▲",
            sub = "Up",
            onDown = onUpKey,
            onUp = onRelease,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 92.dp, height = 64.dp),
        )
        DpadArm(
            label = "▼",
            sub = "Down",
            onDown = onDownKey,
            onUp = onRelease,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 92.dp, height = 64.dp),
        )
        DpadArm(
            label = "◀",
            sub = "Left",
            onDown = onLeftKey,
            onUp = onRelease,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 64.dp, height = 92.dp),
        )
        DpadArm(
            label = "▶",
            sub = "Right",
            onDown = onRightKey,
            onUp = onRelease,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 64.dp, height = 92.dp),
        )

        val view = LocalView.current
        val okSource = remember { MutableInteractionSource() }
        val okPressed by okSource.collectIsPressedAsState()
        val okScale by animateFloatAsState(
            targetValue = if (okPressed) 0.91f else 1f,
            animationSpec = spring(stiffness = 650f),
            label = "okScale",
        )
        LaunchedEffect(okSource) {
            okSource.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> {
                        performPressHaptic(view, true)
                        onOkKey()
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        performPressHaptic(view, false)
                        onRelease()
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = okScale
                    scaleY = okScale
                }
                .size(92.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        if (okPressed) listOf(AirColors.Mist, AirColors.Teal)
                        else listOf(AirColors.Teal, AirColors.TealDim),
                    ),
                )
                .border(if (okPressed) 3.dp else 2.dp, AirColors.Mist.copy(alpha = if (okPressed) 0.7f else 0.25f), CircleShape)
                .clickable(interactionSource = okSource, indication = null, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OK", style = MaterialTheme.typography.titleLarge, color = AirColors.Ink)
                Text("Return", style = MaterialTheme.typography.bodySmall, color = AirColors.Ink.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun DpadArm(
    label: String,
    sub: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = 650f),
        label = "dpadScale",
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) AirColors.Teal.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.04f),
        label = "dpadBg",
    )
    LaunchedEffect(src) {
        src.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    performPressHaptic(view, true)
                    onDown()
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    performPressHaptic(view, false)
                    onUp()
                }
            }
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (pressed) AirColors.Teal else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource = src, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (pressed) AirColors.Teal else AirColors.Mist)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = AirColors.Mute)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun SettingsSheetContent(
    state: UiState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSensitivity: (Float) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("连接与设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            GhostButton(text = "完成", onClick = onClose, modifier = Modifier.size(width = 76.dp, height = 42.dp))
        }

        SectionLabel("操作")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "连接所选电脑",
                onClick = onConnect,
                enabled = state.selectedAddress != null,
                modifier = Modifier.weight(1.2f),
            )
            GhostButton(text = "断开", onClick = onDisconnect, modifier = Modifier.weight(0.7f))
            GhostButton(text = "刷新", onClick = onRefresh, modifier = Modifier.weight(0.7f))
        }

        SectionLabel("已配对电脑")
        if (state.devices.isEmpty()) {
            Text("暂无已配对设备，请先在系统蓝牙设置中配对电脑。", color = AirColors.Mute, style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.devices.forEach { device ->
                    val selected = device.address == state.selectedAddress
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(AirColors.PanelLift)
                            .border(
                                1.dp,
                                if (selected) AirColors.Teal else AirColors.Line,
                                MaterialTheme.shapes.medium,
                            )
                            .clickable { onSelect(device.address) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name ?: "未命名设备", style = MaterialTheme.typography.titleMedium)
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) StatusPill("已选", StatusTone.Active)
                    }
                }
            }
        }

        SectionLabel("触控板灵敏度  ${"%.1f".format(state.sensitivity)}×")
        Slider(
            value = state.sensitivity,
            onValueChange = onSensitivity,
            valueRange = 0.2f..4f,
            colors = SliderDefaults.colors(
                thumbColor = AirColors.Teal,
                activeTrackColor = AirColors.Teal,
                inactiveTrackColor = AirColors.Line,
            ),
        )

        SectionLabel("链路状态")
        AirPanel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DiagLine("蓝牙", if (state.bluetoothEnabled) "已开启" else "未开启")
                DiagLine("权限", if (state.permissionGranted) "已授予" else "缺失")
                DiagLine("HID", "${if (state.hidRegistered) "已注册" else "未注册"} · ${if (state.hidConnected) "已连接" else "未连接"}")
                DiagLine("RFCOMM", state.rfcommState)
                DiagLine("语音", if (state.micActive) "采集中" else "关闭")
                if (state.statusMessage.isNotEmpty()) {
                    DiagLine("提示", state.statusMessage)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AirColors.Mute, modifier = Modifier.weight(0.28f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = AirColors.Mist, modifier = Modifier.weight(0.72f), style = MaterialTheme.typography.bodyMedium)
    }
}
