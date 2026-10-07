package com.pianoscorefollower.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianoscorefollower.app.audio.AudioViewModel
import com.pianoscorefollower.app.audio.CalibrationPhase
import com.pianoscorefollower.app.audio.CalibrationState
import kotlin.math.abs
import kotlin.math.roundToInt

private val PITCH_CLASS_LABELS = arrayOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    audioViewModel: AudioViewModel,
    onBack: () -> Unit,
) {
    val calibration by audioViewModel.calibrationState.collectAsStateWithLifecycle()
    val listening by audioViewModel.listeningState.collectAsStateWithLifecycle()

    val start: () -> Unit = { audioViewModel.startCalibration() }
    val currentStart by rememberUpdatedState(start)

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) currentStart()
        else audioViewModel.onPermissionDenied()
    }

    // The tuner owns the microphone only while it is on screen.
    DisposableEffect(Unit) {
        if (audioViewModel.hasRecordPermission()) audioViewModel.startCalibration()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
        onDispose { audioViewModel.stopCalibration() }
    }

    LaunchedEffect(Unit) {
        if (!audioViewModel.hasRecordPermission()) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Insets are already consumed by the root column that hosts the tab bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "音准校准",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "先弹中央 C 校准，之后弹哪个音就显示哪个音",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { audioViewModel.resetCalibration() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新校准")
                    }
                    IconButton(
                        onClick = {
                            if (calibration.isRunning) audioViewModel.stopCalibration()
                            else start()
                        }
                    ) {
                        Icon(
                            imageVector = if (calibration.isRunning) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (calibration.isRunning) "停止" else "开始",
                            tint = if (calibration.isRunning) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(calibration = calibration, levelDb = listening.levelDb)

            NoteReadout(calibration = calibration)

            CentsMeter(cents = calibration.centsDeviation, active = calibration.isSounding)

            PitchClassRing(active = calibration.pitchClass)

            CalibrationInfo(calibration = calibration)

            if (calibration.hasCalibration) {
                OutlinedButton(onClick = { audioViewModel.resetCalibration() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重新校准")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(calibration: CalibrationState, levelDb: Float) {
    val message = calibration.message ?: when {
        !calibration.isRunning -> "麦克风未开启"
        calibration.phase == CalibrationPhase.AwaitingReference -> "请弹奏中央 C，校准你的钢琴"
        calibration.isSounding -> "正在识别…"
        else -> "请弹奏任意一个音"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            color = if (calibration.isRunning) Accent else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (calibration.isRunning) "正在聆听" else "已停止",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "电平 %.0f dBFS".format(levelDb),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (calibration.isSounding) TextPrimary else TextSecondary,
            )
            if (calibration.phase == CalibrationPhase.AwaitingReference && calibration.collected > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(calibration.referenceSamples) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .background(
                                    color = if (index < calibration.collected) Accent
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(2.dp),
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteReadout(calibration: CalibrationState) {
    val hasNote = calibration.noteName.isNotEmpty() && calibration.midiNote >= 0
    val deviation = calibration.centsDeviation

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (hasNote) calibration.noteName else "—",
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = when {
                !hasNote -> TextSecondary
                abs(deviation) <= 5f -> Accent
                abs(deviation) <= 20f -> Warning
                else -> Danger
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hasNote) {
                "%.1f Hz · %s %.0f 音分".format(
                    calibration.frequencyHz,
                    if (deviation >= 0f) "+" else "−",
                    abs(deviation),
                )
            } else {
                "等待检测到音高"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Needle tuner over a ±50 cent window; the centre is equal temperament. */
@Composable
private fun CentsMeter(cents: Float, active: Boolean) {
    val clamped = cents.coerceIn(-50f, 50f)
    val color = when {
        !active -> MaterialTheme.colorScheme.outline
        abs(cents) <= 5f -> Accent
        abs(cents) <= 20f -> Warning
        else -> Danger
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            val trackWidth = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            )
            // Centre reference and the ±20 cent "in tune enough" band.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
            val fraction = (clamped + 50f) / 100f
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = trackWidth * fraction - 2.dp)
                    .width(4.dp)
                    .height(40.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("−50", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = TextSecondary)
            Text("0", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = TextSecondary)
            Text("+50", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = TextSecondary)
        }
    }
}

@Composable
private fun PitchClassRing(active: Int) {
    val rows = listOf(0 until 6, 6 until 12)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { range ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                range.forEach { pitchClass ->
                    val selected = pitchClass == active
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        color = if (selected) Accent else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) Accent else MaterialTheme.colorScheme.outline,
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = PITCH_CLASS_LABELS[pitchClass],
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color(0xFF04121F) else TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationInfo(calibration: CalibrationState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoRow("校准状态", if (calibration.hasCalibration) "已校准" else "未校准")
            InfoRow(
                "整体偏移",
                if (calibration.hasCalibration) {
                    "%s%.0f 音分".format(
                        if (calibration.offsetCents >= 0f) "+" else "−",
                        abs(calibration.offsetCents),
                    )
                } else {
                    "—"
                },
            )
            InfoRow("音高置信度", if (calibration.isSounding) "${(calibration.confidence * 100).roundToInt()}%" else "—")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
