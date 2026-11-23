package com.example.engine

import kotlin.random.Random

/**
 * Pure, deterministic Ludo game engine supporting all 9 v2.1 modes across all 3 match formats:
 * - Main Modes: Classic, Blockades, Master, Quick, Arrow, Magic
 * - Extra Modes: Timed, Chaos, Elimination
 * - Formats: 1v1, 4 Players, 2v2 Team
 *
 * Core rules (Part 1):
 * - Single die rolled with Yalla-Ludo 6-stacking roll tray (e.g., [6, 6, 3]).
 * - Three 6s in a row discards the entire tray and passes the turn.
 * - Tray values are spent one by one in any order on any controlled token; unplayable values auto-discard.
 * - Bonus rolls (+1 on capture, +1 on token reaching Home) are granted after the current tray is fully spent.
 * - 8 safe squares (4 starts + 4 stars); exact finish required; captures all stacked non-safe opponents
 *   except in Blockades (walls block passing/landing) and Master (Joint Tokens).
 */
class LudoEngine(
    val ruleSet: LudoRuleSet = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.FOUR_PLAYERS),
    private val eventRandom: Random = Random.Default
) {

    fun createGame(
        playerSetups: List<PlayerSetup>,
        mode: GameModeId = ruleSet.modeId,
        matchFormat: MatchFormat = ruleSet.matchFormat,
        timedDurationSeconds: Int = 300,
        turnTimerSeconds: Int = TurnTimerOption.DEFAULT_SECONDS
    ): GameState {
        require(playerSetups.size in 2..4) { "Ludo requires 2 to 4 players" }
        val ordered = playerSetups.sortedBy { it.color.cornerOrder }
        val players = ordered.map { setup ->
            PlayerState(
                setup = setup,
                tokens = List(ruleSet.tokensPerPlayer) { tokenId ->
                    val startProgress = if (tokenId < ruleSet.startingTokensOnTrack) {
                        0
                    } else {
                        Token.BASE_PROGRESS
                    }
                    Token(
                        id = tokenId,
                        color = setup.color,
                        progress = startProgress
                    )
                }
            )
        }
        val initialState = GameState(
            mode = mode,
            matchFormat = matchFormat,
            turnTimerSeconds = turnTimerSeconds,
            players = players,
            activePlayerIndex = 0,
            phase = TurnPhase.AWAITING_ROLL,
            timedDurationSeconds = timedDurationSeconds,
            timedRemainingSeconds = timedDurationSeconds,
            lastEventMessage = buildStartMessage(mode, matchFormat, ordered.first().nickname)
        )
        return if (ruleSet.magicItemsEnabled) {
            spawnInitialMagicItems(initialState)
        } else {
            initialState
        }
    }

    /**
     * Resolves which color's tokens the active player controls on this turn.
     */
    fun controlledColor(state: GameState): PlayerColor =
        TurnManager.controlledColor(state, ruleSet)

    fun validateRoll(state: GameState, actingColor: PlayerColor? = null): TurnGuardResult =
        TurnManager.validateRoll(state, actingColor)

    fun validateMove(
        state: GameState,
        move: LegalMove,
        actingColor: PlayerColor? = null
    ): TurnGuardResult = TurnManager.validateMove(state, ruleSet, move, actingColor)

    fun validateTokenTap(
        state: GameState,
        tokenRef: TokenRef,
        actingColor: PlayerColor? = null
    ): TurnGuardResult = TurnManager.validateTokenTap(state, ruleSet, tokenRef, actingColor)

    fun isPlayersTurn(state: GameState, color: PlayerColor): Boolean =
        TurnManager.isPlayersTurn(state, color)

    fun upcomingTurnCycle(state: GameState): List<PlayerState> =
        TurnManager.upcomingTurnCycle(state, ruleSet)

    /**
     * Selects which unspent die value from the roll tray is currently targeted for token taps.
     */
    fun selectDieValue(state: GameState, dieValue: Int): GameState {
        if (state.phase != TurnPhase.AWAITING_MOVE) return state
        val playable = state.playableDiceValues()
        if (dieValue !in playable) return state
        return state.copy(selectedDieValue = dieValue)
    }

    /**
     * Rolls a single die (1..6) using Yalla-Ludo roll stacking:
     * - If Golden Dice is active on the player, forces the roll to 6 and consumes 1 charge.
     * - Rolling a 6 stacks 6 into [GameState.rolledDice] and keeps phase = AWAITING_ROLL
     *   so the player rolls again immediately.
     * - Three 6s in a row discards the entire tray and cancels the turn ("Three 6s — turn lost").
     * - Rolling a non-6 (or a Dice Storm doubled roll) ends the stack and transitions to AWAITING_MOVE
     *   so the player can spend all stacked tray values one by one in any order.
     */
    fun applyDiceRoll(
        state: GameState,
        rawDiceValue: Int
    ): RollOutcome {
        require(rawDiceValue in 1..6) { "Dice value must be 1..6" }
        if (state.isMatchFinished || state.phase != TurnPhase.AWAITING_ROLL) {
            return RollOutcome.NoLegalMoves(state)
        }

        val active = state.activePlayer
        val usedGoldenDice = active.goldenDiceCharges > 0
        val effectiveRaw = if (usedGoldenDice) 6 else rawDiceValue

        val usedDiceStorm = active.diceStormCharges > 0
        val effectiveDice = if (usedDiceStorm) (effectiveRaw * 2).coerceAtMost(12) else effectiveRaw

        val playersAfterCharges = state.players.mapIndexed { idx, p ->
            if (idx == state.activePlayerIndex) {
                p.copy(
                    goldenDiceCharges = if (usedGoldenDice) (p.goldenDiceCharges - 1).coerceAtLeast(0) else p.goldenDiceCharges,
                    diceStormCharges = if (usedDiceStorm) (p.diceStormCharges - 1).coerceAtLeast(0) else p.diceStormCharges
                )
            } else {
                p
            }
        }

        // Handle rolling a 6 (Yalla Ludo roll stacking)
        if (ruleSet.rollStackingEnabled && effectiveRaw == 6 && !usedDiceStorm) {
            val nextConsecutiveSixes = state.consecutiveSixes + 1
            if (nextConsecutiveSixes >= ruleSet.maxConsecutiveSixes) {
                val cancelled = state.copy(
                    players = playersAfterCharges,
                    phase = TurnPhase.TURN_CANCELLED_TRIPLE_SIX,
                    lastDiceValue = 6,
                    lastRawDiceValue = 6,
                    lastSecondDiceValue = null,
                    rolledDice = emptyList(),
                    remainingDice = emptyList(),
                    selectedDieValue = null,
                    earnedBonusRollThisTurn = false,
                    pendingBonusRolls = 0,
                    wasDiceDoubled = false,
                    wasDoubleDiceRoll = false,
                    wasGoldenDiceRoll = usedGoldenDice,
                    consecutiveSixes = 0,
                    legalMoves = emptyList(),
                    lastEventMessage = "Three 6s — turn lost"
                )
                return RollOutcome.TripleSixCancelled(cancelled)
            }

            val stackedTray = state.rolledDice + 6
            val stackedState = state.copy(
                players = playersAfterCharges,
                phase = TurnPhase.AWAITING_ROLL,
                lastDiceValue = 6,
                lastRawDiceValue = 6,
                lastSecondDiceValue = null,
                rolledDice = stackedTray,
                remainingDice = stackedTray,
                selectedDieValue = null,
                wasDiceDoubled = false,
                wasDoubleDiceRoll = false,
                wasGoldenDiceRoll = usedGoldenDice,
                consecutiveSixes = nextConsecutiveSixes,
                legalMoves = emptyList(),
                lastEventMessage = buildString {
                    if (usedGoldenDice) append("Golden Dice! ")
                    append("${active.nickname} rolled 6 (${stackedTray.joinToString(", ")}) — roll again!")
                }
            )
            return RollOutcome.RollAgainStacked(stackedState, 6)
        }

        // Stack ends with a non-6 (or a Dice Storm doubled value)
        val finalTray = state.rolledDice + effectiveDice
        val preMoveState = state.copy(
            players = playersAfterCharges,
            lastDiceValue = effectiveDice,
            lastRawDiceValue = effectiveRaw,
            lastSecondDiceValue = null,
            rolledDice = finalTray,
            remainingDice = finalTray,
            wasDiceDoubled = usedDiceStorm,
            wasDoubleDiceRoll = false,
            wasGoldenDiceRoll = usedGoldenDice,
            consecutiveSixes = 0
        )

        val allowDoubledSixBaseEntry = usedDiceStorm && effectiveRaw == 6
        val legalMoves = computeLegalMovesForDice(
            state = preMoveState,
            diceValues = finalTray,
            allowBaseEntryOverride = allowDoubledSixBaseEntry
        )

        if (legalMoves.isEmpty()) {
            val noMoveState = preMoveState.copy(
                phase = TurnPhase.NO_LEGAL_MOVES,
                remainingDice = emptyList(),
                selectedDieValue = null,
                legalMoves = emptyList(),
                lastEventMessage = buildString {
                    append("${active.nickname} rolled ${finalTray.joinToString(" + ")}")
                    if (usedDiceStorm) append(" (Dice Storm!)")
                    append(" — no legal moves")
                }
            )
            return RollOutcome.NoLegalMoves(noMoveState)
        }

        val defaultSelected = legalMoves.first().dieValueUsed
        val awaitingMoveState = preMoveState.copy(
            phase = TurnPhase.AWAITING_MOVE,
            legalMoves = legalMoves,
            selectedDieValue = defaultSelected,
            lastEventMessage = buildString {
                if (usedDiceStorm) append("Dice Storm! ")
                append("${active.nickname} rolled ${finalTray.joinToString(", ")}")
                if (controlledColor(preMoveState) != active.color) {
                    append(" (moving partner ${controlledColor(preMoveState).displayName})")
                }
            }
        )

        return if (legalMoves.size == 1 && finalTray.size == 1) {
            RollOutcome.SingleLegalMove(awaitingMoveState, legalMoves.first())
        } else {
            RollOutcome.MultipleLegalMoves(awaitingMoveState, legalMoves)
        }
    }

    /**
     * Computes all distinct legal moves across a list of unspent die values in the roll tray.
     */
    fun computeLegalMovesForDice(
        state: GameState,
        diceValues: List<Int>,
        allowBaseEntryOverride: Boolean = false
    ): List<LegalMove> {
        val distinctValues = diceValues.distinct()
        if (distinctValues.isEmpty()) return emptyList()
        val combined = mutableListOf<LegalMove>()
        for (dieVal in distinctValues) {
            combined += computeLegalMoves(
                state = state,
                diceValue = dieVal,
                allowBaseEntryOverride = allowBaseEntryOverride
            )
        }
        return combined
    }

    /**
     * Computes all legal token moves for the currently controlled color with a single [diceValue].
     */
    fun computeLegalMoves(
        state: GameState,
        diceValue: Int,
        allowBaseEntryOverride: Boolean = false
    ): List<LegalMove> {
        val movingColor = controlledColor(state)
        val player = state.playerByColor(movingColor) ?: return emptyList()
        if (player.isEliminated) return emptyList()

        val activeSafeIndices = state.activeSafeTrackIndices()
        val partnerColor = if (ruleSet.teamsMode) movingColor.opposite() else null
        val canEnterHomeColumn = !ruleSet.requiresCaptureBeforeHomeColumn || player.hasCapturedOpponent
        val opponentBlockades = if (ruleSet.blockadesEnabled) {
            computeOpponentBlockadeTracks(state, movingColor, partnerColor, activeSafeIndices)
        } else {
            emptySet()
        }

        val handledTokenIds = mutableSetOf<Int>()
        val result = mutableListOf<LegalMove>()

        for (token in player.tokens) {
            if (token.isHome || token.id in handledTokenIds) continue

            // In Master mode, a Joint Token (2 paired tokens) moves as ONE unit using ONE die value
            val jointPartner = if (ruleSet.jointTokensEnabled && token.jointPartnerId != null) {
                player.tokens.firstOrNull {
                    it.id == token.jointPartnerId &&
                        !it.isInBase && !it.isHome &&
                        it.progress == token.progress &&
                        it.outerWrap51 == token.outerWrap51
                }
            } else {
                null
            }

            val isJointMove = jointPartner != null
            val movingIds = if (jointPartner != null) {
                listOf(token.id, jointPartner.id).sorted()
            } else {
                listOf(token.id)
            }
            handledTokenIds += movingIds
            val primaryTokenId = movingIds.first()

            if (token.isInBase) {
                val canLeave = diceValue in ruleSet.entryRolls || allowBaseEntryOverride
                if (!canLeave) continue

                val startTrack = movingColor.startTrackIndex
                if (ruleSet.blockadesEnabled && startTrack in opponentBlockades) continue

                val landing = evaluateLanding(
                    state = state,
                    movingColor = movingColor,
                    partnerColor = partnerColor,
                    destProgress = 0,
                    destOuterWrap51 = false,
                    isJointMove = false,
                    tokenIsShielded = token.isShielded,
                    activeSafeIndices = activeSafeIndices,
                    opponentBlockades = opponentBlockades,
                    movingTokenIds = movingIds
                ) ?: continue

                result += LegalMove(
                    tokenId = primaryTokenId,
                    tokenIds = movingIds,
                    isJointMove = false,
                    color = movingColor,
                    fromProgress = Token.BASE_PROGRESS,
                    fromOuterWrap51 = false,
                    toProgress = landing.finalProgress,
                    toOuterWrap51 = landing.finalOuterWrap51,
                    stepPath = listOf(StepTarget(0, false)) + landing.extraStepTargets,
                    capturedTokens = landing.captures,
                    reachesHome = false,
                    leavesBase = true,
                    teleportedFromTrackIndex = landing.portalFrom,
                    teleportedToTrackIndex = landing.portalTo,
                    arrowFromTrackIndex = landing.arrowFrom,
                    arrowToTrackIndex = landing.arrowTo,
                    collectedPowerUp = landing.collectedTile?.type,
                    collectedTileId = landing.collectedTile?.id,
                    triggeredBomb = landing.triggeredBomb,
                    dieValueUsed = diceValue,
                    formsBlockade = landing.formsBlockade
                )
            } else {
                val stepPath = buildStepPath(
                    fromProgress = token.progress,
                    fromOuterWrap51 = token.outerWrap51,
                    steps = diceValue,
                    canEnterHomeColumn = canEnterHomeColumn,
                    movingColor = movingColor,
                    opponentBlockades = opponentBlockades
                ) ?: continue

                val landed = stepPath.last()
                val landing = evaluateLanding(
                    state = state,
                    movingColor = movingColor,
                    partnerColor = partnerColor,
                    destProgress = landed.progress,
                    destOuterWrap51 = landed.outerWrap51,
                    isJointMove = isJointMove,
                    tokenIsShielded = token.isShielded,
                    activeSafeIndices = activeSafeIndices,
                    opponentBlockades = opponentBlockades,
                    movingTokenIds = movingIds
                ) ?: continue

                result += LegalMove(
                    tokenId = primaryTokenId,
                    tokenIds = movingIds,
                    isJointMove = isJointMove,
                    color = movingColor,
                    fromProgress = token.progress,
                    fromOuterWrap51 = token.outerWrap51,
                    toProgress = landing.finalProgress,
                    toOuterWrap51 = landing.finalOuterWrap51,
                    stepPath = stepPath + landing.extraStepTargets,
                    capturedTokens = landing.captures,
                    reachesHome = !landing.finalOuterWrap51 && landing.finalProgress == Token.HOME_PROGRESS,
                    leavesBase = false,
                    teleportedFromTrackIndex = landing.portalFrom,
                    teleportedToTrackIndex = landing.portalTo,
                    arrowFromTrackIndex = landing.arrowFrom,
                    arrowToTrackIndex = landing.arrowTo,
                    collectedPowerUp = landing.collectedTile?.type,
                    collectedTileId = landing.collectedTile?.id,
                    triggeredBomb = landing.triggeredBomb,
                    dieValueUsed = diceValue,
                    formsBlockade = landing.formsBlockade
                )
            }
        }
        return result
    }

    private data class LandingEvaluation(
        val finalProgress: Int,
        val finalOuterWrap51: Boolean,
        val extraStepTargets: List<StepTarget> = emptyList(),
        val captures: List<TokenRef> = emptyList(),
        val portalFrom: Int? = null,
        val portalTo: Int? = null,
        val arrowFrom: Int? = null,
        val arrowTo: Int? = null,
        val collectedTile: PowerUpTile? = null,
        val triggeredBomb: BombTrap? = null,
        val formsBlockade: Boolean = false
    )

    /**
     * Evaluates arrow slides, Chaos portals, Master Joint Token landing restrictions,
     * opponent captures, Blockades formation, Magic item pickups, and hidden bombs.
     * Returns null if the landing is illegal (e.g., single token trying to land on an
     * opponent Joint Token on a non-safe square, or landing on an opponent blockade).
     */
    private fun evaluateLanding(
        state: GameState,
        movingColor: PlayerColor,
        partnerColor: PlayerColor?,
        destProgress: Int,
        destOuterWrap51: Boolean,
        isJointMove: Boolean,
        tokenIsShielded: Boolean,
        activeSafeIndices: Set<Int>,
        opponentBlockades: Set<Int>,
        movingTokenIds: List<Int>
    ): LandingEvaluation? {
        var finalProgress = destProgress
        var finalOuterWrap51 = destOuterWrap51
        val extraSteps = mutableListOf<StepTarget>()
        var arrowFrom: Int? = null
        var arrowTo: Int? = null
        var portalFrom: Int? = null
        var portalTo: Int? = null

        // 1. Arrow Mode exact landing slide (tail -> tip)
        if (ruleSet.arrowsEnabled && (finalOuterWrap51 || finalProgress in 0..Token.MAX_MAIN_TRACK_PROGRESS)) {
            val landedOffset = if (finalOuterWrap51) 51 else finalProgress
            val landedTrack = (movingColor.startTrackIndex + landedOffset) % Token.TRACK_LENGTH
            val tipTrack = LudoBoardGeometry.ARROW_SHORTCUTS[landedTrack]
            if (tipTrack != null) {
                arrowFrom = landedTrack
                arrowTo = tipTrack
                val newOffset = (tipTrack - movingColor.startTrackIndex + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
                finalProgress = newOffset
                finalOuterWrap51 = newOffset == 51
                if (finalOuterWrap51) finalProgress = Token.MAX_MAIN_TRACK_PROGRESS
                extraSteps += StepTarget(finalProgress, finalOuterWrap51)
            }
        }

        // 2. Chaos Mode linked portals
        val portalPair = state.chaosState.portalPair
        if (portalPair != null && (finalOuterWrap51 || finalProgress in 0..Token.MAX_MAIN_TRACK_PROGRESS)) {
            val currentOffset = if (finalOuterWrap51) 51 else finalProgress
            val currentTrack = (movingColor.startTrackIndex + currentOffset) % Token.TRACK_LENGTH
            val exitTrack = when (currentTrack) {
                portalPair.first -> portalPair.second
                portalPair.second -> portalPair.first
                else -> null
            }
            if (exitTrack != null) {
                portalFrom = currentTrack
                portalTo = exitTrack
                val exitOffset = (exitTrack - movingColor.startTrackIndex + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
                if (exitOffset == 51) {
                    finalProgress = Token.MAX_MAIN_TRACK_PROGRESS
                    finalOuterWrap51 = true
                } else {
                    finalProgress = exitOffset
                    finalOuterWrap51 = false
                }
                extraSteps += StepTarget(finalProgress, finalOuterWrap51)
            }
        }

        // Home column or center Home: no opponent interaction, no items, no bombs
        if (!finalOuterWrap51 && finalProgress > Token.MAX_MAIN_TRACK_PROGRESS) {
            return LandingEvaluation(
                finalProgress = finalProgress,
                finalOuterWrap51 = false,
                extraStepTargets = extraSteps,
                portalFrom = portalFrom,
                portalTo = portalTo,
                arrowFrom = arrowFrom,
                arrowTo = arrowTo
            )
        }

        val finalOffset = if (finalOuterWrap51) 51 else finalProgress
        val finalTrackIndex = (movingColor.startTrackIndex + finalOffset) % Token.TRACK_LENGTH

        // Cannot land on an opponent blockade (after arrow/portal too)
        if (ruleSet.blockadesEnabled && finalTrackIndex in opponentBlockades) {
            return null
        }

        val isSafe = finalTrackIndex in activeSafeIndices

        // Master Mode Joint Token landing restriction:
        // On a non-safe square, a single token CANNOT land on or capture an opponent Joint Token.
        if (ruleSet.jointTokensEnabled && !isSafe && !isJointMove) {
            if (hasOpponentJointTokenAt(state, movingColor, partnerColor, finalTrackIndex)) {
                return null
            }
        }

        // Check hidden opponent Bomb Trap (Magic mode)
        val bombAtDest = if (ruleSet.magicItemsEnabled) {
            state.powerUpState.bombs.firstOrNull { bomb ->
                bomb.trackIndex == finalTrackIndex &&
                    bomb.ownerColor != movingColor &&
                    bomb.ownerColor != partnerColor
            }
        } else {
            null
        }
        val triggersBomb = bombAtDest != null && !tokenIsShielded

        // Captures on non-safe squares (skipped if the moving token itself blew up on a bomb)
        val captures = if (!triggersBomb && !isSafe) {
            findCapturableOpponents(
                state = state,
                movingColor = movingColor,
                partnerColor = partnerColor,
                trackIndex = finalTrackIndex,
                attackerIsJoint = isJointMove
            )
        } else {
            emptyList()
        }

        // Magic item pickup on exact landing (skipped if blown up on a bomb)
        val collectedTile = if (ruleSet.magicItemsEnabled && !triggersBomb) {
            state.powerUpState.tiles.firstOrNull { it.trackIndex == finalTrackIndex }
        } else {
            null
        }

        // Check if this move forms a blockade (Blockades mode only, non-safe square, >=1 other own token already there)
        val formsBlockade = if (ruleSet.blockadesEnabled && !isSafe && !triggersBomb) {
            val ownTokensAlreadyThere = state.playerByColor(movingColor)?.tokens?.count { t ->
                t.id !in movingTokenIds && t.mainTrackIndex() == finalTrackIndex
            } ?: 0
            ownTokensAlreadyThere >= 1
        } else {
            false
        }

        return LandingEvaluation(
            finalProgress = finalProgress,
            finalOuterWrap51 = finalOuterWrap51,
            extraStepTargets = extraSteps,
            captures = captures,
            portalFrom = portalFrom,
            portalTo = portalTo,
            arrowFrom = arrowFrom,
            arrowTo = arrowTo,
            collectedTile = collectedTile,
            triggeredBomb = if (triggersBomb) bombAtDest else null,
            formsBlockade = formsBlockade
        )
    }

    /**
     * Applies a chosen [LegalMove] from the current roll tray:
     * - Moves the token (or both tokens of a Master Joint Token).
     * - Applies Arrow slide, Chaos portal, opponent captures, Bomb Trap explosion, or instant Magic item.
     * - Removes one occurrence of `move.dieValueUsed` from `remainingDice`.
     * - If Bomb Trap item was collected with valid squares ahead, enters `AWAITING_BOMB_PLACEMENT`.
     * - Otherwise recomputes legal moves for remaining tray dice; auto-discards unplayable values;
     *   grants bonus roll(s) AFTER the tray is fully spent; or advances to next eligible player.
     */
    fun applyMove(state: GameState, move: LegalMove): MoveOutcome {
        if (state.isMatchFinished || state.phase != TurnPhase.AWAITING_MOVE) {
            return MoveOutcome(state, move, grantedExtraRoll = false)
        }

        val activePlayer = state.activePlayer
        val movingColor = move.color
        val partnerColor = if (ruleSet.teamsMode) movingColor.opposite() else null
        val activeSafeIndices = state.activeSafeTrackIndices()

        var updatedPlayers = state.players
        var updatedPowerUpState = state.powerUpState
        val capturedIdsByColor = move.capturedTokens.groupBy({ it.color }, { it.tokenId })
        var totalCaptured = move.capturedTokens.size
        var actualCapturesList = move.capturedTokens.toMutableList()
        var actualReachesHome = move.reachesHome
        var actualFinalProgress = move.toProgress
        var actualFinalOuterWrap = move.toOuterWrap51
        var actualTriggeredBomb = move.triggeredBomb
        var rocketBoostSteps = 0
        var collectedPowerUp = move.collectedPowerUp
        var pendingBombForPlacement: PendingBombPlacement? = null
        var unlockedMasterHome = false
        var eventNote = ""

        val movingTokenIdSet = move.tokenIds.toSet()

        // Step 1: Move the token(s) to move.toProgress (or back to base if triggered a bomb)
        updatedPlayers = updatedPlayers.map { player ->
            when {
                player.color == movingColor -> {
                    val movedTokens = player.tokens.map { token ->
                        if (token.id in movingTokenIdSet) {
                            if (actualTriggeredBomb != null) {
                                token.copy(
                                    progress = Token.BASE_PROGRESS,
                                    outerWrap51 = false,
                                    shieldTurnsRemaining = 0,
                                    jointPartnerId = null
                                )
                            } else {
                                token.copy(
                                    progress = actualFinalProgress,
                                    outerWrap51 = actualFinalOuterWrap,
                                    jointPartnerId = if (actualReachesHome) null else token.jointPartnerId
                                )
                            }
                        } else {
                            token
                        }
                    }
                    val stepPoints = if (ruleSet.timedScoringEnabled && actualTriggeredBomb == null) {
                        if (move.leavesBase) 1 else move.stepPath.size
                    } else 0
                    val capturePoints = if (ruleSet.timedScoringEnabled) totalCaptured * 10 else 0
                    val homePoints = if (ruleSet.timedScoringEnabled && actualReachesHome) {
                        25 * movingTokenIdSet.size
                    } else 0
                    val bombPenalty = if (ruleSet.timedScoringEnabled && actualTriggeredBomb != null) -5 else 0

                    val unlockedNow = totalCaptured > 0 && !player.hasCapturedOpponent
                    if (unlockedNow && ruleSet.requiresCaptureBeforeHomeColumn) {
                        unlockedMasterHome = true
                    }

                    val finalizedTokens = if (ruleSet.jointTokensEnabled) {
                        reconcileMasterJointTokens(movedTokens)
                    } else {
                        movedTokens
                    }

                    player.copy(
                        tokens = finalizedTokens,
                        hasCapturedOpponent = player.hasCapturedOpponent || totalCaptured > 0,
                        score = (player.score + stepPoints + capturePoints + homePoints + bombPenalty).coerceAtLeast(0),
                        capturesMade = player.capturesMade + totalCaptured
                    )
                }

                player.color in capturedIdsByColor -> {
                    val victims = capturedIdsByColor.getValue(player.color).toSet()
                    val resetTokens = player.tokens.map { token ->
                        if (token.id in victims) {
                            token.copy(
                                progress = Token.BASE_PROGRESS,
                                outerWrap51 = false,
                                shieldTurnsRemaining = 0,
                                jointPartnerId = null
                            )
                        } else if (token.jointPartnerId in victims) {
                            token.copy(jointPartnerId = null)
                        } else {
                            token
                        }
                    }
                    val penalty = if (ruleSet.timedScoringEnabled) -5 * victims.size else 0
                    player.copy(
                        tokens = resetTokens,
                        score = (player.score + penalty).coerceAtLeast(0)
                    )
                }

                else -> player
            }
        }

        // Remove triggered bomb from the board if it exploded
        if (actualTriggeredBomb != null) {
            updatedPowerUpState = updatedPowerUpState.copy(
                bombs = updatedPowerUpState.bombs.filterNot { it.id == actualTriggeredBomb.id }
            )
            eventNote = "BOOM! ${activePlayer.nickname} hit ${actualTriggeredBomb.ownerColor.displayName}'s Bomb Trap!"
        }

        // Step 2: Instant Magic Item Activation (Magic mode only)
        if (ruleSet.magicItemsEnabled && collectedPowerUp != null && move.collectedTileId != null && actualTriggeredBomb == null) {
            updatedPowerUpState = updatedPowerUpState.copy(
                tiles = updatedPowerUpState.tiles.filterNot { it.id == move.collectedTileId }
            )
            // Credit powerUpsUsed to active player
            updatedPlayers = updatedPlayers.mapIndexed { idx, p ->
                if (idx == state.activePlayerIndex) p.copy(powerUpsUsed = p.powerUpsUsed + 1) else p
            }

            when (collectedPowerUp) {
                PowerUpType.SHIELD -> {
                    updatedPlayers = updatedPlayers.map { p ->
                        if (p.color == movingColor) {
                            p.copy(
                                tokens = p.tokens.map { t ->
                                    if (t.id in movingTokenIdSet) {
                                        t.copy(shieldTurnsRemaining = PowerUpState.SHIELD_DURATION_TURNS)
                                    } else t
                                }
                            )
                        } else p
                    }
                    eventNote = "${activePlayer.nickname} grabbed a Shield! Protected for 2 turns."
                }

                PowerUpType.GOLDEN_DICE -> {
                    updatedPlayers = updatedPlayers.mapIndexed { idx, p ->
                        if (idx == state.activePlayerIndex) {
                            p.copy(goldenDiceCharges = p.goldenDiceCharges + 1)
                        } else p
                    }
                    eventNote = "${activePlayer.nickname} grabbed Golden Dice! Next roll is a guaranteed 6!"
                }

                PowerUpType.ROCKET -> {
                    val boostRoll = eventRandom.nextInt(1, 9) // 1..8 inclusive
                    val tempState = state.copy(players = updatedPlayers, powerUpState = updatedPowerUpState)
                    val movingPlayerNow = tempState.playerByColor(movingColor)!!
                    val movingTokenNow = movingPlayerNow.tokens.first { it.id == move.tokenId }
                    val canEnterHome = !ruleSet.requiresCaptureBeforeHomeColumn || movingPlayerNow.hasCapturedOpponent
                    val rocketRes = applyRocketBoost(
                        state = tempState,
                        token = movingTokenNow,
                        boostSteps = boostRoll,
                        canEnterHome = canEnterHome,
                        partnerColor = partnerColor,
                        activeSafeIndices = activeSafeIndices
                    )
                    rocketBoostSteps = rocketRes.actualSteps
                    actualFinalProgress = rocketRes.finalProgress
                    actualFinalOuterWrap = rocketRes.finalOuterWrap51
                    actualReachesHome = actualReachesHome || rocketRes.reachedHome
                    if (rocketRes.triggeredBomb != null) {
                        actualTriggeredBomb = rocketRes.triggeredBomb
                        updatedPowerUpState = updatedPowerUpState.copy(
                            bombs = updatedPowerUpState.bombs.filterNot { it.id == rocketRes.triggeredBomb.id }
                        )
                    }
                    if (rocketRes.captures.isNotEmpty()) {
                        totalCaptured += rocketRes.captures.size
                        actualCapturesList += rocketRes.captures
                    }
                    val rocketVictimMap = rocketRes.captures.groupBy({ it.color }, { it.tokenId })
                    updatedPlayers = updatedPlayers.map { p ->
                        when {
                            p.color == movingColor -> {
                                val nextTokens = p.tokens.map { t ->
                                    if (t.id == movingTokenNow.id) {
                                        if (rocketRes.triggeredBomb != null) {
                                            t.copy(
                                                progress = Token.BASE_PROGRESS,
                                                outerWrap51 = false,
                                                shieldTurnsRemaining = 0,
                                                jointPartnerId = null
                                            )
                                        } else {
                                            t.copy(
                                                progress = rocketRes.finalProgress,
                                                outerWrap51 = rocketRes.finalOuterWrap51,
                                                jointPartnerId = if (rocketRes.reachedHome) null else t.jointPartnerId
                                            )
                                        }
                                    } else t
                                }
                                p.copy(
                                    tokens = nextTokens,
                                    hasCapturedOpponent = p.hasCapturedOpponent || rocketRes.captures.isNotEmpty(),
                                    capturesMade = p.capturesMade + rocketRes.captures.size
                                )
                            }
                            p.color in rocketVictimMap -> {
                                val victims = rocketVictimMap.getValue(p.color).toSet()
                                p.copy(
                                    tokens = p.tokens.map { t ->
                                        if (t.id in victims) {
                                            t.copy(
                                                progress = Token.BASE_PROGRESS,
                                                outerWrap51 = false,
                                                shieldTurnsRemaining = 0,
                                                jointPartnerId = null
                                            )
                                        } else t
                                    }
                                )
                            }
                            else -> p
                        }
                    }
                    eventNote = when {
                        rocketRes.triggeredBomb != null ->
                            "Rocket boosted +$rocketBoostSteps into ${rocketRes.triggeredBomb.ownerColor.displayName}'s Bomb Trap!"
                        rocketRes.reachedHome ->
                            "Rocket boosted +$rocketBoostSteps straight into Home!"
                        rocketRes.captures.isNotEmpty() ->
                            "Rocket boosted +$rocketBoostSteps and captured ${rocketRes.captures.first().color.displayName}!"
                        else ->
                            "Rocket launched ${activePlayer.nickname}'s token +$rocketBoostSteps squares!"
                    }
                }

                PowerUpType.BOMB_TRAP -> {
                    val tempState = state.copy(players = updatedPlayers, powerUpState = updatedPowerUpState)
                    val movingTokenNow = tempState.playerByColor(movingColor)
                        ?.tokens?.firstOrNull { it.id == move.tokenId }
                    val validBombSquares = if (movingTokenNow != null) {
                        computeValidBombSquaresAhead(tempState, movingColor, movingTokenNow)
                    } else {
                        emptyList()
                    }
                    if (validBombSquares.isNotEmpty()) {
                        pendingBombForPlacement = PendingBombPlacement(
                            ownerColor = activePlayer.color,
                            sourceTokenColor = movingColor,
                            sourceTokenId = move.tokenId,
                            validTrackIndices = validBombSquares
                        )
                        updatedPowerUpState = updatedPowerUpState.copy(
                            pendingBombPlacement = pendingBombForPlacement
                        )
                        eventNote = "${activePlayer.nickname} grabbed a Bomb Trap! Pick a glowing square ahead to hide it."
                    } else {
                        eventNote = "${activePlayer.nickname} grabbed a Bomb Trap, but no empty squares are available ahead."
                    }
                }
            }
        }

        // Update Master home unlock event tracking
        val nextUnlockColor = if (unlockedMasterHome) movingColor else state.lastHomeUnlockedColor
        val nextUnlockEventId = if (unlockedMasterHome) state.lastHomeUnlockEventId + 1 else state.lastHomeUnlockEventId

        // Step 3: Check finish order & match completion
        val finishResult = evaluateFinishAndMatchEnd(
            state = state,
            updatedPlayers = updatedPlayers,
            movingColor = movingColor
        )
        val finishOrder = finishResult.finishOrder
        val matchFinished = finishResult.matchFinished
        val winningTeamLabel = finishResult.winningTeamLabel
        val newlyFinishedRank = finishResult.newlyFinishedRank

        // Compute earned bonus rolls from THIS move (applied ONLY after current roll tray is fully spent)
        var bonusRollsAdded = 0
        if (actualTriggeredBomb == null) {
            if (ruleSet.extraRollOnCapture && totalCaptured > 0) {
                bonusRollsAdded += 1
            }
            if (ruleSet.extraRollOnHome && actualReachesHome) {
                bonusRollsAdded += 1
            }
        }
        val nextPendingBonusRolls = state.pendingBonusRolls + bonusRollsAdded
        val nextEarnedBonusRoll = state.earnedBonusRollThisTurn || nextPendingBonusRolls > 0

        val remainingAfterMove = removeOneOccurrence(state.remainingDice, move.dieValueUsed)

        val resolvedMove = move.copy(
            toProgress = actualFinalProgress,
            toOuterWrap51 = actualFinalOuterWrap,
            capturedTokens = actualCapturesList,
            reachesHome = actualReachesHome,
            triggeredBomb = actualTriggeredBomb,
            rocketBoostSteps = rocketBoostSteps
        )

        if (matchFinished) {
            val winnerColor = finishOrder.firstOrNull()
            val winnerName = winningTeamLabel ?: winnerColor?.let { c ->
                updatedPlayers.firstOrNull { it.color == c }?.nickname
            } ?: "Winner"
            val finishedState = state.copy(
                players = updatedPlayers,
                powerUpState = updatedPowerUpState.copy(pendingBombPlacement = null),
                phase = TurnPhase.MATCH_FINISHED,
                remainingDice = emptyList(),
                selectedDieValue = null,
                earnedBonusRollThisTurn = false,
                pendingBonusRolls = 0,
                legalMoves = emptyList(),
                finishOrder = finishOrder,
                winningTeamLabel = winningTeamLabel,
                lastHomeUnlockedColor = nextUnlockColor,
                lastHomeUnlockEventId = nextUnlockEventId,
                lastEventMessage = "$winnerName wins!"
            )
            return MoveOutcome(
                state = finishedState,
                move = resolvedMove,
                grantedExtraRoll = false,
                hasRemainingDiceMoves = false,
                playerFinishedRank = newlyFinishedRank,
                collectedPowerUp = collectedPowerUp,
                triggeredBomb = actualTriggeredBomb,
                rocketBoostSteps = rocketBoostSteps,
                unlockedMasterHome = unlockedMasterHome
            )
        }

        // Step 4: If Magic Bomb Trap needs square selection, pause turn in AWAITING_BOMB_PLACEMENT
        if (pendingBombForPlacement != null) {
            val bombWaitingState = state.copy(
                players = updatedPlayers,
                powerUpState = updatedPowerUpState,
                phase = TurnPhase.AWAITING_BOMB_PLACEMENT,
                remainingDice = remainingAfterMove,
                selectedDieValue = null,
                earnedBonusRollThisTurn = nextEarnedBonusRoll,
                pendingBonusRolls = nextPendingBonusRolls,
                legalMoves = emptyList(),
                finishOrder = finishOrder,
                lastHomeUnlockedColor = nextUnlockColor,
                lastHomeUnlockEventId = nextUnlockEventId,
                lastEventMessage = eventNote
            )
            return MoveOutcome(
                state = bombWaitingState,
                move = resolvedMove,
                grantedExtraRoll = false,
                hasRemainingDiceMoves = remainingAfterMove.isNotEmpty(),
                awaitingBombPlacement = true,
                playerFinishedRank = newlyFinishedRank,
                collectedPowerUp = collectedPowerUp,
                triggeredBomb = actualTriggeredBomb,
                rocketBoostSteps = rocketBoostSteps,
                unlockedMasterHome = unlockedMasterHome
            )
        }

        // Step 5: Complete turn continuation (remaining tray dice -> bonus roll -> advance turn)
        val defaultSummary = eventNote.ifBlank {
            buildMoveSummary(
                activeName = activePlayer.nickname,
                move = resolvedMove,
                unlockedMasterHome = unlockedMasterHome,
                newlyFinishedRank = newlyFinishedRank
            )
        }
        val intermediate = state.copy(
            players = updatedPlayers,
            powerUpState = updatedPowerUpState,
            remainingDice = remainingAfterMove,
            earnedBonusRollThisTurn = nextEarnedBonusRoll,
            pendingBonusRolls = nextPendingBonusRolls,
            finishOrder = finishOrder,
            lastHomeUnlockedColor = nextUnlockColor,
            lastHomeUnlockEventId = nextUnlockEventId,
            lastEventMessage = defaultSummary
        )

        return finalizePostMoveTurnFlow(
            state = intermediate,
            resolvedMove = resolvedMove,
            newlyFinishedRank = newlyFinishedRank,
            collectedPowerUp = collectedPowerUp,
            actualTriggeredBomb = actualTriggeredBomb,
            rocketBoostSteps = rocketBoostSteps,
            unlockedMasterHome = unlockedMasterHome,
            customNote = defaultSummary
        )
    }

    /**
     * Completes an instant Bomb Trap pickup in Magic mode by hiding the bomb on [trackIndex]
     * (must be in `powerUpState.pendingBombPlacement.validTrackIndices`) and resuming the turn.
     */
    fun placePendingBomb(state: GameState, trackIndex: Int): GameState {
        val pending = state.powerUpState.pendingBombPlacement ?: return state
        if (state.phase != TurnPhase.AWAITING_BOMB_PLACEMENT) return state
        if (trackIndex !in pending.validTrackIndices) return state

        val newBomb = BombTrap(
            id = state.powerUpState.nextEntityId,
            ownerColor = pending.ownerColor,
            trackIndex = trackIndex,
            roundsRemaining = PowerUpState.BOMB_LIFETIME_ROUNDS
        )
        val updatedPu = state.powerUpState.copy(
            bombs = state.powerUpState.bombs + newBomb,
            nextEntityId = state.powerUpState.nextEntityId + 1,
            pendingBombPlacement = null
        )
        val note = "${state.activePlayer.nickname} hid a Bomb Trap!"
        val resumed = state.copy(
            powerUpState = updatedPu,
            lastEventMessage = note
        )

        val dummyMove = LegalMove(
            tokenId = pending.sourceTokenId,
            color = pending.sourceTokenColor,
            fromProgress = 0,
            toProgress = 0,
            stepPath = emptyList(),
            dieValueUsed = 0
        )
        return finalizePostMoveTurnFlow(
            state = resumed,
            resolvedMove = dummyMove,
            newlyFinishedRank = null,
            collectedPowerUp = null,
            actualTriggeredBomb = null,
            rocketBoostSteps = 0,
            unlockedMasterHome = false,
            customNote = note
        ).state
    }

    /**
     * Continues a turn after a move (or pending bomb placement):
     * 1. If `remainingDice` still has at least one playable value, stay in `AWAITING_MOVE`.
     * 2. Otherwise discard any unplayable leftover tray values.
     * 3. If `pendingBonusRolls > 0` (and active player is still eligible), grant 1 bonus roll
     *    (`AWAITING_ROLL`, clear tray, decrement `pendingBonusRolls`).
     * 4. Otherwise advance turn to next eligible player (decrementing shields & running round hooks).
     */
    private fun finalizePostMoveTurnFlow(
        state: GameState,
        resolvedMove: LegalMove,
        newlyFinishedRank: Int?,
        collectedPowerUp: PowerUpType?,
        actualTriggeredBomb: BombTrap?,
        rocketBoostSteps: Int,
        unlockedMasterHome: Boolean,
        customNote: String
    ): MoveOutcome {
        val activeStillEligible = TurnManager.isPlayerEligibleForTurns(
            players = state.players,
            index = state.activePlayerIndex,
            finishOrder = state.finishOrder,
            ruleSet = ruleSet
        )

        // 1. Check remaining unspent dice in the roll tray
        if (state.remainingDice.isNotEmpty() && activeStillEligible) {
            val nextLegalMoves = computeLegalMovesForDice(state, state.remainingDice)
            if (nextLegalMoves.isNotEmpty()) {
                val nextSelected = nextLegalMoves.first().dieValueUsed
                val continueState = state.copy(
                    phase = TurnPhase.AWAITING_MOVE,
                    legalMoves = nextLegalMoves,
                    selectedDieValue = nextSelected,
                    lastEventMessage = buildString {
                        if (customNote.isNotBlank()) {
                            append(customNote)
                            append(" • ")
                        }
                        append("Spend remaining: ${state.remainingDice.joinToString(", ")}")
                    }
                )
                return MoveOutcome(
                    state = continueState,
                    move = resolvedMove,
                    grantedExtraRoll = false,
                    hasRemainingDiceMoves = true,
                    playerFinishedRank = newlyFinishedRank,
                    collectedPowerUp = collectedPowerUp,
                    triggeredBomb = actualTriggeredBomb,
                    rocketBoostSteps = rocketBoostSteps,
                    unlockedMasterHome = unlockedMasterHome
                )
            }
        }

        // 2. Tray fully spent (or leftover dice have no legal moves -> auto-discarded).
        //    Check pending bonus rolls (granted AFTER the tray is fully spent!).
        if (state.pendingBonusRolls > 0 && activeStillEligible) {
            val bonusState = state.copy(
                phase = TurnPhase.AWAITING_ROLL,
                rolledDice = emptyList(),
                remainingDice = emptyList(),
                selectedDieValue = null,
                legalMoves = emptyList(),
                consecutiveSixes = 0,
                pendingBonusRolls = (state.pendingBonusRolls - 1).coerceAtLeast(0),
                earnedBonusRollThisTurn = state.pendingBonusRolls > 1,
                lastEventMessage = buildString {
                    if (customNote.isNotBlank()) {
                        append(customNote)
                        append(" • ")
                    }
                    append("Bonus roll!")
                }
            )
            return MoveOutcome(
                state = bonusState,
                move = resolvedMove,
                grantedExtraRoll = true,
                hasRemainingDiceMoves = false,
                playerFinishedRank = newlyFinishedRank,
                collectedPowerUp = collectedPowerUp,
                triggeredBomb = actualTriggeredBomb,
                rocketBoostSteps = rocketBoostSteps,
                unlockedMasterHome = unlockedMasterHome
            )
        }

        // 3. Turn ends: advance to next eligible player
        val (advancedState, chaosEvent, eliminated) = advanceTurnInternal(
            state = state.copy(
                rolledDice = emptyList(),
                remainingDice = emptyList(),
                selectedDieValue = null,
                legalMoves = emptyList(),
                consecutiveSixes = 0,
                pendingBonusRolls = 0,
                earnedBonusRollThisTurn = false
            ),
            eventPrefix = customNote
        )

        return MoveOutcome(
            state = advancedState,
            move = resolvedMove,
            grantedExtraRoll = false,
            hasRemainingDiceMoves = false,
            playerFinishedRank = newlyFinishedRank,
            triggeredChaosEvent = chaosEvent,
            eliminatedPlayer = eliminated,
            collectedPowerUp = collectedPowerUp,
            triggeredBomb = actualTriggeredBomb,
            rocketBoostSteps = rocketBoostSteps,
            unlockedMasterHome = unlockedMasterHome
        )
    }

    /**
     * Advances from NO_LEGAL_MOVES or TURN_CANCELLED_TRIPLE_SIX to the next eligible player.
     */
    fun advanceTurnAfterSkip(state: GameState): GameState {
        if (state.isMatchFinished) return state
        val (advanced, _, _) = advanceTurnInternal(
            state = state.copy(
                rolledDice = emptyList(),
                remainingDice = emptyList(),
                selectedDieValue = null,
                legalMoves = emptyList(),
                consecutiveSixes = 0,
                pendingBonusRolls = 0,
                earnedBonusRollThisTurn = false,
                powerUpState = state.powerUpState.copy(pendingBombPlacement = null)
            ),
            eventPrefix = state.lastEventMessage
        )
        return advanced
    }

    /**
     * Advances the match clock by 1 second in Timed mode; when it reaches 0, ends the match
     * and ranks players (or teams in 2v2 Team) by score.
     */
    fun tickTimedClock(state: GameState, secondsElapsed: Int = 1): GameState {
        if (!ruleSet.timedScoringEnabled || state.isMatchFinished) return state
        val nextRemaining = (state.timedRemainingSeconds - secondsElapsed).coerceAtLeast(0)
        if (nextRemaining > 0) {
            return state.copy(timedRemainingSeconds = nextRemaining)
        }
        return finishTimedMatch(state.copy(timedRemainingSeconds = 0))
    }

    /**
     * Ends a multi-player match early after at least 1st place has been decided,
     * ranking the remaining active players by finished tokens / score / total progress.
     */
    fun endMatchEarly(state: GameState): GameState {
        if (state.isMatchFinished || !state.hasWinner) return state
        val finishOrder = state.finishOrder.toMutableList()
        val remaining = state.players
            .filter { !it.isEliminated && it.color !in finishOrder }
            .sortedWith(
                compareByDescending<PlayerState> {
                    if (ruleSet.timedScoringEnabled) it.score else it.finishedTokenCount * 1000 + it.totalProgress
                }.thenByDescending { it.totalProgress }
            )
        for (p in remaining) {
            finishOrder += p.color
        }
        for (elimColor in state.eliminatedOrder.reversed()) {
            if (elimColor !in finishOrder) finishOrder += elimColor
        }
        val winnerName = state.winningTeamLabel
            ?: finishOrder.firstOrNull()?.let { c -> state.playerByColor(c)?.nickname }
            ?: "Winner"
        return state.copy(
            phase = TurnPhase.MATCH_FINISHED,
            finishOrder = finishOrder,
            remainingDice = emptyList(),
            selectedDieValue = null,
            legalMoves = emptyList(),
            pendingBonusRolls = 0,
            earnedBonusRollThisTurn = false,
            powerUpState = state.powerUpState.copy(pendingBombPlacement = null),
            lastEventMessage = "$winnerName wins!"
        )
    }

    /**
     * Computes all valid empty, non-safe outer track squares within 1..6 steps AHEAD of [token]
     * for instant Bomb Trap placement in Magic mode.
     */
    fun computeValidBombSquaresAhead(
        state: GameState,
        color: PlayerColor,
        token: Token
    ): List<Int> {
        if (!ruleSet.magicItemsEnabled) return emptyList()
        val startOffset = token.outerTrackOffset() ?: return emptyList()
        val safeSquares = state.activeSafeTrackIndices()
        val occupiedByTokens = state.players
            .filterNot { it.isEliminated }
            .flatMap { it.tokens }
            .mapNotNull { it.mainTrackIndex() }
            .toSet()
        val occupiedByBombs = state.powerUpState.bombs.map { it.trackIndex }.toSet()
        val occupiedByTiles = state.powerUpState.tiles.map { it.trackIndex }.toSet()
        val portalSquares = state.chaosState.portalPair?.let { setOf(it.first, it.second) } ?: emptySet()

        val valid = mutableListOf<Int>()
        for (step in 1..PowerUpState.BOMB_PLACEMENT_RANGE) {
            val candidateOffset = startOffset + step
            if (candidateOffset > Token.MAX_MAIN_TRACK_PROGRESS) break
            val trackIdx = (color.startTrackIndex + candidateOffset) % Token.TRACK_LENGTH
            if (trackIdx !in safeSquares &&
                trackIdx !in occupiedByTokens &&
                trackIdx !in occupiedByBombs &&
                trackIdx !in occupiedByTiles &&
                trackIdx !in portalSquares
            ) {
                valid += trackIdx
            }
        }
        return valid
    }

    /**
     * Returns all outer track indices currently forming a Blockade (2+ same-color tokens on
     * a non-safe outer square) in Blockades mode, mapped to their owning [PlayerColor].
     */
    fun activeBlockades(state: GameState): Map<Int, PlayerColor> {
        if (!ruleSet.blockadesEnabled) return emptyMap()
        val safeSquares = state.activeSafeTrackIndices()
        val result = mutableMapOf<Int, PlayerColor>()
        for (player in state.players) {
            if (player.isEliminated) continue
            val counts = mutableMapOf<Int, Int>()
            for (token in player.tokens) {
                val track = token.mainTrackIndex() ?: continue
                if (track in safeSquares) continue
                counts[track] = (counts[track] ?: 0) + 1
            }
            for ((track, count) in counts) {
                if (count >= 2) {
                    result[track] = player.color
                }
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Internal Helpers
    // -------------------------------------------------------------------------

    private fun buildStepPath(
        fromProgress: Int,
        fromOuterWrap51: Boolean,
        steps: Int,
        canEnterHomeColumn: Boolean,
        movingColor: PlayerColor,
        opponentBlockades: Set<Int>
    ): List<StepTarget>? {
        val path = ArrayList<StepTarget>(steps)
        var curProgress = fromProgress
        var curWrap51 = fromOuterWrap51

        for (step in 1..steps) {
            if (curWrap51) {
                // Step from 52nd outer square back to own start square (offset 0)
                curProgress = 0
                curWrap51 = false
            } else if (curProgress == Token.MAX_MAIN_TRACK_PROGRESS) {
                if (canEnterHomeColumn) {
                    curProgress = Token.MAX_MAIN_TRACK_PROGRESS + 1
                    curWrap51 = false
                } else {
                    // Master mode locked home column: keep looping around the outer track
                    curWrap51 = true
                }
            } else {
                val next = curProgress + 1
                if (next > Token.HOME_PROGRESS) {
                    // Exact finish required: overshoot rejected!
                    return null
                }
                curProgress = next
            }

            // Blockades mode: opponent tokens CANNOT pass through OR land on a blockade!
            if (ruleSet.blockadesEnabled && (curWrap51 || curProgress in 0..Token.MAX_MAIN_TRACK_PROGRESS)) {
                val offset = if (curWrap51) 51 else curProgress
                val trackIdx = (movingColor.startTrackIndex + offset) % Token.TRACK_LENGTH
                if (trackIdx in opponentBlockades) {
                    return null
                }
            }

            path += StepTarget(curProgress, curWrap51)
        }
        return path
    }

    private data class RocketResult(
        val actualSteps: Int,
        val finalProgress: Int,
        val finalOuterWrap51: Boolean,
        val reachedHome: Boolean,
        val captures: List<TokenRef>,
        val triggeredBomb: BombTrap?
    )

    private fun applyRocketBoost(
        state: GameState,
        token: Token,
        boostSteps: Int,
        canEnterHome: Boolean,
        partnerColor: PlayerColor?,
        activeSafeIndices: Set<Int>
    ): RocketResult {
        var curProgress = token.progress
        var curWrap51 = token.outerWrap51
        var stepsMoved = 0

        for (step in 1..boostSteps) {
            if (!curWrap51 && curProgress == Token.HOME_PROGRESS) {
                // Stop at the last legal square (Home) rather than overshooting!
                break
            }
            if (curWrap51) {
                curProgress = 0
                curWrap51 = false
            } else if (curProgress == Token.MAX_MAIN_TRACK_PROGRESS) {
                if (canEnterHome) {
                    curProgress = Token.MAX_MAIN_TRACK_PROGRESS + 1
                } else {
                    curWrap51 = true
                }
            } else {
                curProgress += 1
            }
            stepsMoved += 1
        }

        val reachedHome = !curWrap51 && curProgress == Token.HOME_PROGRESS
        if (!curWrap51 && curProgress > Token.MAX_MAIN_TRACK_PROGRESS) {
            return RocketResult(
                actualSteps = stepsMoved,
                finalProgress = curProgress,
                finalOuterWrap51 = false,
                reachedHome = reachedHome,
                captures = emptyList(),
                triggeredBomb = null
            )
        }

        val offset = if (curWrap51) 51 else curProgress
        val destTrack = (token.color.startTrackIndex + offset) % Token.TRACK_LENGTH

        val bombAtDest = state.powerUpState.bombs.firstOrNull { bomb ->
            bomb.trackIndex == destTrack &&
                bomb.ownerColor != token.color &&
                bomb.ownerColor != partnerColor
        }
        val triggeredBomb = if (bombAtDest != null && !token.isShielded) bombAtDest else null
        val captures = if (triggeredBomb == null && destTrack !in activeSafeIndices) {
            findCapturableOpponents(
                state = state,
                movingColor = token.color,
                partnerColor = partnerColor,
                trackIndex = destTrack,
                attackerIsJoint = token.isJoint
            )
        } else {
            emptyList()
        }

        return RocketResult(
            actualSteps = stepsMoved,
            finalProgress = curProgress,
            finalOuterWrap51 = curWrap51,
            reachedHome = reachedHome,
            captures = captures,
            triggeredBomb = triggeredBomb
        )
    }

    private fun computeOpponentBlockadeTracks(
        state: GameState,
        movingColor: PlayerColor,
        partnerColor: PlayerColor?,
        activeSafeIndices: Set<Int>
    ): Set<Int> {
        val blockades = mutableSetOf<Int>()
        for (other in state.players) {
            if (other.isEliminated) continue
            if (other.color == movingColor || other.color == partnerColor) continue
            val counts = mutableMapOf<Int, Int>()
            for (t in other.tokens) {
                val track = t.mainTrackIndex() ?: continue
                if (track in activeSafeIndices) continue
                counts[track] = (counts[track] ?: 0) + 1
            }
            for ((track, count) in counts) {
                if (count >= 2) blockades += track
            }
        }
        return blockades
    }

    private fun hasOpponentJointTokenAt(
        state: GameState,
        movingColor: PlayerColor,
        partnerColor: PlayerColor?,
        trackIndex: Int
    ): Boolean {
        for (other in state.players) {
            if (other.isEliminated) continue
            if (other.color == movingColor || other.color == partnerColor) continue
            if (other.tokens.any { it.isJoint && it.mainTrackIndex() == trackIndex }) {
                return true
            }
        }
        return false
    }

    private fun findCapturableOpponents(
        state: GameState,
        movingColor: PlayerColor,
        partnerColor: PlayerColor?,
        trackIndex: Int,
        attackerIsJoint: Boolean
    ): List<TokenRef> {
        val captures = mutableListOf<TokenRef>()
        for (other in state.players) {
            if (other.isEliminated) continue
            if (other.color == movingColor || other.color == partnerColor) continue
            for (t in other.tokens) {
                if (t.mainTrackIndex() != trackIndex) continue
                if (t.isShielded) continue
                if (ruleSet.jointTokensEnabled && t.isJoint && !attackerIsJoint) continue
                captures += TokenRef(other.color, t.id)
            }
        }
        return captures
    }

    /**
     * Reconciles Master mode Joint Tokens for a single player:
     * - Preserves valid pairs standing on the same non-base, non-home position.
     * - Clears stale partner IDs if tokens separated or entered base/home.
     * - Merges any two unpaired active tokens standing on the same non-base, non-home position.
     */
    internal fun reconcileMasterJointTokens(tokens: List<Token>): List<Token> {
        val byId = tokens.associateBy { it.id }.toMutableMap()

        // 1. Validate existing pairs
        for (t in tokens) {
            val partnerId = t.jointPartnerId ?: continue
            val partner = byId[partnerId]
            val valid = partner != null &&
                partner.jointPartnerId == t.id &&
                !t.isInBase && !t.isHome &&
                !partner.isInBase && !partner.isHome &&
                t.progress == partner.progress &&
                t.outerWrap51 == partner.outerWrap51
            if (!valid) {
                byId[t.id] = t.copy(jointPartnerId = null)
            }
        }

        // 2. Pair up any unpaired active tokens sharing the exact same board position
        val unpairedActive = byId.values
            .filter { !it.isInBase && !it.isHome && it.jointPartnerId == null }
            .sortedBy { it.id }
            .groupBy { it.progress to it.outerWrap51 }

        for ((_, group) in unpairedActive) {
            var idx = 0
            while (idx + 1 < group.size) {
                val a = group[idx]
                val b = group[idx + 1]
                byId[a.id] = a.copy(jointPartnerId = b.id)
                byId[b.id] = b.copy(jointPartnerId = a.id)
                idx += 2
            }
        }

        return tokens.map { byId.getValue(it.id) }
    }

    private data class FinishEvalResult(
        val finishOrder: List<PlayerColor>,
        val matchFinished: Boolean,
        val winningTeamLabel: String?,
        val newlyFinishedRank: Int?
    )

    private fun evaluateFinishAndMatchEnd(
        state: GameState,
        updatedPlayers: List<PlayerState>,
        movingColor: PlayerColor
    ): FinishEvalResult {
        val finishOrder = state.finishOrder.toMutableList()
        val movedPlayer = updatedPlayers.first { it.color == movingColor }
        var newlyFinishedRank: Int? = null

        if (movedPlayer.hasReachedGoal(ruleSet.tokensHomeToWin) && movingColor !in finishOrder) {
            finishOrder += movingColor
            newlyFinishedRank = finishOrder.size
        }

        if (ruleSet.teamsMode) {
            val partnerColor = movingColor.opposite()
            val partner = updatedPlayers.firstOrNull { it.color == partnerColor }
            val teamDone = movedPlayer.hasReachedGoal(ruleSet.tokensHomeToWin) &&
                (partner == null || partner.isEliminated || partner.hasReachedGoal(ruleSet.tokensHomeToWin))
            if (teamDone) {
                if (partner != null && partner.color !in finishOrder) {
                    finishOrder += partner.color
                }
                val otherTeam = updatedPlayers
                    .filter { it.color !in finishOrder }
                    .sortedByDescending { it.finishedTokenCount * 100 + it.totalProgress }
                for (opp in otherTeam) {
                    finishOrder += opp.color
                }
                val isTeamA = movingColor == PlayerColor.RED || movingColor == PlayerColor.YELLOW
                val label = if (isTeamA) "Team A (Red & Yellow)" else "Team B (Green & Blue)"
                return FinishEvalResult(
                    finishOrder = finishOrder,
                    matchFinished = true,
                    winningTeamLabel = label,
                    newlyFinishedRank = newlyFinishedRank ?: 1
                )
            }
            return FinishEvalResult(
                finishOrder = finishOrder,
                matchFinished = false,
                winningTeamLabel = null,
                newlyFinishedRank = newlyFinishedRank
            )
        }

        val activeRemaining = updatedPlayers.filter { !it.isEliminated && it.color !in finishOrder }
        val shouldEnd = when {
            ruleSet.immediateWinOnFirstFinisher && finishOrder.isNotEmpty() -> true
            activeRemaining.size <= 1 -> true
            else -> false
        }

        if (shouldEnd) {
            val rankedRemaining = activeRemaining.sortedWith(
                compareByDescending<PlayerState> {
                    if (ruleSet.timedScoringEnabled) it.score else it.finishedTokenCount * 1000 + it.totalProgress
                }.thenByDescending { it.totalProgress }
            )
            for (p in rankedRemaining) {
                if (p.color !in finishOrder) finishOrder += p.color
            }
            for (elimColor in state.eliminatedOrder.reversed()) {
                if (elimColor !in finishOrder) finishOrder += elimColor
            }
        }

        return FinishEvalResult(
            finishOrder = finishOrder,
            matchFinished = shouldEnd,
            winningTeamLabel = null,
            newlyFinishedRank = newlyFinishedRank
        )
    }

    private fun advanceTurnInternal(
        state: GameState,
        eventPrefix: String
    ): Triple<GameState, ChaosEventType?, PlayerColor?> {
        val nextIdx = TurnManager.nextEligiblePlayerIndex(
            players = state.players,
            currentIndex = state.activePlayerIndex,
            finishOrder = state.finishOrder,
            ruleSet = ruleSet
        )
        val wrappedRound = nextIdx <= state.activePlayerIndex
        val nextFullRounds = if (wrappedRound) state.fullRoundsCompleted + 1 else state.fullRoundsCompleted

        // Decrement incoming player's active shields at the start of their next turn
        // so a newly picked-up Shield (2 turns) protects across 2 full opponent cycles.
        val playersAfterShieldTick = state.players.mapIndexed { idx, p ->
            if (idx == nextIdx) {
                p.copy(
                    tokens = p.tokens.map { t ->
                        if (t.shieldTurnsRemaining > 0) {
                            t.copy(shieldTurnsRemaining = t.shieldTurnsRemaining - 1)
                        } else t
                    }
                )
            } else p
        }

        var working = state.copy(
            players = playersAfterShieldTick,
            activePlayerIndex = nextIdx,
            phase = TurnPhase.AWAITING_ROLL,
            turnNumber = state.turnNumber + 1,
            fullRoundsCompleted = nextFullRounds
        )

        var triggeredChaos: ChaosEventType? = null
        var eliminatedColor: PlayerColor? = null

        if (wrappedRound) {
            // 1. Magic Mode: tick bomb lifetimes (3 rounds) & spawn 1 item every 2 full rounds (max 3)
            if (ruleSet.magicItemsEnabled) {
                working = tickMagicRoundState(working)
            }

            // 2. Chaos Mode: tick active Chaos effects & trigger a new event every 3 full rounds
            if (ruleSet.chaosEventsEveryNRounds != null) {
                working = tickChaosRoundTimers(working)
                if (nextFullRounds > 0 && nextFullRounds % ruleSet.chaosEventsEveryNRounds!! == 0) {
                    val (chaosUpdated, event) = triggerRandomChaosEvent(working)
                    working = chaosUpdated
                    triggeredChaos = event
                }
            }

            // 3. Elimination Mode: every 5 full rounds knock out lowest-progress active player
            if (ruleSet.eliminationEveryNRounds != null &&
                nextFullRounds > 0 &&
                nextFullRounds % ruleSet.eliminationEveryNRounds!! == 0
            ) {
                val (elimUpdated, elim) = runEliminationRound(working)
                working = elimUpdated
                eliminatedColor = elim
            }
        }

        // Ensure active player index is still eligible if Elimination knocked out nextIdx
        if (!working.isMatchFinished && !TurnManager.isPlayerEligibleForTurns(
                working.players,
                working.activePlayerIndex,
                working.finishOrder,
                ruleSet
            )
        ) {
            val safeIdx = TurnManager.nextEligiblePlayerIndex(
                players = working.players,
                currentIndex = working.activePlayerIndex,
                finishOrder = working.finishOrder,
                ruleSet = ruleSet
            )
            working = working.copy(activePlayerIndex = safeIdx)
        }

        if (working.lastEventMessage == state.lastEventMessage && !working.isMatchFinished) {
            val nextName = working.activePlayer.nickname
            val msg = if (eventPrefix.isNotBlank()) "$eventPrefix • $nextName's turn" else "$nextName's turn"
            working = working.copy(lastEventMessage = msg)
        }

        return Triple(working, triggeredChaos, eliminatedColor)
    }

    private fun spawnInitialMagicItems(state: GameState): GameState {
        var working = state
        repeat(PowerUpState.MAX_TILES_ON_BOARD) {
            working = spawnOneMagicItem(working)
        }
        return working
    }

    private fun tickMagicRoundState(state: GameState): GameState {
        val tickedBombs = state.powerUpState.bombs
            .map { it.copy(roundsRemaining = it.roundsRemaining - 1) }
            .filter { it.roundsRemaining > 0 }

        var nextRoundsUntilSpawn = state.powerUpState.roundsUntilNextSpawn - 1
        var working = state.copy(
            powerUpState = state.powerUpState.copy(
                bombs = tickedBombs,
                roundsUntilNextSpawn = nextRoundsUntilSpawn
            )
        )

        if (nextRoundsUntilSpawn <= 0) {
            nextRoundsUntilSpawn = PowerUpState.RESPAWN_ROUNDS
            working = working.copy(
                powerUpState = working.powerUpState.copy(roundsUntilNextSpawn = nextRoundsUntilSpawn)
            )
            if (working.powerUpState.tiles.size < PowerUpState.MAX_TILES_ON_BOARD) {
                working = spawnOneMagicItem(working)
            }
        }
        return working
    }

    private fun spawnOneMagicItem(state: GameState): GameState {
        if (state.powerUpState.tiles.size >= PowerUpState.MAX_TILES_ON_BOARD) return state
        val safeSquares = state.activeSafeTrackIndices()
        val occupiedByTokens = state.players
            .filterNot { it.isEliminated }
            .flatMap { it.tokens }
            .mapNotNull { it.mainTrackIndex() }
            .toSet()
        val occupiedByTiles = state.powerUpState.tiles.map { it.trackIndex }.toSet()
        val occupiedByBombs = state.powerUpState.bombs.map { it.trackIndex }.toSet()

        val candidates = (0 until Token.TRACK_LENGTH).filter { idx ->
            idx !in safeSquares &&
                idx !in occupiedByTokens &&
                idx !in occupiedByTiles &&
                idx !in occupiedByBombs
        }
        if (candidates.isEmpty()) return state

        val chosenTrack = candidates[eventRandom.nextInt(candidates.size)]
        val types = PowerUpType.entries
        val chosenType = types[eventRandom.nextInt(types.size)]
        val newTile = PowerUpTile(
            id = state.powerUpState.nextEntityId,
            trackIndex = chosenTrack,
            type = chosenType
        )
        return state.copy(
            powerUpState = state.powerUpState.copy(
                tiles = state.powerUpState.tiles + newTile,
                nextEntityId = state.powerUpState.nextEntityId + 1
            )
        )
    }

    private fun tickChaosRoundTimers(state: GameState): GameState {
        val chaos = state.chaosState
        val nextStarRounds = (chaos.safeStarsRoundsRemaining - 1).coerceAtLeast(0)
        val nextPortalRounds = (chaos.portalsRoundsRemaining - 1).coerceAtLeast(0)
        return state.copy(
            chaosState = chaos.copy(
                activeSafeTrackIndices = if (nextStarRounds > 0) chaos.activeSafeTrackIndices else null,
                safeStarsRoundsRemaining = nextStarRounds,
                portalPair = if (nextPortalRounds > 0) chaos.portalPair else null,
                portalsRoundsRemaining = nextPortalRounds
            )
        )
    }

    private fun triggerRandomChaosEvent(state: GameState): Pair<GameState, ChaosEventType> {
        val events = ChaosEventType.entries.filter { it != state.chaosState.lastTriggeredEvent }
            .ifEmpty { ChaosEventType.entries }
        val event = events[eventRandom.nextInt(events.size)]
        val nextId = state.chaosState.announcementId + 1

        return when (event) {
            ChaosEventType.MOVING_STARS -> {
                val nonStarts = (0 until Token.TRACK_LENGTH).filter { it !in LudoBoardGeometry.START_TRACK_INDICES }
                val newStars = nonStarts.shuffled(eventRandom).take(4).toSet()
                val updated = state.copy(
                    chaosState = state.chaosState.copy(
                        activeSafeTrackIndices = LudoBoardGeometry.START_TRACK_INDICES + newStars,
                        safeStarsRoundsRemaining = 3,
                        lastTriggeredEvent = event,
                        activeAnnouncement = event,
                        announcementDetail = event.description,
                        announcementId = nextId
                    ),
                    lastEventMessage = "Chaos: ${event.title}"
                )
                updated to event
            }

            ChaosEventType.PORTALS -> {
                val safe = state.activeSafeTrackIndices()
                val candidates = (0 until Token.TRACK_LENGTH).filter { it !in safe }
                val pair = if (candidates.size >= 2) {
                    val shuffled = candidates.shuffled(eventRandom)
                    shuffled[0] to shuffled[1]
                } else {
                    10 to 36
                }
                val updated = state.copy(
                    chaosState = state.chaosState.copy(
                        portalPair = pair,
                        portalsRoundsRemaining = 3,
                        lastTriggeredEvent = event,
                        activeAnnouncement = event,
                        announcementDetail = event.description,
                        announcementId = nextId
                    ),
                    lastEventMessage = "Chaos: ${event.title}"
                )
                updated to event
            }

            ChaosEventType.DICE_STORM -> {
                val boostedPlayers = state.players.map { p ->
                    if (!p.isEliminated && p.color !in state.finishOrder) {
                        p.copy(diceStormCharges = p.diceStormCharges + 1)
                    } else p
                }
                val updated = state.copy(
                    players = boostedPlayers,
                    chaosState = state.chaosState.copy(
                        lastTriggeredEvent = event,
                        activeAnnouncement = event,
                        announcementDetail = event.description,
                        announcementId = nextId
                    ),
                    lastEventMessage = "Chaos: ${event.title}"
                )
                updated to event
            }

            ChaosEventType.EARTHQUAKE -> {
                val targets = state.players
                    .filterNot { it.isEliminated }
                    .flatMap { p -> p.tokens.filter { it.isOnMainTrack && it.progress >= 3 } }
                val updatedPlayers = if (targets.isNotEmpty()) {
                    val victim = targets[eventRandom.nextInt(targets.size)]
                    state.players.map { p ->
                        if (p.color == victim.color) {
                            p.copy(
                                tokens = p.tokens.map { t ->
                                    if (t.id == victim.id) {
                                        t.copy(
                                            progress = (t.progress - 3).coerceAtLeast(0),
                                            outerWrap51 = false
                                        )
                                    } else t
                                }
                            )
                        } else p
                    }
                } else state.players

                val updated = state.copy(
                    players = updatedPlayers,
                    chaosState = state.chaosState.copy(
                        lastTriggeredEvent = event,
                        activeAnnouncement = event,
                        announcementDetail = event.description,
                        announcementId = nextId
                    ),
                    lastEventMessage = "Chaos: ${event.title}"
                )
                updated to event
            }

            ChaosEventType.LUCKY_WIND -> {
                val boostedPlayers = state.players.map { p ->
                    if (p.isEliminated) p else p.copy(
                        tokens = p.tokens.map { t ->
                            if (t.isInHomeColumn && t.progress < Token.HOME_PROGRESS - 1) {
                                t.copy(progress = t.progress + 1)
                            } else t
                        }
                    )
                }
                val updated = state.copy(
                    players = boostedPlayers,
                    chaosState = state.chaosState.copy(
                        lastTriggeredEvent = event,
                        activeAnnouncement = event,
                        announcementDetail = event.description,
                        announcementId = nextId
                    ),
                    lastEventMessage = "Chaos: ${event.title}"
                )
                updated to event
            }
        }
    }

    private fun runEliminationRound(state: GameState): Pair<GameState, PlayerColor?> {
        val activeContenders = state.players.filter { !it.isEliminated && it.color !in state.finishOrder }
        if (activeContenders.size <= 1) return state to null

        val lowest = activeContenders.minWithOrNull(
            compareBy<PlayerState> { it.totalProgress }
                .thenBy { it.finishedTokenCount }
                .thenByDescending { it.color.cornerOrder }
        ) ?: return state to null

        val updatedPlayers = state.players.map { p ->
            if (p.color == lowest.color) {
                p.copy(
                    isEliminated = true,
                    tokens = p.tokens.map { t ->
                        t.copy(
                            progress = Token.BASE_PROGRESS,
                            outerWrap51 = false,
                            shieldTurnsRemaining = 0,
                            jointPartnerId = null
                        )
                    }
                )
            } else p
        }
        val nextEliminated = state.eliminatedOrder + lowest.color
        val remaining = updatedPlayers.filter { !it.isEliminated && it.color !in state.finishOrder }

        if (ruleSet.teamsMode) {
            val teamAAlive = remaining.any { it.color == PlayerColor.RED || it.color == PlayerColor.YELLOW }
            val teamBAlive = remaining.any { it.color == PlayerColor.GREEN || it.color == PlayerColor.BLUE }
            if (!teamAAlive || !teamBAlive) {
                val winnerTeamColors = if (teamAAlive) listOf(PlayerColor.RED, PlayerColor.YELLOW) else listOf(PlayerColor.GREEN, PlayerColor.BLUE)
                val loserTeamColors = if (teamAAlive) listOf(PlayerColor.GREEN, PlayerColor.BLUE) else listOf(PlayerColor.RED, PlayerColor.YELLOW)
                val finalOrder = (state.finishOrder + winnerTeamColors + loserTeamColors).distinct()
                val label = if (teamAAlive) "Team A (Red & Yellow)" else "Team B (Green & Blue)"
                val finished = state.copy(
                    players = updatedPlayers,
                    eliminatedOrder = nextEliminated,
                    finishOrder = finalOrder,
                    winningTeamLabel = label,
                    phase = TurnPhase.MATCH_FINISHED,
                    lastEventMessage = "${lowest.nickname} eliminated! $label win!"
                )
                return finished to lowest.color
            }
        } else if (remaining.size <= 1) {
            val finalOrder = buildList {
                addAll(state.finishOrder)
                remaining.forEach { if (it.color !in this) add(it.color) }
                nextEliminated.reversed().forEach { if (it !in this) add(it) }
            }
            val winnerName = finalOrder.firstOrNull()?.let { c ->
                updatedPlayers.firstOrNull { it.color == c }?.nickname
            } ?: "Winner"
            val finished = state.copy(
                players = updatedPlayers,
                eliminatedOrder = nextEliminated,
                finishOrder = finalOrder,
                phase = TurnPhase.MATCH_FINISHED,
                lastEventMessage = "${lowest.nickname} eliminated! $winnerName wins!"
            )
            return finished to lowest.color
        }

        val updated = state.copy(
            players = updatedPlayers,
            eliminatedOrder = nextEliminated,
            lastEventMessage = "${lowest.nickname} was eliminated (lowest progress)!"
        )
        return updated to lowest.color
    }

    private fun finishTimedMatch(state: GameState): GameState {
        if (ruleSet.teamsMode) {
            val teamAScore = state.players
                .filter { it.color == PlayerColor.RED || it.color == PlayerColor.YELLOW }
                .sumOf { it.score }
            val teamBScore = state.players
                .filter { it.color == PlayerColor.GREEN || it.color == PlayerColor.BLUE }
                .sumOf { it.score }
            val teamAWins = teamAScore >= teamBScore
            val winnerColors = if (teamAWins) listOf(PlayerColor.RED, PlayerColor.YELLOW) else listOf(PlayerColor.GREEN, PlayerColor.BLUE)
            val loserColors = if (teamAWins) listOf(PlayerColor.GREEN, PlayerColor.BLUE) else listOf(PlayerColor.RED, PlayerColor.YELLOW)
            val label = if (teamAWins) "Team A (Red & Yellow)" else "Team B (Green & Blue)"
            return state.copy(
                phase = TurnPhase.MATCH_FINISHED,
                finishOrder = (winnerColors + loserColors).filter { c -> state.players.any { it.color == c } },
                winningTeamLabel = label,
                legalMoves = emptyList(),
                remainingDice = emptyList(),
                lastEventMessage = "Time's up! $label win (${maxOf(teamAScore, teamBScore)} pts)!"
            )
        }

        val ranked = state.players.sortedWith(
            compareByDescending<PlayerState> { it.score }
                .thenByDescending { it.finishedTokenCount }
                .thenByDescending { it.totalProgress }
        )
        val winner = ranked.firstOrNull()
        return state.copy(
            phase = TurnPhase.MATCH_FINISHED,
            finishOrder = ranked.map { it.color },
            legalMoves = emptyList(),
            remainingDice = emptyList(),
            lastEventMessage = "Time's up! ${winner?.nickname ?: "Winner"} wins with ${winner?.score ?: 0} pts!"
        )
    }

    private fun removeOneOccurrence(list: List<Int>, value: Int): List<Int> {
        val idx = list.indexOf(value)
        if (idx < 0) return list
        return list.filterIndexed { i, _ -> i != idx }
    }

    private fun buildStartMessage(
        mode: GameModeId,
        format: MatchFormat,
        firstPlayerName: String
    ): String = "${mode.title} (${format.displayName}) — $firstPlayerName rolls first!"

    private fun buildMoveSummary(
        activeName: String,
        move: LegalMove,
        unlockedMasterHome: Boolean,
        newlyFinishedRank: Int?
    ): String = when {
        newlyFinishedRank != null -> "$activeName finished #$newlyFinishedRank!"
        unlockedMasterHome -> "$activeName captured & unlocked Home Column!"
        move.arrowToTrackIndex != null && move.capturedTokens.isNotEmpty() ->
            "$activeName shot an Arrow & captured ${move.capturedTokens.first().color.displayName}!"
        move.arrowToTrackIndex != null ->
            "$activeName rode an Arrow shortcut ahead!"
        move.capturedTokens.size > 1 ->
            "$activeName captured ${move.capturedTokens.size} tokens!"
        move.capturedTokens.size == 1 ->
            "$activeName captured ${move.capturedTokens.first().color.displayName}!"
        move.reachesHome ->
            if (move.isJointMove) "$activeName brought a Joint Token Home!" else "$activeName brought a token Home!"
        move.formsBlockade ->
            "$activeName formed a Blockade!"
        move.leavesBase ->
            "$activeName entered the track!"
        move.isJointMove ->
            "$activeName moved Joint Token ${move.dieValueUsed}"
        else ->
            "$activeName moved ${move.dieValueUsed}"
    }
}
