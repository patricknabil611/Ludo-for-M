package com.example.ui.game

import androidx.compose.ui.graphics.Color
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.PlayerColor
import com.example.engine.PlayerSetup
import com.example.engine.PlayerState
import com.example.engine.Token
import com.example.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllTokensHomeCelebrationTest {

    private fun playerWithHomeCount(
        seatIndex: Int,
        name: String,
        color: PlayerColor,
        homeCount: Int
    ): PlayerState {
        val tokens = List(4) { idx ->
            Token(
                id = idx,
                color = color,
                progress = if (idx < homeCount) Token.HOME_PROGRESS else 12 + idx
            )
        }
        return PlayerState(
            setup = PlayerSetup(seatIndex = seatIndex, nickname = name, color = color),
            tokens = tokens
        )
    }

    @Test
    fun detectAllFourTokensHome_returnsNullForFirstSecondAndThirdTokenHome() {
        for (prevHome in 0..2) {
            val prev = GameState(
                players = listOf(
                    playerWithHomeCount(0, "Maya", PlayerColor.RED, prevHome),
                    playerWithHomeCount(1, "Leo", PlayerColor.YELLOW, 0)
                )
            )
            val next = GameState(
                players = listOf(
                    playerWithHomeCount(0, "Maya", PlayerColor.RED, prevHome + 1),
                    playerWithHomeCount(1, "Leo", PlayerColor.YELLOW, 0)
                )
            )
            assertNull(
                "Expected no full-home celebration when moving token #${prevHome + 1} home",
                CelebrationSequenceHelper.detectAllFourTokensHome(prev, next, nextTriggerKey = 1)
            )
        }
    }

    @Test
    fun detectAllFourTokensHome_triggersOnFourthTokenInTwoPlayerMatch() {
        val prev = GameState(
            players = listOf(
                playerWithHomeCount(0, "Maya", PlayerColor.RED, 3),
                playerWithHomeCount(1, "Leo", PlayerColor.YELLOW, 1)
            ),
            phase = TurnPhase.AWAITING_MOVE
        )
        val next = GameState(
            players = listOf(
                playerWithHomeCount(0, "Maya", PlayerColor.RED, 4),
                playerWithHomeCount(1, "Leo", PlayerColor.YELLOW, 1)
            ),
            phase = TurnPhase.MATCH_FINISHED,
            finishOrder = listOf(PlayerColor.RED, PlayerColor.YELLOW)
        )

        val celebration = CelebrationSequenceHelper.detectAllFourTokensHome(prev, next, nextTriggerKey = 7)
        assertNotNull(celebration)
        assertEquals("Maya", celebration!!.playerName)
        assertEquals(PlayerColor.RED, celebration.color)
        assertEquals(1, celebration.rank)
        assertTrue(celebration.isMatchOver)
        assertFalse(celebration.isTeamsPartnerContinuation)
        assertEquals(7, celebration.triggerKey)
    }

    @Test
    fun detectAllFourTokensHome_tracksMultiPlayerRanksWithoutRetriggering() {
        val stateBeforeFirst = GameState(
            players = listOf(
                playerWithHomeCount(0, "Red", PlayerColor.RED, 3),
                playerWithHomeCount(1, "Green", PlayerColor.GREEN, 3),
                playerWithHomeCount(2, "Yellow", PlayerColor.YELLOW, 1),
                playerWithHomeCount(3, "Blue", PlayerColor.BLUE, 0)
            )
        )
        val stateAfterFirst = GameState(
            players = listOf(
                playerWithHomeCount(0, "Red", PlayerColor.RED, 4),
                playerWithHomeCount(1, "Green", PlayerColor.GREEN, 3),
                playerWithHomeCount(2, "Yellow", PlayerColor.YELLOW, 1),
                playerWithHomeCount(3, "Blue", PlayerColor.BLUE, 0)
            ),
            finishOrder = listOf(PlayerColor.RED)
        )

        val firstCelebration = CelebrationSequenceHelper.detectAllFourTokensHome(
            prevState = stateBeforeFirst,
            nextState = stateAfterFirst,
            nextTriggerKey = 1
        )
        assertNotNull(firstCelebration)
        assertEquals(PlayerColor.RED, firstCelebration!!.color)
        assertEquals(1, firstCelebration.rank)
        assertFalse(firstCelebration.isMatchOver)

        // Next move by Yellow does not retrigger Red's celebration
        val stateYellowMoved = stateAfterFirst.copy(
            players = stateAfterFirst.players.map {
                if (it.color == PlayerColor.YELLOW) playerWithHomeCount(2, "Yellow", PlayerColor.YELLOW, 2) else it
            }
        )
        assertNull(
            CelebrationSequenceHelper.detectAllFourTokensHome(stateAfterFirst, stateYellowMoved, 2)
        )

        // Green finishes 2nd while match continues for 3rd
        val stateAfterSecond = stateYellowMoved.copy(
            players = stateYellowMoved.players.map {
                if (it.color == PlayerColor.GREEN) playerWithHomeCount(1, "Green", PlayerColor.GREEN, 4) else it
            },
            finishOrder = listOf(PlayerColor.RED, PlayerColor.GREEN)
        )
        val secondCelebration = CelebrationSequenceHelper.detectAllFourTokensHome(
            prevState = stateYellowMoved,
            nextState = stateAfterSecond,
            nextTriggerKey = 2
        )
        assertNotNull(secondCelebration)
        assertEquals(PlayerColor.GREEN, secondCelebration!!.color)
        assertEquals(2, secondCelebration.rank)
        assertFalse(secondCelebration.isMatchOver)
    }

    @Test
    fun detectAllFourTokensHome_handlesTeams2v2PartnerContinuationAndTeamVictory() {
        val beforeFirstTeammate = GameState(
            mode = GameModeId.TEAMS_2V2,
            players = listOf(
                playerWithHomeCount(0, "Red", PlayerColor.RED, 3),
                playerWithHomeCount(1, "Green", PlayerColor.GREEN, 1),
                playerWithHomeCount(2, "Yellow", PlayerColor.YELLOW, 3),
                playerWithHomeCount(3, "Blue", PlayerColor.BLUE, 0)
            )
        )
        val afterFirstTeammate = beforeFirstTeammate.copy(
            players = listOf(
                playerWithHomeCount(0, "Red", PlayerColor.RED, 4),
                playerWithHomeCount(1, "Green", PlayerColor.GREEN, 1),
                playerWithHomeCount(2, "Yellow", PlayerColor.YELLOW, 3),
                playerWithHomeCount(3, "Blue", PlayerColor.BLUE, 0)
            ),
            finishOrder = listOf(PlayerColor.RED)
        )

        val first = CelebrationSequenceHelper.detectAllFourTokensHome(
            prevState = beforeFirstTeammate,
            nextState = afterFirstTeammate,
            nextTriggerKey = 1
        )
        assertNotNull(first)
        assertTrue(first!!.isTeamsPartnerContinuation)
        assertFalse(first.isMatchOver)

        val afterSecondTeammate = afterFirstTeammate.copy(
            players = listOf(
                playerWithHomeCount(0, "Red", PlayerColor.RED, 4),
                playerWithHomeCount(1, "Green", PlayerColor.GREEN, 1),
                playerWithHomeCount(2, "Yellow", PlayerColor.YELLOW, 4),
                playerWithHomeCount(3, "Blue", PlayerColor.BLUE, 0)
            ),
            phase = TurnPhase.MATCH_FINISHED,
            finishOrder = listOf(PlayerColor.RED, PlayerColor.YELLOW, PlayerColor.GREEN, PlayerColor.BLUE),
            winningTeamLabel = "Team Red & Yellow"
        )
        val teamWin = CelebrationSequenceHelper.detectAllFourTokensHome(
            prevState = afterFirstTeammate,
            nextState = afterSecondTeammate,
            nextTriggerKey = 2
        )
        assertNotNull(teamWin)
        assertTrue(teamWin!!.isMatchOver)
        assertFalse(teamWin.isTeamsPartnerContinuation)
        assertEquals("Team Red & Yellow", teamWin.teamLabel)
    }

    @Test
    fun tokenRevealProgress_staggersFourTokensSequentially() {
        assertEquals(0f, CelebrationSequenceHelper.tokenRevealProgress(elapsedMs = 0L, tokenIndex = 0), 0.0001f)
        val midFirst = CelebrationSequenceHelper.FIRST_TOKEN_REVEAL_DELAY_MS + 100L
        assertTrue(CelebrationSequenceHelper.tokenRevealProgress(midFirst, tokenIndex = 0) > 0f)
        assertEquals(0f, CelebrationSequenceHelper.tokenRevealProgress(midFirst, tokenIndex = 1), 0.0001f)

        val afterAllMs = CelebrationSequenceHelper.FIRST_TOKEN_REVEAL_DELAY_MS +
            3 * CelebrationSequenceHelper.TOKEN_REVEAL_STAGGER_MS +
            CelebrationSequenceHelper.TOKEN_REVEAL_DURATION_MS + 50L
        for (idx in 0..3) {
            assertEquals(1f, CelebrationSequenceHelper.tokenRevealProgress(afterAllMs, idx), 0.0001f)
        }
    }

    @Test
    fun generateCannonParticles_isDeterministicAndIncludesAllShapes() {
        val firstRun = CelebrationSequenceHelper.generateCannonParticles(seed = 12, accentColor = Color.Red, count = 96)
        val secondRun = CelebrationSequenceHelper.generateCannonParticles(seed = 12, accentColor = Color.Red, count = 96)
        assertEquals(96, firstRun.size)
        assertEquals(firstRun, secondRun)
        assertEquals(CelebrationParticleShape.entries.toSet(), firstRun.map { it.shape }.toSet())
        assertTrue(firstRun.any { it.originXFraction < 0.1f && it.velocityX > 0f })
        assertTrue(firstRun.any { it.originXFraction > 0.9f && it.velocityX < 0f })
    }
}
