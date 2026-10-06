package com.pianofollower.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianofollower.audio.AudioViewModel

/** The three screens the app can be on. */
enum class AppRoute { Score, Settings, Tuner }

/** Widest the floating bar ever gets; on wider screens it stays centred. */
private const val BAR_MAX_WIDTH_DP = 620

/** The bar never changes height, so the score can reserve exactly this much. */
private val BAR_HEIGHT = 64.dp

/** Height plus its margins, i.e. how much of the bottom the score must give up. */
private val BAR_SPACE = 88.dp

/**
 * Lets the active screen hand its transport controls to the one floating bar.
 *
 * The bar belongs to the root because it has to survive screen switches and sit above
 * everything. Screens publish only their own controls — playback and position — rather
 * than each drawing a bar of its own.
 */
@Stable
class BottomBarHost {
    private var owner: Any? = null

    internal var transport: (@Composable RowScope.() -> Unit)? by mutableStateOf(null)
        private set

    fun setTransport(owner: Any, content: (@Composable RowScope.() -> Unit)?) {
        this.owner = owner
        transport = content
    }

    /**
     * Clears the bar only if [owner] is still the one on screen. Switching between the
     * notation and the scrolling score disposes one screen as the other publishes, and
     * an unconditional clear here would blank the bar the new screen just filled.
     */
    fun clearTransport(owner: Any) {
        if (this.owner === owner) {
            transport = null
        }
    }
}

val LocalBottomBarHost = staticCompositionLocalOf<BottomBarHost> {
    error("BottomBarHost is only available below AppRoot")
}

@Composable
fun AppRoot(settings: SettingsStore) {
    var route by rememberSaveable { mutableStateOf(AppRoute.Score) }
    var barVisible by rememberSaveable { mutableStateOf(true) }
    val bottomBarHost = remember { BottomBarHost() }

    CompositionLocalProvider(LocalBottomBarHost provides bottomBarHost) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val transport = bottomBarHost.transport
            val showBar = route == AppRoute.Score && transport != null && barVisible

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showBar) BAR_SPACE else 0.dp),
            ) {
                when (route) {
                    AppRoute.Score -> ScoreScreen(onOpenSettings = { route = AppRoute.Settings })

                    AppRoute.Settings -> SettingsScreen(
                        settings = settings,
                        onBack = { route = AppRoute.Score },
                        onOpenTuner = { route = AppRoute.Tuner },
                    )

                    AppRoute.Tuner -> {
                        val audioViewModel: AudioViewModel = viewModel()
                        CalibrationScreen(
                            audioViewModel = audioViewModel,
                            onBack = { route = AppRoute.Settings },
                        )
                    }
                }
            }

            if (route == AppRoute.Score && transport != null && !barVisible) {
                RevealHandle(
                    onReveal = { barVisible = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (route == AppRoute.Score && transport != null) {
                AnimatedVisibility(
                    visible = barVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { height -> height } + fadeIn(tween(120)),
                    exit = slideOutVertically { height -> height } + fadeOut(tween(120)),
                ) {
                    FloatingTransportBar(
                        onHide = { barVisible = false },
                        content = transport,
                    )
                }
            }
        }
    }
}

/**
 * The single control bar, centred at the bottom of the screen.
 *
 * It is deliberately short and narrow: a piece of glass laid over the score rather than
 * a strip that eats a band of staves. Its height is fixed, so switching between the
 * notation and the scrolling score never reflows the page. Dragging it down dismisses it.
 */
@Composable
private fun FloatingTransportBar(
    onHide: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = BAR_MAX_WIDTH_DP.dp)
            .fillMaxWidth(0.96f)
            .height(BAR_HEIGHT)
            .shadow(14.dp, RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 8f) onHide()
                }
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

/**
 * The only thing left on screen while the bar is dismissed: a small grab tab at the
 * bottom edge. Swiping it up (or tapping it) brings the bar back.
 */
@Composable
private fun RevealHandle(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -4f) onReveal()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .alpha(0.8f)
                .clickable(onClick = onReveal),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "显示控制栏",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "控制栏",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
