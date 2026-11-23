package com.example.ui.game

import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LudoEngine
import com.example.engine.MatchFormat
import com.example.engine.PlayerColor
import com.example.engine.PlayerControllerType
import com.example.engine.PlayerSetup
import com.example.engine.RuleSetRegistry
import com.example.engine.Token
import com.example.engine.TokenRef
import com.example.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenMoveOptionResolverTest {

    private fun twoPlayerSetups() = listOf(
        PlayerSetup(
            seatIndex = 0,
            nickname = "Red",
            color = PlayerColor.RED,
            controllerType = PlayerControllerType.HUMAN_LOCAL
        ),
        PlayerSetup(
            seatIndex = 1,
            nickname = "Yellow",
            color = PlayerColor.YELLOW,
            controllerType = PlayerControllerType.HUMAN_LOCAL
        )
    )

    private fun stateWithTray(
        mode: GameModeId = GameModeId.CLASSIC,
        redTokens: List<Token>,
        tray: List<Int>
    ): GameState {
        val ruleSet = RuleSetRegistry.forMode(mode, MatchFormat.ONE_VS_ONE)
        val engine = LudoEngine(ruleSet)
        var state = engine.createGame(
            playerSetups = twoPlayerSetups(),
            mode = mode,
            matchFormat = MatchFormat.ONE_VS_ONE
        )
        state = state.copy(
            players = state.players.map { player ->
                if (player.color == PlayerColor.RED) player.copy(tokens = redTokens) else player
            }
        )
        for (die in tray) {
            state = engine.applyDiceRoll(state, die).state
        }
        return state
    }

    @Test
    fun singleUsableTrayValue_returnsSingleOptionForImmediateMove() {
        val tokens = listOf(
            Token(id = 0, color = PlayerColor.RED, progress = 5),
            Token(id = 1, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 2, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 3, color = PlayerColor.RED, progress = Token.BASE_PROGRESS)
        )
        val state = stateWithTray(redTokens = tokens, tray = listOf(4))
        assertEquals(TurnPhase.AWAITING_MOVE, state.phase)

        val options = TokenMoveOptionResolver.optionsForToken(state, TokenRef(PlayerColor.RED, 0))
        assertEquals(1, options.size)
        assertEquals(4, options.first().totalSteps)
        assertFalse(options.first().isCombined)
    }

    @Test
    fun multipleUsableTrayValues_includesCombinedSequentialOptionWhenLegal() {
        val tokens = listOf(
            Token(id = 0, color = PlayerColor.RED, progress = 5),
            Token(id = 1, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 2, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 3, color = PlayerColor.RED, progress = Token.BASE_PROGRESS)
        )
        val state = stateWithTray(redTokens = tokens, tray = listOf(6, 3))
        assertEquals(TurnPhase.AWAITING_MOVE, state.phase)

        val options = TokenMoveOptionResolver.optionsForToken(state, TokenRef(PlayerColor.RED, 0))
        val totals = options.map { it.totalSteps }
        assertTrue("Expected single [6] option in $totals", 6 in totals)
        assertTrue("Expected single [3] option in $totals", 3 in totals)
        assertTrue("Expected combined [9] option in $totals", 9 in totals)

        val combined = options.first { it.totalSteps == 9 }
        assertTrue(combined.isCombined)
        assertEquals(2, combined.moves.size)
        assertEquals(listOf(6, 3), combined.moves.map { it.dieValueUsed })
    }

    @Test
    fun multipleUsableTrayValues_omitsCombinedOptionWhenOvershootingHome() {
        // HOME_PROGRESS is 56. Progress 48 is 8 steps from Home:
        // 6 -> 54 (legal), 3 -> 51 (legal), 6+3=9 -> 57 (overshoots -> NOT legal!)
        val tokens = listOf(
            Token(id = 0, color = PlayerColor.RED, progress = 48),
            Token(id = 1, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 2, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 3, color = PlayerColor.RED, progress = Token.BASE_PROGRESS)
        )
        val state = stateWithTray(redTokens = tokens, tray = listOf(6, 3))
        assertEquals(TurnPhase.AWAITING_MOVE, state.phase)

        val options = TokenMoveOptionResolver.optionsForToken(state, TokenRef(PlayerColor.RED, 0))
        val totals = options.map { it.totalSteps }
        assertEquals(listOf(6, 3), totals)
        assertTrue(options.none { it.isCombined })
    }

    @Test
    fun findAutoMove_triggersWhenOnlyOneLegalMoveExistsForWholeTray() {
        val tokens = listOf(
            Token(id = 0, color = PlayerColor.RED, progress = 10),
            Token(id = 1, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 2, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 3, color = PlayerColor.RED, progress = Token.BASE_PROGRESS)
        )
        val state = stateWithTray(redTokens = tokens, tray = listOf(4))
        val auto = TokenMoveOptionResolver.findAutoMove(state)
        assertNotNull(auto)
        assertEquals(0, auto!!.tokenId)
        assertEquals(4, auto.dieValueUsed)
    }

    @Test
    fun findAutoMove_triggersWhenAllTokensInBaseAndSixRolled() {
        val tokens = listOf(
            Token(id = 0, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 1, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 2, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 3, color = PlayerColor.RED, progress = Token.BASE_PROGRESS)
        )
        val state = stateWithTray(redTokens = tokens, tray = listOf(6, 2))
        val auto = TokenMoveOptionResolver.findAutoMove(state)
        assertNotNull("All tokens in base with a 6 should auto-exit a token", auto)
        assertTrue(auto!!.leavesBase)
        assertEquals(6, auto.dieValueUsed)
    }

    @Test
    fun findAutoMove_returnsNullWhenPlayerHasChoiceBetweenTokens() {
        val tokens = listOf(
            Token(id = 0, color = PlayerColor.RED, progress = 5),
            Token(id = 1, color = PlayerColor.RED, progress = 12),
            Token(id = 2, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 3, color = PlayerColor.RED, progress = Token.BASE_PROGRESS)
        )
        val state = stateWithTray(redTokens = tokens, tray = listOf(4))
        assertNull(TokenMoveOptionResolver.findAutoMove(state))
    }

    @Test
    fun masterJointToken_resolvesAsSingleUnitOnEitherMemberTap() {
        val tokens = listOf(
            Token(id = 0, color = PlayerColor.RED, progress = 10, jointPartnerId = 1),
            Token(id = 1, color = PlayerColor.RED, progress = 10, jointPartnerId = 0),
            Token(id = 2, color = PlayerColor.RED, progress = Token.BASE_PROGRESS),
            Token(id = 3, color = PlayerColor.RED, progress = Token.BASE_PROGRESS)
        )
        val state = stateWithTray(mode = GameModeId.MASTER, redTokens = tokens, tray = listOf(4))
        val optionsFor0 = TokenMoveOptionResolver.optionsForToken(state, TokenRef(PlayerColor.RED, 0))
        val optionsFor1 = TokenMoveOptionResolver.optionsForToken(state, TokenRef(PlayerColor.RED, 1))
        assertEquals(1, optionsFor0.size)
        assertEquals(1, optionsFor1.size)
        assertTrue(optionsFor0.first().moves.first().isJointMove)
        assertEquals(2, optionsFor0.first().moves.first().tokenIds.size)
        assertEquals(optionsFor0.first().moves.first().tokenIds, optionsFor1.first().moves.first().tokenIds)
    }
}
