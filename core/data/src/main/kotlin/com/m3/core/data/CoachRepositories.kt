package com.m3.core.data

import androidx.room.withTransaction
import com.m3.core.data.db.ChatMessageEntity
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.DayOverrideEntity
import com.m3.core.data.db.ExcludedExerciseEntity
import com.m3.core.model.EquipmentKind
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class ChatAuthor { OWNER, COACH }

enum class ProposalStatus { PENDING, APPLIED, DISMISSED, UNDONE, REJECTED, BLOCKED }

data class StoredProposal(
    /** The model's tool JSON; null when the reply's tool couldn't be read at all. */
    val json: String?,
    val status: ProposalStatus,
    val summary: String,
    val details: List<String>,
    val undoJson: String? = null,
)

data class StoredChatMessage(
    val id: Long,
    val author: ChatAuthor,
    val text: String,
    val createdAt: Long,
    val proposal: StoredProposal?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(
    private val opener: DatabaseOpener,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(limit: Int = DEFAULT_LIMIT): Flow<List<StoredChatMessage>> =
        flow { emit(opener.get()) }.flatMapLatest { db -> db.chatDao().observeRecent(limit).map { rows -> rows.map(::toModel) } }

    suspend fun add(author: ChatAuthor, text: String, proposal: StoredProposal? = null): Long =
        opener.get().chatDao().insert(
            ChatMessageEntity(
                role = author.name.lowercase(),
                text = text,
                createdAt = clock(),
                proposalJson = proposal?.json,
                proposalStatus = proposal?.status?.name,
                proposalSummary = proposal?.summary,
                proposalDetails = proposal?.details?.joinToString("\n"),
                undoJson = proposal?.undoJson,
            ),
        )

    /** Saves the owner's message and the Coach's reply together, so a turn is never half-saved. */
    suspend fun addTurn(ownerText: String, coachText: String, proposal: StoredProposal?) {
        val db = opener.get()
        db.withTransaction {
            add(ChatAuthor.OWNER, ownerText)
            add(ChatAuthor.COACH, coachText, proposal)
        }
    }

    suspend fun latestAppliedId(): Long? = opener.get().chatDao().latestAppliedId()

    suspend fun get(id: Long): StoredChatMessage? = opener.get().chatDao().get(id)?.let(::toModel)

    suspend fun setProposalStatus(id: Long, status: ProposalStatus, undoJson: String? = null) {
        val dao = opener.get().chatDao()
        val row = dao.get(id) ?: return
        dao.update(row.copy(proposalStatus = status.name, undoJson = undoJson ?: row.undoJson))
    }

    suspend fun clear() = opener.get().chatDao().clear()

    private fun toModel(row: ChatMessageEntity) = StoredChatMessage(
        id = row.id,
        author = if (row.role == "owner") ChatAuthor.OWNER else ChatAuthor.COACH,
        text = row.text,
        createdAt = row.createdAt,
        proposal = row.proposalStatus?.let { status ->
            StoredProposal(
                json = row.proposalJson,
                status = ProposalStatus.entries.firstOrNull { it.name == status } ?: ProposalStatus.REJECTED,
                summary = row.proposalSummary.orEmpty(),
                details = row.proposalDetails?.split("\n")?.filter { it.isNotBlank() }.orEmpty(),
                undoJson = row.undoJson,
            )
        },
    )

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}

/** What the Coach has changed on top of the base plan. */
data class StoredOverrides(
    val dayEquipment: Map<LocalDate, Set<EquipmentKind>> = emptyMap(),
    /** Exercise id → reason. */
    val excluded: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanOverridesRepository(
    private val opener: DatabaseOpener,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val itemsSerializer = ListSerializer(String.serializer())

    fun observe(): Flow<StoredOverrides> = flow { emit(opener.get()) }.flatMapLatest { db ->
        val dao = db.planOverridesDao()
        combine(dao.observeDayOverrides(), dao.observeExcluded()) { days, excluded ->
            StoredOverrides(
                dayEquipment = days.mapNotNull { row ->
                    val date = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: return@mapNotNull null
                    date to decodeItems(row.itemsJson)
                }.toMap(),
                excluded = excluded.associate { it.exerciseId to it.reason },
            )
        }
    }

    suspend fun dayEquipment(date: LocalDate): Set<EquipmentKind>? =
        opener.get().planOverridesDao().dayOverride(date.toString())?.let { decodeItems(it.itemsJson) }

    /** Null removes the override, so the place's usual equipment applies again. */
    suspend fun setDayEquipment(date: LocalDate, items: Set<EquipmentKind>?) {
        val dao = opener.get().planOverridesDao()
        if (items == null) {
            dao.deleteDayOverride(date.toString())
        } else {
            val json = Json.encodeToString(itemsSerializer, items.map { it.name }.sorted())
            dao.replaceDayOverride(DayOverrideEntity(date = date.toString(), itemsJson = json, createdAt = clock()))
        }
    }

    suspend fun excludedReason(exerciseId: String): String? = opener.get().planOverridesDao().excluded(exerciseId)?.reason

    /** Null reason allows the exercise again. */
    suspend fun setExcluded(exerciseId: String, reason: String?) {
        val dao = opener.get().planOverridesDao()
        if (reason == null) dao.deleteExcluded(exerciseId) else dao.upsertExcluded(ExcludedExerciseEntity(exerciseId, reason, clock()))
    }

    /** Day overrides only matter for the current week; older ones are dropped. */
    suspend fun pruneBefore(date: LocalDate) = opener.get().planOverridesDao().deleteDayOverridesBefore(date.toString())

    private fun decodeItems(json: String): Set<EquipmentKind> =
        runCatching { Json.decodeFromString(itemsSerializer, json) }.getOrDefault(emptyList())
            .mapNotNull(EquipmentKind::fromNameOrNull).toSet()
}
