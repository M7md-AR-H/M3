package com.m3.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.Masthead
import com.m3.core.designsystem.theme.M3

/**
 * Drawn over the (hidden) app until the owner unlocks: opaque, swallows touches, and takes Back so
 * the screens underneath can't be driven while locked.
 */
@Composable
fun LockScreen(message: String?, onUnlock: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    GateLayout(
        kicker = "Locked",
        title = "Unlock to continue.",
        body = message ?: "Use your fingerprint or screen-lock PIN. Your data stays encrypted on this phone.",
        buttonText = "Unlock",
        onClick = onUnlock,
        modifier = Modifier
            .background(M3.colors.ground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    )
}

@Composable
fun StorageErrorScreen(onRetry: () -> Unit) {
    GateLayout(
        kicker = "Storage",
        title = "Couldn't open your data.",
        body = "M³ couldn't open its encrypted storage. Nothing was deleted. Try again; if it keeps " +
            "happening, restart the phone.",
        buttonText = "Try again",
        onClick = onRetry,
    )
}

/** Blank ground while the encrypted database opens (well under a second). */
@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize())
}

@Composable
private fun GateLayout(
    kicker: String,
    title: String,
    body: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
    ) {
        Masthead(issueLine = "Private")
        Kicker(text = kicker, color = M3.colors.accent)
        Text(text = title, style = M3.typography.display, color = M3.colors.ink)
        Text(text = body, style = M3.typography.body, color = M3.colors.muted)
        M3Button(text = buttonText, onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}
