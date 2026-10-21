package com.m3.app.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * True while the lock layer covers the app. The app stays composed underneath, so anything that acts on its
 * own (timers, keep-screen-on, dialogs in their own window) must check this.
 */
val LocalAppLocked = compositionLocalOf { false }
