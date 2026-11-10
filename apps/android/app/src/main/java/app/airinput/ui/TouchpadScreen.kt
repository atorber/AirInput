package app.airinput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.airinput.ui.components.AirScaffold
import app.airinput.ui.components.GhostButton
import app.airinput.ui.components.TopBar
import app.airinput.ui.theme.AirColors

@Composable
fun TouchpadScreen(
    onBack: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onWheel: (Int) -> Unit,
    onLeftDown: (Boolean) -> Unit,
    onRightDown: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDragStart: () -> Unit,
) {
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var fingerDownAt by remember { mutableLongStateOf(0L) }
    var moved by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }

    AirScaffold(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopBar(title = "触控板", onBack = onBack)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF15202A), Color(0xFF0E171F), Color(0xFF152832)),
                            start = Offset.Zero,
                            end = Offset(900f, 1400f),
                        ),
                    )
                    .border(
                        1.dp,
                        if (touching) AirColors.Teal.copy(alpha = 0.55f) else AirColors.Line,
                        RoundedCornerShape(28.dp),
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            touching = true
                            fingerDownAt = System.currentTimeMillis()
                            moved = false
                            var dragging = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (event.changes.size >= 2) {
                                    val dy = event.changes.take(2).map { it.positionChange().y }.average().toFloat()
                                    if (kotlin.math.abs(dy) > 0.5f) {
                                        onWheel((-dy / 8f).toInt().coerceIn(-7, 7))
                                        moved = true
                                    }
                                } else {
                                    val delta = change.positionChange()
                                    if (kotlin.math.abs(delta.x) > 0.2f || kotlin.math.abs(delta.y) > 0.2f) {
                                        onMove(delta.x, delta.y)
                                        moved = true
                                        if (!dragging && System.currentTimeMillis() - fingerDownAt > 350) {
                                            dragging = true
                                            onDragStart()
                                        }
                                    }
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })

                            touching = false
                            onLeftDown(false)
                            if (!moved) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapAt < 280) {
                                    onDoubleTap()
                                    lastTapAt = 0
                                } else {
                                    onTap()
                                    lastTapAt = now
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        "触控区",
                        style = MaterialTheme.typography.titleLarge,
                        color = AirColors.Mist.copy(alpha = 0.9f),
                    )
                    Text(
                        "单指移动 · 轻点单击 · 双击 · 长按拖动\n双指上下滚动",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AirColors.Mute,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "左键：轻点触控区",
                    modifier = Modifier.weight(1.2f).padding(vertical = 18.dp),
                    color = AirColors.Mute,
                    style = MaterialTheme.typography.bodyMedium,
                )
                GhostButton(
                    text = "右键",
                    onClick = {
                        onRightDown(true)
                        onRightDown(false)
                    },
                    modifier = Modifier.weight(0.8f),
                )
            }
        }
    }
}
