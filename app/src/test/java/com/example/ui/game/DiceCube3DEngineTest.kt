package com.example.ui.game

import com.example.engine.GameModeId
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.engine.PlayerControllerType
import com.example.engine.PlayerSetup
import com.example.engine.RollOutcome
import com.example.engine.RuleSetRegistry
import com.example.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceCube3DEngineTest {

    @Test
    fun rollRandomFace_alwaysProducesValuesBetween1And6() {
        val random = Random(42)
        val seen = mutableSetOf<Int>()
        repeat(240) {
            val rolled = DiceCube3DEngine.rollRandomFace(random)
            assertTrue("Rolled value $rolled out of 1..6 range", rolled in 1..6)
            seen += rolled
        }
        assertEquals(setOf(1, 2, 3, 4, 5, 6), seen)
    }

    @Test
    fun targetAnglesForFace_orientEveryFace1To6DirectlyForward() {
        for (face in 1..6) {
            val angles = DiceCube3DEngine.targetAnglesForFace(face)
            val front = DiceCube3DEngine.frontFacingValue(
                rotXDeg = angles.rotX,
                rotYDeg = angles.rotY,
                rotZDeg = 0f
            )
            assertEquals("Face $face should orient forward for angles $angles", face, front)
        }
    }

    @Test
    fun standardDieGeometry_oppositeFacesSumToSevenAndHaveCorrectPipCounts() {
        val byValue = DiceCube3DEngine.faces.associateBy { it.value }
        assertEquals(6, byValue.size)
        for (value in 1..6) {
            val face = byValue.getValue(value)
            val opposite = byValue.getValue(7 - value)
            assertEquals(value, face.pips.size)
            assertEquals(-face.normal.x, opposite.normal.x, 0.001f)
            assertEquals(-face.normal.y, opposite.normal.y, 0.001f)
            assertEquals(-face.normal.z, opposite.normal.z, 0.001f)
        }
    }

    @Test
    fun rolledDiceValues_updateGameStateAccordingly() {
        val setups = listOf(
            PlayerSetup(0, "Red", PlayerColor.RED, PlayerControllerType.HUMAN_LOCAL),
            PlayerSetup(1, "Yellow", PlayerColor.YELLOW, PlayerControllerType.HUMAN_LOCAL)
        )
        val engine = LudoEngine(RuleSetRegistry.forMode(GameModeId.CLASSIC), Random(1))
        val initial = engine.createGame(setups, GameModeId.CLASSIC)

        // Rolling a non-6 with all tokens in base yields NoLegalMoves and updates lastDiceValue
        val nonSix = engine.applyDiceRoll(initial, 3)
        assertTrue(nonSix is RollOutcome.NoLegalMoves)
        assertEquals(3, nonSix.state.lastDiceValue)
        assertEquals(TurnPhase.NO_LEGAL_MOVES, nonSix.state.phase)

        // Rolling a 6 stacks into the roll tray and prompts an immediate bonus roll
        val rolledSix = engine.applyDiceRoll(initial, 6)
        assertTrue(rolledSix is RollOutcome.RollAgainStacked)
        assertEquals(6, rolledSix.state.lastDiceValue)
        assertEquals(listOf(6), rolledSix.state.rolledDice)
        assertEquals(TurnPhase.AWAITING_ROLL, rolledSix.state.phase)

        // Following non-6 ends the stack and unlocks all 4 base tokens to enter start square via the 6
        val rolledFollowUp = engine.applyDiceRoll(rolledSix.state, 2)
        assertTrue(rolledFollowUp is RollOutcome.MultipleLegalMoves)
        assertEquals(listOf(6, 2), rolledFollowUp.state.rolledDice)
        assertEquals(TurnPhase.AWAITING_MOVE, rolledFollowUp.state.phase)
        assertEquals(4, rolledFollowUp.state.legalMoves.size)
    }
}
