package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.PlayerColor
import com.example.online.OperationType
import com.example.online.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.matchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "ludo_match_history")

data class MatchPlayerDetail(
    val nickname: String,
    val color: PlayerColor,
    val isBot: Boolean = false,
    val rank: Int = 0,
    val capturesMade: Int = 0,
    val tokensHome: Int = 0,
    val powerUpsUsed: Int = 0,
    val score: Int = 0
)

data class MatchHistoryEntry(
    val id: String,
    val mode: GameModeId,
    val onlineMatch: Boolean,
    val roomCode: String,
    val winnerName: String,
    val winnerColor: PlayerColor,
    val won: Boolean,
    val rank: Int,
    val playerSummary: String,
    val turnsPlayed: Int,
    val playedAtMs: Long,
    val capturesMade: Int = 0,
    val tokensHome: Int = 0,
    val powerUpsUsed: Int = 0,
    val playerDetails: List<MatchPlayerDetail> = emptyList()
)

/**
 * Dual-backed Match History repository:
 * - Signed-in Google accounts store match history durably in Firestore (/users/{uid}/matchHistory/{matchId})
 * - Local/Guest sessions also store recent matches in DataStore so offline history always works
 */
class MatchHistoryRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private object Keys {
        val LOCAL_RECORDS_JSON = stringPreferencesKey("local_match_history_json")
    }

    val localHistoryFlow: Flow<List<MatchHistoryEntry>> = context.matchHistoryDataStore.data.map { prefs ->
        decodeLocalList(prefs[Keys.LOCAL_RECORDS_JSON].orEmpty())
    }

    fun observeCloudHistory(uid: String): Flow<List<MatchHistoryEntry>> = callbackFlow {
        val path = "users/$uid/matchHistory"
        val reg = firestore.collection("users")
            .document(uid)
            .collection("matchHistory")
            .addSnapshotListener { snaps, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                val items = snaps?.documents.orEmpty()
                    .mapNotNull(::parseCloudDoc)
                    .sortedByDescending { it.playedAtMs }
                    .take(100)
                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    suspend fun recordMatch(
        entry: MatchHistoryEntry,
        authenticatedUid: String?
    ): Result<Unit> {
        saveLocalEntry(entry)
        if (authenticatedUid.isNullOrBlank()) {
            return Result.success(Unit)
        }
        val cleanId = entry.id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .take(64)
            .ifBlank { "match_${System.currentTimeMillis()}" }
        val path = "users/$authenticatedUid/matchHistory/$cleanId"
        val playerMaps = entry.playerDetails.take(4).map { d ->
            mapOf(
                "nickname" to d.nickname.trim().take(24).ifBlank { d.color.displayName },
                "color" to d.color.name,
                "isBot" to d.isBot,
                "rank" to d.rank.coerceIn(0, 4),
                "capturesMade" to d.capturesMade.coerceIn(0, 500),
                "tokensHome" to d.tokensHome.coerceIn(0, 4),
                "powerUpsUsed" to d.powerUpsUsed.coerceIn(0, 200),
                "score" to d.score.coerceIn(-1000, 10000)
            )
        }
        return try {
            firestore.collection("users")
                .document(authenticatedUid)
                .collection("matchHistory")
                .document(cleanId)
                .set(
                    mapOf(
                        "userId" to authenticatedUid,
                        "mode" to entry.mode.name,
                        "onlineMatch" to entry.onlineMatch,
                        "roomCode" to entry.roomCode.take(6),
                        "winnerName" to entry.winnerName.trim().take(40).ifBlank { "Player" },
                        "winnerColor" to entry.winnerColor.name,
                        "won" to entry.won,
                        "rank" to entry.rank.coerceIn(0, 4),
                        "playerSummary" to entry.playerSummary.take(180),
                        "turnsPlayed" to entry.turnsPlayed.coerceIn(0, 5000),
                        "capturesMade" to entry.capturesMade.coerceIn(0, 500),
                        "tokensHome" to entry.tokensHome.coerceIn(0, 4),
                        "powerUpsUsed" to entry.powerUpsUsed.coerceIn(0, 200),
                        "players" to playerMaps,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun saveLocalEntry(entry: MatchHistoryEntry) {
        context.matchHistoryDataStore.edit { prefs ->
            val current = decodeLocalList(prefs[Keys.LOCAL_RECORDS_JSON].orEmpty())
            val merged = (listOf(entry) + current.filterNot { it.id == entry.id }).take(60)
            prefs[Keys.LOCAL_RECORDS_JSON] = encodeLocalList(merged)
        }
    }

    private fun parseCloudDoc(doc: DocumentSnapshot): MatchHistoryEntry? {
        if (!doc.exists()) return null
        val est = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        val mode = GameModeId.entries.firstOrNull { it.name == doc.getString("mode") } ?: GameModeId.CLASSIC
        val color = PlayerColor.entries.firstOrNull { it.name == doc.getString("winnerColor") } ?: PlayerColor.RED
        val ts = doc.getTimestamp("createdAt", est) ?: Timestamp.now()
        val rawPlayers = doc.get("players") as? List<*>
        val playerDetails = rawPlayers.orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val pColor = PlayerColor.entries.firstOrNull { it.name == m["color"] as? String }
                ?: return@mapNotNull null
            MatchPlayerDetail(
                nickname = (m["nickname"] as? String).orEmpty().ifBlank { pColor.displayName },
                color = pColor,
                isBot = (m["isBot"] as? Boolean) ?: false,
                rank = (m["rank"] as? Number)?.toInt() ?: 0,
                capturesMade = (m["capturesMade"] as? Number)?.toInt() ?: 0,
                tokensHome = (m["tokensHome"] as? Number)?.toInt() ?: 0,
                powerUpsUsed = (m["powerUpsUsed"] as? Number)?.toInt() ?: 0,
                score = (m["score"] as? Number)?.toInt() ?: 0
            )
        }
        return MatchHistoryEntry(
            id = doc.id,
            mode = mode,
            onlineMatch = doc.getBoolean("onlineMatch") ?: false,
            roomCode = doc.getString("roomCode").orEmpty(),
            winnerName = doc.getString("winnerName") ?: "Player",
            winnerColor = color,
            won = doc.getBoolean("won") ?: false,
            rank = (doc.getLong("rank") ?: 0L).toInt(),
            playerSummary = doc.getString("playerSummary").orEmpty(),
            turnsPlayed = (doc.getLong("turnsPlayed") ?: 0L).toInt(),
            playedAtMs = ts.toDate().time,
            capturesMade = (doc.getLong("capturesMade") ?: 0L).toInt(),
            tokensHome = (doc.getLong("tokensHome") ?: 0L).toInt(),
            powerUpsUsed = (doc.getLong("powerUpsUsed") ?: 0L).toInt(),
            playerDetails = playerDetails
        )
    }

    companion object {
        fun buildEntryFromGameState(
            finalState: GameState,
            onlineMatch: Boolean,
            roomCode: String = "",
            localColor: PlayerColor? = null
        ): MatchHistoryEntry {
            val rankedPlayers = buildList {
                val seen = mutableSetOf<PlayerColor>()
                for (c in finalState.finishOrder) {
                    finalState.playerByColor(c)?.let {
                        add(it)
                        seen += c
                    }
                }
                for (p in finalState.players) {
                    if (p.color !in seen) add(p)
                }
            }
            val winner = rankedPlayers.firstOrNull()
            val winnerColor = winner?.color ?: PlayerColor.RED
            val winnerName = finalState.winningTeamLabel ?: winner?.nickname ?: winnerColor.displayName

            val targetPlayer = (localColor?.let { finalState.playerByColor(it) })
                ?: finalState.players.firstOrNull { !it.setup.isBot }
                ?: rankedPlayers.firstOrNull()
            val targetColor = targetPlayer?.color
            val rankIndex = if (targetColor != null) rankedPlayers.indexOfFirst { it.color == targetColor } else -1
            val rank = if (rankIndex >= 0) rankIndex + 1 else 0
            val won = when {
                targetColor == null -> false
                finalState.mode == GameModeId.TEAMS_2V2 ->
                    targetColor == winnerColor || targetColor == winnerColor.opposite()
                else -> targetColor == winnerColor
            }

            val playerDetails = rankedPlayers.mapIndexed { idx, p ->
                MatchPlayerDetail(
                    nickname = p.nickname.take(24),
                    color = p.color,
                    isBot = p.setup.isBot,
                    rank = idx + 1,
                    capturesMade = p.capturesMade,
                    tokensHome = p.finishedTokenCount,
                    powerUpsUsed = p.powerUpsUsed,
                    score = p.score
                )
            }

            val summary = rankedPlayers.mapIndexed { idx, p ->
                val ordinal = when (idx) {
                    0 -> "1st"
                    1 -> "2nd"
                    2 -> "3rd"
                    else -> "4th"
                }
                "${p.nickname} ($ordinal)"
            }.joinToString(" • ").take(180)

            val idPrefix = if (onlineMatch && roomCode.isNotBlank()) "online_${roomCode}_" else "local_"
            val uniqueId = "${idPrefix}${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

            return MatchHistoryEntry(
                id = uniqueId,
                mode = finalState.mode,
                onlineMatch = onlineMatch,
                roomCode = roomCode.trim().uppercase().take(6),
                winnerName = winnerName.take(40),
                winnerColor = winnerColor,
                won = won,
                rank = rank,
                playerSummary = summary,
                turnsPlayed = finalState.turnNumber.coerceAtLeast(1),
                playedAtMs = System.currentTimeMillis(),
                capturesMade = targetPlayer?.capturesMade ?: 0,
                tokensHome = targetPlayer?.finishedTokenCount ?: 0,
                powerUpsUsed = targetPlayer?.powerUpsUsed ?: 0,
                playerDetails = playerDetails
            )
        }

        fun encodeLocalList(items: List<MatchHistoryEntry>): String {
            val arr = JSONArray()
            for (item in items) {
                val playersArr = JSONArray()
                for (p in item.playerDetails) {
                    playersArr.put(
                        JSONObject().apply {
                            put("nickname", p.nickname)
                            put("color", p.color.name)
                            put("isBot", p.isBot)
                            put("rank", p.rank)
                            put("capturesMade", p.capturesMade)
                            put("tokensHome", p.tokensHome)
                            put("powerUpsUsed", p.powerUpsUsed)
                            put("score", p.score)
                        }
                    )
                }
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("mode", item.mode.name)
                        put("onlineMatch", item.onlineMatch)
                        put("roomCode", item.roomCode)
                        put("winnerName", item.winnerName)
                        put("winnerColor", item.winnerColor.name)
                        put("won", item.won)
                        put("rank", item.rank)
                        put("playerSummary", item.playerSummary)
                        put("turnsPlayed", item.turnsPlayed)
                        put("playedAtMs", item.playedAtMs)
                        put("capturesMade", item.capturesMade)
                        put("tokensHome", item.tokensHome)
                        put("powerUpsUsed", item.powerUpsUsed)
                        put("players", playersArr)
                    }
                )
            }
            return arr.toString()
        }

        fun decodeLocalList(raw: String): List<MatchHistoryEntry> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                List(arr.length()) { idx ->
                    val o = arr.getJSONObject(idx)
                    val pArr = o.optJSONArray("players")
                    val details = if (pArr != null) {
                        List(pArr.length()) { pIdx ->
                            val po = pArr.getJSONObject(pIdx)
                            val col = PlayerColor.entries.firstOrNull { it.name == po.optString("color") }
                                ?: PlayerColor.RED
                            MatchPlayerDetail(
                                nickname = po.optString("nickname", col.displayName),
                                color = col,
                                isBot = po.optBoolean("isBot", false),
                                rank = po.optInt("rank", pIdx + 1),
                                capturesMade = po.optInt("capturesMade", 0),
                                tokensHome = po.optInt("tokensHome", 0),
                                powerUpsUsed = po.optInt("powerUpsUsed", 0),
                                score = po.optInt("score", 0)
                            )
                        }
                    } else emptyList()

                    MatchHistoryEntry(
                        id = o.optString("id", "local_$idx"),
                        mode = GameModeId.entries.firstOrNull { it.name == o.optString("mode") }
                            ?: GameModeId.CLASSIC,
                        onlineMatch = o.optBoolean("onlineMatch", false),
                        roomCode = o.optString("roomCode", ""),
                        winnerName = o.optString("winnerName", "Player"),
                        winnerColor = PlayerColor.entries.firstOrNull { it.name == o.optString("winnerColor") }
                            ?: PlayerColor.RED,
                        won = o.optBoolean("won", false),
                        rank = o.optInt("rank", 0),
                        playerSummary = o.optString("playerSummary", ""),
                        turnsPlayed = o.optInt("turnsPlayed", 1),
                        playedAtMs = o.optLong("playedAtMs", 0L),
                        capturesMade = o.optInt("capturesMade", 0),
                        tokensHome = o.optInt("tokensHome", 0),
                        powerUpsUsed = o.optInt("powerUpsUsed", 0),
                        playerDetails = details
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
