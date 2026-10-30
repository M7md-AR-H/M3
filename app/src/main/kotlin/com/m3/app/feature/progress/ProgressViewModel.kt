package com.m3.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.core.data.BodyRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.model.BodyPart
import com.m3.core.model.Measurement
import com.m3.core.model.TrendPoint
import com.m3.core.model.WeightEntry
import com.m3.core.model.WeightTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The latest value of a body part, and its change since the first one. */
data class PartSummary(val part: BodyPart, val cm: Double, val changeCm: Double?)

data class ProgressState(
    val points: List<TrendPoint> = emptyList(),
    val weeklyChangeKg: Double? = null,
    val parts: List<PartSummary> = emptyList(),
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val body: BodyRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    val state: StateFlow<ProgressState> =
        combine(body.observeWeights(), body.observeMeasurements()) { weights, measurements ->
            val points = WeightTrend.points(weights)
            ProgressState(points, WeightTrend.weeklyChangeKg(points), summarise(measurements))
        }
            .catch { emit(ProgressState()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProgressState())

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    /** Today's weigh-in. The profile follows the trend, so calorie targets stay current without a day's noise. */
    fun logWeight(kg: Double, onSaved: () -> Unit = {}) = save(onSaved) {
        body.saveWeight(WeightEntry(LocalDate.now(), kg))
        val trend = WeightTrend.points(body.observeWeights().first()).last().trendKg
        profiles.observe().first()?.let { profiles.save(it.copy(weightKg = (trend * 10).roundToInt() / 10.0)) }
    }

    fun saveMeasurements(values: Map<BodyPart, Double>, onSaved: () -> Unit = {}) = save(onSaved) {
        val today = LocalDate.now()
        values.forEach { (part, cm) -> body.saveMeasurement(Measurement(today, part, cm)) }
    }

    private fun save(onSaved: () -> Unit, block: suspend () -> Unit) {
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                block()
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
            }
        }
    }

    private fun summarise(measurements: List<Measurement>): List<PartSummary> =
        BodyPart.entries.mapNotNull { part ->
            val history = measurements.filter { it.part == part }.sortedBy { it.date }
            val latest = history.lastOrNull() ?: return@mapNotNull null
            PartSummary(part, latest.cm, (latest.cm - history.first().cm).takeIf { history.size > 1 })
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
