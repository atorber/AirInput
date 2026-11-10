package app.airinput.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.airinput.hid.KeyboardReport
import app.airinput.ui.components.AirPanel
import app.airinput.ui.components.AirScaffold
import app.airinput.ui.components.PressKey
import app.airinput.ui.components.SectionLabel
import app.airinput.ui.components.TopBar
import app.airinput.ui.theme.AirColors

@Composable
fun KeyboardScreen(
    onBack: () -> Unit,
    onKeyDown: (Int, Int) -> Unit,
    onKeyUp: () -> Unit,
) {
    val cmd = KeyboardReport.MOD_LMETA
    val shift = KeyboardReport.MOD_LSHIFT

    AirScaffold(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TopBar(title = "更多快捷键与组合键", onBack = onBack)
            Text(
                "对应 macOS Command (⌘) / Windows 徽标键常用操作。按住生效，松开释放。",
                style = MaterialTheme.typography.bodyMedium,
                color = AirColors.Mute,
            )

            AirPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("编辑与剪贴板")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PressKey("复制", { onKeyDown(KeyboardReport.KEY_C, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘C")
                        PressKey("粘贴", { onKeyDown(KeyboardReport.KEY_V, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘V")
                        PressKey("全选", { onKeyDown(KeyboardReport.KEY_A, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘A")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PressKey("撤销", { onKeyDown(KeyboardReport.KEY_Z, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘Z")
                        PressKey("重做", { onKeyDown(KeyboardReport.KEY_Z, cmd or shift) }, onKeyUp, Modifier.weight(1f), sublabel = "⇧⌘Z")
                        PressKey("查找", { onKeyDown(KeyboardReport.KEY_F, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘F")
                    }
                }
            }

            AirPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("窗口与系统")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PressKey("Spotlight", { onKeyDown(KeyboardReport.KEY_SPACE, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘Space")
                        PressKey("切应用", { onKeyDown(KeyboardReport.KEY_TAB, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘Tab")
                        PressKey("关标签", { onKeyDown(KeyboardReport.KEY_W, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘W")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PressKey("隐藏", { onKeyDown(KeyboardReport.KEY_H, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘H")
                        PressKey("退出", { onKeyDown(KeyboardReport.KEY_Q, cmd) }, onKeyUp, Modifier.weight(1f), sublabel = "⌘Q")
                        PressKey("上下文菜单", { onKeyDown(KeyboardReport.KEY_APPLICATION, 0) }, onKeyUp, Modifier.weight(1f), sublabel = "Menu")
                    }
                }
            }

            AirPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("导航与标准键")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PressKey("Home", { onKeyDown(KeyboardReport.KEY_HOME, 0) }, onKeyUp, Modifier.weight(1f))
                        PressKey("End", { onKeyDown(KeyboardReport.KEY_END, 0) }, onKeyUp, Modifier.weight(1f))
                        PressKey("Tab", { onKeyDown(KeyboardReport.KEY_TAB, 0) }, onKeyUp, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PressKey("Esc", { onKeyDown(KeyboardReport.KEY_ESC, 0) }, onKeyUp, Modifier.weight(1f))
                        PressKey("Delete ⌦", { onKeyDown(KeyboardReport.KEY_DELETE, 0) }, onKeyUp, Modifier.weight(1f))
                        PressKey("Return", { onKeyDown(KeyboardReport.KEY_ENTER, 0) }, onKeyUp, Modifier.weight(1f), emphasized = true)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
