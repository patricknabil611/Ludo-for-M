package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TurnManagerTest {

    private val classicRules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.FOUR_PLAYERS)
    private val quickRules = RuleSetRegistry.forMode(GameModeId.QUICK, MatchFormat.ONE_VS_ONE)
    private val eliminationRules = RuleSetRegistry.forMode(GameModeId.ELIMINATION, MatchFormat.FOUR_PLAYERS)
    private val teamRules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.TEAMS_2V2)

    private fun twoPlayerClassicState(): GameState {
        val rules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(ruleSet = rules, eventRandom = Random(42))
        return engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC,
            matchFormat = MatchFormat.ONE_VS_ONE
        )
    }

    private fun fourPlayerState(
        mode: GameModeId,
        format: MatchFormat = MatchFormat.FOUR_PLAYERS
    ): GameState {
        val engine = LudoEngine(ruleSet = RuleSetRegistry.forMode(mode, format), eventRandom = Random(42))
        return engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 1, nickname = "Green", color = PlayerColor.GREEN),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW),
                PlayerSetup(seatIndex = 3, nickname = "Blue", color = PlayerColor.BLUE)
            ),
            mode = mode,
            matchFormat = format
        )
    }

    @Test
    fun cyclesClockwiseAcrossFourPlayersAndTracksRounds() {
        val engine = LudoEngine(ruleSet = classicRules, eventRandom = Random(1))
        var state = fourPlayerState(GameModeId.CLASSIC)

        assertEquals(PlayerColor.RED, TurnManager.activePlayer(state).color)
        assertEquals(
            listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE),
            TurnManager.upcomingTurnCycle(state, classicRules).map { it.color }
        )

        // Roll a 2 (no legal moves from base) -> passes to GREEN
        val r1 = engine.applyDiceRoll(state, 2)
        assertTrue(r1 is RollOutcome.NoLegalMoves)
        state = engine.advanceTurnAfterSkip(r1.state)
        assertEquals(PlayerColor.GREEN, state.activePlayer.color)
        assertEquals(0, state.fullRoundsCompleted)

        // GREEN -> YELLOW -> BLUE -> RED completes 1 full round
        state = engine.advanceTurnAfterSkip(engine.applyDiceRoll(state, 3).state)
        assertEquals(PlayerColor.YELLOW, state.activePlayer.color)

        state = engine.advanceTurnAfterSkip(engine.applyDiceRoll(state, 4).state)
        assertEquals(PlayerColor.BLUE, state.activePlayer.color)

        state = engine.advanceTurnAfterSkip(engine.applyDiceRoll(state, 5).state)
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
            ruleSet = eliminationRules
        )
        assertEquals(PlayerColor.BLUE, state.players[nextIdx].color)
        assertEquals(
            listOf(PlayerColor.RED, PlayerColor.BLUE),
            TurnManager.upcomingTurnCycle(state, eliminationRules).map { it.color }
        )
    }

    @Test
    fun teams2v2FinishedPlayerStillCyclesAndControlsPartnerTokens() {
        val base = fourPlayerState(GameModeId.CLASSIC, MatchFormat.TEAMS_2V2)
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

        assertTrue(TurnManager.isPlayerEligibleForTurns(state.players, 0, state.finishOrder, teamRules))
        assertEquals(PlayerColor.YELLOW, TurnManager.controlledColor(state, teamRules))

        // Tapping RED's token when controlling partner YELLOW is rejected
        val tapRed = TurnManager.validateTokenTap(state, teamRules, TokenRef(PlayerColor.RED, 0))
        assertTrue(tapRed is TurnGuardResult.NotPlayersTurn)
        assertEquals(PlayerColor.YELLOW, (tapRed as TurnGuardResult.NotPlayersTurn).expectedColor)
    }

    @Test
    fun rejectsOutOfTurnRollGuard() {
        val state = twoPlayerClassicState()

        assertTrue(TurnManager.isPlayersTurn(state, PlayerColor.RED))
        assertFalse(TurnManager.isPlayersTurn(state, PlayerColor.YELLOW))

        val guard = TurnManager.validateRoll(state, actingColor = PlayerColor.YELLOW)
        assertTrue(guard is TurnGuardResult.NotPlayersTurn)
        assertEquals(PlayerColor.RED, (guard as TurnGuardResult.NotPlayersTurn).expectedColor)
        assertEquals(PlayerColor.YELLOW, guard.attemptedColor)
    }

    @Test
    fun rejectsOutOfTurnTokenTapAndMove() {
        val engine = LudoEngine(ruleSet = quickRules, eventRandom = Random(9))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK,
            matchFormat = MatchFormat.ONE_VS_ONE
        )

        // Before rolling, even RED cannot move a token
        val tapBeforeRoll = TurnManager.validateTokenTap(initial, quickRules, TokenRef(PlayerColor.RED, 0))
        assertTrue(tapBeforeRoll is TurnGuardResult.InvalidPhase)

        // RED starts with 1 token out in Quick -> rolling 4 yields SingleLegalMove
        val rolled = engine.applyDiceRoll(initial, 4)
        assertTrue(rolled is RollOutcome.SingleLegalMove)
        val awaitingState = rolled.state

        // Tapping YELLOW's token during RED's turn is blocked
        val wrongOwnerTap = TurnManager.validateTokenTap(awaitingState, quickRules, TokenRef(PlayerColor.YELLOW, 0))
        assertTrue(wrongOwnerTap is TurnGuardResult.NotPlayersTurn)

        // Acting as YELLOW with RED's legal move is also blocked
        val redMove = awaitingState.legalMoves.first()
        val wrongActorMove = TurnManager.validateMove(awaitingState, quickRules, redMove, actingColor = PlayerColor.YELLOW)
        assertTrue(wrongActorMove is TurnGuardResult.NotPlayersTurn)

        // Acting as RED succeeds and advances turn to YELLOW
        val validTap = TurnManager.validateTokenTap(
            awaitingState,
            quickRules,
            TokenRef(PlayerColor.RED, redMove.tokenId),
            actingColor = PlayerColor.RED
        )
        assertTrue(validTap.isAllowed)

        val moveResult = engine.applyMove(awaitingState, redMove)
        assertEquals(PlayerColor.YELLOW, moveResult.state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, moveResult.state.phase)
    }

    @Test
    fun rollingSixStacksIntoTrayAndKeepsSamePlayerRolling() {
        val engine = LudoEngine(ruleSet = quickRules, eventRandom = Random(11))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(seatIndex = 0, nickname = "Red", color = PlayerColor.RED),
                PlayerSetup(seatIndex = 2, nickname = "Yellow", color = PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK,
            matchFormat = MatchFormat.ONE_VS_ONE
        )

        val rolled = engine.applyDiceRoll(initial, 6)
        assertTrue(rolled is RollOutcome.RollAgainStacked)
        assertEquals(PlayerColor.RED, rolled.state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, rolled.state.phase)
        assertEquals(listOf(6), rolled.state.rolledDice)
        assertEquals(1, rolled.state.consecutiveSixes)
    }
}
