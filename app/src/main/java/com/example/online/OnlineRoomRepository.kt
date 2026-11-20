package com.example.online

import android.content.Context
import com.example.R
import com.example.bot.BotDecisionEngine
import com.example.bot.BotPowerUpAction
import com.example.bot.LudoBotBrain
import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.engine.PowerUpType
import com.example.engine.RollOutcome
import com.example.engine.RuleSetRegistry
import com.example.engine.TurnPhase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Authoritative Firestore multiplayer repository for /rooms/{roomCode}.
 * Handles private room codes, public matchmaking, atomic turn transitions,
 * server-timestamped fair dice, 30s timeout skips, 3-miss/60s-disconnect Medium bot takeover,
 * reconnection, host handoff, and unanimous post-game rematch voting.
 */
class OnlineRoomRepository(
    private val firestore: FirebaseFirestore,
    private val botBrain: BotDecisionEngine = LudoBotBrain()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val roomsCol get() = firestore.collection("rooms")

    fun observeRoom(roomCode: String): Flow<OnlineRoom?> = callbackFlow {
        val code = roomCode.trim().uppercase()
        val path = "rooms/$code"
        val reg = roomsCol.document(code).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, path)
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.let(::parseRoom))
        }
        awaitClose { reg.remove() }
    }

    fun observeMyActiveRooms(uid: String): Flow<List<OnlineRoom>> = callbackFlow {
        val query = roomsCol.whereArrayContains("memberUids", uid)
        val reg = query.addSnapshotListener { snaps, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "rooms")
                close(error)
                return@addSnapshotListener
            }
            val rooms = snaps?.documents.orEmpty()
                .mapNotNull(::parseRoom)
                .filter { it.status == RoomStatus.LOBBY || it.status == RoomStatus.PLAYING }
                .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            trySend(rooms)
        }
        awaitClose { reg.remove() }
    }

    /**
     * Checks if the player has a live PLAYING match to reconnect to when the app reopens.
     * Checks [preferredRoomCode] first, then scans the player's member rooms.
     */
    suspend fun findRunningRoomForUser(uid: String, preferredRoomCode: String = ""): OnlineRoom? {
        if (uid.isBlank()) return null
        return try {
            val clean = preferredRoomCode.trim().uppercase()
            if (clean.length == 6) {
                val snap = roomsCol.document(clean).get().await()
                val direct = parseRoom(snap)
                if (direct != null && direct.status == RoomStatus.PLAYING && uid in direct.memberUids) {
                    return direct
                }
            }
            val memberSnap = roomsCol.whereArrayContains("memberUids", uid).get().await()
            memberSnap.documents
                .mapNotNull(::parseRoom)
                .filter { it.status == RoomStatus.PLAYING }
                .maxByOrNull { it.updatedAt?.seconds ?: 0L }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rooms")
            null
        }
    }

    suspend fun createPrivateRoom(
        hostUid: String,
        hostNickname: String,
        mode: GameModeId,
        targetPlayerCount: Int,
        timedDurationSeconds: Int
    ): Result<String> {
        return createRoomInternal(
            hostUid = hostUid,
            hostNickname = hostNickname,
            mode = mode,
            targetPlayerCount = targetPlayerCount,
            timedDurationSeconds = timedDurationSeconds,
            publicMatchmaking = false
        )
    }

    /**
     * Searches open public matchmaking lobbies matching [mode] & [targetPlayerCount],
     * atomically joins the best candidate (auto-starting if full), or creates a new public lobby.
     */
    suspend fun findOrCreateMatchmakingRoom(
        uid: String,
        nickname: String,
        mode: GameModeId,
        targetPlayerCount: Int,
        timedDurationSeconds: Int = 300
    ): Result<String> {
        val effectiveCount = if (mode == GameModeId.TEAMS_2V2) 4 else targetPlayerCount.coerceIn(2, 4)
        return try {
            val openSnap = roomsCol
                .whereEqualTo("publicMatchmaking", true)
                .whereEqualTo("status", RoomStatus.LOBBY.name)
                .get()
                .await()

            val nowMs = System.currentTimeMillis()
            val candidates = openSnap.documents
                .mapNotNull(::parseRoom)
                .filter { room ->
                    room.mode == mode &&
                        room.targetPlayerCount == effectiveCount &&
                        (room.memberUids.contains(uid) || room.memberUids.size < room.targetPlayerCount) &&
                        ((nowMs - (room.updatedAt?.toDate()?.time ?: nowMs)) < 10 * 60_000L)
                }
                .sortedByDescending { it.memberUids.size }

            for (candidate in candidates) {
                if (uid in candidate.memberUids) {
                    return Result.success(candidate.roomCode)
                }
                val joinRes = joinRoomByCode(candidate.roomCode, uid, nickname, autoStartWhenFull = true)
                if (joinRes.isSuccess) {
                    return Result.success(candidate.roomCode)
                }
            }

            createRoomInternal(
                hostUid = uid,
                hostNickname = nickname,
                mode = mode,
                targetPlayerCount = effectiveCount,
                timedDurationSeconds = timedDurationSeconds,
                publicMatchmaking = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rooms")
            Result.failure(e)
        }
    }

    private suspend fun createRoomInternal(
        hostUid: String,
        hostNickname: String,
        mode: GameModeId,
        targetPlayerCount: Int,
        timedDurationSeconds: Int,
        publicMatchmaking: Boolean
    ): Result<String> {
        val count = if (mode == GameModeId.TEAMS_2V2) 4 else targetPlayerCount.coerceIn(2, 4)
        val cleanName = hostNickname.trim().take(24).ifBlank { "Player" }
        val duration = timedDurationSeconds.coerceIn(60, 1800)

        repeat(5) {
            val code = FairDiceRoller.generateRoomCode()
            val ref = roomsCol.document(code)
            try {
                val created = firestore.runTransaction { tx ->
                    val existing = tx.get(ref)
                    if (existing.exists()) return@runTransaction false

                    val seats = buildInitialSeats(count, hostUid, cleanName)
                    val seed = Random.nextLong(1L, Long.MAX_VALUE)
                    tx.set(
                        ref,
                        mapOf(
                            "roomCode" to code,
                            "hostUid" to hostUid,
                            "mode" to mode.name,
                            "targetPlayerCount" to count,
                            "timedDurationSeconds" to duration,
                            "status" to RoomStatus.LOBBY.name,
                            "publicMatchmaking" to publicMatchmaking,
                            "memberUids" to listOf(hostUid),
                            "seats" to seats.map { it.toFirestoreMap() },
                            "diceSeed" to seed,
                            "rollSeq" to 0,
                            "turnSeq" to 0,
                            "lastActionType" to OnlineActionType.NONE.name,
                            "lastActionSummary" to "$cleanName created the room",
                            "lastMovedColor" to "",
                            "lastMovedTokenId" to -1,
                            "lastMovedFromProgress" to -1,
                            "lastMovedToProgress" to -1,
                            "gameStateMap" to emptyMap<String, Any>(),
                            "turnStartedAt" to FieldValue.serverTimestamp(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    true
                }.await()
                if (created) return Result.success(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, "rooms/$code")
                return Result.failure(e)
            }
        }
        return Result.failure(IllegalStateException("Could not allocate a unique room code. Please try again."))
    }

    suspend fun joinRoomByCode(
        roomCode: String,
        uid: String,
        nickname: String,
        autoStartWhenFull: Boolean = false
    ): Result<OnlineRoom> {
        val code = roomCode.trim().uppercase()
        if (!Regex("^[A-Z0-9]{6}$").matches(code)) {
            return Result.failure(IllegalArgumentException("Enter a valid 6-character room code."))
        }
        val cleanName = nickname.trim().take(24).ifBlank { "Player" }
        val ref = roomsCol.document(code)

        return try {
            val joinedRoom = firestore.runTransaction { tx ->
                val snap = tx.get(ref)
                val room = parseRoom(snap)
                    ?: throw IllegalStateException("Room $code not found.")

                // Rejoin if already a member
                if (uid in room.memberUids) {
                    if (room.status == RoomStatus.CLOSED) {
                        throw IllegalStateException("This room has already closed.")
                    }
                    return@runTransaction room
                }

                if (room.status != RoomStatus.LOBBY) {
                    throw IllegalStateException("This match has already started.")
                }

                val openIdx = room.seats.indexOfFirst { it.controllerType == SeatControl.OPEN }
                if (openIdx < 0 || room.memberUids.size >= room.targetPlayerCount) {
                    throw IllegalStateException("Room $code is already full.")
                }

                val nowMs = System.currentTimeMillis()
                val updatedSeats = room.seats.mapIndexed { idx, seat ->
                    if (idx == openIdx) {
                        seat.copy(
                            uid = uid,
                            nickname = cleanName,
                            controllerType = SeatControl.HUMAN,
                            connected = true,
                            botTakeover = false,
                            consecutiveMisses = 0,
                            lastHeartbeatMs = nowMs,
                            rematchVoted = false
                        )
                    } else seat
                }
                val updatedMembers = room.memberUids + uid
                val shouldAutoStart = autoStartWhenFull &&
                    room.publicMatchmaking &&
                    updatedSeats.none { it.controllerType == SeatControl.OPEN }

                if (shouldAutoStart) {
                    val freshState = createAuthoritativeGameState(
                        seats = updatedSeats,
                        mode = room.mode,
                        timedDurationSeconds = room.timedDurationSeconds,
                        seed = room.diceSeed
                    )
                    tx.update(
                        ref,
                        mapOf(
                            "memberUids" to updatedMembers,
                            "seats" to updatedSeats.map { it.toFirestoreMap() },
                            "status" to RoomStatus.PLAYING.name,
                            "turnSeq" to room.turnSeq + 1,
                            "lastActionType" to OnlineActionType.START.name,
                            "lastActionSummary" to "Match found! ${freshState.activePlayer.nickname}'s turn!",
                            "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(freshState),
                            "turnStartedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    room.copy(
                        memberUids = updatedMembers,
                        seats = updatedSeats,
                        status = RoomStatus.PLAYING,
                        gameState = freshState
                    )
                } else {
                    tx.update(
                        ref,
                        mapOf(
                            "memberUids" to updatedMembers,
                            "seats" to updatedSeats.map { it.toFirestoreMap() },
                            "lastActionSummary" to "$cleanName joined the lobby",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    room.copy(
                        memberUids = updatedMembers,
                        seats = updatedSeats
                    )
                }
            }.await()

            Result.success(joinedRoom)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    suspend fun setLobbySeatBot(
        roomCode: String,
        hostUid: String,
        seatIndex: Int,
        difficulty: BotDifficulty?
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.LOBBY || room.hostUid != hostUid) return@runTransaction
                val updatedSeats = room.seats.map { seat ->
                    if (seat.seatIndex == seatIndex && seat.controllerType != SeatControl.HUMAN) {
                        if (difficulty != null) {
                            seat.copy(
                                uid = "",
                                nickname = "${seat.color.displayName} Bot",
                                controllerType = SeatControl.BOT,
                                botDifficulty = difficulty,
                                connected = true,
                                botTakeover = false
                            )
                        } else {
                            seat.copy(
                                uid = "",
                                nickname = "Open Seat",
                                controllerType = SeatControl.OPEN,
                                botDifficulty = BotDifficulty.MEDIUM,
                                connected = false,
                                botTakeover = false
                            )
                        }
                    } else seat
                }
                tx.update(
                    ref,
                    mapOf(
                        "seats" to updatedSeats.map { it.toFirestoreMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Starts a lobby match. If [fillOpenSeatsWithBots] is true (used by matchmaking 30s fallback
     * or when host starts with fewer players than target), open seats are filled with bots.
     */
    suspend fun startLobbyMatch(
        roomCode: String,
        actorUid: String,
        fillOpenSeatsWithBots: Boolean = true,
        defaultBotDifficulty: BotDifficulty = BotDifficulty.MEDIUM
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.LOBBY) return@runTransaction
                if (actorUid !in room.memberUids) return@runTransaction
                if (!room.publicMatchmaking && room.hostUid != actorUid) return@runTransaction

                val nowMs = System.currentTimeMillis()
                val finalizedSeats = room.seats.map { seat ->
                    when {
                        seat.controllerType == SeatControl.HUMAN -> seat.copy(
                            connected = true,
                            botTakeover = false,
                            consecutiveMisses = 0,
                            lastHeartbeatMs = nowMs,
                            rematchVoted = false
                        )
                        seat.controllerType == SeatControl.BOT -> seat.copy(
                            connected = true,
                            botTakeover = false,
                            rematchVoted = false
                        )
                        fillOpenSeatsWithBots -> seat.copy(
                            uid = "",
                            nickname = "${seat.color.displayName} Bot",
                            controllerType = SeatControl.BOT,
                            botDifficulty = defaultBotDifficulty,
                            connected = true,
                            botTakeover = false,
                            rematchVoted = false
                        )
                        else -> seat
                    }
                }.filter { it.controllerType != SeatControl.OPEN }

                if (finalizedSeats.size < room.mode.minPlayers) {
                    throw IllegalStateException("Need at least ${room.mode.minPlayers} active seats to start.")
                }

                val newSeed = Random.nextLong(1L, Long.MAX_VALUE)
                val initialGameState = createAuthoritativeGameState(
                    seats = finalizedSeats,
                    mode = room.mode,
                    timedDurationSeconds = room.timedDurationSeconds,
                    seed = newSeed
                )

                tx.update(
                    ref,
                    mapOf(
                        "seats" to finalizedSeats.map { it.toFirestoreMap() },
                        "status" to RoomStatus.PLAYING.name,
                        "diceSeed" to newSeed,
                        "rollSeq" to 0,
                        "turnSeq" to room.turnSeq + 1,
                        "lastActionType" to OnlineActionType.START.name,
                        "lastActionSummary" to "Match started! ${initialGameState.activePlayer.nickname}'s turn!",
                        "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(initialGameState),
                        "turnStartedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Leaves a room cleanly:
     * - In LOBBY: frees the seat, transfers hostUid if host left, or closes room if empty.
     * - In PLAYING: keeps match running! Transfers hostUid if host left, hands departing seat
     *   over to a Medium bot (`botTakeover = true`), or closes room if no human players remain.
     * - In FINISHED: removes from memberUids, transfers hostUid, or closes room if empty.
     */
    suspend fun leaveRoom(roomCode: String, uid: String): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (uid !in room.memberUids || room.status == RoomStatus.CLOSED) return@runTransaction

                val remainingMembers = room.memberUids.filterNot { it == uid }
                val nextHost = if (room.hostUid == uid) remainingMembers.firstOrNull().orEmpty() else room.hostUid
                val leavingName = room.seatForUid(uid)?.nickname ?: "Player"

                when (room.status) {
                    RoomStatus.LOBBY -> {
                        val updatedSeats = room.seats.map { seat ->
                            if (seat.uid == uid) {
                                seat.copy(
                                    uid = "",
                                    nickname = "Open Seat",
                                    controllerType = SeatControl.OPEN,
                                    connected = false,
                                    botTakeover = false,
                                    rematchVoted = false
                                )
                            } else seat
                        }
                        val nextStatus = if (remainingMembers.isEmpty()) RoomStatus.CLOSED else RoomStatus.LOBBY
                        tx.update(
                            ref,
                            mapOf(
                                "hostUid" to nextHost,
                                "memberUids" to remainingMembers,
                                "seats" to updatedSeats.map { it.toFirestoreMap() },
                                "status" to nextStatus.name,
                                "lastActionType" to OnlineActionType.LEAVE.name,
                                "lastActionSummary" to "$leavingName left the lobby",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    RoomStatus.PLAYING -> {
                        val updatedSeats = room.seats.map { seat ->
                            if (seat.uid == uid) {
                                seat.copy(
                                    connected = false,
                                    botTakeover = true,
                                    rematchVoted = false
                                )
                            } else seat
                        }
                        val nextStatus = if (remainingMembers.isEmpty()) RoomStatus.CLOSED else RoomStatus.PLAYING
                        val syncedGame = room.gameState?.let { syncGameStateWithSeats(it, updatedSeats) }
                        val updates = mutableMapOf<String, Any>(
                            "hostUid" to nextHost,
                            "memberUids" to remainingMembers,
                            "seats" to updatedSeats.map { it.toFirestoreMap() },
                            "status" to nextStatus.name,
                            "turnSeq" to room.turnSeq + 1,
                            "lastActionType" to OnlineActionType.BOT_TAKEOVER.name,
                            "lastActionSummary" to "$leavingName left — Medium Bot took over!",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (syncedGame != null) {
                            updates["gameStateMap"] = GameStateFirestoreMapper.toFirestoreMap(syncedGame)
                        }
                        tx.update(ref, updates)
                    }

                    RoomStatus.FINISHED -> {
                        val updatedSeats = room.seats.map { seat ->
                            if (seat.uid == uid) seat.copy(connected = false, rematchVoted = false) else seat
                        }
                        val nextStatus = if (remainingMembers.isEmpty()) RoomStatus.CLOSED else RoomStatus.FINISHED
                        tx.update(
                            ref,
                            mapOf(
                                "hostUid" to nextHost,
                                "memberUids" to remainingMembers,
                                "seats" to updatedSeats.map { it.toFirestoreMap() },
                                "status" to nextStatus.name,
                                "lastActionType" to OnlineActionType.LEAVE.name,
                                "lastActionSummary" to "$leavingName left the room",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    RoomStatus.CLOSED -> Unit
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Authoritative fair dice roll transaction.
     * Dice value is derived deterministically from Firestore server `turnStartedAt` + `diceSeed` + `rollSeq`.
     */
    suspend fun submitDiceRoll(
        roomCode: String,
        actorUid: String,
        expectedTurnSeq: Int,
        asBotCoordinator: Boolean = false
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.PLAYING || room.turnSeq != expectedTurnSeq) return@runTransaction
                val state = room.gameState ?: return@runTransaction
                if (state.isMatchFinished || state.phase != TurnPhase.AWAITING_ROLL) return@runTransaction

                val activeColor = state.activePlayer.color
                val activeSeat = room.seatForColor(activeColor) ?: return@runTransaction
                val authorized = if (asBotCoordinator) {
                    activeSeat.isEffectiveBot && room.isBotCoordinator(actorUid)
                } else {
                    !activeSeat.isEffectiveBot && activeSeat.uid == actorUid
                }
                if (!authorized) return@runTransaction

                val engine = LudoEngine(
                    ruleSet = RuleSetRegistry.forMode(room.mode),
                    eventRandom = deterministicEventRandom(room.diceSeed, room.turnSeq + 1)
                )
                val nextRollSeq = room.rollSeq + 1
                val serverSec = room.turnStartedAt?.seconds ?: 0L
                val isDouble = state.activePlayer.doubleDiceActive

                val (die1, die2) = if (isDouble) {
                    FairDiceRoller.computeDoubleRoll(
                        roomCode = room.roomCode,
                        diceSeed = room.diceSeed,
                        rollSeq = nextRollSeq,
                        turnStartedAtSeconds = serverSec,
                        activeColor = activeColor
                    )
                } else {
                    FairDiceRoller.computeSingleRoll(
                        roomCode = room.roomCode,
                        diceSeed = room.diceSeed,
                        rollSeq = nextRollSeq,
                        turnStartedAtSeconds = serverSec,
                        activeColor = activeColor
                    ) to null
                }

                val outcome = engine.applyDiceRoll(state, die1, die2)
                val updatedSeats = room.seats.map { seat ->
                    if (seat.color == activeColor && !asBotCoordinator) {
                        seat.copy(
                            consecutiveMisses = 0,
                            connected = true,
                            lastHeartbeatMs = System.currentTimeMillis()
                        )
                    } else seat
                }

                val finalState: GameState
                val summary: String
                when (outcome) {
                    is RollOutcome.TripleSixCancelled -> {
                        val advanced = engine.commitTurnAdvance(outcome.state)
                        finalState = advanced.copy(
                            lastDiceValue = outcome.state.lastDiceValue,
                            lastRawDiceValue = outcome.state.lastRawDiceValue,
                            lastSecondDiceValue = outcome.state.lastSecondDiceValue,
                            lastEventMessage = "${state.activePlayer.nickname} rolled three 6s! Turn passed."
                        )
                        summary = finalState.lastEventMessage
                    }
                    is RollOutcome.NoLegalMoves -> {
                        val advanced = engine.commitTurnAdvance(outcome.state)
                        finalState = advanced.copy(
                            lastDiceValue = outcome.state.lastDiceValue,
                            lastRawDiceValue = outcome.state.lastRawDiceValue,
                            lastSecondDiceValue = outcome.state.lastSecondDiceValue,
                            lastEventMessage = "${state.activePlayer.nickname} rolled ${outcome.state.lastDiceValue} — no moves!"
                        )
                        summary = finalState.lastEventMessage
                    }
                    is RollOutcome.SingleLegalMove, is RollOutcome.MultipleLegalMoves -> {
                        finalState = outcome.state
                        summary = if (die2 != null) {
                            "${state.activePlayer.nickname} rolled $die1 + $die2!"
                        } else {
                            "${state.activePlayer.nickname} rolled $die1!"
                        }
                    }
                }

                val synced = syncGameStateWithSeats(finalState, updatedSeats)
                val nextStatus = if (synced.isMatchFinished) RoomStatus.FINISHED else RoomStatus.PLAYING

                tx.update(
                    ref,
                    mapOf(
                        "seats" to updatedSeats.map { it.toFirestoreMap() },
                        "status" to nextStatus.name,
                        "rollSeq" to nextRollSeq,
                        "turnSeq" to room.turnSeq + 1,
                        "lastActionType" to OnlineActionType.ROLL.name,
                        "lastActionSummary" to summary.take(180),
                        "lastMovedColor" to "",
                        "lastMovedTokenId" to -1,
                        "lastMovedFromProgress" to -1,
                        "lastMovedToProgress" to -1,
                        "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(synced),
                        "turnStartedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Authoritative token move transaction.
     */
    suspend fun submitTokenMove(
        roomCode: String,
        actorUid: String,
        expectedTurnSeq: Int,
        tokenColor: PlayerColor,
        tokenId: Int,
        asBotCoordinator: Boolean = false
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.PLAYING || room.turnSeq != expectedTurnSeq) return@runTransaction
                val state = room.gameState ?: return@runTransaction
                if (state.isMatchFinished || state.phase != TurnPhase.AWAITING_MOVE) return@runTransaction

                val activeColor = state.activePlayer.color
                val activeSeat = room.seatForColor(activeColor) ?: return@runTransaction
                val authorized = if (asBotCoordinator) {
                    activeSeat.isEffectiveBot && room.isBotCoordinator(actorUid)
                } else {
                    !activeSeat.isEffectiveBot && activeSeat.uid == actorUid
                }
                if (!authorized) return@runTransaction

                val move = state.legalMoves.firstOrNull {
                    it.color == tokenColor && it.tokenId == tokenId
                } ?: return@runTransaction

                val engine = LudoEngine(
                    ruleSet = RuleSetRegistry.forMode(room.mode),
                    eventRandom = deterministicEventRandom(room.diceSeed, room.turnSeq + 1)
                )
                val outcome = engine.applyMove(state, move)
                val updatedSeats = room.seats.map { seat ->
                    if (seat.color == activeColor && !asBotCoordinator) {
                        seat.copy(
                            consecutiveMisses = 0,
                            connected = true,
                            lastHeartbeatMs = System.currentTimeMillis()
                        )
                    } else seat
                }
                val synced = syncGameStateWithSeats(outcome.state, updatedSeats)
                val nextStatus = if (synced.isMatchFinished) RoomStatus.FINISHED else RoomStatus.PLAYING

                tx.update(
                    ref,
                    mapOf(
                        "seats" to updatedSeats.map { it.toFirestoreMap() },
                        "status" to nextStatus.name,
                        "turnSeq" to room.turnSeq + 1,
                        "lastActionType" to OnlineActionType.MOVE.name,
                        "lastActionSummary" to synced.lastEventMessage.take(180),
                        "lastMovedColor" to move.color.name,
                        "lastMovedTokenId" to move.tokenId,
                        "lastMovedFromProgress" to move.fromProgress,
                        "lastMovedToProgress" to move.toProgress,
                        "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(synced),
                        "turnStartedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Authoritative power-up activation before rolling (Shield, Double Dice, or Bomb Trap).
     */
    suspend fun submitPowerUpAction(
        roomCode: String,
        actorUid: String,
        expectedTurnSeq: Int,
        action: BotPowerUpAction,
        asBotCoordinator: Boolean = false
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.PLAYING || room.turnSeq != expectedTurnSeq) return@runTransaction
                if (room.mode != GameModeId.POWER_UP) return@runTransaction
                val state = room.gameState ?: return@runTransaction
                if (state.isMatchFinished || state.phase != TurnPhase.AWAITING_ROLL) return@runTransaction

                val activeColor = state.activePlayer.color
                val activeSeat = room.seatForColor(activeColor) ?: return@runTransaction
                val authorized = if (asBotCoordinator) {
                    activeSeat.isEffectiveBot && room.isBotCoordinator(actorUid)
                } else {
                    !activeSeat.isEffectiveBot && activeSeat.uid == actorUid
                }
                if (!authorized) return@runTransaction

                val engine = LudoEngine(RuleSetRegistry.forMode(room.mode))
                val (nextState, actionType) = when (action) {
                    is BotPowerUpAction.UseShield -> {
                        if (PowerUpType.SHIELD !in state.activePlayer.heldPowerUps) return@runTransaction
                        engine.activateShield(state, action.tokenId) to OnlineActionType.SHIELD
                    }
                    BotPowerUpAction.UseDoubleDice -> {
                        if (PowerUpType.DOUBLE_DICE !in state.activePlayer.heldPowerUps ||
                            state.activePlayer.doubleDiceActive
                        ) return@runTransaction
                        engine.activateDoubleDice(state) to OnlineActionType.DOUBLE_DICE
                    }
                    is BotPowerUpAction.UseBombTrap -> {
                        if (PowerUpType.BOMB_TRAP !in state.activePlayer.heldPowerUps) return@runTransaction
                        engine.placeBombTrap(state, action.trackIndex) to OnlineActionType.BOMB
                    }
                }

                tx.update(
                    ref,
                    mapOf(
                        "turnSeq" to room.turnSeq + 1,
                        "lastActionType" to actionType.name,
                        "lastActionSummary" to nextState.lastEventMessage.take(180),
                        "lastMovedColor" to activeColor.name,
                        "lastMovedTokenId" to -1,
                        "lastMovedFromProgress" to -1,
                        "lastMovedToProgress" to -1,
                        "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(nextState),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Handles synced 30-second turn timeout: skips turn, increments consecutiveMisses,
     * and switches the seat to a Medium bot after 3 missed turns in a row.
     */
    suspend fun submitTurnTimeout(
        roomCode: String,
        actorUid: String,
        expectedTurnSeq: Int
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.PLAYING || room.turnSeq != expectedTurnSeq) return@runTransaction
                if (actorUid !in room.memberUids) return@runTransaction
                val state = room.gameState ?: return@runTransaction
                if (state.isMatchFinished) return@runTransaction

                val activeColor = state.activePlayer.color
                val activeSeat = room.seatForColor(activeColor) ?: return@runTransaction

                var triggeredTakeover = false
                val updatedSeats = room.seats.map { seat ->
                    if (seat.color == activeColor && seat.controllerType == SeatControl.HUMAN && !seat.botTakeover) {
                        val misses = seat.consecutiveMisses + 1
                        val takeover = misses >= 3
                        if (takeover) triggeredTakeover = true
                        seat.copy(
                            consecutiveMisses = misses,
                            botTakeover = takeover
                        )
                    } else seat
                }

                val engine = LudoEngine(
                    ruleSet = RuleSetRegistry.forMode(room.mode),
                    eventRandom = deterministicEventRandom(room.diceSeed, room.turnSeq + 1)
                )
                val timedOut = engine.handleTurnTimeout(state)
                val synced = syncGameStateWithSeats(timedOut, updatedSeats)
                val summary = if (triggeredTakeover) {
                    "${activeSeat.nickname} missed 3 turns — Medium Bot took over!"
                } else {
                    "${activeSeat.nickname} ran out of time! Turn skipped."
                }
                val finalState = synced.copy(lastEventMessage = summary)
                val nextStatus = if (finalState.isMatchFinished) RoomStatus.FINISHED else RoomStatus.PLAYING

                tx.update(
                    ref,
                    mapOf(
                        "seats" to updatedSeats.map { it.toFirestoreMap() },
                        "status" to nextStatus.name,
                        "turnSeq" to room.turnSeq + 1,
                        "lastActionType" to if (triggeredTakeover) OnlineActionType.BOT_TAKEOVER.name else OnlineActionType.TIMEOUT.name,
                        "lastActionSummary" to summary.take(180),
                        "lastMovedColor" to "",
                        "lastMovedTokenId" to -1,
                        "lastMovedFromProgress" to -1,
                        "lastMovedToProgress" to -1,
                        "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(finalState),
                        "turnStartedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Handles Timed-mode match clock expiry atomically on the server document.
     */
    suspend fun submitTimedClockExpired(
        roomCode: String,
        actorUid: String,
        expectedTurnSeq: Int
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.PLAYING || room.turnSeq != expectedTurnSeq) return@runTransaction
                if (room.mode != GameModeId.TIMED || actorUid !in room.memberUids) return@runTransaction
                val state = room.gameState ?: return@runTransaction
                if (state.isMatchFinished) return@runTransaction

                val engine = LudoEngine(RuleSetRegistry.forMode(room.mode))
                val expired = engine.handleTimedClockExpired(state)
                tx.update(
                    ref,
                    mapOf(
                        "status" to RoomStatus.FINISHED.name,
                        "turnSeq" to room.turnSeq + 1,
                        "lastActionType" to OnlineActionType.TIMEOUT.name,
                        "lastActionSummary" to expired.lastEventMessage.take(180),
                        "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(expired),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Updates the local player's heartbeat, restores their seat immediately if they reconnected
     * after a bot takeover, and switches any other player disconnected >60s to Medium bot takeover.
     */
    suspend fun sendHeartbeatAndSyncPresence(roomCode: String, uid: String): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.PLAYING || uid !in room.memberUids) return@runTransaction

                val nowMs = System.currentTimeMillis()
                var reconnectedName: String? = null
                var disconnectedName: String? = null
                var changed = false

                val updatedSeats = room.seats.map { seat ->
                    when {
                        seat.uid == uid -> {
                            val wasTakenOver = seat.botTakeover || !seat.connected
                            if (wasTakenOver) reconnectedName = seat.nickname
                            changed = true
                            seat.copy(
                                connected = true,
                                botTakeover = false,
                                consecutiveMisses = 0,
                                lastHeartbeatMs = nowMs
                            )
                        }
                        seat.controllerType == SeatControl.HUMAN &&
                            !seat.botTakeover &&
                            seat.lastHeartbeatMs > 0L &&
                            (nowMs - seat.lastHeartbeatMs) > DISCONNECT_TAKEOVER_MS -> {
                            disconnectedName = seat.nickname
                            changed = true
                            seat.copy(
                                connected = false,
                                botTakeover = true
                            )
                        }
                        else -> seat
                    }
                }

                if (!changed) return@runTransaction

                val updates = mutableMapOf<String, Any>(
                    "seats" to updatedSeats.map { it.toFirestoreMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val state = room.gameState
                if (state != null && (reconnectedName != null || disconnectedName != null)) {
                    val synced = syncGameStateWithSeats(state, updatedSeats)
                    val summary = when {
                        reconnectedName != null -> "$reconnectedName reconnected and reclaimed their seat!"
                        disconnectedName != null -> "$disconnectedName disconnected (60s) — Medium Bot took over!"
                        else -> synced.lastEventMessage
                    }
                    updates["gameStateMap"] = GameStateFirestoreMapper.toFirestoreMap(
                        synced.copy(lastEventMessage = summary)
                    )
                    updates["lastActionType"] = if (reconnectedName != null) {
                        OnlineActionType.RECONNECT.name
                    } else {
                        OnlineActionType.BOT_TAKEOVER.name
                    }
                    updates["lastActionSummary"] = summary.take(180)
                }

                tx.update(ref, updates)
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    /**
     * Executes the next bot step (pre-roll power-up, roll, or move) when the active seat
     * is a Bot or Bot-Takeover seat and the caller is the deterministic bot coordinator.
     */
    suspend fun executeBotStep(
        roomCode: String,
        coordinatorUid: String,
        expectedTurnSeq: Int
    ): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val snap = try {
            roomsCol.document(code).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "rooms/$code")
            return Result.failure(e)
        }
        val room = parseRoom(snap) ?: return Result.success(Unit)
        if (room.status != RoomStatus.PLAYING || room.turnSeq != expectedTurnSeq) return Result.success(Unit)
        if (!room.isBotCoordinator(coordinatorUid)) return Result.success(Unit)

        val state = room.gameState ?: return Result.success(Unit)
        if (state.isMatchFinished) return Result.success(Unit)
        val activeSeat = room.seatForColor(state.activePlayer.color) ?: return Result.success(Unit)
        if (!activeSeat.isEffectiveBot) return Result.success(Unit)

        val ruleSet = RuleSetRegistry.forMode(room.mode)
        return when (state.phase) {
            TurnPhase.AWAITING_ROLL -> {
                if (ruleSet.powerUpsEnabled && state.activePlayer.heldPowerUps.isNotEmpty()) {
                    val powerUpAction = botBrain.choosePowerUpBeforeRoll(
                        state = state,
                        ruleSet = ruleSet,
                        difficulty = activeSeat.effectiveBotDifficulty
                    )
                    if (powerUpAction != null) {
                        return submitPowerUpAction(
                            roomCode = code,
                            actorUid = coordinatorUid,
                            expectedTurnSeq = expectedTurnSeq,
                            action = powerUpAction,
                            asBotCoordinator = true
                        )
                    }
                }
                submitDiceRoll(
                    roomCode = code,
                    actorUid = coordinatorUid,
                    expectedTurnSeq = expectedTurnSeq,
                    asBotCoordinator = true
                )
            }

            TurnPhase.AWAITING_MOVE -> {
                if (state.legalMoves.isEmpty()) return Result.success(Unit)
                val chosen: LegalMove = if (state.legalMoves.size == 1) {
                    state.legalMoves.first()
                } else {
                    botBrain.chooseMove(
                        state = state,
                        ruleSet = ruleSet,
                        legalMoves = state.legalMoves,
                        difficulty = activeSeat.effectiveBotDifficulty
                    )
                }
                submitTokenMove(
                    roomCode = code,
                    actorUid = coordinatorUid,
                    expectedTurnSeq = expectedTurnSeq,
                    tokenColor = chosen.color,
                    tokenId = chosen.tokenId,
                    asBotCoordinator = true
                )
            }

            else -> Result.success(Unit)
        }
    }

    /**
     * Post-game unanimous Rematch vote. Automatically starts a fresh match as soon as
     * every human player still in `memberUids` has voted Rematch.
     */
    suspend fun voteRematch(roomCode: String, uid: String): Result<Unit> {
        val code = roomCode.trim().uppercase()
        val ref = roomsCol.document(code)
        return try {
            firestore.runTransaction { tx ->
                val room = parseRoom(tx.get(ref)) ?: return@runTransaction
                if (room.status != RoomStatus.FINISHED || uid !in room.memberUids) return@runTransaction

                val nowMs = System.currentTimeMillis()
                val votedSeats = room.seats.map { seat ->
                    if (seat.uid == uid) seat.copy(rematchVoted = true, connected = true, lastHeartbeatMs = nowMs)
                    else seat
                }

                val remainingHumanSeats = votedSeats.filter { it.uid in room.memberUids }
                val allAgreed = remainingHumanSeats.isNotEmpty() && remainingHumanSeats.all { it.rematchVoted }

                if (allAgreed) {
                    val resetSeats = votedSeats.map { seat ->
                        val stillInRoom = seat.uid in room.memberUids
                        if (seat.controllerType == SeatControl.HUMAN && stillInRoom) {
                            seat.copy(
                                connected = true,
                                botTakeover = false,
                                consecutiveMisses = 0,
                                lastHeartbeatMs = nowMs,
                                rematchVoted = false
                            )
                        } else if (seat.controllerType == SeatControl.HUMAN && !stillInRoom) {
                            seat.copy(
                                uid = "",
                                nickname = "${seat.color.displayName} Bot",
                                controllerType = SeatControl.BOT,
                                botDifficulty = BotDifficulty.MEDIUM,
                                connected = true,
                                botTakeover = false,
                                rematchVoted = false
                            )
                        } else {
                            seat.copy(rematchVoted = false)
                        }
                    }

                    val newSeed = Random.nextLong(1L, Long.MAX_VALUE)
                    val freshState = createAuthoritativeGameState(
                        seats = resetSeats,
                        mode = room.mode,
                        timedDurationSeconds = room.timedDurationSeconds,
                        seed = newSeed
                    )
                    tx.update(
                        ref,
                        mapOf(
                            "seats" to resetSeats.map { it.toFirestoreMap() },
                            "status" to RoomStatus.PLAYING.name,
                            "diceSeed" to newSeed,
                            "rollSeq" to 0,
                            "turnSeq" to room.turnSeq + 1,
                            "lastActionType" to OnlineActionType.REMATCH.name,
                            "lastActionSummary" to "Rematch started! ${freshState.activePlayer.nickname}'s turn!",
                            "lastMovedColor" to "",
                            "lastMovedTokenId" to -1,
                            "lastMovedFromProgress" to -1,
                            "lastMovedToProgress" to -1,
                            "gameStateMap" to GameStateFirestoreMapper.toFirestoreMap(freshState),
                            "turnStartedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val voterName = room.seatForUid(uid)?.nickname ?: "Player"
                    val yesCount = remainingHumanSeats.count { it.rematchVoted }
                    tx.update(
                        ref,
                        mapOf(
                            "seats" to votedSeats.map { it.toFirestoreMap() },
                            "lastActionType" to OnlineActionType.REMATCH.name,
                            "lastActionSummary" to "$voterName voted Rematch ($yesCount/${remainingHumanSeats.size})",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rooms/$code")
            Result.failure(e)
        }
    }

    private fun buildInitialSeats(
        targetCount: Int,
        hostUid: String,
        hostNickname: String
    ): List<OnlineRoomSeat> {
        val colors = when (targetCount) {
            2 -> listOf(PlayerColor.RED, PlayerColor.YELLOW)
            3 -> listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW)
            else -> listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
        }
        val nowMs = System.currentTimeMillis()
        return colors.mapIndexed { idx, color ->
            if (idx == 0) {
                OnlineRoomSeat(
                    seatIndex = 0,
                    color = color,
                    uid = hostUid,
                    nickname = hostNickname,
                    controllerType = SeatControl.HUMAN,
                    botDifficulty = BotDifficulty.MEDIUM,
                    connected = true,
                    botTakeover = false,
                    consecutiveMisses = 0,
                    lastHeartbeatMs = nowMs,
                    rematchVoted = false
                )
            } else {
                OnlineRoomSeat(
                    seatIndex = idx,
                    color = color,
                    uid = "",
                    nickname = "Open Seat",
                    controllerType = SeatControl.OPEN,
                    botDifficulty = BotDifficulty.MEDIUM,
                    connected = false,
                    botTakeover = false,
                    consecutiveMisses = 0,
                    lastHeartbeatMs = 0L,
                    rematchVoted = false
                )
            }
        }
    }

    private fun createAuthoritativeGameState(
        seats: List<OnlineRoomSeat>,
        mode: GameModeId,
        timedDurationSeconds: Int,
        seed: Long
    ): GameState {
        val setups = seats.map { it.toPlayerSetup() }
        val engine = LudoEngine(
            ruleSet = RuleSetRegistry.forMode(mode),
            eventRandom = Random(seed)
        )
        return engine.createGame(setups, mode, timedDurationSeconds)
    }

    private fun syncGameStateWithSeats(state: GameState, seats: List<OnlineRoomSeat>): GameState {
        val byColor = seats.associateBy { it.color }
        val updatedPlayers = state.players.map { player ->
            val seat = byColor[player.color] ?: return@map player
            player.copy(setup = seat.toPlayerSetup())
        }
        return state.copy(players = updatedPlayers)
    }

    private fun deterministicEventRandom(diceSeed: Long, seq: Int): Random =
        Random(diceSeed xor (seq.toLong() * 0x9E3779B97F4A7C15UL.toLong()))

    private fun parseRoom(doc: DocumentSnapshot): OnlineRoom? {
        if (!doc.exists()) return null
        val est = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        val mode = GameModeId.entries.firstOrNull { it.name == doc.getString("mode") } ?: GameModeId.CLASSIC
        val memberUids = (doc.get("memberUids") as? List<*>).orEmpty().mapNotNull { it as? String }
        val seats = (doc.get("seats") as? List<*>).orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.let(OnlineRoomSeat::fromFirestoreMap) }
        val rawGameState = doc.get("gameStateMap") as? Map<*, *>
        val lastColorRaw = doc.getString("lastMovedColor").orEmpty()
        val lastMovedColor = PlayerColor.entries.firstOrNull { it.name == lastColorRaw }

        return OnlineRoom(
            roomCode = doc.getString("roomCode") ?: doc.id,
            hostUid = doc.getString("hostUid").orEmpty(),
            mode = mode,
            targetPlayerCount = (doc.getLong("targetPlayerCount") ?: 2L).toInt(),
            timedDurationSeconds = (doc.getLong("timedDurationSeconds") ?: 300L).toInt(),
            status = RoomStatus.fromRaw(doc.getString("status")),
            publicMatchmaking = doc.getBoolean("publicMatchmaking") ?: false,
            memberUids = memberUids,
            seats = seats,
            diceSeed = doc.getLong("diceSeed") ?: 1L,
            rollSeq = (doc.getLong("rollSeq") ?: 0L).toInt(),
            turnSeq = (doc.getLong("turnSeq") ?: 0L).toInt(),
            lastActionType = OnlineActionType.fromRaw(doc.getString("lastActionType")),
            lastActionSummary = doc.getString("lastActionSummary").orEmpty(),
            lastMovedColor = lastMovedColor,
            lastMovedTokenId = (doc.getLong("lastMovedTokenId") ?: -1L).toInt(),
            lastMovedFromProgress = (doc.getLong("lastMovedFromProgress") ?: -1L).toInt(),
            lastMovedToProgress = (doc.getLong("lastMovedToProgress") ?: -1L).toInt(),
            gameState = GameStateFirestoreMapper.fromFirestoreMap(rawGameState),
            turnStartedAt = doc.getTimestamp("turnStartedAt", est) ?: Timestamp.now(),
            createdAt = doc.getTimestamp("createdAt", est) ?: Timestamp.now(),
            updatedAt = doc.getTimestamp("updatedAt", est) ?: Timestamp.now()
        )
    }

    companion object {
        const val DISCONNECT_TAKEOVER_MS = 60_000L
    }
}
