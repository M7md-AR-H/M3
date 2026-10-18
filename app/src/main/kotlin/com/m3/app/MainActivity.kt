package com.m3.app

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.OnboardingScreen
import com.m3.app.ui.LoadingScreen
import com.m3.app.ui.LockScreen
import com.m3.app.ui.M3App
import com.m3.app.ui.StorageErrorScreen
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.theme.M3Theme
import com.m3.core.security.AppLock
import com.m3.core.security.UnlockPrompt
import com.m3.core.security.UnlockResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var appLock: AppLock

    private lateinit var unlockPrompt: UnlockPrompt

    private val lockMessage = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        unlockPrompt = UnlockPrompt(this)
        enableEdgeToEdge()
        setContent {
            val config by viewModel.config.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()
            val startup by viewModel.startupState.collectAsStateWithLifecycle()
            val dark = when (config.theme.mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Keep system bar icons legible when the app theme differs from the system theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            M3Theme(config.theme) {
                // While locked, the app stays composed (so a re-lock keeps unsaved edits and navigation) but is
                // hidden under the opaque lock screen and removed from the accessibility tree.
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) {
                        when (val state = startup) {
                            StartupState.Failed -> StorageErrorScreen(onRetry = viewModel::retryStartup)
                            StartupState.Loading -> LoadingScreen()
                            is StartupState.Ready -> if (state.onboardingCompleted) M3App(config) else OnboardingScreen()
                        }
                    }
                    if (locked) LockScreen(message = lockMessage.value, onUnlock = ::requestUnlock, onBack = { moveTaskToBack(true) })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    override fun onResume() {
        super.onResume()
        if (appLock.claimAutoPrompt()) requestUnlock()
    }

    override fun onStop() {
        super.onStop()
        appLock.onBackground()
    }

    override fun onDestroy() {
        unlockPrompt.cancel()
        super.onDestroy()
    }

    private fun requestUnlock() {
        unlockPrompt.show(title = "Unlock M³", subtitle = "Your data stays on this phone") { result ->
            lockMessage.value = when (result) {
                UnlockResult.Success -> {
                    appLock.unlock()
                    null
                }
                is UnlockResult.Failed -> "${result.message.trimEnd('.')}. Tap Unlock to try again."
                UnlockResult.NoScreenLock ->
                    "This phone has no screen lock. Set a PIN, pattern or password in Android Settings → " +
                        "Security, then tap Unlock. M³ won't open without one, to keep your data private."
            }
        }
    }
}
