package com.example.engine

import com.example.online.GameStateFirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TwoDiceGameplayTest {

    @Test
    fun allGameModesDefaultToTwoDice() {
        for (mode in GameModeId.entries) {
            val ruleSet = RuleSetRegistry.forMode(mode)
            assertEquals("Expected 2 dice by default for $mode", 2, ruleSet.diceCount)
        }
    }

    @Test
    fun playerCanChooseWhichDieNumberAndWhichTokenToMoveAcrossTwoMoves() {
        val engine = LudoEngine(ruleSet = QuickRuleSet, eventRandom = Random(12))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK
        )

        // Roll 5 and 3 -> both dice are playable on all 4 tokens on start square
        val rollOutcome = engine.applyDiceRoll(initial, 5, 3, actingColor = PlayerColor.RED)
        assertTrue(rollOutcome is RollOutcome.MultipleLegalMoves)
        var state = rollOutcome.state

        assertEquals(listOf(5, 3), state.rolledDice)
        assertEquals(listOf(5, 3), state.remainingDice)
        assertEquals(listOf(5, 3), state.playableDiceValues())
        assertEquals(5, state.effectiveSelectedDieValue())

        // Player chooses the 3-step die first and moves token 1 by 3 steps
        state = engine.selectDieValue(state, 3)
        assertEquals(3, state.effectiveSelectedDieValue())
        assertTrue(state.legalMovesForSelectedDie().all { it.dieValueUsed == 3 })

        val moveToken1By3 = state.resolveMoveForToken(TokenRef(PlayerColor.RED, 1))!!
        assertEquals(3, moveToken1By3.dieValueUsed)
        assertEquals(3, moveToken1By3.toProgress)

        val firstMoveOutcome = engine.applyMove(state, moveToken1By3, actingColor = PlayerColor.RED)
        assertTrue(firstMoveOutcome.hasRemainingDiceMoves)
        assertFalse(firstMoveOutcome.grantedExtraRoll)
        state = firstMoveOutcome.state

        // Still Red's turn with only the 5-step die remaining
        assertEquals(PlayerColor.RED, state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_MOVE, state.phase)
        assertEquals(listOf(5), state.remainingDice)
        assertEquals(5, state.effectiveSelectedDieValue())

        // Player moves token 2 by 5 steps
        val moveToken2By5 = state.resolveMoveForToken(TokenRef(PlayerColor.RED, 2))!!
        assertEquals(5, moveToken2By5.dieValueUsed)
        assertEquals(5, moveToken2By5.toProgress)

        val secondMoveOutcome = engine.applyMove(state, moveToken2By5, actingColor = PlayerColor.RED)
        assertFalse(secondMoveOutcome.hasRemainingDiceMoves)
        assertFalse(secondMoveOutcome.grantedExtraRoll)

        // Turn advances to Yellow and both tokens kept their distinct movements
        val finalState = secondMoveOutcome.state
        assertEquals(PlayerColor.YELLOW, finalState.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, finalState.phase)
        assertTrue(finalState.remainingDice.isEmpty())
        val redTokens = finalState.playerByColor(PlayerColor.RED)!!.tokens
        assertEquals(0, redTokens[0].progress)
        assertEquals(3, redTokens[1].progress)
        assertEquals(5, redTokens[2].progress)
        assertEquals(0, redTokens[3].progress)
    }

    @Test
    fun rollingSixAndFourFromBaseExitsWithSixThenMovesFourAndGrantsBonusRoll() {
        val engine = LudoEngine(ruleSet = ClassicRuleSet, eventRandom = Random(5))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC
        )

        // All tokens in base: roll 6 and 4 -> only 6 is initially playable
        val roll = engine.applyDiceRoll(initial, 6, 4, actingColor = PlayerColor.RED)
        assertTrue(roll is RollOutcome.MultipleLegalMoves)
        var state = roll.state
        assertEquals(listOf(6), state.playableDiceValues())
        assertEquals(6, state.effectiveSelectedDieValue())

        // Exit base with token 0 using the 6
        val exitMove = state.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!
        assertEquals(6, exitMove.dieValueUsed)
        assertTrue(exitMove.leavesBase)

        val afterExit = engine.applyMove(state, exitMove, actingColor = PlayerColor.RED)
        assertTrue(afterExit.hasRemainingDiceMoves)
        state = afterExit.state

        // Now token 0 is on start square (progress 0) so the remaining 4 becomes playable!
        assertEquals(listOf(4), state.remainingDice)
        assertEquals(1, state.legalMoves.size)
        assertEquals(4, state.legalMoves.first().dieValueUsed)
        assertEquals(0, state.legalMoves.first().tokenId)

        // Move token 0 forward 4 steps -> both dice spent, and rolling 6 grants a bonus roll!
        val afterFour = engine.applyMove(state, state.legalMoves.first(), actingColor = PlayerColor.RED)
        assertFalse(afterFour.hasRemainingDiceMoves)
        assertTrue(afterFour.grantedExtraRoll)
        assertEquals(PlayerColor.RED, afterFour.state.activePlayer.color)
        assertEquals(TurnPhase.AWAITING_ROLL, afterFour.state.phase)
        assertEquals(4, afterFour.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
    }

    @Test
    fun rollingDoublesConsumesOnlyOneDiePerMove() {
        val engine = LudoEngine(ruleSet = QuickRuleSet, eventRandom = Random(3))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK
        )

        val rolled = engine.applyDiceRoll(initial, 4, 4, actingColor = PlayerColor.RED).state
        assertEquals(listOf(4, 4), rolled.remainingDice)

        // Move token 0 by first 4 -> second 4 remains!
        val first = engine.applyMove(rolled, rolled.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!)
        assertTrue(first.hasRemainingDiceMoves)
        assertEquals(listOf(4), first.state.remainingDice)
        assertEquals(4, first.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)

        // Move same token 0 by second 4 -> reaches progress 8 and passes turn to Yellow!
        val second = engine.applyMove(first.state, first.state.resolveMoveForToken(TokenRef(PlayerColor.RED, 0))!!)
        assertFalse(second.hasRemainingDiceMoves)
        assertEquals(8, second.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
        assertEquals(PlayerColor.YELLOW, second.state.activePlayer.color)
    }

    @Test
    fun firestoreMapperRoundTripsTwoDiceStateAndLegalMoves() {
        val engine = LudoEngine(ruleSet = QuickRuleSet, eventRandom = Random(19))
        val initial = engine.createGame(
            playerSetups = listOf(
                PlayerSetup(0, "Red", PlayerColor.RED),
                PlayerSetup(2, "Yellow", PlayerColor.YELLOW)
            ),
            mode = GameModeId.QUICK
        )

        val rolled = engine.applyDiceRoll(initial, 5, 2).state
        val selected = engine.selectDieValue(rolled, 2)

        val serialized = GameStateFirestoreMapper.toFirestoreMap(selected)
        val restored = GameStateFirestoreMapper.fromFirestoreMap(serialized)!!

        assertEquals(listOf(5, 2), restored.rolledDice)
        assertEquals(listOf(5, 2), restored.remainingDice)
        assertEquals(2, restored.selectedDieValue)
        assertEquals(listOf(5, 2), restored.playableDiceValues())
        assertEquals(4, restored.legalMovesForSelectedDie().size)
        assertTrue(restored.legalMovesForSelectedDie().all { it.dieValueUsed == 2 })
    }
}
