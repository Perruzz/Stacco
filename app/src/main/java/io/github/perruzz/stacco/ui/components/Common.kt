package io.github.perruzz.stacco.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse

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

/**
 * Clickable with squish + haptic tick. [role] and [onClickLabel] tell TalkBack what the element is
 * and what a double tap does.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressed: Float = 0.95f,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    this
        .pressScale(interaction, pressed)
        .clickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
        ) {
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

/** The widest a screen's content gets: on a tablet or in landscape the cards stay readable. */
val MaxContentWidth = 720.dp

/** Centres a scrolling screen's content within [MaxContentWidth]; it still scrolls from anywhere. */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = MaxContentWidth)

/**
 * Items of the same width side by side, as many per line as the widest one allows: three tiles in
 * a row on a phone, two and one (or one per line) with large fonts on a narrow screen. A plain row
 * of weighted items would cut the longest label ("Set balan…", "Pianific…") instead.
 */
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        if (measurables.isEmpty()) return@Layout layout(width, 0) {}
        val widest = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val columns = ((width + gap) / (widest + gap)).coerceIn(1, measurables.size)
        val cell = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
        // Items of a line share its height, like the cells of a table (each measured once).
        val rows = measurables.chunked(columns)
        val lines = rows.map { row -> row.maxOf { it.maxIntrinsicHeight(cell) } }
        val remeasured = rows.mapIndexed { r, row -> row.map { it.measure(Constraints.fixed(cell, lines[r])) } }
        val height = lines.sum() + gap * (lines.size - 1)
        layout(width, height) {
            var y = 0
            remeasured.forEachIndexed { r, row ->
                row.forEachIndexed { i, placeable -> placeable.place(i * (cell + gap), y) }
                y += lines[r] + gap
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val label = @Composable {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (trailing == null) {
        Box(modifier.fillMaxWidth()) { label() }
        return
    }
    // The label keeps at least its longest word whole (no "REPERIBILIT/À" with large fonts on a
    // narrow phone); what follows gets the rest and wraps in it, a button keeps its size.
    Layout(
        content = {
            label()
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val (labelPart, trailingPart) = measurables
        val longestWord = labelPart.minIntrinsicWidth(constraints.maxHeight)
        val after = trailingPart.measure(Constraints(maxWidth = (width - longestWord - gap).coerceAtLeast(0)))
        val before = labelPart.measure(Constraints(maxWidth = (width - after.width - gap).coerceAtLeast(0)))
        val height = maxOf(before.height, after.height)
        layout(width, height) {
            before.place(0, (height - before.height) / 2)
            after.place(width - after.width, (height - after.height) / 2)
        }
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
    // TalkBack reads the value whole ("08:02"), not one rolling digit after the other.
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
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

// --- Segmented buttons that hold up with large fonts --------------------------------------------

/** A segmented row whose segments are all as tall as the tallest, so a label on two lines keeps it even. */
val SegmentRowModifier: Modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)

/** Less side padding than the default 12dp: short labels like "15 min" still fit on one line at 130%. */
val SegmentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

/**
 * A segment or button label that is never cut with very large fonts: several words go on two
 * centred lines, a single word ("Quarter") stays whole and shrinks a little only if it must.
 */
@Composable
fun SegmentLabel(text: String, style: TextStyle = LocalTextStyle.current) {
    val singleWord = text.trim().none { it.isWhitespace() }
    Text(
        text,
        style = style,
        textAlign = TextAlign.Center,
        maxLines = if (singleWord) 1 else 2,
        softWrap = !singleWord,
        autoSize = if (singleWord) TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize.takeOrElse { 14.sp }) else null,
    )
}
