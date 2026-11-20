package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LudoModesEngineTest {

    private fun twoPlayers() = listOf(
        PlayerSetup(0, "Alice", PlayerColor.RED),
        PlayerSetup(1, "Bob", PlayerColor.YELLOW)
    )

    private fun threePlayers() = listOf(
        PlayerSetup(0, "Alice", PlayerColor.RED),
        PlayerSetup(1, "Bob", PlayerColor.GREEN),
        PlayerSetup(2, "Cara", PlayerColor.YELLOW)
    )

    private fun fourPlayers() = listOf(
        PlayerSetup(0, "Red", PlayerColor.RED),
        PlayerSetup(1, "Green", PlayerColor.GREEN),
        PlayerSetup(2, "Yellow", PlayerColor.YELLOW),
        PlayerSetup(3, "Blue", PlayerColor.BLUE)
    )

    @Test
    fun `quick mode starts all tokens on start square so non-six rolls are immediately playable`() {
        val engine = LudoEngine(QuickRuleSet)
        val state = engine.createGame(twoPlayers(), GameModeId.QUICK)

        assertTrue(state.players.all { p -> p.tokens.all { it.progress == 0 } })

        val roll = engine.applyDiceRoll(state, 3) as RollOutcome.MultipleLegalMoves
        assertEquals(4, roll.moves.size)
        val move = roll.moves.first()
        val outcome = engine.applyMove(roll.state, move)
        assertEquals(3, outcome.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
    }

    @Test
    fun `master mode keeps circling board until first capture then unlocks home column`() {
        val engine = LudoEngine(MasterRuleSet)
        val base = engine.createGame(twoPlayers(), GameModeId.MASTER)

        // Red token 0 at progress 49 (before home entry 50), hasCapturedOpponent = false
        val stateBeforeCapture = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        hasCapturedOpponent = false,
                        tokens = p.tokens.mapIndexed { idx, t ->
                            if (idx == 0) t.copy(progress = 49) else t
                        }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t ->
                            if (idx == 0) t.copy(progress = 1) else t
                        }
                    )
                    else -> p
                }
            }
        )

        // Roll 4: steps 50 -> outerWrap51 -> 0 -> 1 (circles past home entry!)
        val roll = engine.applyDiceRoll(stateBeforeCapture, 4) as RollOutcome.SingleLegalMove
        assertEquals(1, roll.move.toProgress)
        assertFalse(roll.move.toOuterWrap51)
        val afterCircle = engine.applyMove(roll.state, roll.move)
        assertEquals(1, afterCircle.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)

        // Now test stopping exactly on outerWrap51 when rolling 2 from 49
        val rollTo51 = engine.applyDiceRoll(stateBeforeCapture, 2) as RollOutcome.SingleLegalMove
        assertTrue(rollTo51.move.toOuterWrap51)
        assertEquals(51, rollTo51.state.playerByColor(PlayerColor.RED)!!.let {
            it.tokens[0].copy(progress = rollTo51.move.toProgress, outerWrap51 = rollTo51.move.toOuterWrap51).outerTrackOffset()
        })

        // Now unlock via capture: Red at 24 rolls 3 -> captures Yellow at 1 (track 27)
        val readyToCapture = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        hasCapturedOpponent = false,
                        tokens = p.tokens.mapIndexed { idx, t ->
                            if (idx == 0) t.copy(progress = 24) else if (idx == 1) t.copy(progress = 49) else t
                        }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t ->
                            if (idx == 0) t.copy(progress = 1) else t
                        }
                    )
                    else -> p
                }
            }
        )
        val capRoll = engine.applyDiceRoll(readyToCapture, 3) as RollOutcome.MultipleLegalMoves
        val capMove = capRoll.moves.first { it.tokenId == 0 }
        val afterCap = engine.applyMove(capRoll.state, capMove).state
        assertTrue(afterCap.playerByColor(PlayerColor.RED)!!.hasCapturedOpponent)

        // Next Red turn: token 1 at progress 49 rolls 3 -> enters Home Column 52!
        val redTurnAgain = afterCap.copy(activePlayerIndex = 0)
        val enterHomeRoll = engine.applyDiceRoll(redTurnAgain, 3) as RollOutcome.MultipleLegalMoves
        val enterHomeMove = enterHomeRoll.moves.first { it.tokenId == 1 }
        assertEquals(52, enterHomeMove.toProgress)
    }

    @Test
    fun `timed mode tracks move capture penalty and home scores and crowns highest scorer on clock end`() {
        val engine = LudoEngine(TimedRuleSet)
        val base = engine.createGame(twoPlayers(), GameModeId.TIMED, timedDurationSeconds = 300)

        // Red token 0 at progress 24 -> rolls 3 -> moves 3 (+3) + captures Yellow (+10) -> +13; Yellow -5
        // Red token 1 at progress 53 -> rolls 3 -> moves 3 (+3) + reaches Home (+25) -> +28
        val setup = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.RED, 24),
                            Token(1, PlayerColor.RED, 53),
                            Token(2, PlayerColor.RED, Token.BASE_PROGRESS),
                            Token(3, PlayerColor.RED, Token.BASE_PROGRESS)
                        )
                    )
                    PlayerColor.YELLOW -> p.copy(
                        score = 10,
                        tokens = p.tokens.mapIndexed { idx, t ->
                            if (idx == 0) t.copy(progress = 1) else t
                        }
                    )
                    else -> p
                }
            }
        )

        val capRoll = engine.applyDiceRoll(setup, 3) as RollOutcome.MultipleLegalMoves
        val afterCap = engine.applyMove(capRoll.state, capRoll.moves.first { it.tokenId == 0 }).state
        assertEquals(13, afterCap.playerByColor(PlayerColor.RED)!!.score)
        assertEquals(5, afterCap.playerByColor(PlayerColor.YELLOW)!!.score)

        // Give Red another turn to bring token 1 home
        val redTurn = afterCap.copy(activePlayerIndex = 0)
        val homeRoll = engine.applyDiceRoll(redTurn, 3) as RollOutcome.MultipleLegalMoves
        val afterHome = engine.applyMove(homeRoll.state, homeRoll.moves.first { it.tokenId == 1 }).state
        assertEquals(13 + 3 + 25, afterHome.playerByColor(PlayerColor.RED)!!.score)

        val expired = engine.handleTimedClockExpired(afterHome)
        assertTrue(expired.isMatchFinished)
        assertEquals(PlayerColor.RED, expired.winner)
    }

    @Test
    fun `teams 2v2 prevents partner capture and lets finished player roll for partner until both finish`() {
        val engine = LudoEngine(Teams2v2RuleSet)
        val base = engine.createGame(fourPlayers(), GameModeId.TEAMS_2V2)

        // Red has all 4 tokens home; Yellow has token 0 at 54; Red's turn!
        // Also place Yellow token 1 on trackIndex 10 and test Red never captures Yellow.
        val setup = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        tokens = List(4) { Token(it, PlayerColor.RED, Token.HOME_PROGRESS) }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.YELLOW, 54),
                            Token(1, PlayerColor.YELLOW, Token.HOME_PROGRESS),
                            Token(2, PlayerColor.YELLOW, Token.HOME_PROGRESS),
                            Token(3, PlayerColor.YELLOW, Token.HOME_PROGRESS)
                        )
                    )
                    else -> p
                }
            },
            activePlayerIndex = 0 // Red's turn
        )

        // Red rolls for Yellow!
        assertEquals(PlayerColor.YELLOW, engine.controlledColorForActivePlayer(setup))
        val roll = engine.applyDiceRoll(setup, 2) as RollOutcome.SingleLegalMove
        assertEquals(PlayerColor.YELLOW, roll.move.color)
        assertTrue(roll.move.capturedTokens.isEmpty())

        val outcome = engine.applyMove(roll.state, roll.move)
        assertTrue(outcome.state.isMatchFinished)
        assertEquals("Team Red & Yellow", outcome.state.winningTeamLabel)
        assertEquals(setOf(PlayerColor.RED, PlayerColor.YELLOW), outcome.state.finishOrder.take(2).toSet())
    }

    @Test
    fun `one token race ends immediately when first token reaches home`() {
        val engine = LudoEngine(OneTokenRaceRuleSet)
        val base = engine.createGame(twoPlayers(), GameModeId.ONE_TOKEN_RACE)
        val state = base.copy(
            players = base.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.RED, 54),
                            Token(1, PlayerColor.RED, Token.BASE_PROGRESS),
                            Token(2, PlayerColor.RED, Token.BASE_PROGRESS),
                            Token(3, PlayerColor.RED, Token.BASE_PROGRESS)
                        )
                    )
                } else p
            }
        )

        val roll = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        val outcome = engine.applyMove(roll.state, roll.move)
        assertTrue(outcome.state.isMatchFinished)
        assertEquals(PlayerColor.RED, outcome.state.winner)
    }

    @Test
    fun `chaos triggers event every 3 full rounds and never repeats same event twice in a row`() {
        val engine = LudoEngine(ChaosRuleSet, eventRandom = Random(7))
        var state = engine.createGame(twoPlayers(), GameModeId.CHAOS)
        val seenEvents = mutableListOf<ChaosEventType>()

        // Simulate 6 full rounds of non-6 rolls (12 turns total)
        repeat(12) {
            val roll = engine.applyDiceRoll(state, 1) as RollOutcome.NoLegalMoves
            state = engine.commitTurnAdvance(roll.state)
            val ann = state.chaosState.activeAnnouncement
            if (ann != null && seenEvents.size < state.chaosState.announcementId) {
                seenEvents += ann
            }
        }

        assertEquals(6, state.fullRoundsCompleted)
        assertEquals(2, seenEvents.size)
        assertNotEquals(seenEvents[0], seenEvents[1])
    }

    @Test
    fun `chaos dice storm doubles next roll up to 12`() {
        val engine = LudoEngine(ChaosRuleSet)
        val base = engine.createGame(twoPlayers(), GameModeId.CHAOS)
        val state = base.copy(
            players = base.players.mapIndexed { idx, p ->
                if (idx == 0) {
                    p.copy(
                        diceStormCharges = 1,
                        tokens = p.tokens.mapIndexed { tIdx, t ->
                            if (tIdx == 0) t.copy(progress = 10) else t
                        }
                    )
                } else p
            }
        )

        val roll = engine.applyDiceRoll(state, 6) as RollOutcome.SingleLegalMove
        assertTrue(roll.state.wasDiceDoubled)
        assertEquals(12, roll.state.lastDiceValue)
        assertEquals(22, roll.move.toProgress)
        assertEquals(0, roll.state.activePlayer.diceStormCharges)
    }

    @Test
    fun `elimination knocks out lowest progress player every 5 full rounds until last player stands`() {
        val engine = LudoEngine(EliminationRuleSet)
        var state = engine.createGame(threePlayers(), GameModeId.ELIMINATION)

        // Give Red highest progress (30), Green medium (15), Yellow lowest (0)
        state = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 30) else t }
                    )
                    PlayerColor.GREEN -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 15) else t }
                    )
                    else -> p
                }
            }
        )

        // Advance 5 full rounds (3 players * 5 rounds = 15 turn passes)
        repeat(15) {
            val roll = engine.applyDiceRoll(state, 2)
            state = when (roll) {
                is RollOutcome.NoLegalMoves -> engine.commitTurnAdvance(roll.state)
                is RollOutcome.SingleLegalMove -> engine.applyMove(roll.state, roll.move).state
                is RollOutcome.MultipleLegalMoves -> engine.applyMove(roll.state, roll.moves.first()).state
                is RollOutcome.TripleSixCancelled -> engine.commitTurnAdvance(roll.state)
            }
        }

        assertEquals(5, state.fullRoundsCompleted)
        assertTrue(state.playerByColor(PlayerColor.YELLOW)!!.isEliminated)
        assertFalse(state.isMatchFinished)

        // Advance 5 more full rounds (2 surviving players * 5 rounds = 10 turn passes)
        repeat(10) {
            if (!state.isMatchFinished) {
                val roll = engine.applyDiceRoll(state, 1)
                state = when (roll) {
                    is RollOutcome.NoLegalMoves -> engine.commitTurnAdvance(roll.state)
                    is RollOutcome.SingleLegalMove -> engine.applyMove(roll.state, roll.move).state
                    is RollOutcome.MultipleLegalMoves -> engine.applyMove(roll.state, roll.moves.first()).state
                    is RollOutcome.TripleSixCancelled -> engine.commitTurnAdvance(roll.state)
                }
            }
        }

        assertTrue(state.isMatchFinished)
        assertEquals(PlayerColor.RED, state.winner)
    }
}
