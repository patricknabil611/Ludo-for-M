package com.example.online

import androidx.annotation.Keep
import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.PlayerColor
import com.example.engine.PlayerControllerType
import com.example.engine.PlayerSetup
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Private authenticated user profile stored at /users/{userId}.
 * Avoids `is`-prefixed Boolean properties for 1:1 Firestore serialization parity.
 */
@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val nickname: String = "Player",
    val googleLinked: Boolean = true,
    val wins: Long = 0L,
    val gamesPlayed: Long = 0L,
    val weeklyWins: Long = 0L,
    val weeklyGamesPlayed: Long = 0L,
    val weekKey: String = WeekKeyUtil.currentWeekKey(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Public non-PII leaderboard entry stored at /leaderboard/{userId} (Google accounts only).
 */
@Keep
@IgnoreExtraProperties
data class LeaderboardEntryDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val nickname: String = "Player",
    val googleLinked: Boolean = true,
    val wins: Long = 0L,
    val gamesPlayed: Long = 0L,
    val weeklyWins: Long = 0L,
    val weeklyGamesPlayed: Long = 0L,
    val weekKey: String = WeekKeyUtil.currentWeekKey(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun winRatePercent(weekly: Boolean): Int {
        val played = if (weekly) weeklyGamesPlayed else gamesPlayed
        val won = if (weekly) weeklyWins else wins
        if (played <= 0L) return 0
        return ((won * 100.0) / played.toDouble()).toInt().coerceIn(0, 100)
    }
}

data class RankedLeaderboardRow(
    val rank: Int,
    val entry: LeaderboardEntryDoc,
    val wins: Int,
    val gamesPlayed: Int,
    val winRatePercent: Int
)

data class LeaderboardSnapshot(
    val topEntries: List<RankedLeaderboardRow> = emptyList(),
    val myRow: RankedLeaderboardRow? = null,
    val totalPlayers: Int = 0
)

/**
 * Local Guest profile persisted in DataStore (never uses anonymous Firebase Auth).
 * Accumulated local stats are seamlessly migrated when the user upgrades to Google Sign-In.
 */
data class GuestProfile(
    val nickname: String = "",
    val guestModeActive: Boolean = false,
    val wins: Int = 0,
    val gamesPlayed: Int = 0,
    val weeklyWins: Int = 0,
    val weeklyGamesPlayed: Int = 0,
    val weekKey: String = WeekKeyUtil.currentWeekKey()
)

enum class RoomStatus {
    LOBBY,
    PLAYING,
    FINISHED,
    CLOSED;

    companion object {
        fun fromRaw(raw: String?): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: LOBBY
    }
}

enum class SeatControl {
    HUMAN,
    BOT,
    OPEN;

    companion object {
        fun fromRaw(raw: String?): SeatControl =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class OnlineActionType {
    NONE,
    START,
    ROLL,
    MOVE,
    SHIELD,
    DOUBLE_DICE,
    BOMB,
    TIMEOUT,
    BOT_TAKEOVER,
    RECONNECT,
    REMATCH,
    LEAVE;

    companion object {
        fun fromRaw(raw: String?): OnlineActionType =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

data class OnlineRoomSeat(
    val seatIndex: Int,
    val color: PlayerColor,
    val uid: String = "",
    val nickname: String = "Open Seat",
    val controllerType: SeatControl = SeatControl.OPEN,
    val botDifficulty: BotDifficulty = BotDifficulty.MEDIUM,
    val connected: Boolean = false,
    val botTakeover: Boolean = false,
    val consecutiveMisses: Int = 0,
    val lastHeartbeatMs: Long = 0L,
    val rematchVoted: Boolean = false
) {
    val isEffectiveBot: Boolean
        get() = controllerType == SeatControl.BOT || botTakeover

    val effectiveBotDifficulty: BotDifficulty
        get() = if (botTakeover) BotDifficulty.MEDIUM else botDifficulty

    fun toPlayerSetup(): PlayerSetup = PlayerSetup(
        seatIndex = seatIndex,
        nickname = if (botTakeover) "$nickname (Bot)" else nickname,
        color = color,
        controllerType = if (isEffectiveBot) PlayerControllerType.BOT else PlayerControllerType.ONLINE_REMOTE,
        botDifficulty = effectiveBotDifficulty
    )

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "seatIndex" to seatIndex,
        "color" to color.name,
        "uid" to uid,
        "nickname" to nickname,
        "controllerType" to controllerType.name,
        "botDifficulty" to botDifficulty.name,
        "connected" to connected,
        "botTakeover" to botTakeover,
        "consecutiveMisses" to consecutiveMisses,
        "lastHeartbeatMs" to lastHeartbeatMs,
        "rematchVoted" to rematchVoted
    )

    companion object {
        fun fromFirestoreMap(map: Map<*, *>): OnlineRoomSeat {
            val color = PlayerColor.entries.firstOrNull { it.name == map["color"] as? String } ?: PlayerColor.RED
            val diff = BotDifficulty.entries.firstOrNull { it.name == map["botDifficulty"] as? String }
                ?: BotDifficulty.MEDIUM
            return OnlineRoomSeat(
                seatIndex = (map["seatIndex"] as? Number)?.toInt() ?: 0,
                color = color,
                uid = (map["uid"] as? String).orEmpty(),
                nickname = (map["nickname"] as? String).orEmpty().ifBlank { "Player" },
                controllerType = SeatControl.fromRaw(map["controllerType"] as? String),
                botDifficulty = diff,
                connected = (map["connected"] as? Boolean) ?: false,
                botTakeover = (map["botTakeover"] as? Boolean) ?: false,
                consecutiveMisses = (map["consecutiveMisses"] as? Number)?.toInt() ?: 0,
                lastHeartbeatMs = (map["lastHeartbeatMs"] as? Number)?.toLong() ?: 0L,
                rematchVoted = (map["rematchVoted"] as? Boolean) ?: false
            )
        }
    }
}

data class OnlineRoom(
    val roomCode: String,
    val hostUid: String,
    val mode: GameModeId,
    val targetPlayerCount: Int,
    val timedDurationSeconds: Int,
    val status: RoomStatus,
    val publicMatchmaking: Boolean,
    val memberUids: List<String>,
    val seats: List<OnlineRoomSeat>,
    val diceSeed: Long,
    val rollSeq: Int,
    val turnSeq: Int,
    val lastActionType: OnlineActionType,
    val lastActionSummary: String,
    val lastMovedColor: PlayerColor?,
    val lastMovedTokenId: Int,
    val lastMovedFromProgress: Int,
    val lastMovedToProgress: Int,
    val gameState: GameState?,
    val turnStartedAt: Timestamp?,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?
) {
    fun seatForUid(uid: String): OnlineRoomSeat? =
        seats.firstOrNull { it.uid == uid }

    fun seatForColor(color: PlayerColor): OnlineRoomSeat? =
        seats.firstOrNull { it.color == color }

    /**
     * Lowest-index connected human player acts as the deterministic coordinator for bot turns
     * and disconnected-player timeouts (protected by Firestore transaction `turnSeq` guards).
     */
    fun isBotCoordinator(uid: String): Boolean {
        val coordinator = seats
            .filter { it.controllerType == SeatControl.HUMAN && it.uid.isNotBlank() && it.connected && !it.botTakeover }
            .minByOrNull { it.seatIndex }
            ?: seats.firstOrNull { it.uid in memberUids }
        return coordinator?.uid == uid
    }
}

object WeekKeyUtil {
    fun currentWeekKey(timeMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = timeMillis
        }
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }
}

/**
 * Deterministic, verifiable fair dice roller seeded with Firestore's server `turnStartedAt`
 * timestamp + room `diceSeed` + monotonic `rollSeq` so no client can forge or cherry-pick rolls.
 */
object FairDiceRoller {
    fun computeSingleRoll(
        roomCode: String,
        diceSeed: Long,
        rollSeq: Int,
        turnStartedAtSeconds: Long,
        activeColor: PlayerColor,
        dieSlot: Int = 1
    ): Int {
        val input = "$roomCode:$diceSeed:$rollSeq:$turnStartedAtSeconds:${activeColor.name}:$dieSlot"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val unsigned = ((digest[0].toInt() and 0xFF) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
        return (Math.floorMod(unsigned, 6)) + 1
    }

    fun computeDoubleRoll(
        roomCode: String,
        diceSeed: Long,
        rollSeq: Int,
        turnStartedAtSeconds: Long,
        activeColor: PlayerColor
    ): Pair<Int, Int> {
        val d1 = computeSingleRoll(roomCode, diceSeed, rollSeq, turnStartedAtSeconds, activeColor, dieSlot = 1)
        val d2 = computeSingleRoll(roomCode, diceSeed, rollSeq, turnStartedAtSeconds, activeColor, dieSlot = 2)
        return d1 to d2
    }

    fun generateRoomCode(random: kotlin.random.Random = kotlin.random.Random.Default): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }
}
