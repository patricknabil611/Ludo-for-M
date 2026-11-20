package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTest {

    private val engine = LudoEngine()

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
    fun `board geometry has 52 unique outer cells and 8 safe squares`() {
        assertEquals(52, LudoBoardGeometry.OUTER_TRACK.size)
        assertEquals(52, LudoBoardGeometry.OUTER_TRACK.distinct().size)
        assertEquals(setOf(0, 8, 13, 21, 26, 34, 39, 47), LudoBoardGeometry.SAFE_TRACK_INDICES)
    }

    @Test
    fun `rolling non-six when all tokens in base yields NoLegalMoves and passes turn`() {
        val state = engine.createGame(twoPlayerSetups())
        val roll = engine.applyDiceRoll(state, 4)
        assertTrue(roll is RollOutcome.NoLegalMoves)
        assertEquals(TurnPhase.NO_LEGAL_MOVES, roll.state.phase)

        val next = engine.commitTurnAdvance(roll.state)
        assertEquals(PlayerColor.YELLOW, next.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, next.phase)
    }

    @Test
    fun `rolling six moves token out of base onto start square and grants extra roll`() {
        val state = engine.createGame(twoPlayerSetups())
        val roll = engine.applyDiceRoll(state, 6)
        assertTrue(roll is RollOutcome.MultipleLegalMoves)
        assertEquals(4, roll.state.legalMoves.size)

        val move = roll.state.legalMoves.first()
        val outcome = engine.applyMove(roll.state, move)
        assertTrue(outcome.grantedExtraRoll)
        assertEquals(PlayerColor.RED, outcome.state.activePlayer.color)
        assertEquals(0, outcome.state.activePlayer.tokens[0].progress)
        assertEquals(1, outcome.state.consecutiveSixes)
    }

    @Test
    fun `rolling three sixes in a row cancels turn without playing third roll`() {
        var state = engine.createGame(twoPlayerSetups())

        // 1st six -> bring token 0 out
        val r1 = engine.applyDiceRoll(state, 6) as RollOutcome.MultipleLegalMoves
        state = engine.applyMove(r1.state, r1.moves[0]).state
        assertEquals(1, state.consecutiveSixes)

        // 2nd six -> advance token 0 by 6
        val r2 = engine.applyDiceRoll(state, 6) as RollOutcome.MultipleLegalMoves
        val moveToken0 = r2.moves.first { it.tokenId == 0 }
        state = engine.applyMove(r2.state, moveToken0).state
        assertEquals(2, state.consecutiveSixes)
        assertEquals(6, state.activePlayer.tokens[0].progress)

        // 3rd six -> cancelled! Token 0 remains at progress 6
        val r3 = engine.applyDiceRoll(state, 6)
        assertTrue(r3 is RollOutcome.TripleSixCancelled)
        assertEquals(TurnPhase.TURN_CANCELLED_TRIPLE_SIX, r3.state.phase)
        assertEquals(6, r3.state.activePlayer.tokens[0].progress)

        val afterCancel = engine.commitTurnAdvance(r3.state)
        assertEquals(PlayerColor.YELLOW, afterCancel.activePlayer.color)
        assertEquals(0, afterCancel.consecutiveSixes)
    }

    @Test
    fun `capturing opponent on non-safe square sends opponent to base and grants NO extra roll`() {
        val baseState = engine.createGame(twoPlayerSetups())
        // Red token 0 at progress 27 -> mainTrackIndex = 27 (non-safe; Yellow start is 26)
        // Yellow token 0 at progress 1 -> mainTrackIndex = (26 + 1) % 52 = 27!
        // Put Red at progress 24, roll 3 -> lands on 27 where Yellow sits!
        val modifiedPlayers = baseState.players.map { player ->
            when (player.color) {
                PlayerColor.RED -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx == 0) t.copy(progress = 24) else t
                    }
                )
                PlayerColor.YELLOW -> player.copy(
                    tokens = player.tokens.mapIndexed { idx, t ->
                        if (idx == 0) t.copy(progress = 1) else t
                    }
                )
                else -> player
            }
        }
        val state = baseState.copy(players = modifiedPlayers, activePlayerIndex = 0)
        val roll = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        assertEquals(1, roll.move.capturedTokens.size)
        assertEquals(TokenRef(PlayerColor.YELLOW, 0), roll.move.capturedTokens.first())

        val outcome = engine.applyMove(roll.state, roll.move)
        assertFalse(outcome.grantedExtraRoll)
        assertEquals(PlayerColor.YELLOW, outcome.state.activePlayer.color)
        val yellowToken0 = outcome.state.playerByColor(PlayerColor.YELLOW)!!.tokens[0]
        assertTrue(yellowToken0.isInBase)
    }

    @Test
    fun `tokens on safe star and start squares cannot be captured`() {
        val baseState = engine.createGame(twoPlayerSetups())
        // Star square index 8 (8 steps after Red start): Red at progress 5, rolls 3 -> 8.
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
    fun `no blocks exist so tokens can pass over stacked opponent tokens`() {
        val baseState = engine.createGame(twoPlayerSetups())
        // Put 2 Yellow tokens stacked at trackIndex 5 (Yellow progress 31 -> (26+31)%52 = 5)
        // Red token at progress 2 rolls 5 -> jumps over index 5 and lands on progress 7.
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
    fun `exact roll required to enter home triangle`() {
        val baseState = engine.createGame(twoPlayerSetups())
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

        // Rolling 2 lands exactly on 56 -> Home!
        val exact = engine.applyDiceRoll(state, 2)
        assertTrue(exact is RollOutcome.SingleLegalMove)
        val move = (exact as RollOutcome.SingleLegalMove).move
        assertTrue(move.reachesHome)
        assertEquals(Token.HOME_PROGRESS, move.toProgress)
    }

    @Test
    fun `first player to get all 4 tokens home wins and game continues for 2nd place in 3-player game`() {
        val baseState = engine.createGame(threePlayerSetups())
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

        val roll = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        val outcome = engine.applyMove(roll.state, roll.move)

        assertEquals(1, outcome.playerFinishedRank)
        assertEquals(listOf(PlayerColor.RED), outcome.state.finishOrder)
        assertFalse(outcome.state.isMatchFinished)
        assertEquals(PlayerColor.GREEN, outcome.state.activePlayer.color)

        // Option to end match early once winner is decided
        val endedEarly = engine.endMatchEarly(outcome.state)
        assertTrue(endedEarly.isMatchFinished)
        assertEquals(PlayerColor.RED, endedEarly.finishOrder.first())
        assertEquals(3, endedEarly.finishOrder.size)
    }

    @Test
    fun `turn timeout skips active player turn cleanly`() {
        val state = engine.createGame(twoPlayerSetups())
        assertEquals(PlayerColor.RED, state.activePlayer.color)

        val afterTimeout = engine.handleTurnTimeout(state)
        assertEquals(PlayerColor.YELLOW, afterTimeout.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, afterTimeout.phase)
        assertEquals(0, afterTimeout.consecutiveSixes)
    }
}
