package com.m3.app.feature.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.app.data.PlanRepository
import com.m3.core.data.FoodRepository
import com.m3.core.data.GroceryRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.data.StoredGroceryItem
import com.m3.core.model.Grocery
import com.m3.core.model.GroceryPlanner
import com.m3.core.model.Nutrition
import com.m3.core.model.NutritionTargets
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GroceryState {
    data object Loading : GroceryState
    data object Failed : GroceryState

    /** [hasTargets]: a profile exists, so "Refresh from my targets" can work. */
    data class Ready(val items: List<StoredGroceryItem>, val hasTargets: Boolean) : GroceryState
}

/**
 * The list is derived from what's saved; the amounts come from [GroceryPlanner] (Kotlin), never from the model.
 * Personal data: nothing here is logged.
 */
@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val grocery: GroceryRepository,
    private val food: FoodRepository,
    profiles: ProfileRepository,
    plans: PlanRepository,
) : ViewModel() {
    private val targets: Flow<NutritionTargets?> =
        combine(profiles.observe(), plans.observeWeek()) { profile, plan ->
            profile?.let { Nutrition.targets(it, Year.now().value, plan.sessions.size) }
        }

    val state: StateFlow<GroceryState> =
        combine(grocery.observe(), targets) { items, t -> GroceryState.Ready(items, hasTargets = t != null) as GroceryState }
            .catch { emit(GroceryState.Failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GroceryState.Loading)

    init {
        // The first time the list is opened it builds itself.
        viewModelScope.launch { safely { if (grocery.isEmpty() && targets.first() != null) refreshNow() } }
    }

    fun refresh() {
        viewModelScope.launch { safely { refreshNow() } }
    }

    private suspend fun refreshNow() {
        val t = targets.first() ?: return
        val today = LocalDate.now()
        val usual = Grocery.frequentFoods(food.between(today.minusDays(Grocery.FREQUENT_DAYS - 1L), today), today)
        grocery.refresh(GroceryPlanner.week(t, usual))
    }

    fun setChecked(item: StoredGroceryItem, checked: Boolean) {
        viewModelScope.launch { safely { grocery.setChecked(item.id, checked) } }
    }

    fun remove(item: StoredGroceryItem) {
        viewModelScope.launch { safely { grocery.remove(item.id) } }
    }

    fun restore(item: StoredGroceryItem) {
        viewModelScope.launch { safely { grocery.restore(item) } }
    }

    fun clearTicked() {
        viewModelScope.launch { safely { grocery.clearTicked() } }
    }

    /** [onResult]: false when the name is invalid or already on the list. */
    fun add(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(safely { grocery.add(name) } ?: false) }
    }

    /** A failed write leaves the list as it was (the screen is derived from what's saved). */
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
