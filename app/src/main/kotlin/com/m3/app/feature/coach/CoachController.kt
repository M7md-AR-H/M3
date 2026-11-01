package com.m3.app.feature.coach

import com.m3.app.data.PlanInputs
import com.m3.app.data.PlanRepository
import com.m3.app.feature.workout.WorkoutTargets
import com.m3.coach.ChatRole
import com.m3.coach.ChatTurn
import com.m3.coach.CoachAction
import com.m3.coach.CoachFacts
import com.m3.coach.CoachPrompt
import com.m3.coach.CoachTools
import com.m3.coach.DailyAnswers
import com.m3.coach.DailyFacts
import com.m3.coach.ExerciseProgress
import com.m3.coach.progressKey
import com.m3.coach.ReplyParser
import com.m3.coach.ToolContext
import com.m3.coach.ToolResult
import com.m3.core.data.BodyRepository
import com.m3.core.data.ChatAuthor
import com.m3.core.data.ChatRepository
import com.m3.core.data.DailyRepository
import com.m3.core.data.FoodRepository
import com.m3.core.data.PlanOverridesRepository
import com.m3.core.data.ProposalStatus
import com.m3.core.data.StepRepository
import com.m3.core.data.StoredChatMessage
import com.m3.core.data.StoredProposal
import com.m3.core.data.TrainingSetupRepository
import com.m3.core.data.WorkoutRepository
import com.m3.core.data.WorkoutWithSets
import com.m3.core.designsystem.config.Actor
import com.m3.core.designsystem.config.ApplyResult
import com.m3.core.designsystem.config.RejectReason
import com.m3.core.designsystem.config.Specialist
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.UiConfigStore
import com.m3.core.designsystem.config.displayName
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Nutrition
import com.m3.core.model.WeightTrend
import com.m3.core.training.PlanDiff
import com.m3.core.training.WeekPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How to reverse an applied proposal. Stored as JSON on the chat message. */
@Serializable
sealed class UndoInfo {
    @Serializable
    @SerialName("day")
    data class Day(val date: String, val previousItems: List<String>?) : UndoInfo()

    @Serializable
    @SerialName("exercise")
    data class Exercise(val exerciseId: String, val previousReason: String?) : UndoInfo()

    /** A whole family avoided for pain: each exercise's reason before (null = it was allowed). */
    @Serializable
    @SerialName("exercises")
    data class Exercises(val previousReasons: Map<String, String?>) : UndoInfo()

    @Serializable
    @SerialName("availability")
    data class Availability(val day: Int, val previousLocationId: String?) : UndoInfo()

    @Serializable
    @SerialName("ui")
    data class Ui(val inverse: UiChange) : UndoInfo()
}

/**
 * The Coach's turn loop (ADR 0010): build the prompt from the app's facts, run the on-device model,
 * keep its words, and turn any tool call into a validated proposal the owner applies or dismisses.
 * Every number and every change is decided here in Kotlin, never by the model (ADR 0003).
 */
