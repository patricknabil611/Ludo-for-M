package com.example.engine

import com.example.online.GameStateFirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Verifies v2.1 Yalla-Ludo roll stacking, roll tray selection, multi-value spending,
 * auto-discard of unplayable leftovers, triple-6 cancellation, and bonus roll deferral.
 */
class TwoDiceGameplayTest {

    @Test
    fun allGameModesUseSingleDieWithRollStackingEnabled() {
        for (mode in GameModeId.entries) {
            val ruleSet = RuleSetRegistry.forMode(mode)
            assertEquals("Expected single die with roll stacking for $mode", 1, ruleSet.diceCount)
            assertTrue("Expected rollStackingEnabled for $mode", ruleSet.rollStackingEnabled)
        }
    }

    @Test
    fun rollingSixThenFourStacksInTrayAndAllowsSpendingInAnyOrderAcrossTokens() {
        val rules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(ruleSet = rules, eventRandom = Random(12))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC,
            matchFormat = MatchFormat.ONE_VS_ONE
        ).let { s ->
            // Put Red tokens 0 and 1 out on track so both 6 and 4 are playable immediately
            s.copy(
                players = s.players.map { p ->
                    if (p.color == PlayerColor.RED) {
                        p.copy(
                            tokens = listOf(
                                Token(0, PlayerColor.RED, 2),
                                Token(1, PlayerColor.RED, 10),
                                Token(2, PlayerColor.RED, Token.BASE_PROGRESS),
                                Token(3, PlayerColor.RED, Token.BASE_PROGRESS)
                            )
                        )
                    } else p
                }
            )
        }

        // Roll 6 -> stacks into tray, stays AWAITING_ROLL
        val r1 = engine.applyDiceRoll(initial, 6)
        assertTrue(r1 is RollOutcome.RollAgainStacked)
        assertEquals(listOf(6), r1.state.rolledDice)
        assertEquals(TurnPhase.AWAITING_ROLL, r1.state.phase)

        // Roll 4 -> ends stack, tray = [6, 4], enters AWAITING_MOVE
        val r2 = engine.applyDiceRoll(r1.state, 4)
        assertTrue(r2 is RollOutcome.MultipleLegalMoves)
        var state = r2.state
        assertEquals(listOf(6, 4), state.rolledDice)
        assertEquals(listOf(6, 4), state.remainingDice)
        assertEquals(listOf(6, 4), state.playableDiceValues())

        // Player chooses the 4-step die first and moves token 1 by 4 steps (10 -> 14)
        state = engine.selectDieValue(state, 4)
        assertEquals(4, state.effectiveSelectedDieValue())
        assertTrue(state.legalMovesForSelectedDie().all { it.dieValueUsed == 4 })

        val moveToken1By4 = state.resolveMoveForToken(TokenRef(PlayerColor.RED, 1))!!
        assertEquals(4, moveToken1By4.dieValueUsed)
        assertEquals(14, moveToken1By4.toProgress)

        val firstMove = engine.applyMove(state, moveToken1By4)
        assertTrue(firstMove.hasRemainingDiceMoves)
        assertFalse(firstMove.grantedExtraRoll)
        state = firstMove.state

        // Still Red's turn with [6] remaining
        assertEquals(PlayerColor.RED, state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_MOVE, state.phase)
        assertEquals(listOf(6), state.remainingDice)

        // Player uses the 6 to bring token 2 out of base
        val moveToken2Exit = state.resolveMoveForToken(TokenRef(PlayerColor.RED, 2))!!
        assertEquals(6, moveToken2Exit.dieValueUsed)
        assertTrue(moveToken2Exit.leavesBase)

        val secondMove = engine.applyMove(state, moveToken2Exit)
        assertFalse(secondMove.hasRemainingDiceMoves)
        assertFalse(secondMove.grantedExtraRoll)

