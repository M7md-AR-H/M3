package com.m3.app.feature.fuel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.app.feature.coach.CoachRuntime
import com.m3.coach.MealEstimate
import com.m3.coach.MealReading
import com.m3.coach.MealVision
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MealPhotoState {
    data object Waiting : MealPhotoState
    data object Reading : MealPhotoState
    data class Ready(val estimate: MealEstimate) : MealPhotoState
    data class Failed(val message: String) : MealPhotoState
}

/** Reads one meal photo with the on-device model. The photo is only held for this; it's never stored. */
@HiltViewModel
class MealPhotoViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtime: CoachRuntime,
) : ViewModel() {
    private val _state = MutableStateFlow<MealPhotoState>(MealPhotoState.Waiting)
    val state: StateFlow<MealPhotoState> = _state.asStateFlow()

    private var job: Job? = null

    /** Where the camera app writes the photo. Inside the app's private storage, and deleted right after reading. */
    fun photoFile(): File = File(File(context.filesDir, PHOTO_DIR).apply { mkdirs() }, PHOTO_NAME)

    fun read(photo: Uri) {
        if (_state.value == MealPhotoState.Reading) return
        _state.value = MealPhotoState.Reading
        job = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    try {
                        smallPng(photo)
                    } finally {
                        photoFile().delete()
                    }
                }
                val raw = runtime.use { engine ->
                    engine.generate(prompt = MealVision.PROMPT, imagePng = image, maxOutputTokens = MealVision.MAX_OUTPUT_TOKENS).text
                }
                _state.value = when (val reading = MealVision.parse(raw)) {
                    is MealReading.Food -> MealPhotoState.Ready(reading.estimate)
                    MealReading.NotFood -> MealPhotoState.Failed("I can't see food in that photo. Try again, closer to the plate.")
                    MealReading.Unreadable -> MealPhotoState.Failed("I couldn't read that one. Try again, or add it by hand.")
                }
            } catch (e: CancellationException) {
                // Left the app or tapped Stop: nothing was saved. Offer another try rather than a stuck screen.
                if (_state.value == MealPhotoState.Reading) _state.value = MealPhotoState.Waiting
                throw e
            } catch (_: Exception) {
                // No details: they could contain prompt text.
                _state.value = MealPhotoState.Failed("The photo couldn't be read here. Try again, or add it by hand.")
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = MealPhotoState.Waiting
    }

    /** No photo was taken, or the owner wants another go. */
    fun reset() {
        photoFile().delete()
        _state.value = MealPhotoState.Waiting
    }

    private fun smallPng(photo: Uri): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, photo)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_SIDE) {
                val scale = MAX_IMAGE_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    override fun onCleared() {
        photoFile().delete()
    }

    private companion object {
        const val PHOTO_DIR = "meal"
        const val PHOTO_NAME = "photo.jpg"
        const val MAX_IMAGE_SIDE = 768
    }
}
