package com.m3.app.feature.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.app.data.PlanRepository
import com.m3.core.data.FoodRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.model.DayTotals
import com.m3.core.model.FoodEntry
import com.m3.core.model.Nutrition
import com.m3.core.model.NutritionTargets
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface FuelState {
    data object Loading : FuelState
    data object Failed : FuelState
    data class Ready(val targets: NutritionTargets?, val eaten: DayTotals, val entries: List<FoodEntry>) : FuelState
}

@HiltViewModel
class FuelViewModel @Inject constructor(
    private val food: FoodRepository,
    private val profiles: ProfileRepository,
    private val plans: PlanRepository,
) : ViewModel() {
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    fun day(date: LocalDate): Flow<FuelState> =
        combine(profiles.observe(), plans.observeWeek(), food.observeDay(date)) { profile, plan, entries ->
            val targets = profile?.let { Nutrition.targets(it, Year.now().value, plan.sessions.size) }
            FuelState.Ready(targets, Nutrition.totals(entries), entries) as FuelState
        }.catch { emit(FuelState.Failed) }

    fun save(entry: FoodEntry, onSaved: () -> Unit = {}) {
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                food.save(entry)
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
            }
        }
    }

    fun remove(entry: FoodEntry) {
        viewModelScope.launch {
            try {
                food.delete(entry.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
            }
        }
    }
}