        // Tray fully spent with no capture/home -> turn passes to Yellow
        val finalState = secondMove.state
        assertEquals(PlayerColor.YELLOW, finalState.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, finalState.phase)
        assertTrue(finalState.remainingDice.isEmpty())
        val redTokens = finalState.playerByColor(PlayerColor.RED)!!.tokens
        assertEquals(2, redTokens[0].progress)
        assertEquals(14, redTokens[1].progress)
        assertEquals(0, redTokens[2].progress)
    }

    @Test
    fun rollingSixSixThenThreeFromBaseAllowsSequentialSpendingAndConsumesOneSixPerMove() {
        val rules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(ruleSet = rules, eventRandom = Random(5))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC,
            matchFormat = MatchFormat.ONE_VS_ONE
        )

        // Roll 6 -> 6 -> 3 -> tray is [6, 6, 3]
        val s1 = (engine.applyDiceRoll(initial, 6) as RollOutcome.RollAgainStacked).state
        val s2 = (engine.applyDiceRoll(s1, 6) as RollOutcome.RollAgainStacked).state
        val s3 = (engine.applyDiceRoll(s2, 3) as RollOutcome.MultipleLegalMoves).state

        assertEquals(listOf(6, 6, 3), s3.rolledDice)
        assertEquals(listOf(6), s3.playableDiceValues()) // all tokens in base -> only 6 is playable

        // Spend first 6: token 0 exits base
        val afterExit = engine.applyMove(s3, s3.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!)
        assertTrue(afterExit.hasRemainingDiceMoves)
        assertEquals(listOf(6, 3), afterExit.state.remainingDice)

        // Spend 3 on token 0 (0 -> 3)
        val select3 = engine.selectDieValue(afterExit.state, 3)
        val afterThree = engine.applyMove(select3, select3.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!)
        assertTrue(afterThree.hasRemainingDiceMoves)
        assertEquals(listOf(6), afterThree.state.remainingDice)
        assertEquals(3, afterThree.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)

        // Spend final 6 on token 0 (3 -> 9) -> turn passes to Yellow
        val afterSecondSix = engine.applyMove(afterThree.state, afterThree.state.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!)
        assertFalse(afterSecondSix.hasRemainingDiceMoves)
        assertEquals(9, afterSecondSix.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
        assertEquals(PlayerColor.YELLOW, afterSecondSix.state.activePlayer.color)
    }

    @Test
    fun threeSixesInARowDiscardsEntireTrayAndCancelsTurn() {
        val rules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(ruleSet = rules, eventRandom = Random(7))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC,
            matchFormat = MatchFormat.ONE_VS_ONE
        )

        val r1 = engine.applyDiceRoll(initial, 6) as RollOutcome.RollAgainStacked
        val r2 = engine.applyDiceRoll(r1.state, 6) as RollOutcome.RollAgainStacked
        val r3 = engine.applyDiceRoll(r2.state, 6)

        assertTrue(r3 is RollOutcome.TripleSixCancelled)
        assertEquals(TurnPhase.TURN_CANCELLED_TRIPLE_SIX, r3.state.phase)
        assertTrue(r3.state.rolledDice.isEmpty())
        assertTrue(r3.state.remainingDice.isEmpty())
        assertEquals("Three 6s — turn lost", r3.state.lastEventMessage)

        val next = engine.advanceTurnAfterSkip(r3.state)
        assertEquals(PlayerColor.YELLOW, next.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, next.phase)
    }

    @Test
    fun bonusRollFromCaptureIsGrantedOnlyAfterCurrentTrayIsFullySpent() {
        val rules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(ruleSet = rules, eventRandom = Random(9))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC,
            matchFormat = MatchFormat.ONE_VS_ONE
        ).let { s ->
            // Red token 0 at progress 24 -> +3 lands on track 27 where Yellow token 0 sits (Yellow progress 1 -> track 27)
            s.copy(
                players = s.players.map { p ->
                    when (p.color) {
                        PlayerColor.RED -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 24) else it })
                        PlayerColor.YELLOW -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
                        else -> p
                    }
                }
            )
        }

        // Roll 6 then 3 -> tray [6, 3]
        val s1 = (engine.applyDiceRoll(initial, 6) as RollOutcome.RollAgainStacked).state
        val s2 = (engine.applyDiceRoll(s1, 3) as RollOutcome.MultipleLegalMoves).state

        // Spend 3 first to capture Yellow! Bonus roll is queued, NOT fired until 6 is also spent
        val pick3 = engine.selectDieValue(s2, 3)
        val capOutcome = engine.applyMove(pick3, pick3.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!)
        assertTrue(capOutcome.move.capturedTokens.isNotEmpty())
        assertTrue("Remaining 6 must still be spent before bonus roll", capOutcome.hasRemainingDiceMoves)
        assertFalse(capOutcome.grantedExtraRoll)
        assertEquals(1, capOutcome.state.pendingBonusRolls)

        // Now spend the remaining 6 -> tray empty -> queued bonus roll fires!
        val spend6 = engine.applyMove(capOutcome.state, capOutcome.state.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!)
        assertFalse(spend6.hasRemainingDiceMoves)
        assertTrue("Bonus roll must fire after tray is spent", spend6.grantedExtraRoll)
        assertEquals(PlayerColor.RED, spend6.state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, spend6.state.phase)
    }

    @Test
    fun firestoreMapperRoundTripsStackedRollTrayAndSelectedDie() {
        val rules = RuleSetRegistry.forMode(GameModeId.QUICK, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(ruleSet = rules, eventRandom = Random(19))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK,
            matchFormat = MatchFormat.ONE_VS_ONE
        )

        val r1 = (engine.applyDiceRoll(initial, 6) as RollOutcome.RollAgainStacked).state
        val r2 = (engine.applyDiceRoll(r1, 2) as RollOutcome.MultipleLegalMoves).state
        val selected = engine.selectDieValue(r2, 2)

        val serialized = GameStateFirestoreMapper.toFirestoreMap(selected)
        val restored = GameStateFirestoreMapper.fromFirestoreMap(serialized)!!

        assertEquals(listOf(6, 2), restored.rolledDice)
        assertEquals(listOf(6, 2), restored.remainingDice)
        assertEquals(2, restored.selectedDieValue)
        assertEquals(listOf(6, 2), restored.playableDiceValues())
        assertTrue(restored.legalMovesForSelectedDie().all { it.dieValueUsed == 2 })
    }
}
