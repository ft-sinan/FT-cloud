package com.ftcloud.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ftcloud.app.ui.theme.LocalAnimationsEnabled
import com.ftcloud.app.ui.theme.localSelectionHaptic
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BlobShape(
    private val lobes: Int = 9,
    private val wave: Float = 0.13f,
    private val phase: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        val radiusX = size.width / 2f / (1f + wave)
        val radiusY = size.height / 2f / (1f + wave)
        val path = Path()
        val steps = 144
        for (step in 0..steps) {
            val theta = (step.toFloat() / steps) * (2f * PI.toFloat())
            val scale = 1f + wave * cos(lobes * theta + phase)
            val x = centreX + radiusX * scale * cos(theta)
            val y = centreY + radiusY * scale * sin(theta)
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is BlobShape && lobes == other.lobes && wave == other.wave && phase == other.phase

    override fun hashCode(): Int = listOf(lobes, wave, phase).hashCode()
}

@Composable
fun MorphingShapeBox(
    modifier: Modifier = Modifier,
    color: Color,
    lobes: Int = 9,
    wave: Float = 0.13f,
    spinMillis: Int = 18_000,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val transition = rememberInfiniteTransition(label = "morphingShape")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(spinMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shapePhase",
    )
    val animatedWave by transition.animateFloat(
        initialValue = wave * 0.72f,
        targetValue = wave * 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shapeWave",
    )
    Box(
        modifier = modifier
            .clip(
                BlobShape(
                    lobes = lobes,
                    wave = if (animationsEnabled) animatedWave else wave,
                    phase = if (animationsEnabled) animatedPhase else 0f,
                )
            )
            .background(color),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun CloudMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    MorphingShapeBox(
        modifier = modifier.size(size),
        color = containerColor,
        lobes = 9,
        wave = 0.14f,
        spinMillis = 16_000,
    ) {
        Icon(
            imageVector = Icons.Filled.Cloud,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** FT Franchise Shield + Lime Orbit brand tile matching FT Vault's launcher emblem. */
@Composable
fun FTCloudShieldMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    containerColor: Color = Color(0xFF173F35),
    shieldColor: Color = Color(0xFFF2EFE3),
    accentColor: Color = Color(0xFFC8F169),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val sx = this.size.width / 108f
            val sy = this.size.height / 108f
            fun point(x: Float, y: Float) = Offset(x * sx, y * sy)

            drawCircle(
                color = accentColor.copy(alpha = 0.14f),
                radius = 26f * sx,
                center = point(88f, 15f),
            )

            val shield = Path().apply {
                var pt = point(54f, 22.2f); moveTo(pt.x, pt.y)
                pt = point(78.5f, 29.1f); lineTo(pt.x, pt.y)
                pt = point(78.5f, 48.8f); lineTo(pt.x, pt.y)
                var c1 = point(78.5f, 64.3f); var c2 = point(68.7f, 75.5f); var e = point(54f, 83.2f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                c1 = point(39.3f, 75.5f); c2 = point(29.5f, 64.3f); e = point(29.5f, 48.8f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                pt = point(29.5f, 29.1f); lineTo(pt.x, pt.y)
                close()
            }
            drawPath(shield, shieldColor)

            val inner = Path().apply {
                var pt = point(54f, 29.9f); moveTo(pt.x, pt.y)
                pt = point(70.7f, 35.1f); lineTo(pt.x, pt.y)
                pt = point(70.7f, 48.8f); lineTo(pt.x, pt.y)
                var c1 = point(70.7f, 60f); var c2 = point(63.8f, 68.6f); var e = point(54f, 73.8f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                c1 = point(44.2f, 68.6f); c2 = point(37.3f, 60f); e = point(37.3f, 48.8f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                pt = point(37.3f, 35.1f); lineTo(pt.x, pt.y)
                close()
            }
            drawPath(
                path = inner,
                color = containerColor,
                style = Stroke(width = 3.6f * sx, join = StrokeJoin.Round),
            )

            drawCircle(accentColor, radius = 11.8f * sx, center = point(54f, 54f))
            drawCircle(containerColor, radius = 4.2f * sx, center = point(54f, 54f))
        }
    }
}

@Composable
fun Modifier.springPress(
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val animationsEnabled = LocalAnimationsEnabled.current
    val haptic = localSelectionHaptic()
    val targetScale = when {
        pressed -> 0.94f
        focused -> 1.03f
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.62f, stiffness = 520f)
        } else {
            snap()
        },
        label = "springPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
        ) {
            haptic()
            onClick()
        }
}

@Composable
fun HeaderCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(containerColor)
            .springPress(CircleShape, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun ExpressiveTopBar(
    modifier: Modifier = Modifier,
    showMark: Boolean = true,
    content: @Composable RowScope.() -> Unit,
    pillActions: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showMark) {
                        CloudMark(size = 40.dp)
                        Spacer(Modifier.width(9.dp))
                    }
                    content()
                    pillActions()
                }
            }
            trailing()
        }
    }
}

@Composable
fun ExpressiveHeroHeader(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    includeStatusPadding: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
    ) {
        MorphingShapeBox(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 52.dp, y = (-92).dp)
                .size(250.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
            lobes = 12,
            wave = 0.19f,
            spinMillis = 30_000,
        )
        Column(
            modifier = Modifier
                .then(if (includeStatusPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onBack != null) {
                    HeaderCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                        onClick = onBack,
                    )
                }
                Spacer(Modifier.weight(1f))
                actions()
            }
            if (onBack != null) {
                Spacer(Modifier.height(10.dp))
            }
            MorphingShapeBox(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                lobes = 9,
                wave = 0.15f,
                spinMillis = 20_000,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(31.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        }
    }
}

@Composable
fun ExpressiveChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
    }
}

@Composable
fun ExpressiveStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
