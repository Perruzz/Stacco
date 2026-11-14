package io.github.perruzz.stacco.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Squishy press feedback: everything tappable in Stacco gives a little under the finger. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.94f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Clickable with squish + haptic tick. */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressed: Float = 0.95f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    this
        .pressScale(interaction, pressed)
        .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        }
}

@Composable
fun StaccoCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    border: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = if (onClick != null) modifier.bouncyClickable(pressed = 0.98f, onClick = onClick) else modifier,
        shape = shape,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) else null,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .background(color, CircleShape)
            .then(if (onClick != null) Modifier.bouncyClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = style, color = contentColor, maxLines = 1)
    }
}

/** Label on the left, value on the right; optional supporting line. */
@Composable
fun InfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasized: Boolean = false,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, tint = iconTint, container = iconTint.copy(alpha = 0.14f), size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        RollingText(
            text = value,
            style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = valueColor,
        )
    }
}

/** Text whose characters roll like an odometer when they change. */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    countingDown: Boolean = false,
) {
    Row(modifier) {
        text.forEachIndexed { index, char ->
            androidx.compose.runtime.key(index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val up = if (countingDown) targetState < initialState else targetState > initialState
                        val dir = if (up) 1 else -1
                        (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { dir * it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { -dir * it / 3 } + fadeOut(tween(140))) using
                            SizeTransform(clip = true)
                    },
                    // Digits roll inside their own slot, like an odometer.
                    modifier = Modifier.clipToBounds(),
                    label = "rolling",
                ) { c ->
                    Text(c.toString(), style = style, color = color)
                }
            }
        }
    }
}

@Composable
fun BigPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    trailing: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.pressScale(interaction, 0.93f),
        shape = RoundedCornerShape(28.dp),
        color = if (enabled) container else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (enabled) 6.dp else 0.dp,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f, fill = false))
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    trailing,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
    }
}
