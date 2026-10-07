package com.pianoscorefollower.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pianoscorefollower.app.audio.ListeningState
import com.pianoscorefollower.app.follower.FollowState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val METER_SEGMENTS = 28
private const val ONSET_FLASH_MS = 110L

private val PITCH_CLASS_NAMES = arrayOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
)

/**
 * Live microphone readout for the following screen.
 *
 * The panel sits over the notation, so it can be dismissed entirely: it slides off
 * the left edge leaving nothing but a small translucent arrow tab, and tapping that
 * tab brings it back. While it is on screen the header carries the status and
 * confidence; the meters stay folded away until the player taps the header.
 */
@Composable
fun MicrophonePanel(
    listening: ListeningState,
    follow: FollowState,
    modifier: Modifier = Modifier,
) {
    var hidden by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var onsetFlash by remember { mutableStateOf(false) }

    LaunchedEffect(follow.onsetPulse) {
        if (follow.onsetPulse > 0L) {
            onsetFlash = true
            delay(ONSET_FLASH_MS)
            onsetFlash = false
        }
    }

    val level by animateFloatAsState(
        targetValue = listening.normalizedLevel,
        animationSpec = tween(durationMillis = 70),
        label = "microphoneLevel",
    )
    val confidence by animateFloatAsState(
        targetValue = follow.confidence,
        animationSpec = tween(durationMillis = 120),
        label = "followConfidence",
    )

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !hidden,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            enter = slideInHorizontally(animationSpec = tween(180)) { -it } + fadeIn(tween(140)),
            exit = slideOutHorizontally(animationSpec = tween(160)) { -it } + fadeOut(tween(120)),
        ) {
            MicrophoneCard(
                listening = listening,
                follow = follow,
                expanded = expanded,
                confidence = confidence,
                level = level,
                onsetFlash = onsetFlash,
                onToggleExpanded = { expanded = !expanded },
                onHide = { hidden = true },
            )
        }

        if (hidden) {
            RevealTab(
                listening = listening,
                onReveal = { hidden = false },
                // Flush with the left edge and level with the collapsed panel it
                // stands in for, so it reads as the panel parked off-screen rather
                // than as a control floating in the middle of the score.
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 16.dp),
            )
        }
    }
}

/** The readout itself: header always visible, meters only when unfolded. */
@Composable
private fun MicrophoneCard(
    listening: ListeningState,
    follow: FollowState,
    expanded: Boolean,
    confidence: Float,
    level: Float,
    onsetFlash: Boolean,
    onToggleExpanded: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(
        // Spans the available width on a phone in portrait but never grows past a
        // comfortable reading measure on a tablet.
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            color = if (listening.isRunning) Accent else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (listening.isRunning) "正在跟谱" else "未开启跟谱",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (listening.isRunning) TextPrimary else TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = if (confidence >= 0.5f) Accent else TextSecondary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (expanded) "收起详情" else "展开详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onHide),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "隐藏跟谱面板",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Collapsed summary: just enough to tell what was heard and what was
            // expected, without the meters covering the staves.
            if (!expanded) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = compactFollowLine(follow),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    LevelMeter(level = level, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "电平 %.0f dBFS · 峰值 %.0f · %.1f kHz".format(
                                listening.levelDb,
                                listening.peakDb,
                                listening.sampleRate / 1000f,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "起音 ${listening.onsetCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (onsetFlash) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = buildFollowLine(follow),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * What is left on the left edge while the panel is dismissed: a narrow, mostly
 * transparent tab with an arrow. Tapping it slides the panel back in.
 */
@Composable
private fun RevealTab(
    listening: ListeningState,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .alpha(0.6f)
            .clickable(onClick = onReveal),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (listening.isRunning) Accent else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "展开跟谱面板",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun pitchNames(pitchClasses: List<Int>): String =
    pitchClasses
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { PITCH_CLASS_NAMES.getOrElse(it) { "?" } }
        ?: "—"

/** One line for the collapsed panel: heard note, expected note, match score. */
private fun compactFollowLine(follow: FollowState): String {
    if (!follow.isFollowing) return "识别 — · 目标 —"
    return "识别 %s · 目标 %s · 匹配 %d%% · 第 %d 小节".format(
        pitchNames(follow.topPitchClasses),
        pitchNames(follow.targetPitchClasses),
        (follow.bestSimilarity * 100).roundToInt(),
        follow.measure + 1,
    )
}

private fun buildFollowLine(follow: FollowState): String {
    val detected = pitchNames(follow.topPitchClasses)
    val target = pitchNames(follow.targetPitchClasses)
    val match = if (follow.isFollowing) "${(follow.bestSimilarity * 100).roundToInt()}%" else "—"
    val measure = if (follow.isFollowing) "${follow.measure + 1}" else "—"
    val tick = if (follow.isFollowing) follow.cursorTick.toString() else "—"
    return "识别 %s · 目标 %s · 匹配 %s\n第 %s 小节 · tick %s"
        .format(detected, target, match, measure, tick)
}

@Composable
private fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    val litSegments = (level * METER_SEGMENTS).roundToInt().coerceIn(0, METER_SEGMENTS)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(METER_SEGMENTS) { index ->
            val active = index < litSegments
            val color = when {
                !active -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                index >= METER_SEGMENTS - 3 -> Danger
                index >= METER_SEGMENTS - 8 -> Warning
                else -> Accent
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
