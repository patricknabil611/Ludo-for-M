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
    fun `blockades mode prevents opponents from passing or landing on same-color wall and breaks when one leaves`() {
        val rules = RuleSetRegistry.forMode(GameModeId.BLOCKADES, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(rules)
        val base = engine.createGame(twoPlayers(), GameModeId.BLOCKADES, MatchFormat.ONE_VS_ONE)

        // Place 2 Yellow tokens on non-safe track 5 (Yellow progress 31 -> (26+31)%52 = 5)
        // Red token 0 at progress 2 -> rolling 3 (lands on 5) or 5 (passes through 5) must BOTH be illegal!
        val walledState = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 2) else it })
                    PlayerColor.YELLOW -> p.copy(tokens = p.tokens.map { if (it.id <= 1) it.copy(progress = 31) else it })
                    else -> p
                }
            }
        )

        assertEquals(mapOf(5 to PlayerColor.YELLOW), engine.activeBlockades(walledState))
        assertTrue("Cannot land on opponent blockade", engine.applyDiceRoll(walledState, 3) is RollOutcome.NoLegalMoves)
        assertTrue("Cannot pass through opponent blockade", engine.applyDiceRoll(walledState, 5) is RollOutcome.NoLegalMoves)
        assertTrue("Can stop before blockade (track 4)", engine.applyDiceRoll(walledState, 2) is RollOutcome.SingleLegalMove)

        // Break blockade by moving Yellow token 1 away from track 5 -> Red can now pass/capture!
        val brokenWall = walledState.copy(
            players = walledState.players.map { p ->
                if (p.color == PlayerColor.YELLOW) {
                    p.copy(tokens = p.tokens.map { if (it.id == 1) it.copy(progress = 33) else it })
                } else p
            }
        )
        assertTrue(engine.activeBlockades(brokenWall).isEmpty())
        val capRoll = engine.applyDiceRoll(brokenWall, 3) as RollOutcome.SingleLegalMove
        assertEquals(1, capRoll.move.capturedTokens.size)
    }

    @Test
    fun `master mode locks home until first capture and merges same-square tokens into Joint Tokens`() {
        val rules = RuleSetRegistry.forMode(GameModeId.MASTER, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(rules)
        val base = engine.createGame(twoPlayers(), GameModeId.MASTER, MatchFormat.ONE_VS_ONE)

        // 1. Locked home column: Red token 0 at progress 49, hasCapturedOpponent = false -> circles outer track!
        val stateBeforeCapture = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        hasCapturedOpponent = false,
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 49) else t }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 1) else t }
                    )
                    else -> p
                }
            }
        )

        val circleRoll = engine.applyDiceRoll(stateBeforeCapture, 4) as RollOutcome.SingleLegalMove
        assertEquals(1, circleRoll.move.toProgress)
        assertFalse(circleRoll.move.toOuterWrap51)

        // 2. First capture unlocks home column!
        val readyToCapture = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        hasCapturedOpponent = false,
                        tokens = p.tokens.mapIndexed { idx, t ->
                            when (idx) {
                                0 -> t.copy(progress = 24)
                                1 -> t.copy(progress = 49)
                                else -> t
                            }
                        }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 1) else t }
                    )
                    else -> p
                }
            }
        )
        val capRoll = engine.applyDiceRoll(readyToCapture, 3) as RollOutcome.MultipleLegalMoves
        val capOutcome = engine.applyMove(capRoll.state, capRoll.moves.first { it.tokenId == 0 })
        assertTrue(capOutcome.unlockedMasterHome)
        assertTrue(capOutcome.state.playerByColor(PlayerColor.RED)!!.hasCapturedOpponent)

        // Bonus roll lets Red enter Home Column immediately with token 1!
        val enterHomeRoll = engine.applyDiceRoll(capOutcome.state, 3) as RollOutcome.MultipleLegalMoves
        val enterHomeMove = enterHomeRoll.moves.first { it.tokenId == 1 }
        assertEquals(52, enterHomeMove.toProgress)

        // 3. Joint Token: two Red tokens on same square merge & move together; single Yellow cannot land on it;
        //    Yellow Joint Token CAN land on it and captures BOTH Red tokens!
        val mergeSetup = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.RED, 10),
                            Token(1, PlayerColor.RED, 7),
                            Token(2, PlayerColor.RED, Token.BASE_PROGRESS),
                            Token(3, PlayerColor.RED, Token.BASE_PROGRESS)
                        )
                    )
                    else -> p
                }
            }
        )
        val mergeRoll = engine.applyDiceRoll(mergeSetup, 3) as RollOutcome.MultipleLegalMoves
        val afterMerge = engine.applyMove(mergeRoll.state, mergeRoll.moves.first { it.tokenId == 1 }).state
        val redTokens = afterMerge.playerByColor(PlayerColor.RED)!!.tokens
        assertTrue("Tokens 0 and 1 must merge into a Joint Token", redTokens[0].isJoint && redTokens[1].isJoint)
        assertEquals(1, redTokens[0].jointPartnerId)
        assertEquals(0, redTokens[1].jointPartnerId)

        // Moving the Joint Token moves BOTH tokens using a single die value!
        val jointTurn = afterMerge.copy(activePlayerIndex = 0)
        val jointRoll = engine.applyDiceRoll(jointTurn, 2) as RollOutcome.SingleLegalMove
        assertTrue(jointRoll.move.isJointMove)
        assertEquals(listOf(0, 1), jointRoll.move.tokenIds)
        val afterJointMove = engine.applyMove(jointRoll.state, jointRoll.move).state
        assertEquals(12, afterJointMove.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
        assertEquals(12, afterJointMove.playerByColor(PlayerColor.RED)!!.tokens[1].progress)

        // Single Yellow token at progress 35 (track 9) rolling 3 to land on Red Joint Token at track 12 -> ILLEGAL!
        val singleVsJoint = afterJointMove.copy(
            activePlayerIndex = 1,
            players = afterJointMove.players.map { p ->
                if (p.color == PlayerColor.YELLOW) {
                    p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 35) else it })
                } else p
            }
        )
        assertTrue("Single token cannot land on opponent Joint Token", engine.applyDiceRoll(singleVsJoint, 3) is RollOutcome.NoLegalMoves)

        // Yellow Joint Token (tokens 0 & 1 paired at progress 35, track 9) rolling 3 -> CAPTURES BOTH Red tokens!
        val jointVsJoint = singleVsJoint.copy(
            players = singleVsJoint.players.map { p ->
                if (p.color == PlayerColor.YELLOW) {
                    p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.YELLOW, 35, jointPartnerId = 1),
                            Token(1, PlayerColor.YELLOW, 35, jointPartnerId = 0),
                            Token(2, PlayerColor.YELLOW, Token.BASE_PROGRESS),
                            Token(3, PlayerColor.YELLOW, Token.BASE_PROGRESS)
                        )
                    )
                } else p
            }
        )
        val jointCapRoll = engine.applyDiceRoll(jointVsJoint, 3) as RollOutcome.SingleLegalMove
        assertEquals(2, jointCapRoll.move.capturedTokens.size)
        val afterJointCap = engine.applyMove(jointCapRoll.state, jointCapRoll.move).state
        assertTrue(afterJointCap.playerByColor(PlayerColor.RED)!!.tokens[0].isInBase)
        assertTrue(afterJointCap.playerByColor(PlayerColor.RED)!!.tokens[1].isInBase)
    }

    @Test
    fun `quick mode starts 1 token out and wins immediately when first token reaches home`() {
        val rules = RuleSetRegistry.forMode(GameModeId.QUICK, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(rules)
        val state = engine.createGame(twoPlayers(), GameModeId.QUICK, MatchFormat.ONE_VS_ONE)

        // Exactly 1 token out at progress 0, other 3 in base
        for (p in state.players) {
            assertEquals(1, p.tokens.count { it.progress == 0 })
            assertEquals(3, p.tokens.count { it.isInBase })
        }

        // Non-6 immediately moves the 1 starting token
        val roll = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        val moved = engine.applyMove(roll.state, roll.move).state
        assertEquals(3, moved.playerByColor(PlayerColor.RED)!!.tokens[0].progress)

        // First token reaching Home wins immediately!
        val nearWin = state.copy(
            players = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 54) else it })
                } else p
            }
        )
        val winRoll = engine.applyDiceRoll(nearWin, 2) as RollOutcome.SingleLegalMove
        val winOutcome = engine.applyMove(winRoll.state, winRoll.move)
        assertTrue(winOutcome.state.isMatchFinished)
        assertEquals(PlayerColor.RED, winOutcome.state.winner)
    }

    @Test
    fun `arrow mode slides exact tail landing to tip and captures opponent at tip while passing over does nothing`() {
        val rules = RuleSetRegistry.forMode(GameModeId.ARROW, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(rules)
        val base = engine.createGame(twoPlayers(), GameModeId.ARROW, MatchFormat.ONE_VS_ONE)

        // Arrow 4 -> 10. Red token 0 at progress 1. Yellow token 0 at progress 36 -> (26+36)%52 = 10!
        val setup = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
                    PlayerColor.YELLOW -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 36) else it })
                    else -> p
                }
            }
        )

        // Passing over tail 4 (roll 4 -> lands on 5) does NOT trigger arrow
        val passRoll = engine.applyDiceRoll(setup, 4) as RollOutcome.SingleLegalMove
        assertEquals(5, passRoll.move.toProgress)
        assertEquals(null, passRoll.move.arrowFromTrackIndex)

        // Landing exactly on tail 4 (roll 3 -> 4 -> slides to 10) captures Yellow at 10!
        val landRoll = engine.applyDiceRoll(setup, 3) as RollOutcome.SingleLegalMove
        assertEquals(4, landRoll.move.arrowFromTrackIndex)
        assertEquals(10, landRoll.move.arrowToTrackIndex)
        assertEquals(10, landRoll.move.toProgress)
        assertEquals(listOf(TokenRef(PlayerColor.YELLOW, 0)), landRoll.move.capturedTokens)

        val afterSlide = engine.applyMove(landRoll.state, landRoll.move)
        assertEquals(10, afterSlide.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
        assertTrue(afterSlide.state.playerByColor(PlayerColor.YELLOW)!!.tokens[0].isInBase)
        assertTrue("Capturing at arrow tip grants bonus roll", afterSlide.grantedExtraRoll)
    }

    @Test
    fun `2v2 team format prevents partner capture and lets finished player roll for partner until all 8 home`() {
        val rules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.TEAMS_2V2)
        val engine = LudoEngine(rules)
        val base = engine.createGame(fourPlayers(), GameModeId.CLASSIC, MatchFormat.TEAMS_2V2)

        val setup = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(tokens = List(4) { Token(it, PlayerColor.RED, Token.HOME_PROGRESS) })
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

        assertEquals(PlayerColor.YELLOW, engine.controlledColor(setup))
        val roll = engine.applyDiceRoll(setup, 2) as RollOutcome.SingleLegalMove
        assertEquals(PlayerColor.YELLOW, roll.move.color)
        assertTrue(roll.move.capturedTokens.isEmpty())

        val outcome = engine.applyMove(roll.state, roll.move)
        assertTrue(outcome.state.isMatchFinished)
        assertEquals("Team A (Red & Yellow)", outcome.state.winningTeamLabel)
        assertEquals(setOf(PlayerColor.RED, PlayerColor.YELLOW), outcome.state.finishOrder.take(2).toSet())
    }

    @Test
    fun `timed mode tracks move capture penalty and home scores and crowns highest scorer on clock end`() {
        val rules = RuleSetRegistry.forMode(GameModeId.TIMED, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(rules)
        val base = engine.createGame(twoPlayers(), GameModeId.TIMED, MatchFormat.ONE_VS_ONE, timedDurationSeconds = 300)

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
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 1) else t }
                    )
                    else -> p
                }
            }
        )

        // Red captures Yellow (+3 move +10 capture = +13; Yellow -5 = 5) and earns bonus roll!
        val capRoll = engine.applyDiceRoll(setup, 3) as RollOutcome.MultipleLegalMoves
        val afterCap = engine.applyMove(capRoll.state, capRoll.moves.first { it.tokenId == 0 }).state
        assertEquals(13, afterCap.playerByColor(PlayerColor.RED)!!.score)
        assertEquals(5, afterCap.playerByColor(PlayerColor.YELLOW)!!.score)

        // Bonus roll brings token 1 home (+3 move +25 home = +28 -> 41)
        val homeRoll = engine.applyDiceRoll(afterCap, 3) as RollOutcome.MultipleLegalMoves
        val afterHome = engine.applyMove(homeRoll.state, homeRoll.moves.first { it.tokenId == 1 }).state
        assertEquals(41, afterHome.playerByColor(PlayerColor.RED)!!.score)

        val expired = engine.tickTimedClock(afterHome, secondsElapsed = 300)
        assertTrue(expired.isMatchFinished)
        assertEquals(PlayerColor.RED, expired.winner)
    }

    @Test
    fun `chaos triggers event every 3 full rounds without repeating consecutively and dice storm doubles roll`() {
        val rules = RuleSetRegistry.forMode(GameModeId.CHAOS, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(rules, eventRandom = Random(7))
        var state = engine.createGame(twoPlayers(), GameModeId.CHAOS, MatchFormat.ONE_VS_ONE)
        val seenEvents = mutableListOf<ChaosEventType>()

        repeat(12) {
            val roll = engine.applyDiceRoll(state, 1) as RollOutcome.NoLegalMoves
            state = engine.advanceTurnAfterSkip(roll.state)
            val ann = state.chaosState.activeAnnouncement
            if (ann != null && seenEvents.size < state.chaosState.announcementId) {
                seenEvents += ann
            }
        }

        assertEquals(6, state.fullRoundsCompleted)
        assertEquals(2, seenEvents.size)
        assertNotEquals(seenEvents[0], seenEvents[1])

        // Verify Dice Storm doubles roll up to 12
        val stormState = engine.createGame(twoPlayers(), GameModeId.CHAOS, MatchFormat.ONE_VS_ONE).let { s ->
            s.copy(
                players = s.players.mapIndexed { idx, p ->
                    if (idx == 0) {
                        p.copy(
                            diceStormCharges = 1,
                            tokens = p.tokens.mapIndexed { tIdx, t -> if (tIdx == 0) t.copy(progress = 10) else t }
                        )
                    } else p
                }
            )
        }
        val stormRoll = engine.applyDiceRoll(stormState, 6) as RollOutcome.MultipleLegalMoves
        assertTrue(stormRoll.state.wasDiceDoubled)
        assertEquals(12, stormRoll.state.lastDiceValue)
        assertEquals(22, stormRoll.moves.first { it.tokenId == 0 }.toProgress)
        assertEquals(0, stormRoll.state.activePlayer.diceStormCharges)
    }

    @Test
    fun `elimination knocks out lowest progress player every 5 full rounds until last player stands`() {
        val rules = RuleSetRegistry.forMode(GameModeId.ELIMINATION, MatchFormat.FOUR_PLAYERS)
        val engine = LudoEngine(rules)
        var state = engine.createGame(threePlayers(), GameModeId.ELIMINATION, MatchFormat.FOUR_PLAYERS)

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

        repeat(15) {
            val roll = engine.applyDiceRoll(state, 2)
            state = when (roll) {
                is RollOutcome.NoLegalMoves -> engine.advanceTurnAfterSkip(roll.state)
                is RollOutcome.SingleLegalMove -> engine.applyMove(roll.state, roll.move).state
                is RollOutcome.MultipleLegalMoves -> engine.applyMove(roll.state, roll.moves.first()).state
                is RollOutcome.TripleSixCancelled -> engine.advanceTurnAfterSkip(roll.state)
                is RollOutcome.RollAgainStacked -> roll.state
            }
        }

        assertEquals(5, state.fullRoundsCompleted)
        assertTrue(state.playerByColor(PlayerColor.YELLOW)!!.isEliminated)
        assertFalse(state.isMatchFinished)

        repeat(10) {
            if (!state.isMatchFinished) {
                val roll = engine.applyDiceRoll(state, 1)
                state = when (roll) {
                    is RollOutcome.NoLegalMoves -> engine.advanceTurnAfterSkip(roll.state)
                    is RollOutcome.SingleLegalMove -> engine.applyMove(roll.state, roll.move).state
                    is RollOutcome.MultipleLegalMoves -> engine.applyMove(roll.state, roll.moves.first()).state
                    is RollOutcome.TripleSixCancelled -> engine.advanceTurnAfterSkip(roll.state)
                    is RollOutcome.RollAgainStacked -> roll.state
                }
            }
        }

        assertTrue(state.isMatchFinished)
        assertEquals(PlayerColor.RED, state.winner)
    }
}
