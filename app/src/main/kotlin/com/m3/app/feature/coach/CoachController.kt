package com.m3.app.feature.coach

import com.m3.app.data.PlanInputs
import com.m3.app.data.PlanRepository
import com.m3.coach.ChatRole
import com.m3.coach.ChatTurn
import com.m3.coach.CoachAction
import com.m3.coach.CoachFacts
import com.m3.coach.CoachPrompt
import com.m3.coach.CoachTools
import com.m3.coach.ReplyParser
import com.m3.coach.ToolContext
import com.m3.coach.ToolResult
import com.m3.core.data.ChatAuthor
import com.m3.core.data.ChatRepository
import com.m3.core.data.PlanOverridesRepository
import com.m3.core.data.ProposalStatus
import com.m3.core.data.StoredChatMessage
import com.m3.core.data.StoredProposal
import com.m3.core.data.TrainingSetupRepository
import com.m3.core.designsystem.config.Actor
import com.m3.core.designsystem.config.ApplyResult
import com.m3.core.designsystem.config.Specialist
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.UiConfigStore
import com.m3.core.designsystem.config.displayName
import com.m3.core.model.EquipmentKind
import com.m3.core.training.PlanDiff
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Saves the owner's message, runs the model and saves the Coach's reply. [onText] streams the raw answer. */
    suspend fun send(message: String, onText: (String) -> Unit) {
        val trimmed = message.trim().take(MAX_MESSAGE_CHARS)
        if (trimmed.isEmpty()) return
        val history = chat.observe(HISTORY_MESSAGES).first().map { ChatTurn(it.role, it.text) }
        chat.add(ChatAuthor.OWNER, trimmed)
        val inputs = currentInputs()
        val facts = facts(inputs)
        val raw = runtime.use { engine ->
            val streamed = StringBuilder()
            engine.generate(
                prompt = CoachPrompt.turn(history, trimmed),
                systemInstruction = CoachPrompt.system(facts),
                maxOutputTokens = MAX_OUTPUT_TOKENS,
                onText = { piece ->
                    streamed.append(piece)
                    onText(visibleSoFar(streamed.toString()))
                },
            ).text
        }
        val parsed = ReplyParser.parse(raw)
        val proposal = parsed.toolJson?.let { proposalFor(it, inputs, trimmed) }
        val text = parsed.text.ifBlank { proposal?.summary ?: "Sorry, I didn't get an answer. Please try again." }
        chat.add(ChatAuthor.COACH, text, proposal)
    }

    suspend fun apply(messageId: Long): String? {
        val message = chat.get(messageId) ?: return null
        val proposal = message.proposal ?: return null
        if (proposal.status != ProposalStatus.PENDING) return null
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
        val undo = applyAction(action, inputs) ?: run {
            chat.setProposalStatus(messageId, ProposalStatus.BLOCKED)
            return LOCKED_MESSAGE
        }
        chat.setProposalStatus(messageId, ProposalStatus.APPLIED, json.encodeToString(UndoInfo.serializer(), undo))
        return null
    }

    suspend fun dismiss(messageId: Long) {
        val proposal = chat.get(messageId)?.proposal ?: return
        if (proposal.status == ProposalStatus.PENDING) chat.setProposalStatus(messageId, ProposalStatus.DISMISSED)
    }

    /** Reverses an applied proposal. The owner's own action, so locks don't apply. */
    suspend fun undo(messageId: Long) {
        val proposal = chat.get(messageId)?.proposal ?: return
        if (proposal.status != ProposalStatus.APPLIED) return
        val info = proposal.undoJson?.let { runCatching { json.decodeFromString(UndoInfo.serializer(), it) }.getOrNull() } ?: return
        when (info) {
            is UndoInfo.Day -> overrides.setDayEquipment(
                LocalDate.parse(info.date),
                info.previousItems?.mapNotNull(EquipmentKind::fromNameOrNull)?.toSet(),
            )
            is UndoInfo.Exercise -> overrides.setExcluded(info.exerciseId, info.previousReason)
            is UndoInfo.Availability -> {
                val setup = setups.observe().first()
                val day = DayOfWeek.of(info.day)
                val previous = info.previousLocationId?.takeIf { id -> setup.locations.any { it.id == id } }
                val availability = if (previous == null) setup.availability - day else setup.availability + (day to previous)
                setups.replace(setup.copy(availability = availability))
            }
            is UndoInfo.Ui -> uiStore.apply(info.inverse, Actor.Owner)
        }
        chat.setProposalStatus(messageId, ProposalStatus.UNDONE)
    }

    suspend fun clear() = chat.clear()

    private suspend fun currentInputs(): PlanInputs = plans.observeInputs().first()

    private fun toolContext(inputs: PlanInputs) = ToolContext(LocalDate.now(), inputs.setup, plans.catalog)

    private fun facts(inputs: PlanInputs): CoachFacts {
        val config = uiStore.config.value
        return CoachFacts(
            today = LocalDate.now(),
            profile = inputs.profile,
            setup = inputs.setup,
            plan = inputs.plan,
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
                val status = if (action.lock in uiStore.config.value.locks) ProposalStatus.BLOCKED else ProposalStatus.PENDING
                StoredProposal(toolJson, status, summary(action), withContext(Dispatchers.Default) { planChanges(action, inputs) })
            }
        }

    private fun planChanges(action: CoachAction, inputs: PlanInputs): List<String> {
        val after = when (action) {
            is CoachAction.SetDayEquipment -> {
                // Compare within the target day's own week (it may be next week, e.g. "tomorrow" on a Sunday).
                val before = plans.preview(inputs.profile, inputs.setup, inputs.stored, today = action.date)
                val withDay = inputs.stored.copy(dayEquipment = inputs.stored.dayEquipment + (action.date to action.items))
                val after = plans.preview(inputs.profile, inputs.setup, withDay, today = action.date)
                return PlanDiff.describe(before, after).filter { it.startsWith(dayLabel(action.date.dayOfWeek)) }
                    .ifEmpty { listOf("No change: ${dayLabel(action.date.dayOfWeek)} is a rest day or already fits.") }
            }
            is CoachAction.AvoidExercise ->
                plans.preview(inputs.profile, inputs.setup, inputs.stored.copy(excluded = inputs.stored.excluded + (action.exerciseId to action.reason)))
            is CoachAction.AllowExercise ->
                plans.preview(inputs.profile, inputs.setup, inputs.stored.copy(excluded = inputs.stored.excluded - action.exerciseId))
            is CoachAction.SetAvailability -> {
                val availability = inputs.setup.availability.let { if (action.locationId == null) it - action.day else it + (action.day to action.locationId) }
                plans.preview(inputs.profile, inputs.setup.copy(availability = availability), inputs.stored)
            }
            is CoachAction.Ui -> return emptyList()
        }
        return PlanDiff.describe(inputs.plan, after).ifEmpty { listOf("No change to this week's sessions.") }
    }

    /** Applies [action] and returns how to undo it, or null if the store refused (a lock). */
    private suspend fun applyAction(action: CoachAction, inputs: PlanInputs): UndoInfo? = when (action) {
        is CoachAction.SetDayEquipment -> {
            val previous = overrides.dayEquipment(action.date)
            overrides.setDayEquipment(action.date, action.items)
            UndoInfo.Day(action.date.toString(), previous?.map { it.name })
        }
        is CoachAction.AvoidExercise -> {
            val previous = overrides.excludedReason(action.exerciseId)
            overrides.setExcluded(action.exerciseId, action.reason)
            UndoInfo.Exercise(action.exerciseId, previous)
        }
        is CoachAction.AllowExercise -> {
            val previous = overrides.excludedReason(action.exerciseId)
            overrides.setExcluded(action.exerciseId, null)
            UndoInfo.Exercise(action.exerciseId, previous)
        }
        is CoachAction.SetAvailability -> {
            val setup = inputs.setup
            val previous = setup.availability[action.day]
            val availability = if (action.locationId == null) setup.availability - action.day else setup.availability + (action.day to action.locationId)
            setups.replace(setup.copy(availability = availability))
            UndoInfo.Availability(action.day.value, previous)
        }
        is CoachAction.Ui -> {
            val inverse = inverseOf(action.change, uiStore.config.value)
            when (uiStore.apply(action.change, Actor.Agent(Specialist.COACH))) {
                is ApplyResult.Applied -> UndoInfo.Ui(inverse)
                is ApplyResult.Rejected -> null
            }
        }
    }

    companion object {
        const val MAX_MESSAGE_CHARS = 2_000
        const val MAX_OUTPUT_TOKENS = 400
        const val HISTORY_MESSAGES = 20
        const val LOCKED_MESSAGE = "You've locked this for agents (You → Customize), so nothing changed."

        /** The reply text without a tool block that is still being written. */
        fun visibleSoFar(raw: String): String {
            val start = raw.indexOf("<tool")
            if (start >= 0) return raw.substring(0, start).trim()
            // Also hide a tag that has only partly arrived ("<", "<t", "<to", "<too").
            val partial = (1 until TOOL_TAG.length).firstOrNull { raw.endsWith(TOOL_TAG.substring(0, TOOL_TAG.length - it)) }
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
            is CoachAction.AvoidExercise ->
                "Stop using ${action.exerciseName}" + if (action.reason.isBlank()) "" else " (${action.reason})"
            is CoachAction.AllowExercise -> "Use ${action.exerciseName} again"
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
