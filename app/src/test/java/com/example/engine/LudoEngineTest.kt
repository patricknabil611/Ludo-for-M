package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTest {

    private val classic1v1 = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
    private val classic4p = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.FOUR_PLAYERS)
    private val engine = LudoEngine(classic1v1)

    private fun twoPlayerSetups() = listOf(
        PlayerSetup(0, "Alice", PlayerColor.RED),
        PlayerSetup(1, "Bob", PlayerColor.YELLOW)
    )

    private fun threePlayerSetups() = listOf(
        PlayerSetup(0, "Alice", PlayerColor.RED),
        PlayerSetup(1, "Bob", PlayerColor.GREEN),
        PlayerSetup(2, "Cara", PlayerColor.YELLOW)
    )

    @Test
    fun `board geometry has 52 unique outer cells, 8 safe squares, and 4 arrow shortcuts`() {
        assertEquals(52, LudoBoardGeometry.OUTER_TRACK.size)
        assertEquals(52, LudoBoardGeometry.OUTER_TRACK.distinct().size)
        assertEquals(setOf(0, 8, 13, 21, 26, 34, 39, 47), LudoBoardGeometry.SAFE_TRACK_INDICES)
        assertEquals(mapOf(4 to 10, 17 to 23, 30 to 36, 43 to 49), LudoBoardGeometry.ARROW_SHORTCUTS)
    }

    @Test
    fun `rolling non-six when all tokens in base yields NoLegalMoves and passes turn`() {
        val state = engine.createGame(twoPlayerSetups(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val roll = engine.applyDiceRoll(state, 4)
        assertTrue(roll is RollOutcome.NoLegalMoves)
        assertEquals(TurnPhase.NO_LEGAL_MOVES, roll.state.phase)

        val next = engine.advanceTurnAfterSkip(roll.state)
        assertEquals(PlayerColor.YELLOW, next.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, next.phase)
    }

    @Test
    fun `rolling six stacks into tray and lets token leave base once stack ends`() {
        val state = engine.createGame(twoPlayerSetups(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val r1 = engine.applyDiceRoll(state, 6)
        assertTrue(r1 is RollOutcome.RollAgainStacked)
        assertEquals(listOf(6), r1.state.rolledDice)
        assertEquals(1, r1.state.consecutiveSixes)

        val r2 = engine.applyDiceRoll(r1.state, 3) as RollOutcome.MultipleLegalMoves
        assertEquals(listOf(6, 3), r2.state.rolledDice)

        // Spend 6 to leave base
        val leaveMove = r2.moves.first { it.leavesBase && it.tokenId == 0 }
        val afterLeave = engine.applyMove(r2.state, leaveMove)
        assertTrue(afterLeave.hasRemainingDiceMoves)
        assertEquals(0, afterLeave.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)

        // Spend 3 to move token 0 forward 3 steps
        val move3 = afterLeave.state.legalMoves.first()
        val afterThree = engine.applyMove(afterLeave.state, move3)
        assertFalse(afterThree.hasRemainingDiceMoves)
        assertEquals(3, afterThree.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
        assertEquals(PlayerColor.YELLOW, afterThree.state.activePlayer.color)
    }

    @Test
    fun `rolling three sixes in a row discards entire tray and passes turn`() {
        val state = engine.createGame(twoPlayerSetups(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)

        val r1 = engine.applyDiceRoll(state, 6) as RollOutcome.RollAgainStacked
        assertEquals(listOf(6), r1.state.rolledDice)

        val r2 = engine.applyDiceRoll(r1.state, 6) as RollOutcome.RollAgainStacked
        assertEquals(listOf(6, 6), r2.state.rolledDice)

        val r3 = engine.applyDiceRoll(r2.state, 6)
        assertTrue(r3 is RollOutcome.TripleSixCancelled)
        assertEquals(TurnPhase.TURN_CANCELLED_TRIPLE_SIX, r3.state.phase)
        assertTrue(r3.state.rolledDice.isEmpty())
        assertEquals("Three 6s — turn lost", r3.state.lastEventMessage)
        assertTrue(r3.state.playerByColor(PlayerColor.RED)!!.tokens.all { it.isInBase })

        val afterCancel = engine.advanceTurnAfterSkip(r3.state)
        assertEquals(PlayerColor.YELLOW, afterCancel.activePlayer.color)
        assertEquals(0, afterCancel.consecutiveSixes)
    }

    @Test
    fun `capturing stacked opponent tokens on non-safe square sends ALL to base and grants bonus roll`() {
        val baseState = engine.createGame(twoPlayerSetups(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        // Red token 0 at progress 24 -> rolls 3 -> lands on track 27 (non-safe).
        // Two Yellow tokens at progress 1 -> trackIndex (26 + 1) % 52 = 27!
        val modifiedPlayers = baseState.players.map { player ->
            when (player.color) {
                PlayerColor.RED -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx == 0) t.copy(progress = 24) else t
                    }
                )
                PlayerColor.YELLOW -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx <= 1) t.copy(progress = 1) else t
                    }
                )
                else -> player
            }
        }
        val state = baseState.copy(players = modifiedPlayers, activePlayerIndex = 0)
        val roll = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        assertEquals(2, roll.move.capturedTokens.size)
        assertEquals(
            setOf(TokenRef(PlayerColor.YELLOW, 0), TokenRef(PlayerColor.YELLOW, 1)),
            roll.move.capturedTokens.toSet()
        )

        val outcome = engine.applyMove(roll.state, roll.move)
        assertTrue("Capture must grant +1 bonus roll", outcome.grantedExtraRoll)
        assertEquals(PlayerColor.RED, outcome.state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, outcome.state.phase)
        val yellowTokens = outcome.state.playerByColor(PlayerColor.YELLOW)!!.tokens
        assertTrue(yellowTokens[0].isInBase)
        assertTrue(yellowTokens[1].isInBase)
    }

    @Test
    fun `tokens on safe star and start squares cannot be captured`() {
        val baseState = engine.createGame(twoPlayerSetups(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        // Star square index 8: Red at progress 5, rolls 3 -> 8.
        // Yellow at progress 34 -> (26 + 34) % 52 = 8!
        val modifiedPlayers = baseState.players.map { player ->
            when (player.color) {
                PlayerColor.RED -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx == 0) t.copy(progress = 5) else t
                    }
                )
                PlayerColor.YELLOW -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx == 0) t.copy(progress = 34) else t
                    }
                )
                else -> player
            }
        }
        val state = baseState.copy(players = modifiedPlayers, activePlayerIndex = 0)
        val roll = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        assertTrue(roll.move.capturedTokens.isEmpty())

        val outcome = engine.applyMove(roll.state, roll.move)
        val redToken0 = outcome.state.playerByColor(PlayerColor.RED)!!.tokens[0]
        val yellowToken0 = outcome.state.playerByColor(PlayerColor.YELLOW)!!.tokens[0]
        assertEquals(8, redToken0.mainTrackIndex())
        assertEquals(8, yellowToken0.mainTrackIndex())
    }

    @Test
    fun `classic mode has no blockades so tokens pass over stacked opponent tokens`() {
        val baseState = engine.createGame(twoPlayerSetups(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        // Two Yellow tokens at trackIndex 5 (Yellow progress 31 -> (26+31)%52 = 5)
        // Red token at progress 2 rolls 5 -> jumps over 5 and lands on 7.
        val modifiedPlayers = baseState.players.map { player ->
            when (player.color) {
                PlayerColor.RED -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx == 0) t.copy(progress = 2) else t
                    }
                )
                PlayerColor.YELLOW -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx <= 1) t.copy(progress = 31) else t
                    }
                )
                else -> player
            }
        }
        val state = baseState.copy(players = modifiedPlayers, activePlayerIndex = 0)
        val roll = engine.applyDiceRoll(state, 5)
        assertTrue(roll is RollOutcome.SingleLegalMove)
        val move = (roll as RollOutcome.SingleLegalMove).move
        assertEquals(7, move.toProgress)
        assertTrue(move.capturedTokens.isEmpty())
    }

    @Test
    fun `exact roll required to enter home triangle and grants bonus roll`() {
        val baseState = engine.createGame(twoPlayerSetups(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val modifiedPlayers = baseState.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx == 0) t.copy(progress = 54) else t
                    }
                )
            } else player
        }
        val state = baseState.copy(players = modifiedPlayers, activePlayerIndex = 0)

        // Rolling 3 overshoots 56 -> illegal
        val overshoot = engine.applyDiceRoll(state, 3)
        assertTrue(overshoot is RollOutcome.NoLegalMoves)

        // Rolling 2 lands exactly on 56 -> Home + bonus roll!
        val exact = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        assertTrue(exact.move.reachesHome)
        assertEquals(Token.HOME_PROGRESS, exact.move.toProgress)

        val outcome = engine.applyMove(exact.state, exact.move)
        assertTrue("Reaching Home must grant +1 bonus roll", outcome.grantedExtraRoll)
        assertEquals(PlayerColor.RED, outcome.state.activePlayer.color)
    }

    @Test
    fun `first player to get all 4 tokens home wins and game continues for 2nd place in 3-player game`() {
        val engine3p = LudoEngine(classic4p)
        val baseState = engine3p.createGame(threePlayerSetups(), GameModeId.CLASSIC, MatchFormat.FOUR_PLAYERS)
        val modifiedPlayers = baseState.players.map { player ->
            if (player.color == PlayerColor.RED) {
                player.copy(
                    tokens = listOf(
                        Token(0, PlayerColor.RED, Token.HOME_PROGRESS),
                        Token(1, PlayerColor.RED, Token.HOME_PROGRESS),
                        Token(2, PlayerColor.RED, Token.HOME_PROGRESS),
                        Token(3, PlayerColor.RED, 55)
                    )
                )
            } else player
        }
        val state = baseState.copy(players = modifiedPlayers, activePlayerIndex = 0)

        val roll = engine3p.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        val outcome = engine3p.applyMove(roll.state, roll.move)

        assertEquals(1, outcome.playerFinishedRank)
        assertEquals(listOf(PlayerColor.RED), outcome.state.finishOrder)
        assertFalse(outcome.state.isMatchFinished)
        assertEquals(PlayerColor.GREEN, outcome.state.activePlayer.color)

        // Option to end match early once winner is decided
        val endedEarly = engine3p.endMatchEarly(outcome.state)
        assertTrue(endedEarly.isMatchFinished)
        assertEquals(PlayerColor.RED, endedEarly.finishOrder.first())
        assertEquals(3, endedEarly.finishOrder.size)
    }
}
