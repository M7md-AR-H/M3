package com.m3.app.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.app.data.PlanRepository
import com.m3.app.feature.train.PlanText
import com.m3.core.data.StoredSet
import com.m3.core.data.StoredWorkout
import com.m3.core.data.WorkoutRepository
import com.m3.core.model.EquipmentKind
import com.m3.core.training.ExerciseUnit
import com.m3.core.training.LoadKind
import com.m3.core.training.PlannedSession
import com.m3.core.training.Progression
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** One planned set as the screen shows it. Values are what is saved when [done], else what the Done tick would save. */
data class RowUi(val setNumber: Int, val last: String, val kg: Double?, val amount: Int?, val done: Boolean)

data class ExerciseUi(
    val index: Int,
    val name: String,
    val prescriptionLine: String,
    val reason: String,
    val amountUnit: ExerciseUnit,
    val showKg: Boolean,
    val kgLabel: String,
    val rows: List<RowUi>,
    /** RPE of the most recently ticked set, if the owner gave one. */
    val effort: Double?,
    val canRate: Boolean,
)

data class NextUi(val name: String, val prescription: String)

/** The rest after a set: wall-clock end time, so it stays right while the app is in the background. */
data class RestTimer(val endAt: Long, val totalMillis: Long)

sealed interface WorkoutState {
    data object Loading : WorkoutState

    /** Nothing is open (it was finished, or the screen was opened without one). */
    data object NoWorkout : WorkoutState

    /** A workout is open but its planned session is gone (the plan changed). */
    data class Missing(val workoutId: Long) : WorkoutState

    data class Ready(
        val workoutId: Long,
        val startedAt: Long,
        val sessionName: String,
        val count: Int,
        val current: ExerciseUi,
        val next: NextUi?,
        val rest: RestTimer?,
    ) : WorkoutState {
        val isLast: Boolean get() = current.index == count - 1
    }
}

private sealed interface Base {
    data object None : Base
    data class Missing(val workout: StoredWorkout) : Base
    data class Found(
        val workout: StoredWorkout,
        val session: PlannedSession,
        val targets: List<ExerciseTarget>,
        /** The owner's kg list per variant key (dumbbells, kettlebells); empty when not listed. */
        val listedKg: Map<String, List<Double>>,
    ) : Base
}

private data class RowKey(val variantKey: String, val setNumber: Int)

/**
 * Unticked rows' values that differ from the start values. A key with a null value means "emptied".
 * [directKg]: rows whose kg the owner set themselves; only the others follow an earlier row's kg.
 */
private data class Edits(
    val kg: Map<RowKey, Double?> = emptyMap(),
    val amount: Map<RowKey, Int?> = emptyMap(),
    val directKg: Set<RowKey> = emptySet(),
)

