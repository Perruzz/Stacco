package io.github.perruzz.stacco.ui.today

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.ui.components.StaccoCard
import io.github.perruzz.stacco.ui.theme.StaccoTheme

/** Text on the coffee button: dark on the gold of both themes. */
private val CoffeeInk = Color(0xFF3A2A00)

/**
 * A gentle card at the bottom of Today reminding that Stacco can be supported: it shows up rarely
 * (see [io.github.perruzz.stacco.domain.SupportRules]) and goes away for good on request.
 */
@Composable
fun SupportCard(onOpen: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    val c = StaccoTheme.colors
    val sway = rememberInfiniteTransition(label = "coffee")
    val tilt by sway.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "coffee-tilt",
    )
    StaccoCard(color = c.gold.copy(alpha = 0.12f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(c.gold.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Coffee,
                    contentDescription = null,
                    tint = c.lunch,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { rotationZ = tilt },
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.support_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = CoffeeInk),
        ) {
            Icon(Icons.Rounded.Coffee, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.support_action), textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onLater) { Text(stringResource(R.string.support_later)) }
            TextButton(onClick = onNever) {
                Text(stringResource(R.string.support_never), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
