package com.example.engine

import kotlin.random.Random

/**
 * Pure, deterministic Ludo rules engine supporting Classic, Quick, Master,
 * Power-Up, Timed, Teams 2v2, One Token Race, Chaos, and Elimination modes.
 */
class LudoEngine(
    private val ruleSet: LudoRuleSet = ClassicRuleSet,
    private val eventRandom: Random = Random.Default
) {

    fun createGame(
        playerSetups: List<PlayerSetup>,
        mode: GameModeId = ruleSet.modeId,
        timedDurationSeconds: Int = 300
    ): GameState {
        require(playerSetups.size in mode.minPlayers..mode.maxPlayers) {
            "${mode.title} requires ${mode.minPlayers} to ${mode.maxPlayers} players"
        }
        require(playerSetups.map { it.color }.distinct().size == playerSetups.size) {
            "All players must have distinct colors"
        }

        val initialProgress = if (ruleSet.startOnTrack) 0 else Token.BASE_PROGRESS

        val orderedPlayers = playerSetups
            .sortedBy { it.color.cornerOrder }
            .map { setup ->
                PlayerState(
                    setup = setup,
                    tokens = List(ruleSet.tokensPerPlayer) { idx ->
                        Token(id = idx, color = setup.color, progress = initialProgress)
                    }
                )
            }

        val initialPowerUpState = if (ruleSet.powerUpsEnabled) {
            createInitialPowerUpState()
        } else {
            PowerUpState()
        }

        return GameState(
            mode = mode,
            players = orderedPlayers,
            activePlayerIndex = 0,
            phase = TurnPhase.AWAITING_ROLL,
            lastDiceValue = null,
            consecutiveSixes = 0,
            legalMoves = emptyList(),
            finishOrder = emptyList(),
            turnNumber = 1,
            fullRoundsCompleted = 0,
            timedDurationSeconds = timedDurationSeconds,
            timedRemainingSeconds = timedDurationSeconds,
            powerUpState = initialPowerUpState,
            lastEventMessage = "${orderedPlayers.first().nickname}'s turn!"
        )
    }

    /**
     * In Teams 2v2, if the active player already has all 4 tokens home, they roll for their partner.
     */
    fun controlledColorForActivePlayer(state: GameState): PlayerColor =
        TurnManager.controlledColor(state, ruleSet)

    /**
     * Returns all main-track tokens owned by [ownerColor] that can receive a Shield.
     */
    fun computeValidShieldTokenIds(state: GameState, ownerColor: PlayerColor): List<Int> {
        if (!ruleSet.powerUpsEnabled) return emptyList()
        val player = state.playerByColor(ownerColor) ?: return emptyList()
        return player.tokens.filter { it.isOnMainTrack }.map { it.id }
    }

    /**
     * Returns all valid empty main-track squares (not safe, no token, no tile, no bomb)
     * within 6 squares of any of [ownerColor]'s tokens on the main track.
     */
    fun computeValidBombSquares(state: GameState, ownerColor: PlayerColor): Set<Int> {
        if (!ruleSet.powerUpsEnabled) return emptySet()
        val owner = state.playerByColor(ownerColor) ?: return emptySet()
        val ownerTrackPositions = owner.tokens.mapNotNull { it.mainTrackIndex() }
        if (ownerTrackPositions.isEmpty()) return emptySet()

        val blocked = mutableSetOf<Int>()
        blocked += state.activeSafeTrackIndices()
        blocked += state.powerUpState.tiles.map { it.trackIndex }
        blocked += state.powerUpState.bombs.map { it.trackIndex }
        for (p in state.players) {
            if (p.isEliminated) continue
            for (t in p.tokens) {
                t.mainTrackIndex()?.let { blocked += it }
            }
        }

        val valid = mutableSetOf<Int>()
        for (sq in 0 until Token.TRACK_LENGTH) {
            if (sq in blocked) continue
            val withinRange = ownerTrackPositions.any { tokIdx ->
                val fwd = (sq - tokIdx + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
                val bwd = (tokIdx - sq + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
                minOf(fwd, bwd) in 1..PowerUpState.BOMB_PLACEMENT_RANGE
            }
            if (withinRange) valid += sq
        }
        return valid
    }

    /**
     * Activates a held Shield power-up on one of the active player's main-track tokens before rolling.
     */
    fun activateShield(
        state: GameState,
        tokenId: Int,
        actingColor: PlayerColor? = null
    ): GameState {
        require(ruleSet.powerUpsEnabled) { "Power-ups are only available in Power-Up mode" }
        val guard = TurnManager.validateRoll(state, actingColor)
        require(guard.isAllowed) { "Cannot activate Shield: $guard" }
        val active = state.activePlayer
        require(PowerUpType.SHIELD in active.heldPowerUps) { "Player does not hold a Shield" }
        val targetToken = active.tokens.firstOrNull { it.id == tokenId }
            ?: throw IllegalArgumentException("Invalid tokenId $tokenId")
        require(targetToken.isOnMainTrack) { "Shield can only be placed on a token on the main track" }

        val updatedPlayers = state.players.mapIndexed { idx, p ->
            if (idx == state.activePlayerIndex) {
                p.copy(
                    heldPowerUps = removeFirstOccurrence(p.heldPowerUps, PowerUpType.SHIELD),
                    powerUpsUsed = p.powerUpsUsed + 1,
                    tokens = p.tokens.map { t ->
                        if (t.id == tokenId) {
                            t.copy(shieldTurnsRemaining = PowerUpState.SHIELD_DURATION_TURNS)
                        } else t
                    }
                )
            } else p
        }

        return state.copy(
            players = updatedPlayers,
            lastEventMessage = "${active.nickname} shielded a token for 2 turns!"
        )
    }

    /**
     * Activates a held Double Dice power-up for the active player's upcoming roll.
     */
    fun activateDoubleDice(
        state: GameState,
        actingColor: PlayerColor? = null
    ): GameState {
        require(ruleSet.powerUpsEnabled) { "Power-ups are only available in Power-Up mode" }
        val guard = TurnManager.validateRoll(state, actingColor)
        require(guard.isAllowed) { "Cannot activate Double Dice: $guard" }
        val active = state.activePlayer
        require(PowerUpType.DOUBLE_DICE in active.heldPowerUps) { "Player does not hold Double Dice" }
        require(!active.doubleDiceActive) { "Double Dice is already active this turn" }

        val updatedPlayers = state.players.mapIndexed { idx, p ->
            if (idx == state.activePlayerIndex) {
                p.copy(
                    heldPowerUps = removeFirstOccurrence(p.heldPowerUps, PowerUpType.DOUBLE_DICE),
                    doubleDiceActive = true,
                    powerUpsUsed = p.powerUpsUsed + 1
                )
            } else p
        }

        return state.copy(
            players = updatedPlayers,
            lastEventMessage = "${active.nickname} activated Double Dice!"
        )
    }

    /**
     * Places a hidden Bomb Trap on [trackIndex] before rolling.
     */
    fun placeBombTrap(
        state: GameState,
        trackIndex: Int,
        actingColor: PlayerColor? = null
    ): GameState {
        require(ruleSet.powerUpsEnabled) { "Power-ups are only available in Power-Up mode" }
        val guard = TurnManager.validateRoll(state, actingColor)
        require(guard.isAllowed) { "Cannot place Bomb Trap: $guard" }
        val active = state.activePlayer
        require(PowerUpType.BOMB_TRAP in active.heldPowerUps) { "Player does not hold a Bomb Trap" }
        val validSquares = computeValidBombSquares(state, active.color)
        require(trackIndex in validSquares) { "Square $trackIndex is not a valid Bomb Trap location" }

        val updatedPlayers = state.players.mapIndexed { idx, p ->
            if (idx == state.activePlayerIndex) {
                p.copy(
                    heldPowerUps = removeFirstOccurrence(p.heldPowerUps, PowerUpType.BOMB_TRAP),
                    powerUpsUsed = p.powerUpsUsed + 1
                )
            } else p
        }

        val bombId = state.powerUpState.nextEntityId
        val newBomb = BombTrap(
            id = bombId,
            ownerColor = active.color,
            trackIndex = trackIndex,
            roundsRemaining = PowerUpState.BOMB_LIFETIME_ROUNDS
        )

        return state.copy(
            players = updatedPlayers,
            powerUpState = state.powerUpState.copy(
                bombs = state.powerUpState.bombs + newBomb,
                nextEntityId = bombId + 1
            ),
            lastEventMessage = "${active.nickname} hid a Bomb Trap!"
        )
    }

    fun computeLegalMoves(
        state: GameState,
        diceValue: Int,
        allowBaseEntryOverride: Boolean = false
    ): List<LegalMove> {
        if (state.isMatchFinished) return emptyList()
        require(diceValue in 1..12) { "Dice value must be between 1 and 12" }

        val controlledColor = controlledColorForActivePlayer(state)
        val controlledPlayer = state.playerByColor(controlledColor) ?: return emptyList()
        if (controlledPlayer.isEliminated || controlledPlayer.allTokensHome) return emptyList()

        val canEnterHomeColumn = !ruleSet.requiresCaptureBeforeHomeColumn || controlledPlayer.hasCapturedOpponent
        val safeIndices = state.activeSafeTrackIndices()
        val portalPair = state.chaosState.portalPair
        val canCollectPowerUp = ruleSet.powerUpsEnabled &&
            controlledPlayer.heldPowerUps.size < PowerUpState.MAX_HELD_PER_PLAYER

        val moves = mutableListOf<LegalMove>()
        for (token in controlledPlayer.tokens) {
            if (token.isHome) continue

            if (token.isInBase) {
                if (allowBaseEntryOverride || diceValue in ruleSet.entryRolls) {
                    val stepPath = listOf(StepTarget(progress = 0, outerWrap51 = false))
                    val (finalProgress, finalWrap51, fromPortal, toPortal) = applyPortalIfPresent(
                        color = controlledColor,
                        progress = 0,
                        outerWrap51 = false,
                        portalPair = portalPair
                    )
                    val destTrackIndex = trackIndexFor(controlledColor, finalProgress, finalWrap51)
                    val bombHit = findOpponentBomb(state, controlledColor, destTrackIndex)
                    val tilePickup = if (bombHit == null && canCollectPowerUp) {
                        findPowerUpTile(state, destTrackIndex)
                    } else null
                    val captured = if (bombHit == null) {
                        findCapturableOpponents(
                            state = state,
                            movingColor = controlledColor,
                            destProgress = finalProgress,
                            destOuterWrap51 = finalWrap51,
                            safeIndices = safeIndices
                        )
                    } else emptyList()

                    moves += LegalMove(
                        tokenId = token.id,
                        color = controlledColor,
                        fromProgress = Token.BASE_PROGRESS,
                        fromOuterWrap51 = false,
                        toProgress = finalProgress,
                        toOuterWrap51 = finalWrap51,
                        stepPath = stepPath,
                        capturedTokens = captured,
                        reachesHome = false,
                        leavesBase = true,
                        teleportedFromTrackIndex = fromPortal,
                        teleportedToTrackIndex = toPortal,
                        collectedPowerUp = tilePickup?.type,
                        collectedTileId = tilePickup?.id,
                        triggeredBomb = bombHit
                    )
                }
                continue
            }

            if (!canEnterHomeColumn && token.isOnMainTrack) {
                val startOffset = token.outerTrackOffset() ?: 0
                val steps = mutableListOf<StepTarget>()
                for (step in 1..diceValue) {
                    val offset = (startOffset + step) % Token.TRACK_LENGTH
                    if (offset == 51) {
                        steps += StepTarget(progress = 0, outerWrap51 = true)
                    } else {
                        steps += StepTarget(progress = offset, outerWrap51 = false)
                    }
                }
                val lastStep = steps.last()
                val captured = findCapturableOpponents(
                    state = state,
                    movingColor = controlledColor,
                    destProgress = lastStep.progress,
                    destOuterWrap51 = lastStep.outerWrap51,
                    safeIndices = safeIndices
                )
                moves += LegalMove(
                    tokenId = token.id,
                    color = controlledColor,
                    fromProgress = token.progress,
                    fromOuterWrap51 = token.outerWrap51,
                    toProgress = lastStep.progress,
                    toOuterWrap51 = lastStep.outerWrap51,
                    stepPath = steps,
                    capturedTokens = captured,
                    reachesHome = false,
                    leavesBase = false
                )
                continue
            }

            val steps = mutableListOf<StepTarget>()
            var curProgress = token.progress
            var curWrap51 = token.outerWrap51
            var valid = true

            for (s in 1..diceValue) {
                if (curWrap51) {
                    curWrap51 = false
                    curProgress = 0
                    steps += StepTarget(progress = 0, outerWrap51 = false)
                } else {
                    val nextP = curProgress + 1
                    if (nextP > Token.HOME_PROGRESS) {
                        valid = false
                        break
                    }
                    curProgress = nextP
                    steps += StepTarget(progress = nextP, outerWrap51 = false)
                }
            }

            if (valid && steps.isNotEmpty()) {
                val destStep = steps.last()
                val (finalProgress, finalWrap51, fromPortal, toPortal) = applyPortalIfPresent(
                    color = controlledColor,
                    progress = destStep.progress,
                    outerWrap51 = destStep.outerWrap51,
                    portalPair = portalPair
                )
                val destTrackIndex = trackIndexFor(controlledColor, finalProgress, finalWrap51)
                val bombHit = findOpponentBomb(state, controlledColor, destTrackIndex)
                val tilePickup = if (bombHit == null && canCollectPowerUp) {
                    findPowerUpTile(state, destTrackIndex)
                } else null
                val captured = if (bombHit == null) {
                    findCapturableOpponents(
                        state = state,
                        movingColor = controlledColor,
                        destProgress = finalProgress,
                        destOuterWrap51 = finalWrap51,
                        safeIndices = safeIndices
                    )
                } else emptyList()

                moves += LegalMove(
                    tokenId = token.id,
                    color = controlledColor,
                    fromProgress = token.progress,
                    fromOuterWrap51 = token.outerWrap51,
                    toProgress = finalProgress,
                    toOuterWrap51 = finalWrap51,
                    stepPath = steps,
                    capturedTokens = captured,
                    reachesHome = !finalWrap51 && finalProgress == Token.HOME_PROGRESS,
                    leavesBase = false,
                    teleportedFromTrackIndex = fromPortal,
                    teleportedToTrackIndex = toPortal,
                    collectedPowerUp = tilePickup?.type,
                    collectedTileId = tilePickup?.id,
                    triggeredBomb = bombHit
                )
            }
        }
        return moves
    }

    fun applyDiceRoll(
        state: GameState,
        rawDiceValue: Int,
        secondDiceValue: Int? = null,
        actingColor: PlayerColor? = null
    ): RollOutcome {
        val guard = TurnManager.validateRoll(state, actingColor)
        require(guard.isAllowed) {
            "Cannot roll dice: $guard (phase=${state.phase}, active=${state.activePlayer.color})"
        }
        require(rawDiceValue in 1..6) { "Dice value must be between 1 and 6" }

        val activePlayer = state.activePlayer
        val isDoubleDice = ruleSet.powerUpsEnabled && activePlayer.doubleDiceActive

        if (isDoubleDice) {
            val die2 = secondDiceValue ?: eventRandom.nextInt(1, 7)
            require(die2 in 1..6) { "Second dice value must be between 1 and 6" }
            val total = rawDiceValue + die2

            val consumedPlayers = state.players.mapIndexed { idx, p ->
                if (idx == state.activePlayerIndex) p.copy(doubleDiceActive = false) else p
            }
            val stateAfterConsume = state.copy(players = consumedPlayers)

            // Double Dice: doubled 6s count as 6 for extra roll / base entry; triple-6 rule does NOT apply
            val allowBaseEntry = (rawDiceValue == 6 && die2 == 6) || total in ruleSet.entryRolls
            val legalMoves = computeLegalMoves(
                state = stateAfterConsume,
                diceValue = total,
                allowBaseEntryOverride = allowBaseEntry
            )

            val rollDesc = "$rawDiceValue+$die2=$total"
            if (legalMoves.isEmpty()) {
                val nextState = stateAfterConsume.copy(
                    phase = TurnPhase.NO_LEGAL_MOVES,
                    lastDiceValue = total,
                    lastRawDiceValue = rawDiceValue,
                    lastSecondDiceValue = die2,
                    wasDiceDoubled = false,
                    wasDoubleDiceRoll = true,
                    legalMoves = emptyList(),
                    lastEventMessage = "${activePlayer.nickname} rolled $rollDesc — no legal moves."
                )
                return RollOutcome.NoLegalMoves(nextState)
            }

            val nextState = stateAfterConsume.copy(
                phase = TurnPhase.AWAITING_MOVE,
                lastDiceValue = total,
                lastRawDiceValue = rawDiceValue,
                lastSecondDiceValue = die2,
                wasDiceDoubled = false,
                wasDoubleDiceRoll = true,
                legalMoves = legalMoves,
                lastEventMessage = if (legalMoves.size == 1) {
                    "${activePlayer.nickname} rolled $rollDesc!"
                } else {
                    "${activePlayer.nickname} rolled $rollDesc — choose a token!"
                }
            )
            return if (legalMoves.size == 1) {
                RollOutcome.SingleLegalMove(nextState, legalMoves.first())
            } else {
                RollOutcome.MultipleLegalMoves(nextState, legalMoves)
            }
        }

        val wasChaosDoubled = activePlayer.diceStormCharges > 0
        val effectiveDiceValue = if (wasChaosDoubled) minOf(12, rawDiceValue * 2) else rawDiceValue

        val consumedPlayers = if (wasChaosDoubled) {
            state.players.mapIndexed { idx, p ->
                if (idx == state.activePlayerIndex) {
                    p.copy(diceStormCharges = (p.diceStormCharges - 1).coerceAtLeast(0))
                } else p
            }
        } else {
            state.players
        }
        val stateAfterCharge = state.copy(players = consumedPlayers)

        // Rule: Rolling three 6s in a row cancels the turn (the third roll is not played).
        if (rawDiceValue == 6 && stateAfterCharge.consecutiveSixes + 1 >= ruleSet.maxConsecutiveSixes) {
            val nextState = stateAfterCharge.copy(
                phase = TurnPhase.TURN_CANCELLED_TRIPLE_SIX,
                lastDiceValue = effectiveDiceValue,
                lastRawDiceValue = rawDiceValue,
                lastSecondDiceValue = null,
                wasDiceDoubled = wasChaosDoubled,
                wasDoubleDiceRoll = false,
                consecutiveSixes = stateAfterCharge.consecutiveSixes + 1,
                legalMoves = emptyList(),
                lastEventMessage = "Triple 6! ${activePlayer.nickname}'s turn is cancelled."
            )
            return RollOutcome.TripleSixCancelled(nextState)
        }

        val legalMoves = computeLegalMoves(stateAfterCharge, effectiveDiceValue)
        if (legalMoves.isEmpty()) {
            val rollDesc = if (wasChaosDoubled) "$rawDiceValue×2=$effectiveDiceValue" else "$effectiveDiceValue"
            val nextState = stateAfterCharge.copy(
                phase = TurnPhase.NO_LEGAL_MOVES,
                lastDiceValue = effectiveDiceValue,
                lastRawDiceValue = rawDiceValue,
                lastSecondDiceValue = null,
                wasDiceDoubled = wasChaosDoubled,
                wasDoubleDiceRoll = false,
                legalMoves = emptyList(),
                lastEventMessage = "${activePlayer.nickname} rolled $rollDesc — no legal moves."
            )
            return RollOutcome.NoLegalMoves(nextState)
        }

        val rollDesc = if (wasChaosDoubled) "$rawDiceValue×2 ($effectiveDiceValue)" else "$effectiveDiceValue"
        val nextState = stateAfterCharge.copy(
            phase = TurnPhase.AWAITING_MOVE,
            lastDiceValue = effectiveDiceValue,
            lastRawDiceValue = rawDiceValue,
            lastSecondDiceValue = null,
            wasDiceDoubled = wasChaosDoubled,
            wasDoubleDiceRoll = false,
            legalMoves = legalMoves,
            lastEventMessage = if (legalMoves.size == 1) {
                "${activePlayer.nickname} rolled $rollDesc!"
            } else {
                "${activePlayer.nickname} rolled $rollDesc — choose a token!"
            }
        )

        return if (legalMoves.size == 1) {
            RollOutcome.SingleLegalMove(nextState, legalMoves.first())
        } else {
            RollOutcome.MultipleLegalMoves(nextState, legalMoves)
        }
    }

    fun applyMove(
        state: GameState,
        move: LegalMove,
        actingColor: PlayerColor? = null
    ): MoveOutcome {
        val guard = TurnManager.validateMove(state, ruleSet, move, actingColor)
        require(guard.isAllowed) {
            "Cannot apply move $move: $guard (phase=${state.phase}, active=${state.activePlayer.color})"
        }
        val validMove = state.legalMoves.first {
            it.tokenId == move.tokenId && it.color == move.color && it.toProgress == move.toProgress &&
                it.toOuterWrap51 == move.toOuterWrap51
        }

        val activeRoller = state.activePlayer
        val controlledColor = validMove.color
        val bombTriggered = validMove.triggeredBomb
        val capturedSet = if (bombTriggered == null) validMove.capturedTokens.toSet() else emptySet()
        val captureCountsByVictim = if (bombTriggered == null) {
            validMove.capturedTokens.groupingBy { it.color }.eachCount()
        } else emptyMap()

        var actualCollectedPowerUp: PowerUpType? = null
        var updatedPowerUpState = state.powerUpState

        if (bombTriggered != null) {
            updatedPowerUpState = updatedPowerUpState.copy(
                bombs = updatedPowerUpState.bombs.filterNot { it.id == bombTriggered.id }
            )
        } else if (ruleSet.powerUpsEnabled && validMove.collectedPowerUp != null && validMove.collectedTileId != null) {
            val holder = state.playerByColor(controlledColor)
            if (holder != null && holder.heldPowerUps.size < PowerUpState.MAX_HELD_PER_PLAYER) {
                actualCollectedPowerUp = validMove.collectedPowerUp
                updatedPowerUpState = updatedPowerUpState.copy(
                    tiles = updatedPowerUpState.tiles.filterNot { it.id == validMove.collectedTileId },
                    pendingRespawns = updatedPowerUpState.pendingRespawns +
                        PendingPowerUpRespawn(roundsRemaining = PowerUpState.RESPAWN_ROUNDS)
                )
            }
        }

        val updatedPlayers = state.players.map { player ->
            val updatedTokens = player.tokens.map { token ->
                when {
                    player.color == controlledColor && token.id == validMove.tokenId -> {
                        if (bombTriggered != null) {
                            token.copy(
                                progress = Token.BASE_PROGRESS,
                                outerWrap51 = false,
                                shieldTurnsRemaining = 0
                            )
                        } else {
                            val clearShield = !validMove.toOuterWrap51 &&
                                validMove.toProgress > Token.MAX_MAIN_TRACK_PROGRESS
                            token.copy(
                                progress = validMove.toProgress,
                                outerWrap51 = validMove.toOuterWrap51,
                                shieldTurnsRemaining = if (clearShield) 0 else token.shieldTurnsRemaining
                            )
                        }
                    }
                    TokenRef(player.color, token.id) in capturedSet ->
                        token.copy(
                            progress = Token.BASE_PROGRESS,
                            outerWrap51 = false,
                            shieldTurnsRemaining = 0
                        )
                    else -> token
                }
            }

            val newHasCaptured = player.hasCapturedOpponent ||
                (bombTriggered == null && player.color == controlledColor && validMove.capturedTokens.isNotEmpty())

            val updatedHeld = if (player.color == controlledColor && actualCollectedPowerUp != null) {
                player.heldPowerUps + actualCollectedPowerUp
            } else {
                player.heldPowerUps
            }

            val updatedScore = if (ruleSet.timedScoringEnabled) {
                var s = player.score
                if (player.color == controlledColor) {
                    s += validMove.stepPath.size
                    s += validMove.capturedTokens.size * 10
                    if (validMove.reachesHome) s += 25
                }
                val lostCount = captureCountsByVictim[player.color] ?: 0
                if (lostCount > 0) s -= lostCount * 5
                s
            } else {
                player.score
            }

            val capturesAdded = when {
                bombTriggered != null && player.color == bombTriggered.ownerColor -> 1
                bombTriggered == null && player.color == controlledColor -> validMove.capturedTokens.size
                else -> 0
            }

            player.copy(
                tokens = updatedTokens,
                hasCapturedOpponent = newHasCaptured,
                heldPowerUps = updatedHeld,
                score = updatedScore,
                capturesMade = player.capturesMade + capturesAdded
            )
        }

        val movedPlayer = updatedPlayers.first { it.color == controlledColor }
        val updatedFinishOrder = state.finishOrder.toMutableList()
        var newlyAssignedRank: Int? = null

        val playerJustReachedGoal = movedPlayer.finishedTokenCount >= ruleSet.tokensHomeToWin
        if (playerJustReachedGoal && controlledColor !in updatedFinishOrder) {
            updatedFinishOrder += controlledColor
            newlyAssignedRank = updatedFinishOrder.size
        }

        // Teams 2v2 win check
        if (ruleSet.teamsMode) {
            val partnerColor = controlledColor.opposite()
            val partnerPlayer = updatedPlayers.firstOrNull { it.color == partnerColor }
            if (movedPlayer.allTokensHome && partnerPlayer?.allTokensHome == true) {
                val otherTeam = PlayerColor.entries.filter { it != controlledColor && it != partnerColor }
                val finalOrder = listOf(controlledColor, partnerColor) + otherTeam
                val teamLabel = teamNameFor(controlledColor)
                val finalState = state.copy(
                    players = updatedPlayers,
                    powerUpState = updatedPowerUpState,
                    phase = TurnPhase.MATCH_FINISHED,
                    legalMoves = emptyList(),
                    finishOrder = finalOrder,
                    winningTeamLabel = teamLabel,
                    lastEventMessage = "$teamLabel win the match!"
                )
                return MoveOutcome(
                    state = finalState,
                    move = validMove,
                    grantedExtraRoll = false,
                    playerFinishedRank = 1,
                    collectedPowerUp = actualCollectedPowerUp,
                    triggeredBomb = bombTriggered
                )
            }
        }

        // Immediate win modes
        if (ruleSet.immediateWinOnFirstFinisher && newlyAssignedRank == 1) {
            val remainingRanked = rankRemainingPlayers(updatedPlayers, updatedFinishOrder, state.eliminatedOrder)
            val finalOrder = updatedFinishOrder + remainingRanked
            val finalState = state.copy(
                players = updatedPlayers,
                powerUpState = updatedPowerUpState,
                phase = TurnPhase.MATCH_FINISHED,
                legalMoves = emptyList(),
                finishOrder = finalOrder,
                lastEventMessage = "${movedPlayer.nickname} wins the match!"
            )
            return MoveOutcome(
                state = finalState,
                move = validMove,
                grantedExtraRoll = false,
                playerFinishedRank = 1,
                collectedPowerUp = actualCollectedPowerUp,
                triggeredBomb = bombTriggered
            )
        }

        // Multi-finisher completion check
        val remainingUnfinished = updatedPlayers.filter {
            it.color !in updatedFinishOrder && !it.isEliminated
        }
        val matchEndsNow = !ruleSet.teamsMode && remainingUnfinished.size <= 1
        if (matchEndsNow && remainingUnfinished.size == 1) {
            updatedFinishOrder += remainingUnfinished.first().color
        }
        if (matchEndsNow) {
            val winnerName = updatedPlayers.first { it.color == updatedFinishOrder.first() }.nickname
            val finalState = state.copy(
                players = updatedPlayers,
                powerUpState = updatedPowerUpState,
                phase = TurnPhase.MATCH_FINISHED,
                legalMoves = emptyList(),
                finishOrder = updatedFinishOrder,
                lastEventMessage = "$winnerName wins the match!"
            )
            return MoveOutcome(
                state = finalState,
                move = validMove,
                grantedExtraRoll = false,
                playerFinishedRank = newlyAssignedRank,
                collectedPowerUp = actualCollectedPowerUp,
                triggeredBomb = bombTriggered
            )
        }

        // Extra roll check
        val rawRolled = state.lastRawDiceValue ?: state.lastDiceValue ?: 0
        val rolledBonusTrigger = if (state.wasDoubleDiceRoll) {
            // Double Dice: Doubled 6s count as a 6 for extra-roll purposes; triple-6 rule doesn't apply
            state.lastRawDiceValue == 6 && state.lastSecondDiceValue == 6
        } else {
            rawRolled in ruleSet.extraRollTriggers
        }
        val captureBonus = bombTriggered == null &&
            ruleSet.extraRollOnCapture && validMove.capturedTokens.isNotEmpty()
        val homeBonus = ruleSet.extraRollOnHome && validMove.reachesHome
        val activeStillHasMoves = if (ruleSet.teamsMode) {
            !activeRoller.allTokensHome ||
                updatedPlayers.first { it.color == activeRoller.color.opposite() }.allTokensHome.not()
        } else {
            !movedPlayer.allTokensHome
        }
        val getsExtraRoll = activeStillHasMoves && (rolledBonusTrigger || captureBonus || homeBonus)

        if (getsExtraRoll) {
            val newConsecutiveSixes = when {
                state.wasDoubleDiceRoll -> 0 // Triple-6 rule doesn't apply to Double Dice
                rawRolled == 6 -> state.consecutiveSixes + 1
                else -> 0
            }
            val eventMsg = when {
                bombTriggered != null ->
                    "BOOM! ${activeRoller.nickname} hit a Bomb Trap! Bonus roll!"
                actualCollectedPowerUp != null ->
                    "${activeRoller.nickname} picked up ${actualCollectedPowerUp.title} & gets a bonus roll!"
                validMove.teleportedToTrackIndex != null ->
                    "${activeRoller.nickname} teleported & gets a bonus roll!"
                validMove.capturedTokens.isNotEmpty() ->
                    "${activeRoller.nickname} captured & gets a bonus roll!"
                validMove.reachesHome ->
                    "${activeRoller.nickname} reached Home & gets a bonus roll!"
                state.wasDoubleDiceRoll ->
                    "Double 6s! ${activeRoller.nickname} gets a bonus roll!"
                else ->
                    "${activeRoller.nickname} rolled a 6! Bonus roll!"
            }
            val nextState = state.copy(
                players = updatedPlayers,
                powerUpState = updatedPowerUpState,
                phase = TurnPhase.AWAITING_ROLL,
                lastDiceValue = null,
                lastRawDiceValue = null,
                lastSecondDiceValue = null,
                wasDiceDoubled = false,
                wasDoubleDiceRoll = false,
                consecutiveSixes = newConsecutiveSixes,
                legalMoves = emptyList(),
                finishOrder = updatedFinishOrder,
                lastEventMessage = eventMsg
            )
            return MoveOutcome(
                state = nextState,
                move = validMove,
                grantedExtraRoll = true,
                playerFinishedRank = newlyAssignedRank,
                collectedPowerUp = actualCollectedPowerUp,
                triggeredBomb = bombTriggered
            )
        }

        val preAdvance = state.copy(
            players = updatedPlayers,
            powerUpState = updatedPowerUpState,
            finishOrder = updatedFinishOrder,
            lastDiceValue = null,
            lastRawDiceValue = null,
            lastSecondDiceValue = null,
            wasDiceDoubled = false,
            wasDoubleDiceRoll = false,
            consecutiveSixes = 0,
            legalMoves = emptyList()
        )
        val (advancedState, chaosEvent, eliminatedColor) = advanceTurnAndCheckRoundEvents(
            state = preAdvance,
            headlinePrefix = when {
                bombTriggered != null -> "BOOM! ${activeRoller.nickname} hit a Bomb Trap! "
                newlyAssignedRank != null -> "${movedPlayer.nickname} finished #$newlyAssignedRank! "
                actualCollectedPowerUp != null -> "${activeRoller.nickname} got ${actualCollectedPowerUp.title}! "
                validMove.teleportedToTrackIndex != null -> "${activeRoller.nickname} used a Portal! "
                validMove.capturedTokens.isNotEmpty() -> "${activeRoller.nickname} captured! "
                validMove.reachesHome -> "${activeRoller.nickname} reached Home! "
                else -> ""
            }
        )

        return MoveOutcome(
            state = advancedState,
            move = validMove,
            grantedExtraRoll = false,
            playerFinishedRank = newlyAssignedRank,
            triggeredChaosEvent = chaosEvent,
            eliminatedPlayer = eliminatedColor,
            collectedPowerUp = actualCollectedPowerUp,
            triggeredBomb = bombTriggered
        )
    }

    fun commitTurnAdvance(state: GameState): GameState {
        if (state.isMatchFinished) return state
        return advanceTurnAndCheckRoundEvents(state, headlinePrefix = "").first
    }

    fun handleTurnTimeout(state: GameState): GameState {
        if (state.isMatchFinished) return state
        val timedOutPlayer = state.activePlayer
        return advanceTurnAndCheckRoundEvents(
            state = state,
            headlinePrefix = "Time's up for ${timedOutPlayer.nickname}! "
        ).first
    }

    fun handleTimedClockExpired(state: GameState): GameState {
        if (state.isMatchFinished) return state
        val ranked = state.players
            .sortedWith(
                compareByDescending<PlayerState> { it.score }
                    .thenByDescending { it.finishedTokenCount }
                    .thenByDescending { it.totalProgress }
            )
            .map { it.color }

        val winner = state.players.first { it.color == ranked.first() }
        return state.copy(
            phase = TurnPhase.MATCH_FINISHED,
            timedRemainingSeconds = 0,
            legalMoves = emptyList(),
            finishOrder = ranked,
            lastEventMessage = "Time's up! ${winner.nickname} wins with ${winner.score} pts!"
        )
    }

    fun endMatchEarly(state: GameState): GameState {
        require(state.hasWinner) { "Cannot end match early before a winner is decided" }
        if (state.isMatchFinished) return state

        val remainingRanked = rankRemainingPlayers(state.players, state.finishOrder, state.eliminatedOrder)
        val fullOrder = state.finishOrder + remainingRanked
        val winnerName = state.players.first { it.color == fullOrder.first() }.nickname
        return state.copy(
            phase = TurnPhase.MATCH_FINISHED,
            legalMoves = emptyList(),
            finishOrder = fullOrder,
            lastEventMessage = "$winnerName wins the match!"
        )
    }

    private fun advanceTurnAndCheckRoundEvents(
        state: GameState,
        headlinePrefix: String
    ): Triple<GameState, ChaosEventType?, PlayerColor?> {
        val nextIdx = findNextEligiblePlayerIndex(
            players = state.players,
            currentIndex = state.activePlayerIndex,
            finishOrder = state.finishOrder
        )

        val wrappedRound = nextIdx <= state.activePlayerIndex
        val newRoundsCompleted = if (wrappedRound) state.fullRoundsCompleted + 1 else state.fullRoundsCompleted

        // Decrement active shield turns at the start of the owner's next turn (gives 2 full turns of protection)
        val playersWithShieldTick = if (ruleSet.powerUpsEnabled) {
            state.players.mapIndexed { idx, p ->
                if (idx == nextIdx) {
                    p.copy(
                        doubleDiceActive = false,
                        tokens = p.tokens.map { t ->
                            if (t.shieldTurnsRemaining > 0) {
                                t.copy(shieldTurnsRemaining = (t.shieldTurnsRemaining - 1).coerceAtLeast(0))
                            } else t
                        }
                    )
                } else p
            }
        } else {
            state.players
        }

        var workingState = state.copy(
            players = playersWithShieldTick,
            activePlayerIndex = nextIdx,
            phase = TurnPhase.AWAITING_ROLL,
            lastDiceValue = null,
            lastRawDiceValue = null,
            lastSecondDiceValue = null,
            wasDiceDoubled = false,
            wasDoubleDiceRoll = false,
            consecutiveSixes = 0,
            legalMoves = emptyList(),
            turnNumber = state.turnNumber + 1,
            fullRoundsCompleted = newRoundsCompleted
        )

        var triggeredChaos: ChaosEventType? = null
        var eliminatedColor: PlayerColor? = null

        if (wrappedRound) {
            workingState = tickChaosDurations(workingState)

            if (ruleSet.powerUpsEnabled) {
                workingState = tickPowerUpRoundTimers(workingState)
            }

            val chaosPeriod = ruleSet.chaosEventsEveryNRounds
            if (chaosPeriod != null && newRoundsCompleted > 0 && newRoundsCompleted % chaosPeriod == 0) {
                val (afterChaos, event) = triggerRandomChaosEvent(workingState)
                workingState = afterChaos
                triggeredChaos = event
            }

            val elimPeriod = ruleSet.eliminationEveryNRounds
            if (elimPeriod != null && newRoundsCompleted > 0 && newRoundsCompleted % elimPeriod == 0) {
                val (afterElim, victim) = applyEliminationRound(workingState)
                workingState = afterElim
                eliminatedColor = victim
                if (workingState.isMatchFinished) {
                    return Triple(workingState, triggeredChaos, eliminatedColor)
                }
            }
        }

        val nextPlayer = workingState.activePlayer
        val finalMsg = when {
            triggeredChaos != null -> "${triggeredChaos.title} ${nextPlayer.nickname}'s turn."
            eliminatedColor != null -> {
                val victimName = workingState.playerByColor(eliminatedColor)?.nickname ?: eliminatedColor.displayName
                "$victimName eliminated! ${nextPlayer.nickname}'s turn."
            }
            else -> "$headlinePrefix${nextPlayer.nickname}'s turn!"
        }

        return Triple(
            workingState.copy(lastEventMessage = finalMsg),
            triggeredChaos,
            eliminatedColor
        )
    }

    private fun createInitialPowerUpState(): PowerUpState {
        val eligible = (0 until Token.TRACK_LENGTH)
            .filter { it !in LudoBoardGeometry.SAFE_TRACK_INDICES }
            .shuffled(eventRandom)
            .take(PowerUpState.MAX_TILES_ON_BOARD)

        val types = PowerUpType.entries.shuffled(eventRandom)
        val tiles = eligible.mapIndexed { idx, trackIdx ->
            PowerUpTile(
                id = idx + 1,
                trackIndex = trackIdx,
                type = types[idx % types.size]
            )
        }
        return PowerUpState(
            tiles = tiles,
            pendingRespawns = emptyList(),
            bombs = emptyList(),
            nextEntityId = tiles.size + 1
        )
    }

    private fun tickPowerUpRoundTimers(state: GameState): GameState {
        val pu = state.powerUpState

        // 1. Tick bombs (expire after 5 rounds)
        val remainingBombs = pu.bombs
            .map { it.copy(roundsRemaining = it.roundsRemaining - 1) }
            .filter { it.roundsRemaining > 0 }

        // 2. Tick pending tile respawns (spawn after 2 rounds)
        val stillWaiting = mutableListOf<PendingPowerUpRespawn>()
        var readyToSpawnCount = 0
        for (pending in pu.pendingRespawns) {
            val left = pending.roundsRemaining - 1
            if (left <= 0) {
                readyToSpawnCount++
            } else {
                stillWaiting += pending.copy(roundsRemaining = left)
            }
        }

        val updatedTiles = pu.tiles.toMutableList()
        var nextId = pu.nextEntityId

        // Maintain up to MAX_TILES_ON_BOARD tiles
        val maxAdditional = (PowerUpState.MAX_TILES_ON_BOARD - updatedTiles.size).coerceAtLeast(0)
        val spawnCount = minOf(readyToSpawnCount, maxAdditional)

        repeat(spawnCount) {
            val trackIdx = pickRandomPowerUpSpawnSquare(
                state = state,
                existingTiles = updatedTiles,
                existingBombs = remainingBombs
            )
            if (trackIdx != null) {
                val chosenType = PowerUpType.entries[eventRandom.nextInt(PowerUpType.entries.size)]
                updatedTiles += PowerUpTile(id = nextId, trackIndex = trackIdx, type = chosenType)
                nextId++
            }
        }

        return state.copy(
            powerUpState = pu.copy(
                tiles = updatedTiles,
                pendingRespawns = stillWaiting,
                bombs = remainingBombs,
                nextEntityId = nextId
            )
        )
    }

    private fun pickRandomPowerUpSpawnSquare(
        state: GameState,
        existingTiles: List<PowerUpTile>,
        existingBombs: List<BombTrap>
    ): Int? {
        val forbidden = mutableSetOf<Int>()
        forbidden += state.activeSafeTrackIndices()
        forbidden += existingTiles.map { it.trackIndex }
        forbidden += existingBombs.map { it.trackIndex }

        val occupiedByTokens = state.players
            .filter { !it.isEliminated }
            .flatMap { it.tokens }
            .mapNotNull { it.mainTrackIndex() }
            .toSet()

        val emptyCandidates = (0 until Token.TRACK_LENGTH).filter { it !in forbidden && it !in occupiedByTokens }
        if (emptyCandidates.isNotEmpty()) {
            return emptyCandidates[eventRandom.nextInt(emptyCandidates.size)]
        }
        val fallbackCandidates = (0 until Token.TRACK_LENGTH).filter { it !in forbidden }
        return if (fallbackCandidates.isNotEmpty()) {
            fallbackCandidates[eventRandom.nextInt(fallbackCandidates.size)]
        } else null
    }

    private fun trackIndexFor(color: PlayerColor, progress: Int, outerWrap51: Boolean): Int? {
        val offset = when {
            outerWrap51 -> 51
            progress in 0..Token.MAX_MAIN_TRACK_PROGRESS -> progress
            else -> return null
        }
        return (color.startTrackIndex + offset) % Token.TRACK_LENGTH
    }

    private fun findPowerUpTile(state: GameState, trackIndex: Int?): PowerUpTile? {
        if (!ruleSet.powerUpsEnabled || trackIndex == null) return null
        return state.powerUpState.tiles.firstOrNull { it.trackIndex == trackIndex }
    }

    private fun findOpponentBomb(
        state: GameState,
        movingColor: PlayerColor,
        trackIndex: Int?
    ): BombTrap? {
        if (!ruleSet.powerUpsEnabled || trackIndex == null) return null
        return state.powerUpState.bombs.firstOrNull {
            it.trackIndex == trackIndex && it.ownerColor != movingColor
        }
    }

    private fun removeFirstOccurrence(list: List<PowerUpType>, target: PowerUpType): List<PowerUpType> {
        var removed = false
        val out = mutableListOf<PowerUpType>()
        for (item in list) {
            if (!removed && item == target) {
                removed = true
            } else {
                out += item
            }
        }
        return out
    }

    private fun tickChaosDurations(state: GameState): GameState {
        var chaos = state.chaosState
        if (chaos.safeStarsRoundsRemaining > 0) {
            val left = chaos.safeStarsRoundsRemaining - 1
            chaos = if (left == 0) {
                chaos.copy(activeSafeTrackIndices = null, safeStarsRoundsRemaining = 0)
            } else {
                chaos.copy(safeStarsRoundsRemaining = left)
            }
        }
        if (chaos.portalsRoundsRemaining > 0) {
            val left = chaos.portalsRoundsRemaining - 1
            chaos = if (left == 0) {
                chaos.copy(portalPair = null, portalsRoundsRemaining = 0)
            } else {
                chaos.copy(portalsRoundsRemaining = left)
            }
        }
        return state.copy(chaosState = chaos)
    }

    private fun triggerRandomChaosEvent(state: GameState): Pair<GameState, ChaosEventType> {
        val candidates = ChaosEventType.entries.filter { it != state.chaosState.lastTriggeredEvent }
        val chosen = candidates[eventRandom.nextInt(candidates.size)]

        return when (chosen) {
            ChaosEventType.MOVING_STARS -> {
                val newStars = (0 until Token.TRACK_LENGTH).shuffled(eventRandom).take(8).toSet()
                val nextChaos = state.chaosState.copy(
                    activeSafeTrackIndices = newStars,
                    safeStarsRoundsRemaining = 3,
                    lastTriggeredEvent = chosen,
                    activeAnnouncement = chosen,
                    announcementDetail = "8 new safe stars shimmer on the track for 3 rounds!",
                    announcementId = state.chaosState.announcementId + 1
                )
                state.copy(chaosState = nextChaos) to chosen
            }

            ChaosEventType.PORTALS -> {
                val excluded = LudoBoardGeometry.START_TRACK_INDICES + setOf(12, 25, 38, 51)
                val validSquares = (0 until Token.TRACK_LENGTH).filter { it !in excluded }
                val picked = validSquares.shuffled(eventRandom).take(2)
                val pair = picked[0] to picked[1]
                val nextChaos = state.chaosState.copy(
                    portalPair = pair,
                    portalsRoundsRemaining = 3,
                    lastTriggeredEvent = chosen,
                    activeAnnouncement = chosen,
                    announcementDetail = "Land on either glowing portal to warp across the board!",
                    announcementId = state.chaosState.announcementId + 1
                )
                state.copy(chaosState = nextChaos) to chosen
            }

            ChaosEventType.DICE_STORM -> {
                val boostedPlayers = state.players.map { p ->
                    if (!p.isEliminated && p.color !in state.finishOrder) {
                        p.copy(diceStormCharges = p.diceStormCharges + 1)
                    } else p
                }
                val nextChaos = state.chaosState.copy(
                    lastTriggeredEvent = chosen,
                    activeAnnouncement = chosen,
                    announcementDetail = "Everyone's next die roll is doubled (max 12)!",
                    announcementId = state.chaosState.announcementId + 1
                )
                state.copy(players = boostedPlayers, chaosState = nextChaos) to chosen
            }

            ChaosEventType.EARTHQUAKE -> {
                val movableBoardTokens = state.players
                    .filter { !it.isEliminated }
                    .flatMap { it.tokens }
                    .filter { !it.isInBase && !it.isHome && (it.outerWrap51 || it.progress > 0) }

                var detail = "The board shakes, but no tokens were knocked back!"
                val shakenPlayers = if (movableBoardTokens.isNotEmpty()) {
                    val target = movableBoardTokens[eventRandom.nextInt(movableBoardTokens.size)]
                    val ownerName = state.playerByColor(target.color)?.nickname ?: target.color.displayName
                    detail = "$ownerName's token shook back 3 squares!"
                    state.players.map { p ->
                        if (p.color == target.color) {
                            p.copy(
                                tokens = p.tokens.map { t ->
                                    if (t.id == target.id) {
                                        if (t.outerWrap51) {
                                            t.copy(progress = 48, outerWrap51 = false)
                                        } else {
                                            t.copy(progress = (t.progress - 3).coerceAtLeast(0))
                                        }
                                    } else t
                                }
                            )
                        } else p
                    }
                } else {
                    state.players
                }

                val nextChaos = state.chaosState.copy(
                    lastTriggeredEvent = chosen,
                    activeAnnouncement = chosen,
                    announcementDetail = detail,
                    announcementId = state.chaosState.announcementId + 1
                )
                state.copy(players = shakenPlayers, chaosState = nextChaos) to chosen
            }

            ChaosEventType.LUCKY_WIND -> {
                var boostedCount = 0
                val windPlayers = state.players.map { p ->
                    if (p.isEliminated) return@map p
                    p.copy(
                        tokens = p.tokens.map { t ->
                            if (t.isInHomeColumn) {
                                boostedCount++
                                t.copy(progress = (t.progress + 1).coerceAtMost(Token.HOME_PROGRESS))
                            } else t
                        }
                    )
                }

                val updatedFinishOrder = state.finishOrder.toMutableList()
                for (p in windPlayers) {
                    if (!p.isEliminated && p.finishedTokenCount >= ruleSet.tokensHomeToWin && p.color !in updatedFinishOrder) {
                        updatedFinishOrder += p.color
                    }
                }

                val detail = if (boostedCount > 0) {
                    "$boostedCount home-column token${if (boostedCount > 1) "s" else ""} glided forward 1 square!"
                } else {
                    "Breeze sweeps through the home columns!"
                }

                val nextChaos = state.chaosState.copy(
                    lastTriggeredEvent = chosen,
                    activeAnnouncement = chosen,
                    announcementDetail = detail,
                    announcementId = state.chaosState.announcementId + 1
                )

                val remaining = windPlayers.filter { !it.isEliminated && it.color !in updatedFinishOrder }
                if (remaining.size <= 1) {
                    if (remaining.size == 1) updatedFinishOrder += remaining.first().color
                    val winnerName = windPlayers.first { it.color == updatedFinishOrder.first() }.nickname
                    state.copy(
                        players = windPlayers,
                        finishOrder = updatedFinishOrder,
                        phase = TurnPhase.MATCH_FINISHED,
                        chaosState = nextChaos,
                        lastEventMessage = "$winnerName wins the match!"
                    ) to chosen
                } else {
                    state.copy(
                        players = windPlayers,
                        finishOrder = updatedFinishOrder,
                        chaosState = nextChaos
                    ) to chosen
                }
            }
        }
    }

    private fun applyEliminationRound(state: GameState): Pair<GameState, PlayerColor?> {
        val candidates = state.players.filter { !it.isEliminated && it.color !in state.finishOrder }
        if (candidates.size <= 1) return state to null

        val victim = candidates.minWith(
            compareBy<PlayerState> { it.totalProgress }
                .thenBy { it.finishedTokenCount }
                .thenBy { p -> p.tokens.count { !it.isInBase } }
                .thenByDescending { it.setup.seatIndex }
        )

        val updatedPlayers = state.players.map { p ->
            if (p.color == victim.color) {
                p.copy(
                    isEliminated = true,
                    tokens = p.tokens.map { it.copy(progress = Token.BASE_PROGRESS, outerWrap51 = false) }
                )
            } else p
        }
        val updatedEliminated = state.eliminatedOrder + victim.color
        val survivors = updatedPlayers.filter { !it.isEliminated }

        if (survivors.size == 1) {
            val winner = survivors.first()
            val finalOrder = listOf(winner.color) + updatedEliminated.reversed()
            return state.copy(
                players = updatedPlayers,
                eliminatedOrder = updatedEliminated,
                finishOrder = finalOrder,
                phase = TurnPhase.MATCH_FINISHED,
                legalMoves = emptyList(),
                lastEventMessage = "${winner.nickname} is the last player standing and wins!"
            ) to victim.color
        }

        val nextActiveIdx = if (updatedPlayers[state.activePlayerIndex].isEliminated) {
            findNextEligiblePlayerIndex(updatedPlayers, state.activePlayerIndex, state.finishOrder)
        } else {
            state.activePlayerIndex
        }

        return state.copy(
            players = updatedPlayers,
            eliminatedOrder = updatedEliminated,
            activePlayerIndex = nextActiveIdx
        ) to victim.color
    }

    private fun applyPortalIfPresent(
        color: PlayerColor,
        progress: Int,
        outerWrap51: Boolean,
        portalPair: Pair<Int, Int>?
    ): PortalResult {
        if (portalPair == null || outerWrap51 || progress !in 0..Token.MAX_MAIN_TRACK_PROGRESS) {
            return PortalResult(progress, outerWrap51, null, null)
        }
        val trackIdx = (color.startTrackIndex + progress) % Token.TRACK_LENGTH
        val destIdx = when (trackIdx) {
            portalPair.first -> portalPair.second
            portalPair.second -> portalPair.first
            else -> return PortalResult(progress, outerWrap51, null, null)
        }
        val destOffset = (destIdx - color.startTrackIndex + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
        return if (destOffset == 51) {
            PortalResult(0, true, trackIdx, destIdx)
        } else {
            PortalResult(destOffset, false, trackIdx, destIdx)
        }
    }

    private data class PortalResult(
        val progress: Int,
        val outerWrap51: Boolean,
        val fromTrack: Int?,
        val toTrack: Int?
    )

    private fun findCapturableOpponents(
        state: GameState,
        movingColor: PlayerColor,
        destProgress: Int,
        destOuterWrap51: Boolean,
        safeIndices: Set<Int>
    ): List<TokenRef> {
        val destOffset = when {
            destOuterWrap51 -> 51
            destProgress in 0..Token.MAX_MAIN_TRACK_PROGRESS -> destProgress
            else -> return emptyList()
        }
        val destTrackIndex = (movingColor.startTrackIndex + destOffset) % Token.TRACK_LENGTH
        if (destTrackIndex in safeIndices) return emptyList()

        val partnerColor = if (ruleSet.teamsMode) movingColor.opposite() else null
        val captured = mutableListOf<TokenRef>()
        for (player in state.players) {
            if (player.isEliminated) continue
            if (player.color == movingColor || player.color == partnerColor) continue
            for (token in player.tokens) {
                if (token.isShielded) continue
                if (token.mainTrackIndex() == destTrackIndex) {
                    captured += TokenRef(player.color, token.id)
                }
            }
        }
        return captured
    }

    private fun findNextEligiblePlayerIndex(
        players: List<PlayerState>,
        currentIndex: Int,
        finishOrder: List<PlayerColor>
    ): Int = TurnManager.nextEligiblePlayerIndex(
        players = players,
        currentIndex = currentIndex,
        finishOrder = finishOrder,
        ruleSet = ruleSet
    )

    private fun rankRemainingPlayers(
        players: List<PlayerState>,
        finishOrder: List<PlayerColor>,
        eliminatedOrder: List<PlayerColor>
    ): List<PlayerColor> {
        val aliveUnfinished = players
            .filter { it.color !in finishOrder && !it.isEliminated }
            .sortedWith(
                compareByDescending<PlayerState> { it.score }
                    .thenByDescending { it.finishedTokenCount }
                    .thenByDescending { it.totalProgress }
            )
            .map { it.color }
        val eliminatedReversed = eliminatedOrder.reversed().filter { it !in finishOrder }
        return aliveUnfinished + eliminatedReversed
    }

    private fun teamNameFor(color: PlayerColor): String = when (color) {
        PlayerColor.RED, PlayerColor.YELLOW -> "Team Red & Yellow"
        PlayerColor.GREEN, PlayerColor.BLUE -> "Team Green & Blue"
    }
}
