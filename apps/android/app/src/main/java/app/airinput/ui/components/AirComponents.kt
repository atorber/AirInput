package app.airinput.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.airinput.ui.theme.AirColors

fun performPressHaptic(view: android.view.View, pressed: Boolean) {
    runCatching {
        val code = if (pressed) {
            HapticFeedbackConstants.KEYBOARD_PRESS
        } else {
            HapticFeedbackConstants.KEYBOARD_RELEASE
        }
        view.performHapticFeedback(
            code,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }
}

@Composable
fun AirScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(AirColors.InkLift, AirColors.Ink, Color(0xFF0A1015)),
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        StatusTone.Ok -> AirColors.Ok
        StatusTone.Warn -> AirColors.Warn
        StatusTone.Danger -> AirColors.Danger
        StatusTone.Idle -> AirColors.Mute
        StatusTone.Active -> AirColors.Teal
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

enum class StatusTone { Ok, Warn, Danger, Idle, Active }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = AirColors.Mute,
    )
}

@Composable
fun AirPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = AirColors.Panel.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (active) AirColors.Teal.copy(alpha = 0.18f) else AirColors.PanelLift,
        label = "tileBg",
    )
    val border by animateColorAsState(
        if (active) AirColors.Teal.copy(alpha = 0.55f) else AirColors.Line,
        label = "tileBorder",
    )
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.dp, border, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) AirColors.Mist else AirColors.Mute,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = AirColors.Mute,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) AirColors.Teal else AirColors.Line)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) AirColors.Ink else AirColors.Mute,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, AirColors.Line, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) AirColors.Mist else AirColors.Mute,
        )
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("返回", color = AirColors.Teal) }
            Spacer(Modifier.width(4.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun PressKey(
    label: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    height: Dp = 56.dp,
    emphasized: Boolean = false,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
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
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 650f),
        label = "keyScale",
    )
    val bg by animateColorAsState(
        targetValue = when {
            pressed && emphasized -> AirColors.TealDim
            pressed -> AirColors.Teal.copy(alpha = 0.28f)
            emphasized -> AirColors.Teal
            else -> AirColors.PanelLift
        },
        label = "keyBg",
    )
    val border by animateColorAsState(
        targetValue = when {
            pressed -> AirColors.Teal
            emphasized -> AirColors.Teal
            else -> AirColors.Line
        },
        label = "keyBorder",
    )
    val fg = if (emphasized && !pressed) AirColors.Ink else AirColors.Mist
    val subFg = if (emphasized && !pressed) AirColors.Ink.copy(alpha = 0.72f) else AirColors.Mute

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (pressed) 2.dp.toPx() else 0f
            }
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(if (pressed) 1.5.dp else 1.dp, border, MaterialTheme.shapes.medium)
            .clickable(interactionSource = interactionSource, indication = null, onClick = {})
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
            if (!sublabel.isNullOrEmpty()) {
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = subFg)
            }
        }
    }
}
