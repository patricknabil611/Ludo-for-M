package com.example.engine

/**
 * Result of validating whether a player action is permitted on the current turn.
 */
sealed interface TurnGuardResult {
    data object Allowed : TurnGuardResult
    data object MatchFinished : TurnGuardResult
    data class NotPlayersTurn(
        val expectedColor: PlayerColor,
        val attemptedColor: PlayerColor
    ) : TurnGuardResult
    data class InvalidPhase(
        val expected: TurnPhase,
        val actual: TurnPhase
    ) : TurnGuardResult
    data class IllegalTokenMove(
        val tokenRef: TokenRef
    ) : TurnGuardResult

    val isAllowed: Boolean get() = this is Allowed
}

/**
 * Pure turn-based orchestration and strict turn-ownership validator for Ludo matches.
 *
 * Responsibilities:
 * - Resolves the active player and the token color they currently control (including 2v2 Team partner control).
 * - Cycles turns clockwise across eligible players, automatically skipping eliminated or finished players.
 * - Enforces that players can only roll, place pending bombs, or move tokens on their own turn and phase.
 * - Provides the live ordered turn queue for UI turn-order indicators.
 */
object TurnManager {

    /**
     * Returns the player whose turn is currently active.
     */
    fun activePlayer(state: GameState): PlayerState = state.activePlayer

    /**
     * Returns true iff the match is active and [color] is the player whose turn it currently is.
     */
    fun isPlayersTurn(state: GameState, color: PlayerColor): Boolean =
        !state.isMatchFinished && state.activePlayer.color == color

    /**
     * Resolves which color's tokens the active player controls on this turn.
     * In 2v2 Team format, once a player finishes their tokens (4 home, or 1 home in Quick),
     * they keep rolling every turn and their rolls move their partner's tokens.
     */
    fun controlledColor(state: GameState, ruleSet: LudoRuleSet): PlayerColor {
        val active = state.activePlayer
        val activeReachedGoal = active.hasReachedGoal(ruleSet.tokensHomeToWin)
        if (!ruleSet.teamsMode || !activeReachedGoal) return active.color
        val partner = state.playerByColor(active.color.opposite())
        return if (partner != null && !partner.isEliminated && !partner.hasReachedGoal(ruleSet.tokensHomeToWin)) {
            partner.color
        } else {
            active.color
        }
    }

    /**
     * Validates that a dice roll is legal for [actingColor] (when provided) in the current [state].
     */
    fun validateRoll(
        state: GameState,
        actingColor: PlayerColor? = null
    ): TurnGuardResult {
        if (state.isMatchFinished) return TurnGuardResult.MatchFinished
        if (actingColor != null && actingColor != state.activePlayer.color) {
            return TurnGuardResult.NotPlayersTurn(
                expectedColor = state.activePlayer.color,
                attemptedColor = actingColor
            )
        }
        if (state.phase != TurnPhase.AWAITING_ROLL) {
            return TurnGuardResult.InvalidPhase(
                expected = TurnPhase.AWAITING_ROLL,
                actual = state.phase
            )
        }
        return TurnGuardResult.Allowed
    }

    /**
     * Validates that [move] is performed by the active player (when [actingColor] is provided),
     * targets a token controlled on this turn, happens during [TurnPhase.AWAITING_MOVE],
     * and exists in [GameState.legalMoves].
     */
    fun validateMove(
        state: GameState,
        ruleSet: LudoRuleSet,
        move: LegalMove,
        actingColor: PlayerColor? = null
    ): TurnGuardResult {
        if (state.isMatchFinished) return TurnGuardResult.MatchFinished
        if (actingColor != null && actingColor != state.activePlayer.color) {
            return TurnGuardResult.NotPlayersTurn(
                expectedColor = state.activePlayer.color,
                attemptedColor = actingColor
            )
        }
        if (state.phase != TurnPhase.AWAITING_MOVE) {
            return TurnGuardResult.InvalidPhase(
                expected = TurnPhase.AWAITING_MOVE,
                actual = state.phase
            )
        }
        val expectedControlled = controlledColor(state, ruleSet)
        if (move.color != expectedControlled) {
            return TurnGuardResult.NotPlayersTurn(
                expectedColor = expectedControlled,
                attemptedColor = move.color
            )
        }
        val isLegal = state.legalMoves.any {
            it.tokenId == move.tokenId &&
                it.color == move.color &&
                it.toProgress == move.toProgress &&
                it.toOuterWrap51 == move.toOuterWrap51 &&
                it.dieValueUsed == move.dieValueUsed &&
                it.isJointMove == move.isJointMove
        }
        if (!isLegal) {
            return TurnGuardResult.IllegalTokenMove(TokenRef(move.color, move.tokenId))
        }
        return TurnGuardResult.Allowed
    }

