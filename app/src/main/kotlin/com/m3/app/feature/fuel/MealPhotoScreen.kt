package com.m3.app.feature.fuel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3

/** Photo → the on-device model's estimate → the Add food form, where the owner checks it before saving. */
@Composable
fun MealPhotoScreen(onBack: () -> Unit, viewModel: MealPhotoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    (state as? MealPhotoState.Ready)?.let { ready ->
        FoodEntryScreen(onBack = onBack, estimate = ready.estimate)
        return
    }

    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) viewModel.read(photoUri(context, viewModel)) else viewModel.reset()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::read)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
            TextAction(text = "← Fuel", onClick = onBack)
            Text(text = "Snap meal", style = M3.typography.display, color = M3.colors.ink)
            Spacer(Modifier.size(M3.spacing.xs))
            DoubleRule()
        }
        when (val s = state) {
            MealPhotoState.Reading -> {
                Text(
                    text = "Looking at your plate…",
                    style = M3.typography.serifBody.copy(fontStyle = FontStyle.Italic),
                    color = M3.colors.muted,
                )
                Skeleton()
                M3OutlineButton(text = "Stop", onClick = viewModel::stop, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                (s as? MealPhotoState.Failed)?.let {
                    Text(text = it.message, style = M3.typography.body, color = M3.colors.accent)
                }
                Text(
                    text = "Get the whole plate in the shot, from above. The photo is read on this phone and isn't kept.",
                    style = M3.typography.body,
                    color = M3.colors.muted,
                )
                M3Button(
                    text = "Take a photo",
                    onClick = {
                        viewModel.reset()
                        camera.launch(photoUri(context, viewModel))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                M3OutlineButton(
                    text = "Choose a photo",
                    onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun photoUri(context: android.content.Context, viewModel: MealPhotoViewModel) =
    FileProvider.getUriForFile(context, "${context.packageName}.files", viewModel.photoFile())
