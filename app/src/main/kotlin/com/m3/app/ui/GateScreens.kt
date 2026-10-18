package com.m3.app.ui

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
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.Masthead
import com.m3.core.designsystem.theme.M3

/** Shown instead of any content until the owner unlocks. */
@Composable
fun LockScreen(message: String?, onUnlock: () -> Unit) {
    GateLayout(
        kicker = "Locked",
        title = "Unlock to continue.",
        body = message ?: "Use your fingerprint or screen-lock PIN. Your data stays encrypted on this phone.",
        buttonText = "Unlock",
        onClick = onUnlock,
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
private fun GateLayout(kicker: String, title: String, body: String, buttonText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(M3.spacing.l),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        Masthead(issueLine = "Private")
        Kicker(text = kicker, color = M3.colors.accent)
        Text(text = title, style = M3.typography.display, color = M3.colors.ink)
        Text(text = body, style = M3.typography.body, color = M3.colors.muted)
        M3Button(text = buttonText, onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}
