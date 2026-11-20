package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TurnManagerTest {

    private fun twoPlayerClassicState(): GameState {
        val engine = LudoEngine(ruleSet = ClassicRuleSet, eventRandom = Random(42))
        return engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC
        )
    }

    private fun fourPlayerState(mode: GameModeId): GameState {
        val engine = LudoEngine(ruleSet = RuleSetRegistry.forMode(mode), eventRandom = Random(42))
        return engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 1, nickname = "Green", color = PlayerColor.GREEN),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW),
                PlayerSetup(seatIndex = 3, nickname = "Blue", color = PlayerColor.BLUE)
            ),
            mode = mode
        )
    }

    @Test
    fun cyclesClockwiseAcrossFourPlayersAndTracksRounds() {
        val engine = LudoEngine(ruleSet = ClassicRuleSet, eventRandom = Random(1))
        var state = fourPlayerState(GameModeId.CLASSIC)

        assertEquals(PlayerColor.RED, TurnManager.activePlayer(state).color)
        assertEquals(
            listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE),
            TurnManager.upcomingTurnCycle(state, ClassicRuleSet).map { it.color }
        )

        // Roll a 2 (no legal moves from base) -> passes to GREEN
        val r1 = engine.applyDiceRoll(state, 2, actingColor = PlayerColor.RED)
        assertTrue(r1 is RollOutcome.NoLegalMoves)
        state = engine.commitTurnAdvance(r1.state)
        assertEquals(PlayerColor.GREEN, state.activePlayer.color)
        assertEquals(0, state.fullRoundsCompleted)

        // GREEN -> YELLOW -> BLUE -> RED completes 1 full round
        state = engine.commitTurnAdvance(engine.applyDiceRoll(state, 3, actingColor = PlayerColor.GREEN).state)
        assertEquals(PlayerColor.YELLOW, state.activePlayer.color)

        state = engine.commitTurnAdvance(engine.applyDiceRoll(state, 4, actingColor = PlayerColor.YELLOW).state)
        assertEquals(PlayerColor.BLUE, state.activePlayer.color)

        state = engine.commitTurnAdvance(engine.applyDiceRoll(state, 5, actingColor = PlayerColor.BLUE).state)
        assertEquals(PlayerColor.RED, state.activePlayer.color)
        assertEquals(1, state.fullRoundsCompleted)
    }

    @Test
    fun skipsFinishedAndEliminatedPlayersInCycle() {
        val base = fourPlayerState(GameModeId.ELIMINATION)
        val players = base.players.map { player ->
            when (player.color) {
                PlayerColor.GREEN -> player.copy(isEliminated = true)
                PlayerColor.YELLOW -> player.copy(tokens = player.tokens.map { it.copy(progress = Token.HOME_PROGRESS) })
                else -> player
            }
        }
        val state = base.copy(
            players = players,
            activePlayerIndex = 0, // RED
            finishOrder = listOf(PlayerColor.YELLOW)
        )

        val nextIdx = TurnManager.nextEligiblePlayerIndex(
            players = state.players,
            currentIndex = 0,
            finishOrder = state.finishOrder,
            ruleSet = EliminationRuleSet
        )
        assertEquals(PlayerColor.BLUE, state.players[nextIdx].color)
        assertEquals(
            listOf(PlayerColor.RED, PlayerColor.BLUE),
            TurnManager.upcomingTurnCycle(state, EliminationRuleSet).map { it.color }
        )
    }

    @Test
    fun teams2v2FinishedPlayerStillCyclesAndControlsPartnerTokens() {
        val base = fourPlayerState(GameModeId.TEAMS_2V2)
        val players = base.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(tokens = player.tokens.map { it.copy(progress = Token.HOME_PROGRESS) })
            } else {
                player
            }
        }
        val state = base.copy(
            players = players,
            activePlayerIndex = 0, // RED (all 4 tokens home, partner is YELLOW)
            finishOrder = listOf(PlayerColor.RED)
        )

        assertTrue(TurnManager.isPlayerEligibleForTurns(state.players, 0, state.finishOrder, Teams2v2RuleSet))
        assertEquals(PlayerColor.YELLOW, TurnManager.controlledColor(state, Teams2v2RuleSet))

        // Tapping RED's token when controlling partner YELLOW is rejected
        val tapRed = TurnManager.validateTokenTap(state, Teams2v2RuleSet, TokenRef(PlayerColor.RED, 0))
        assertTrue(tapRed is TurnGuardResult.NotPlayersTurn)
        assertEquals(PlayerColor.YELLOW, (tapRed as TurnGuardResult.NotPlayersTurn).expectedColor)
    }

    @Test
    fun rejectsOutOfTurnRollAndDoesNotMutateState() {
        val engine = LudoEngine(ruleSet = ClassicRuleSet, eventRandom = Random(7))
        val state = twoPlayerClassicState()

        assertTrue(TurnManager.isPlayersTurn(state, PlayerColor.RED))
        assertFalse(TurnManager.isPlayersTurn(state, PlayerColor.YELLOW))

        val guard = TurnManager.validateRoll(state, actingColor = PlayerColor.YELLOW)
        assertTrue(guard is TurnGuardResult.NotPlayersTurn)
        assertEquals(PlayerColor.RED, (guard as TurnGuardResult.NotPlayersTurn).expectedColor)
        assertEquals(PlayerColor.YELLOW, guard.attemptedColor)

        var threw = false
        try {
            engine.applyDiceRoll(state, 6, actingColor = PlayerColor.YELLOW)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected engine to reject out-of-turn dice roll", threw)
    }

    @Test
    fun rejectsOutOfTurnTokenTapAndMove() {
        val engine = LudoEngine(ruleSet = QuickRuleSet, eventRandom = Random(9))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK
        )

        // Before rolling, even RED cannot move a token
        val tapBeforeRoll = TurnManager.validateTokenTap(initial, QuickRuleSet, TokenRef(PlayerColor.RED, 0))
        assertTrue(tapBeforeRoll is TurnGuardResult.InvalidPhase)

        // RED rolls a 4 -> AWAITING_MOVE with legal moves for RED
        val rolled = engine.applyDiceRoll(initial, 4, actingColor = PlayerColor.RED)
        assertTrue(rolled is RollOutcome.MultipleLegalMoves)
        val awaitingState = rolled.state

        // Tapping YELLOW's token during RED's turn is blocked
        val wrongOwnerTap = TurnManager.validateTokenTap(awaitingState, QuickRuleSet, TokenRef(PlayerColor.YELLOW, 0))
        assertTrue(wrongOwnerTap is TurnGuardResult.NotPlayersTurn)

        // Acting as YELLOW with RED's legal move is also blocked
        val redMove = awaitingState.legalMoves.first()
        val wrongActorMove = TurnManager.validateMove(awaitingState, QuickRuleSet, redMove, actingColor = PlayerColor.YELLOW)
        assertTrue(wrongActorMove is TurnGuardResult.NotPlayersTurn)

        var threw = false
        try {
            engine.applyMove(awaitingState, redMove, actingColor = PlayerColor.YELLOW)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected engine to reject out-of-turn move", threw)

        // Acting as RED succeeds and advances turn to YELLOW
        val validTap = TurnManager.validateTokenTap(awaitingState, QuickRuleSet, TokenRef(PlayerColor.RED, redMove.tokenId), actingColor = PlayerColor.RED)
        assertTrue(validTap.isAllowed)

        val moveResult = engine.applyMove(awaitingState, redMove, actingColor = PlayerColor.RED)
        assertEquals(PlayerColor.YELLOW, moveResult.state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, moveResult.state.phase)
    }

    @Test
    fun extraRollOnSixKeepsSamePlayerActive() {
        val engine = LudoEngine(ruleSet = QuickRuleSet, eventRandom = Random(11))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK
        )

        val rolled = engine.applyDiceRoll(initial, 6, actingColor = PlayerColor.RED)
        assertTrue(rolled is RollOutcome.MultipleLegalMoves)
        val moveResult = engine.applyMove(rolled.state, rolled.state.legalMoves.first(), actingColor = PlayerColor.RED)

        assertTrue(moveResult.grantedExtraRoll)
        assertEquals(PlayerColor.RED, moveResult.state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, moveResult.state.phase)
    }
}