/**
 * The live workout. Everything is derived from flows: the plan, the history (for targets) and the sets saved so
 * far. Ticking a set saves it at once, so closing the app loses nothing. Personal data: never logged.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    plans: PlanRepository,
) : ViewModel() {
    private val index = MutableStateFlow<Int?>(null)
    private val edits = MutableStateFlow(Edits())
    private val rest = MutableStateFlow<RestTimer?>(null)

    private var found: Base.Found? = null
    private var currentSets: List<StoredSet> = emptyList()

    /** Every write goes through this, in tap order, so a quick tick-untick or double tap can't interleave. */
    private val writes = Mutex()

    /** The set the effort chips rate: the last one ticked on this screen (not the last one edited). */
    private val lastTicked = MutableStateFlow<RowKey?>(null)

    /** Set once Finish/End starts, so a double tap can't finish (and navigate back) twice. */
    private val finishing = AtomicBoolean(false)
    private val _finishFailed = MutableStateFlow(false)
    val finishFailed: StateFlow<Boolean> = _finishFailed

    private val base = combine(workouts.observeOpen(), plans.observeInputs(), workouts.observeRecent()) { open, inputs, recent ->
        if (open == null) {
            Base.None
        } else {
            val session = WorkoutLogic.findSession(inputs.plan, open.date, open.sessionType, open.locationId)
            if (session == null || session.exercises.isEmpty()) {
                Base.Missing(open)
            } else {
                val targets = WorkoutTargets.forSession(session, inputs.setup, recent)
                val listed = targets.associate { it.variantKey to WorkoutTargets.listedKg(inputs.setup, session.locationId, it.planned.equipment) }
                Base.Found(open, session, targets, listed)
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    val state: StateFlow<WorkoutState> = base.flatMapLatest { b ->
        when (b) {
            Base.None -> flowOf<WorkoutState>(WorkoutState.NoWorkout)
            is Base.Missing -> flowOf<WorkoutState>(WorkoutState.Missing(b.workout.id))
            is Base.Found -> {
                found = b
                combine(
                    workouts.observeSets(b.workout.id).onEach { sets ->
                        currentSets = sets
                        // Jump to the first unfinished exercise once; after that the owner moves by hand.
                        if (index.value == null) {
                            val logged = sets.groupBy({ it.variantKey }, { it.setNumber }).mapValues { it.value.toSet() }
                            index.value = WorkoutLogic.firstIncomplete(
                                b.targets.map { it.variantKey to it.planned.prescription.sets },
                                logged,
                            )
                        }
                    },
                    index, edits, rest, lastTicked,
                ) { sets, idx, e, r, ticked -> if (idx == null) WorkoutState.Loading else build(b, sets, idx, e, r, ticked) }
            }
        }
    }.catch { emit(WorkoutState.NoWorkout) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkoutState.Loading)

    private fun build(b: Base.Found, sets: List<StoredSet>, idx: Int, e: Edits, r: RestTimer?, ticked: RowKey?): WorkoutState.Ready {
        val i = idx.coerceIn(0, b.targets.lastIndex)
        val t = b.targets[i]
        val p = t.planned.prescription
        val kind = Progression.loadKind(t.planned.equipment)
        val bodyweight = kind == LoadKind.NONE && t.planned.equipment.isEmpty()
        val mine = sets.filter { it.variantKey == t.variantKey }
        val stored = mine.associateBy { it.setNumber }
        val rows = (1..p.sets).map { n ->
            val key = RowKey(t.variantKey, n)
            val last = t.last.getOrNull(n - 1)
            val s = stored[n]
            val kg = when {
                bodyweight -> null
                s != null -> s.kg
                e.kg.containsKey(key) -> e.kg[key]
                else -> WorkoutLogic.initialKg(t.target.kg, last?.kg)
            }
            val amount = when {
                s != null -> WorkoutLogic.amountOf(s.reps, s.seconds, p.unit)
                e.amount.containsKey(key) -> e.amount[key]
                else -> WorkoutLogic.initialAmount(last?.let { WorkoutLogic.amountOf(it.reps, it.seconds, p.unit) }, p.max)
            }
            RowUi(n, WorkoutLogic.lastText(last, p.unit), kg, amount, done = s != null)
        }
        // The set ticked last on this screen; after a restart, the last one saved.
        val latest = ticked?.takeIf { it.variantKey == t.variantKey }?.let { stored[it.setNumber] } ?: mine.maxByOrNull { it.completedAt }
        val next = b.targets.getOrNull(i + 1)?.let { NextUi(it.planned.exercise.name, PlanText.prescription(it.planned)) }
        return WorkoutState.Ready(
            workoutId = b.workout.id,
            startedAt = b.workout.startedAt,
            sessionName = b.session.type.displayName,
            count = b.targets.size,
            current = ExerciseUi(
                index = i,
                name = t.planned.exercise.name,
                prescriptionLine = WorkoutLogic.prescriptionLine(t.planned, t.target.kg),
                reason = t.target.reason,
                amountUnit = p.unit,
                showKg = !bodyweight,
                kgLabel = if (EquipmentKind.DUMBBELLS in t.planned.equipment) "kg each" else "kg",
                rows = rows,
                effort = latest?.rpe,
                canRate = latest != null,
            ),
            next = next,
            rest = r,
        )
    }

    private fun ready(): WorkoutState.Ready? = state.value as? WorkoutState.Ready

    /**
     * Runs one write after the previous ones. The row is re-read from the saved sets inside the lock, so a write
     * always starts from the latest saved state, not from a screen state that may be one tap behind.
     */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The screen is derived from what is saved, so a failed save simply leaves the row as it was.
                }
            }
        }
    }

    private suspend fun savedSet(workoutId: Long, variantKey: String, setNumber: Int): StoredSet? =
        workouts.observeSets(workoutId).first().firstOrNull { it.variantKey == variantKey && it.setNumber == setNumber }

    /** The exercise an action came from, or null if the screen has moved on (a tap during the cross-fade). */
    private fun targetFor(exerciseIndex: Int): Pair<WorkoutState.Ready, ExerciseTarget>? {
        val r = ready() ?: return null
        if (r.current.index != exerciseIndex) return null
        val t = found?.targets?.getOrNull(exerciseIndex) ?: return null
        return r to t
    }

    /** Done toggle: ticking saves the row and starts the rest; unticking removes the saved set but keeps its values. */
    fun toggleDone(exerciseIndex: Int, setNumber: Int) {
        val (r, t) = targetFor(exerciseIndex) ?: return
        val row = r.current.rows.getOrNull(setNumber - 1) ?: return
        val key = RowKey(t.variantKey, setNumber)
        write {
            val saved = savedSet(r.workoutId, t.variantKey, setNumber)
            if (saved != null) {
                edits.update {
                    it.copy(
                        kg = it.kg + (key to saved.kg),
                        amount = it.amount + (key to WorkoutLogic.amountOf(saved.reps, saved.seconds, t.planned.prescription.unit)),
                        directKg = it.directKg + key,
                    )
                }
                workouts.removeSet(r.workoutId, t.variantKey, setNumber)
                if (lastTicked.value == key) lastTicked.value = null
            } else {
                workouts.logSet(storedFrom(r.workoutId, t, setNumber, row.kg, row.amount, rpe = null))
                lastTicked.value = key
                startRest(t.planned.prescription.restSeconds)
            }
        }
    }

    fun setKg(exerciseIndex: Int, setNumber: Int, kg: Double?) {
        val (r, t) = targetFor(exerciseIndex) ?: return
        val row = r.current.rows.getOrNull(setNumber - 1) ?: return
        if (!r.current.showKg) return
        if (row.done) {
            write { savedSet(r.workoutId, t.variantKey, setNumber)?.let { workouts.logSet(it.copy(kg = kg)) } }
            return
        }
        edits.update { e ->
            val map = e.kg.toMutableMap()
            val key = RowKey(t.variantKey, setNumber)
            map[key] = kg
            // Later sets the owner hasn't set themselves follow, so a heavier first set doesn't need three edits.
            for (later in setNumber + 1..r.current.rows.size) {
                val laterKey = RowKey(t.variantKey, later)
                if (!r.current.rows[later - 1].done && laterKey !in e.directKg) map[laterKey] = kg
            }
            e.copy(kg = map, directKg = e.directKg + key)
        }
    }

    fun stepKg(exerciseIndex: Int, setNumber: Int, direction: Int) {
        val (r, t) = targetFor(exerciseIndex) ?: return
        val row = r.current.rows.getOrNull(setNumber - 1) ?: return
        val kind = Progression.loadKind(t.planned.equipment)
        val listed = found?.listedKg?.get(t.variantKey).orEmpty()
        setKg(exerciseIndex, setNumber, WorkoutLogic.stepKg(row.kg, direction, kind, listed))
    }

    fun setAmount(exerciseIndex: Int, setNumber: Int, amount: Int?) {
        val (r, t) = targetFor(exerciseIndex) ?: return
        val row = r.current.rows.getOrNull(setNumber - 1) ?: return
        if (row.done) {
            val (reps, seconds) = WorkoutLogic.toStored(amount, t.planned.prescription.unit)
            write { savedSet(r.workoutId, t.variantKey, setNumber)?.let { workouts.logSet(it.copy(reps = reps, seconds = seconds)) } }
        } else {
            edits.update { it.copy(amount = it.amount + (RowKey(t.variantKey, setNumber) to amount)) }
        }
    }

    fun stepAmount(exerciseIndex: Int, setNumber: Int, direction: Int) {
        val (r, _) = targetFor(exerciseIndex) ?: return
        val row = r.current.rows.getOrNull(setNumber - 1) ?: return
        setAmount(exerciseIndex, setNumber, WorkoutLogic.stepAmount(row.amount, direction, r.current.amountUnit))
    }

    /** Sets (or, on the same value again, clears) the effort of the set ticked last in this exercise. */
    fun setEffort(exerciseIndex: Int, rpe: Double) {
        val (r, t) = targetFor(exerciseIndex) ?: return
        write {
            val sets = workouts.observeSets(r.workoutId).first().filter { it.variantKey == t.variantKey }
            val key = lastTicked.value?.takeIf { it.variantKey == t.variantKey }
            val target = key?.let { k -> sets.firstOrNull { it.setNumber == k.setNumber } } ?: sets.maxByOrNull { it.completedAt } ?: return@write
            workouts.logSet(target.copy(rpe = if (target.rpe == rpe) null else rpe))
        }
    }

    fun next() = move(+1)

    fun previous() = move(-1)

    private fun move(step: Int) {
        val r = ready() ?: return
        index.value = (r.current.index + step).coerceIn(0, r.count - 1)
        // A rest that already ran out shouldn't greet the owner on the next exercise.
        rest.update { it?.takeIf { timer -> timer.endAt > System.currentTimeMillis() } }
    }

    fun skipRest() {
        rest.value = null
    }

    /** Ends the workout (sets already logged are kept; an empty workout is dropped) and then calls [onDone]. */
    fun finish(onDone: () -> Unit) {
        if (!finishing.compareAndSet(false, true)) return
        val id = (state.value as? WorkoutState.Ready)?.workoutId ?: (state.value as? WorkoutState.Missing)?.workoutId ?: run {
            onDone()
            return
        }
        viewModelScope.launch {
            // After the queued set writes, so a set ticked just before Finish is kept.
            val ok = writes.withLock {
                try {
                    workouts.finish(id)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            }
            if (ok) {
                onDone()
            } else {
                finishing.set(false)
                _finishFailed.value = true
            }
        }
    }

    fun finishFailedShown() {
        _finishFailed.value = false
    }

    private fun startRest(seconds: Int) {
        if (seconds <= 0) return
        val total = seconds * MILLIS_PER_SECOND
        rest.value = RestTimer(endAt = System.currentTimeMillis() + total, totalMillis = total)
    }

    private fun storedFrom(sessionId: Long, t: ExerciseTarget, setNumber: Int, kg: Double?, amount: Int?, rpe: Double?): StoredSet {
        val (reps, seconds) = WorkoutLogic.toStored(amount, t.planned.prescription.unit)
        // completedAt is stamped by the repository.
        return StoredSet(sessionId, t.planned.exercise.id, t.variantKey, setNumber, kg, reps, seconds, rpe, completedAt = 0)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MILLIS_PER_SECOND = 1000L
    }
}
