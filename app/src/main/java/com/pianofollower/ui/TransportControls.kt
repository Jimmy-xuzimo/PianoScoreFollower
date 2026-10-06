package com.pianofollower.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/*
 * The transport controls are the Material 3 icon buttons rather than hand-rolled
 * clickable boxes, so the ripple, the disabled treatment, the 48dp touch target and
 * the accessibility semantics all come from the framework. Every call site here has a
 * touch target of at least 48dp, which is the documented minimum.
 */

/** A secondary transport action: restart, page step, stop. */
@Composable
internal fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // LocalContentColor already carries the disabled alpha from the button's
            // own colour scheme, so it is the right default.
            tint = tint ?: LocalContentColor.current,
        )
    }
}

/** What the primary transport button is showing right now. */
internal enum class PrimaryActionState { Play, Pause, Loading, Failed }

/**
 * The one filled button in the bar, so the primary action is unambiguous.
 *
 * Besides play/pause it doubles as the soundfont readout: a spinner while the
 * samples are still decoding, and a refresh action once decoding has failed. Both
 * keep the button tappable, which is what stops a warming-up player from becoming a
 * dead end.
 */
@Composable
internal fun RoundPrimaryButton(
    state: PrimaryActionState,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = if (state == PrimaryActionState.Failed) {
        IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        IconButtonDefaults.filledIconButtonColors()
    }

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
    ) {
        when (state) {
            PrimaryActionState.Loading -> CircularProgressIndicator(
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )

            PrimaryActionState.Failed -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "音源加载失败，点击重试",
            )

            PrimaryActionState.Play -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
            )

            PrimaryActionState.Pause -> Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = "暂停",
            )
        }
    }
}

/** Icon-only auto-page-turn switch; lit when the follower is allowed to turn pages. */
@Composable
internal fun AutoPageToggle(
    on: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    TransportIconButton(
        icon = Icons.Filled.Autorenew,
        contentDescription = if (on) "自动翻页已开启" else "自动翻页已关闭",
        onClick = onToggle,
        enabled = enabled,
        tint = if (on) MaterialTheme.colorScheme.primary else null,
    )
}