@Singleton
class CoachController @Inject constructor(
    private val runtime: CoachRuntime,
    private val chat: ChatRepository,
    private val plans: PlanRepository,
    private val setups: TrainingSetupRepository,
    private val overrides: PlanOverridesRepository,
    private val uiStore: UiConfigStore,
    private val workouts: WorkoutRepository,
    private val food: FoodRepository,
    private val steps: StepRepository,
    private val body: BodyRepository,
    private val daily: DailyRepository,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Apply, Not now and Undo run one at a time, so a double tap can't apply twice or corrupt the undo record. */
    private val actions = Mutex()

    /**
     * Runs one turn: the model answers [message], and the owner's message and the reply are saved together,
     * so a failed or stopped answer leaves nothing half-saved. [onText] streams the visible answer.
     */
    suspend fun send(message: String, onText: (String) -> Unit) {
        val trimmed = message.trim().take(MAX_MESSAGE_CHARS)
        if (trimmed.isEmpty()) return
        val history = chat.observe(HISTORY_MESSAGES).first().map { ChatTurn(it.role, it.text) }
        val inputs = currentInputs()
        val today = dailyFacts(inputs)
        // Plain number questions never reach the model: it got them wrong in the eval even when told the answer.
        DailyAnswers.answer(trimmed, today)?.let { answer ->
            chat.addTurn(trimmed, answer, null)
            return
        }
        val system = CoachPrompt.system(facts(inputs, workouts.observeRecent().first(), today))
        val raw = runtime.use { engine ->
            val streamed = StringBuilder()
            engine.generate(
                prompt = CoachPrompt.turn(history, trimmed, systemChars = system.length),
                systemInstruction = system,
                maxOutputTokens = MAX_OUTPUT_TOKENS,
                onText = { piece ->
                    streamed.append(piece)
                    onText(visibleSoFar(streamed.toString()))
                },
            ).text
        }
        val parsed = ReplyParser.parse(raw)
        val fromModel = parsed.toolJson?.let { proposalFor(it, inputs, trimmed) }
        // A plain change to the usual week: the owner's words beat the model, which got it wrong twice on the phone.
        val proposal = CoachTools.scheduleRequest(trimmed, inputs.setup.locations.map { it.name })
            ?.let { proposalFor(it, inputs, trimmed) }
            // A plain theme request the model answered without a usable tool line (seen on the phone): Kotlin proposes it.
            ?: fromModel.takeUnless { it == null || it.status == ProposalStatus.REJECTED }
            ?: CoachTools.themeRequest(trimmed)?.let { proposalFor(themeJson(it), inputs, trimmed) }
            ?: fromModel
        val text = parsed.text.ifBlank { proposal?.summary ?: "Sorry, I didn't get an answer. Please try again." }
        chat.addTurn(trimmed, withClaimCorrection(text, proposal), proposal)
    }

    /**
     * The model sometimes says a change is done ("I have switched to dark mode") when only Apply does that.
     * The correction is stored with the reply, so the model also sees it in later turns and stops repeating it.
     */
    private fun withClaimCorrection(text: String, proposal: StoredProposal?): String {
        if (!CoachTools.claimsChange(text)) return text
        val note = when (proposal?.status) {
            ProposalStatus.PENDING -> NOT_YET_NOTE
            ProposalStatus.BLOCKED -> LOCKED_NOTE
            else -> NOTHING_CHANGED_NOTE
        }
        return "$text\n\n$note"
    }

    /** Applies a pending (or previously locked) proposal. Returns a message for the owner if it didn't apply. */
    suspend fun apply(messageId: Long): String? = actions.withLock {
        val proposal = chat.get(messageId)?.proposal ?: return null
        if (proposal.status != ProposalStatus.PENDING && proposal.status != ProposalStatus.BLOCKED) return null
        val inputs = currentInputs()
        val action = when (val result = CoachTools.validate(proposal.json.orEmpty(), toolContext(inputs))) {
            is ToolResult.Invalid -> {
                chat.setProposalStatus(messageId, ProposalStatus.REJECTED)
                return result.message
            }
            ToolResult.Ignored -> {
                chat.setProposalStatus(messageId, ProposalStatus.REJECTED)
                return "This suggestion can't be applied any more."
            }
            is ToolResult.Valid -> result.action
        }
        if (action.lock in uiStore.config.value.locks) {
            chat.setProposalStatus(messageId, ProposalStatus.BLOCKED)
            return LOCKED_MESSAGE
        }
        return when (val outcome = applyAction(action, inputs)) {
            is Outcome.Done -> {
                chat.setProposalStatus(messageId, ProposalStatus.APPLIED, json.encodeToString(UndoInfo.serializer(), outcome.undo))
                null
            }
            Outcome.Locked -> {
                chat.setProposalStatus(messageId, ProposalStatus.BLOCKED)
                LOCKED_MESSAGE
            }
            is Outcome.Refused -> {
                chat.setProposalStatus(messageId, ProposalStatus.REJECTED)
                outcome.message
            }
        }
    }

    suspend fun dismiss(messageId: Long): Unit = actions.withLock {
        val proposal = chat.get(messageId)?.proposal ?: return
        if (proposal.status == ProposalStatus.PENDING || proposal.status == ProposalStatus.BLOCKED) {
            chat.setProposalStatus(messageId, ProposalStatus.DISMISSED)
        }
    }

    /**
     * Reverses an applied proposal. The owner's own action, so locks don't apply. Only the latest applied
     * proposal can be undone: undoing an older one would overwrite changes made after it.
     */
    suspend fun undo(messageId: Long): String? = actions.withLock {
        val proposal = chat.get(messageId)?.proposal ?: return null
        if (proposal.status != ProposalStatus.APPLIED) return null
        if (chat.latestAppliedId() != messageId) return "Only the most recent applied change can be undone."
        val info = proposal.undoJson?.let { runCatching { json.decodeFromString(UndoInfo.serializer(), it) }.getOrNull() }
            ?: return "This change can't be undone."
        when (info) {
            is UndoInfo.Day -> overrides.setDayEquipment(
                LocalDate.parse(info.date),
                info.previousItems?.mapNotNull(EquipmentKind::fromNameOrNull)?.toSet(),
            )
            is UndoInfo.Exercise -> overrides.setExcluded(info.exerciseId, info.previousReason)
            is UndoInfo.Exercises -> overrides.setExcluded(info.previousReasons)
            is UndoInfo.Availability -> {
                val setup = setups.observe().first()
                val day = DayOfWeek.of(info.day)
                // A place deleted since then can't come back; the day becomes not free instead.
                val previous = info.previousLocationId?.takeIf { id -> setup.locations.any { it.id == id } }
                val availability = if (previous == null) setup.availability - day else setup.availability + (day to previous)
                setups.replace(setup.copy(availability = availability))
            }
            is UndoInfo.Ui -> if (uiStore.apply(info.inverse, Actor.Owner) is ApplyResult.Rejected) {
                return "Couldn't undo this change."
            }
        }
        chat.setProposalStatus(messageId, ProposalStatus.UNDONE)
        null
    }

    private sealed interface Outcome {
        data class Done(val undo: UndoInfo) : Outcome
        data object Locked : Outcome
        data class Refused(val message: String) : Outcome
    }

    suspend fun clear() = chat.clear()

    private suspend fun currentInputs(): PlanInputs = plans.observeInputs().first()

    private fun toolContext(inputs: PlanInputs) = ToolContext(LocalDate.now(), inputs.setup, plans.catalog, inputs.stored.excluded)

    /** Today's logs for the prompt. Every number is worked out here; the model only explains them. */
    private suspend fun dailyFacts(inputs: PlanInputs): DailyFacts {
        val today = LocalDate.now()
        val entries = food.observeDay(today).first()
        val points = WeightTrend.points(body.observeWeights().first())
        return DailyFacts(
            targets = inputs.profile?.let { Nutrition.targets(it, today.year, inputs.plan.sessions.size) },
            eaten = Nutrition.totals(entries),
            foods = entries.takeLast(CoachPrompt.MAX_FOODS).map { it.name },
            steps = steps.observeDay(today).first().takeIf { it > 0 },
            trendKg = points.lastOrNull()?.trendKg,
            weeklyChangeKg = WeightTrend.weeklyChangeKg(points),
            checkIn = daily.observeCheckIn(today).first(),
        )
    }

    private fun facts(inputs: PlanInputs, recent: List<WorkoutWithSets>, today: DailyFacts): CoachFacts {
        val config = uiStore.config.value
        val date = LocalDate.now()
        // Every number here comes from Progression; the model only explains them.
        val progress = inputs.plan.sessions.flatMap { session ->
            WorkoutTargets.forSession(session, inputs.setup, recent, inputs.deloadWeeks, inputs.deloadThisWeek).filter { it.last.isNotEmpty() }.map { t ->
                progressKey(session.day, t.variantKey) to
                    ExerciseProgress(WorkoutTargets.history(t.variantKey, recent, inputs.deloadWeeks).first().date, t.last, t.target)
            }
        }.toMap()
        return CoachFacts(
            progress = progress,
            workoutsLast28Days = recent.count { !it.workout.date.isBefore(date.minusDays(27)) },
            lastWorkout = recent.firstOrNull()?.workout?.date,
            daily = today,
            today = date,
            profile = inputs.profile,
            setup = inputs.setup,
            plan = inputs.plan,
            deloadThisWeek = inputs.deloadThisWeek,
            avoided = inputs.stored.excluded.mapValues { (id, reason) -> (plans.catalog[id]?.name ?: id) to reason },
            dayEquipment = inputs.stored.dayEquipment.filterKeys { !it.isBefore(LocalDate.now()) },
            themeMode = config.theme.mode,
            cards = config.todayCards,
            locks = config.locks,
        )
    }

    /**
     * Validates the model's tool call and describes what it would do, including the plan changes.
     * Null when the call is valid but unrelated to what the owner said (it's dropped, the answer stays).
     */
    private suspend fun proposalFor(toolJson: String, inputs: PlanInputs, ownerMessage: String): StoredProposal? =
        when (val result = CoachTools.validate(toolJson, toolContext(inputs))) {
            ToolResult.Ignored -> null
            is ToolResult.Invalid -> StoredProposal(toolJson, ProposalStatus.REJECTED, result.message, emptyList())
            is ToolResult.Valid -> {
                val action = result.action
                if (!CoachTools.relevantTo(action, ownerMessage)) return null
                val locks = uiStore.config.value.locks
                withContext(Dispatchers.Default) {
                    val status = when {
                        changesNothing(action, inputs) -> ProposalStatus.NO_CHANGE
                        action.lock in locks -> ProposalStatus.BLOCKED
                        else -> ProposalStatus.PENDING
                    }
                    StoredProposal(toolJson, status, summary(action), planChanges(action, inputs))
                }
            }
        }

    /** True when applying [action] would leave everything as it is, so the card offers no Apply. */
    private fun changesNothing(action: CoachAction, inputs: PlanInputs): Boolean = when (action) {
        // Whole sessions, equipment included: the same exercises with other equipment is still a change.
        is CoachAction.SetDayEquipment -> dayPlans(action, inputs).let { (before, after) ->
            before.sessionOn(action.day) == after.sessionOn(action.day)
        }
        is CoachAction.AvoidExercise -> action.allIds.all { it in inputs.stored.excluded }
        is CoachAction.AllowExercise -> action.allIds.none { it in inputs.stored.excluded }
        is CoachAction.SetAvailability -> inputs.setup.availability[action.day] == action.locationId
        is CoachAction.Ui -> (action.change as? UiChange.SetThemeMode)?.mode == uiStore.config.value.theme.mode
    }

    /** The target day's week before and after; it may be next week, e.g. "tomorrow" on a Sunday. */
    private fun dayPlans(action: CoachAction.SetDayEquipment, inputs: PlanInputs): Pair<WeekPlan, WeekPlan> {
        val before = plans.preview(inputs.profile, inputs.setup, inputs.stored, today = action.date)
        val withDay = inputs.stored.copy(dayEquipment = inputs.stored.dayEquipment + (action.date to action.items))
        return before to plans.preview(inputs.profile, inputs.setup, withDay, today = action.date)
    }

    private fun dayDiff(action: CoachAction.SetDayEquipment, inputs: PlanInputs): List<String> {
        val (before, after) = dayPlans(action, inputs)
        return PlanDiff.describe(before, after).filter { it.startsWith(dayLabel(action.date.dayOfWeek)) }
    }

    private fun planChanges(action: CoachAction, inputs: PlanInputs): List<String> {
        val after = when (action) {
            is CoachAction.SetDayEquipment ->
                return dayDiff(action, inputs).ifEmpty { listOf("No change: ${dayLabel(action.date.dayOfWeek)} is a rest day or already fits.") }
            is CoachAction.AvoidExercise ->
                plans.preview(inputs.profile, inputs.setup, inputs.stored.copy(excluded = inputs.stored.excluded + action.allIds.associateWith { action.reason }))
            is CoachAction.AllowExercise ->
                plans.preview(inputs.profile, inputs.setup, inputs.stored.copy(excluded = inputs.stored.excluded - action.allIds.toSet()))
            is CoachAction.SetAvailability -> {
                val availability = inputs.setup.availability.let { if (action.locationId == null) it - action.day else it + (action.day to action.locationId) }
                plans.preview(inputs.profile, inputs.setup.copy(availability = availability), inputs.stored)
            }
            is CoachAction.Ui -> return if (changesNothing(action, inputs)) listOf("It's already set like this.") else emptyList()
        }
        return PlanDiff.describe(inputs.plan, after).ifEmpty { listOf("No change to this week's sessions.") }
    }

    /** Applies [action] and says how to undo it, or why the store refused it. */
    private suspend fun applyAction(action: CoachAction, inputs: PlanInputs): Outcome = when (action) {
        is CoachAction.SetDayEquipment -> {
            val previous = overrides.dayEquipment(action.date)
            overrides.setDayEquipment(action.date, action.items)
            Outcome.Done(UndoInfo.Day(action.date.toString(), previous?.map { it.name }))
        }
        is CoachAction.AvoidExercise -> if (action.similar.isEmpty()) {
            val previous = overrides.excludedReason(action.exerciseId)
            overrides.setExcluded(action.exerciseId, action.reason)
            Outcome.Done(UndoInfo.Exercise(action.exerciseId, previous))
        } else {
            val previous = action.allIds.associateWith { overrides.excludedReason(it) }
            // One reason for the whole family, so "use lunges again" can find it as one group.
            overrides.setExcluded(action.allIds.associateWith { action.reason })
            Outcome.Done(UndoInfo.Exercises(previous))
        }
        is CoachAction.AllowExercise -> if (action.similar.isEmpty()) {
            val previous = overrides.excludedReason(action.exerciseId)
            overrides.setExcluded(action.exerciseId, null)
            Outcome.Done(UndoInfo.Exercise(action.exerciseId, previous))
        } else {
            val previous = action.allIds.associateWith { overrides.excludedReason(it) }
            overrides.setExcluded(action.allIds.associateWith { null })
            Outcome.Done(UndoInfo.Exercises(previous))
        }
        is CoachAction.SetAvailability -> {
            val setup = inputs.setup
            val previous = setup.availability[action.day]
            val availability = if (action.locationId == null) setup.availability - action.day else setup.availability + (action.day to action.locationId)
            setups.replace(setup.copy(availability = availability))
            Outcome.Done(UndoInfo.Availability(action.day.value, previous))
        }
        is CoachAction.Ui -> {
            val inverse = inverseOf(action.change, uiStore.config.value)
            when (val result = uiStore.apply(action.change, Actor.Agent(Specialist.COACH))) {
                is ApplyResult.Applied -> Outcome.Done(UndoInfo.Ui(inverse))
                is ApplyResult.Rejected -> when (result.reason) {
                    RejectReason.Denied -> Outcome.Locked
                    is RejectReason.Invalid -> Outcome.Refused("That change would break the app's look rules (e.g. contrast), so nothing changed.")
                }
            }
        }
    }

    companion object {
        const val MAX_MESSAGE_CHARS = 2_000
        const val MAX_OUTPUT_TOKENS = 400
        const val HISTORY_MESSAGES = 20
        const val LOCKED_MESSAGE = "You've locked this for agents (You → Customize), so nothing changed."
        const val NOTHING_CHANGED_NOTE = "(From the app: nothing has changed. Changes only happen when you tap Apply on a suggestion card.)"
        const val NOT_YET_NOTE = "(From the app: not done yet. Tap Apply below to make this change.)"
        const val LOCKED_NOTE = "(From the app: nothing has changed, because you've locked this for agents.)"

        /** The tool call the app writes itself for a plain theme request; it's validated like the model's. */
        fun themeJson(mode: com.m3.core.designsystem.config.ThemeMode) = """{"name":"theme","mode":"${mode.name}"}"""

        /** The reply text without a tool block that is still being written. */
        fun visibleSoFar(raw: String): String {
            val start = raw.indexOf("<tool", ignoreCase = true)
            if (start >= 0) return raw.substring(0, start).trim()
            // Also hide a tag that has only partly arrived ("<", "<t", "<to", "<too").
            val partial = (1 until TOOL_TAG.length).firstOrNull { raw.endsWith(TOOL_TAG.substring(0, TOOL_TAG.length - it), ignoreCase = true) }
            val cut = partial?.let { raw.dropLast(TOOL_TAG.length - it) } ?: raw
            return cut.trim()
        }

        private const val TOOL_TAG = "<tool"

        private fun dayLabel(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

        fun inverseOf(change: UiChange, config: UiConfig): UiChange = when (change) {
            is UiChange.SetThemeMode -> UiChange.SetThemeMode(config.theme.mode)
            is UiChange.SetCardVisible, is UiChange.AddCard -> {
                val type = if (change is UiChange.SetCardVisible) change.type else (change as UiChange.AddCard).type
                val current = config.todayCards.firstOrNull { it.type == type }
                UiChange.SetCardVisible(type, current?.visible ?: false)
            }
            else -> UiChange.SetCardOrder(config.todayCards.map { it.type })
        }

        fun summary(action: CoachAction): String = when (action) {
            is CoachAction.SetDayEquipment -> {
                val day = action.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))
                if (action.items.isEmpty()) "Bodyweight only on $day (that day only)"
                else "Only ${action.items.sortedBy { it.ordinal }.joinToString(", ") { it.displayName }} on $day (that day only)"
            }
            is CoachAction.AvoidExercise -> {
                val why = if (action.reason.isBlank()) "" else " (${action.reason})"
                if (action.familyName == null) "Stop using ${action.exerciseName}$why until you allow it again"
                else "Stop using all ${action.familyName}$why until you allow them again"
            }
            is CoachAction.AllowExercise ->
                if (action.familyName == null) "Use ${action.exerciseName} again" else "Use all ${action.familyName} again"
            is CoachAction.SetAvailability -> {
                val day = action.day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                if (action.locationName == null) "$day: not free" else "$day: free at ${action.locationName}"
            }
            is CoachAction.Ui -> when (val change = action.change) {
                is UiChange.SetThemeMode -> "Switch the theme to ${change.mode.name.lowercase()}"
                is UiChange.AddCard -> "Show the ${change.type.displayName} card on Today"
                is UiChange.SetCardVisible -> "${if (change.visible) "Show" else "Hide"} the ${change.type.displayName} card on Today"
                is UiChange.SetCardOrder -> "Move ${change.types.firstOrNull()?.displayName ?: "a card"} to the top of Today"
                else -> change.describe().replaceFirstChar { it.uppercase() }
            }
        }
    }
}

private val StoredChatMessage.role: ChatRole get() = if (author == ChatAuthor.OWNER) ChatRole.OWNER else ChatRole.COACH