    /**
     * Validates a direct token tap ([tokenRef]) from a player (optionally scoped to [actingColor]
     * in online or seat-locked play). Matches either token in a Master Joint Token.
     */
    fun validateTokenTap(
        state: GameState,
        ruleSet: LudoRuleSet,
        tokenRef: TokenRef,
        actingColor: PlayerColor? = null
    ): TurnGuardResult {
        if (state.isMatchFinished) return TurnGuardResult.MatchFinished
        if (actingColor != null && actingColor != state.activePlayer.color) {
            return TurnGuardResult.NotPlayersTurn(
                expectedColor = state.activePlayer.color,
                attemptedColor = actingColor
            )
        }
        val expectedControlled = controlledColor(state, ruleSet)
        if (tokenRef.color != expectedControlled) {
            return TurnGuardResult.NotPlayersTurn(
                expectedColor = expectedControlled,
                attemptedColor = tokenRef.color
            )
        }
        if (state.phase != TurnPhase.AWAITING_MOVE) {
            return TurnGuardResult.InvalidPhase(
                expected = TurnPhase.AWAITING_MOVE,
                actual = state.phase
            )
        }
        val hasLegalMove = state.legalMoves.any {
            it.color == tokenRef.color && it.includesToken(tokenRef.tokenId)
        }
        if (!hasLegalMove) {
            return TurnGuardResult.IllegalTokenMove(tokenRef)
        }
        return TurnGuardResult.Allowed
    }

    /**
     * Computes the next eligible player index clockwise around the board, skipping eliminated
     * players and already-finished players (except in 2v2 Team format where a finished teammate
     * continues taking turns to roll for their unfinished partner).
     */
    fun nextEligiblePlayerIndex(
        players: List<PlayerState>,
        currentIndex: Int,
        finishOrder: List<PlayerColor>,
        ruleSet: LudoRuleSet
    ): Int {
        if (players.isEmpty()) return 0
        val total = players.size
        for (offset in 1..total) {
            val candidate = (currentIndex + offset) % total
            if (isPlayerEligibleForTurns(players, candidate, finishOrder, ruleSet)) {
                return candidate
            }
        }
        return currentIndex.coerceIn(0, total - 1)
    }

    /**
     * Returns whether the player at [index] is still eligible to receive turns.
     */
    fun isPlayerEligibleForTurns(
        players: List<PlayerState>,
        index: Int,
        finishOrder: List<PlayerColor>,
        ruleSet: LudoRuleSet
    ): Boolean {
        val candidate = players.getOrNull(index) ?: return false
        if (candidate.isEliminated) return false
        return if (ruleSet.teamsMode) {
            val partner = players.firstOrNull { it.color == candidate.color.opposite() }
            val candidateDone = candidate.hasReachedGoal(ruleSet.tokensHomeToWin)
            val partnerDone = partner == null || partner.isEliminated || partner.hasReachedGoal(ruleSet.tokensHomeToWin)
            !candidateDone || !partnerDone
        } else {
            candidate.color !in finishOrder
        }
    }

    /**
     * Returns the ordered clockwise cycle of eligible players starting from the active player.
     */
    fun upcomingTurnCycle(state: GameState, ruleSet: LudoRuleSet): List<PlayerState> {
        if (state.players.isEmpty() || state.isMatchFinished) return emptyList()
        val total = state.players.size
        val result = ArrayList<PlayerState>(total)
        for (offset in 0 until total) {
            val idx = (state.activePlayerIndex + offset) % total
            if (isPlayerEligibleForTurns(state.players, idx, state.finishOrder, ruleSet)) {
                result += state.players[idx]
            }
        }
        return result
    }
}
