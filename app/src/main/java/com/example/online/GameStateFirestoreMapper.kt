package com.example.online

import com.example.engine.BombTrap
import com.example.engine.BotDifficulty
import com.example.engine.ChaosEventType
import com.example.engine.ChaosState
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoEngine
import com.example.engine.PendingPowerUpRespawn
import com.example.engine.PlayerColor
import com.example.engine.PlayerControllerType
import com.example.engine.PlayerSetup
import com.example.engine.PlayerState
import com.example.engine.PowerUpState
import com.example.engine.PowerUpTile
import com.example.engine.PowerUpType
import com.example.engine.RuleSetRegistry
import com.example.engine.Token
import com.example.engine.TurnPhase

/**
 * Lossless native Firestore Map <-> Ludo GameState serializer.
 * Stores all fields as native Firestore Maps/Lists/Numbers/Strings (no JSON strings).
 */
object GameStateFirestoreMapper {

    fun toFirestoreMap(state: GameState): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "mode" to state.mode.name,
            "activePlayerIndex" to state.activePlayerIndex,
            "phase" to state.phase.name,
            "wasDiceDoubled" to state.wasDiceDoubled,
            "wasDoubleDiceRoll" to state.wasDoubleDiceRoll,
            "consecutiveSixes" to state.consecutiveSixes,
            "finishOrder" to state.finishOrder.map { it.name },
            "eliminatedOrder" to state.eliminatedOrder.map { it.name },
            "turnNumber" to state.turnNumber,
            "fullRoundsCompleted" to state.fullRoundsCompleted,
            "timedDurationSeconds" to state.timedDurationSeconds,
            "timedRemainingSeconds" to state.timedRemainingSeconds,
            "lastEventMessage" to state.lastEventMessage.take(160),
            "players" to state.players.map { playerToMap(it) },
            "chaosState" to chaosToMap(state.chaosState),
            "powerUpState" to powerUpToMap(state.powerUpState)
        )
        state.lastDiceValue?.let { map["lastDiceValue"] = it }
        state.lastRawDiceValue?.let { map["lastRawDiceValue"] = it }
        state.lastSecondDiceValue?.let { map["lastSecondDiceValue"] = it }
        state.winningTeamLabel?.let { map["winningTeamLabel"] = it }
        return map
    }

    fun fromFirestoreMap(raw: Map<*, *>?): GameState? {
        if (raw.isNullOrEmpty()) return null
        val playersRaw = raw["players"] as? List<*> ?: return null
        val players = playersRaw.mapNotNull { (it as? Map<*, *>)?.let(::playerFromMap) }
        if (players.size < 2) return null

        val mode = GameModeId.entries.firstOrNull { it.name == raw["mode"] as? String } ?: GameModeId.CLASSIC
        val phase = TurnPhase.entries.firstOrNull { it.name == raw["phase"] as? String } ?: TurnPhase.AWAITING_ROLL
        val activeIdx = ((raw["activePlayerIndex"] as? Number)?.toInt() ?: 0).coerceIn(0, players.lastIndex)
        val lastDice = (raw["lastDiceValue"] as? Number)?.toInt()
        val lastRawDice = (raw["lastRawDiceValue"] as? Number)?.toInt()
        val lastSecondDice = (raw["lastSecondDiceValue"] as? Number)?.toInt()
        val wasDoubleDice = (raw["wasDoubleDiceRoll"] as? Boolean) ?: false

        val finishOrder = (raw["finishOrder"] as? List<*>).orEmpty().mapNotNull { name ->
            PlayerColor.entries.firstOrNull { it.name == name as? String }
        }
        val eliminatedOrder = (raw["eliminatedOrder"] as? List<*>).orEmpty().mapNotNull { name ->
            PlayerColor.entries.firstOrNull { it.name == name as? String }
        }

        val baseState = GameState(
            mode = mode,
            players = players,
            activePlayerIndex = activeIdx,
            phase = phase,
            lastDiceValue = lastDice,
            lastRawDiceValue = lastRawDice,
            lastSecondDiceValue = lastSecondDice,
            wasDiceDoubled = (raw["wasDiceDoubled"] as? Boolean) ?: false,
            wasDoubleDiceRoll = wasDoubleDice,
            consecutiveSixes = (raw["consecutiveSixes"] as? Number)?.toInt() ?: 0,
            legalMoves = emptyList(),
            finishOrder = finishOrder,
            eliminatedOrder = eliminatedOrder,
            turnNumber = (raw["turnNumber"] as? Number)?.toInt() ?: 1,
            fullRoundsCompleted = (raw["fullRoundsCompleted"] as? Number)?.toInt() ?: 0,
            timedDurationSeconds = (raw["timedDurationSeconds"] as? Number)?.toInt() ?: 300,
            timedRemainingSeconds = (raw["timedRemainingSeconds"] as? Number)?.toInt() ?: 300,
            winningTeamLabel = raw["winningTeamLabel"] as? String,
            chaosState = chaosFromMap(raw["chaosState"] as? Map<*, *>),
            powerUpState = powerUpFromMap(raw["powerUpState"] as? Map<*, *>),
            lastEventMessage = (raw["lastEventMessage"] as? String).orEmpty()
        )

        // Recompute authoritative legal moves deterministically when awaiting a move
        val computedMoves: List<LegalMove> = if (phase == TurnPhase.AWAITING_MOVE && lastDice != null) {
            val engine = LudoEngine(RuleSetRegistry.forMode(mode))
            val allowBaseOverride = wasDoubleDice && lastRawDice == 6 && lastSecondDice == 6
            engine.computeLegalMoves(baseState, lastDice, allowBaseEntryOverride = allowBaseOverride)
        } else {
            emptyList()
        }

        return baseState.copy(legalMoves = computedMoves)
    }

    private fun playerToMap(player: PlayerState): Map<String, Any> = mapOf(
        "seatIndex" to player.setup.seatIndex,
        "nickname" to player.setup.nickname,
        "color" to player.setup.color.name,
        "controllerType" to player.setup.controllerType.name,
        "botDifficulty" to player.setup.botDifficulty.name,
        "hasCapturedOpponent" to player.hasCapturedOpponent,
        "score" to player.score,
        "eliminated" to player.isEliminated,
        "diceStormCharges" to player.diceStormCharges,
        "heldPowerUps" to player.heldPowerUps.map { it.name },
        "doubleDiceActive" to player.doubleDiceActive,
        "capturesMade" to player.capturesMade,
        "powerUpsUsed" to player.powerUpsUsed,
        "tokens" to player.tokens.map { tokenToMap(it) }
    )

    private fun playerFromMap(raw: Map<*, *>): PlayerState {
        val color = PlayerColor.entries.firstOrNull { it.name == raw["color"] as? String } ?: PlayerColor.RED
        val ctrl = PlayerControllerType.entries.firstOrNull { it.name == raw["controllerType"] as? String }
            ?: PlayerControllerType.ONLINE_REMOTE
        val diff = BotDifficulty.entries.firstOrNull { it.name == raw["botDifficulty"] as? String }
            ?: BotDifficulty.MEDIUM
        val setup = PlayerSetup(
            seatIndex = (raw["seatIndex"] as? Number)?.toInt() ?: 0,
            nickname = (raw["nickname"] as? String).orEmpty().ifBlank { color.displayName },
            color = color,
            controllerType = ctrl,
            botDifficulty = diff
        )
        val tokens = (raw["tokens"] as? List<*>).orEmpty().mapIndexedNotNull { idx, item ->
            (item as? Map<*, *>)?.let { tokenFromMap(it, idx, color) }
        }
        val held = (raw["heldPowerUps"] as? List<*>).orEmpty().mapNotNull { name ->
            PowerUpType.entries.firstOrNull { it.name == name as? String }
        }
        return PlayerState(
            setup = setup,
            tokens = tokens.ifEmpty { List(4) { Token(it, color) } },
            hasCapturedOpponent = (raw["hasCapturedOpponent"] as? Boolean) ?: false,
            score = (raw["score"] as? Number)?.toInt() ?: 0,
            isEliminated = (raw["eliminated"] as? Boolean) ?: false,
            diceStormCharges = (raw["diceStormCharges"] as? Number)?.toInt() ?: 0,
            heldPowerUps = held,
            doubleDiceActive = (raw["doubleDiceActive"] as? Boolean) ?: false,
            capturesMade = (raw["capturesMade"] as? Number)?.toInt() ?: 0,
            powerUpsUsed = (raw["powerUpsUsed"] as? Number)?.toInt() ?: 0
        )
    }

    private fun tokenToMap(token: Token): Map<String, Any> = mapOf(
        "id" to token.id,
        "progress" to token.progress,
        "outerWrap51" to token.outerWrap51,
        "shieldTurnsRemaining" to token.shieldTurnsRemaining
    )

    private fun tokenFromMap(raw: Map<*, *>, fallbackId: Int, color: PlayerColor): Token = Token(
        id = (raw["id"] as? Number)?.toInt() ?: fallbackId,
        color = color,
        progress = (raw["progress"] as? Number)?.toInt() ?: Token.BASE_PROGRESS,
        outerWrap51 = (raw["outerWrap51"] as? Boolean) ?: false,
        shieldTurnsRemaining = (raw["shieldTurnsRemaining"] as? Number)?.toInt() ?: 0
    )

    private fun chaosToMap(chaos: ChaosState): Map<String, Any> {
        val m = mutableMapOf<String, Any>(
            "safeStarsRoundsRemaining" to chaos.safeStarsRoundsRemaining,
            "portalsRoundsRemaining" to chaos.portalsRoundsRemaining,
            "announcementDetail" to chaos.announcementDetail,
            "announcementId" to chaos.announcementId
        )
        chaos.activeSafeTrackIndices?.let { m["activeSafeTrackIndices"] = it.toList() }
        chaos.portalPair?.let { (a, b) -> m["portalA"] = a; m["portalB"] = b }
        chaos.lastTriggeredEvent?.let { m["lastTriggeredEvent"] = it.name }
        chaos.activeAnnouncement?.let { m["activeAnnouncement"] = it.name }
        return m
    }

    private fun chaosFromMap(raw: Map<*, *>?): ChaosState {
        if (raw == null) return ChaosState()
        val safeList = (raw["activeSafeTrackIndices"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
        val pA = (raw["portalA"] as? Number)?.toInt()
        val pB = (raw["portalB"] as? Number)?.toInt()
        return ChaosState(
            activeSafeTrackIndices = safeList?.toSet(),
            safeStarsRoundsRemaining = (raw["safeStarsRoundsRemaining"] as? Number)?.toInt() ?: 0,
            portalPair = if (pA != null && pB != null) pA to pB else null,
            portalsRoundsRemaining = (raw["portalsRoundsRemaining"] as? Number)?.toInt() ?: 0,
            lastTriggeredEvent = ChaosEventType.entries.firstOrNull { it.name == raw["lastTriggeredEvent"] as? String },
            activeAnnouncement = ChaosEventType.entries.firstOrNull { it.name == raw["activeAnnouncement"] as? String },
            announcementDetail = (raw["announcementDetail"] as? String).orEmpty(),
            announcementId = (raw["announcementId"] as? Number)?.toInt() ?: 0
        )
    }

    private fun powerUpToMap(pu: PowerUpState): Map<String, Any> = mapOf(
        "tiles" to pu.tiles.map {
            mapOf("id" to it.id, "trackIndex" to it.trackIndex, "type" to it.type.name)
        },
        "pendingRespawns" to pu.pendingRespawns.map {
            mapOf("roundsRemaining" to it.roundsRemaining)
        },
        "bombs" to pu.bombs.map {
            mapOf(
                "id" to it.id,
                "ownerColor" to it.ownerColor.name,
                "trackIndex" to it.trackIndex,
                "roundsRemaining" to it.roundsRemaining
            )
        },
        "nextEntityId" to pu.nextEntityId
    )

    private fun powerUpFromMap(raw: Map<*, *>?): PowerUpState {
        if (raw == null) return PowerUpState()
        val tiles = (raw["tiles"] as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val type = PowerUpType.entries.firstOrNull { it.name == m["type"] as? String } ?: return@mapNotNull null
            PowerUpTile(
                id = (m["id"] as? Number)?.toInt() ?: 0,
                trackIndex = (m["trackIndex"] as? Number)?.toInt() ?: 0,
                type = type
            )
        }
        val respawns = (raw["pendingRespawns"] as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            PendingPowerUpRespawn(roundsRemaining = (m["roundsRemaining"] as? Number)?.toInt() ?: 1)
        }
        val bombs = (raw["bombs"] as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val owner = PlayerColor.entries.firstOrNull { it.name == m["ownerColor"] as? String } ?: return@mapNotNull null
            BombTrap(
                id = (m["id"] as? Number)?.toInt() ?: 0,
                ownerColor = owner,
                trackIndex = (m["trackIndex"] as? Number)?.toInt() ?: 0,
                roundsRemaining = (m["roundsRemaining"] as? Number)?.toInt() ?: 1
            )
        }
        return PowerUpState(
            tiles = tiles,
            pendingRespawns = respawns,
            bombs = bombs,
            nextEntityId = (raw["nextEntityId"] as? Number)?.toInt() ?: 1
        )
    }
}
