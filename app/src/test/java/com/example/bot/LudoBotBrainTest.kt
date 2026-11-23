package com.example.bot

import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.LudoEngine
import com.example.engine.MatchFormat
import com.example.engine.PlayerColor
import com.example.engine.PlayerControllerType
import com.example.engine.PlayerSetup
import com.example.engine.RollOutcome
import com.example.engine.RuleSetRegistry
import com.example.engine.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LudoBotBrainTest {

    private val classicRules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
    private val engine = LudoEngine(classicRules)
    private val botBrain = LudoBotBrain(random = Random(123))

    @Test
    fun `seat planner auto-fills empty seats with bots according to match format`() {
        val seats = listOf(
            SeatConfig(0, SeatOccupancy.HUMAN, "Maya", PlayerColor.RED),
            SeatConfig(1, SeatOccupancy.EMPTY, "Empty 2", PlayerColor.GREEN),
            SeatConfig(2, SeatOccupancy.EMPTY, "Empty 3", PlayerColor.YELLOW),
            SeatConfig(3, SeatOccupancy.EMPTY, "Empty 4", PlayerColor.BLUE)
        )

        val fourPlayer = MatchSeatPlanner.buildMatchSetups(seats, MatchFormat.FOUR_PLAYERS)
        assertEquals(4, fourPlayer.size)
        assertEquals(1, fourPlayer.count { it.controllerType == PlayerControllerType.HUMAN_LOCAL })
        assertEquals(3, fourPlayer.count { it.controllerType == PlayerControllerType.BOT })

        val oneVsOne = MatchSeatPlanner.buildMatchSetups(seats, MatchFormat.ONE_VS_ONE)
        assertEquals(2, oneVsOne.size)
        assertEquals(PlayerColor.RED, oneVsOne[0].color)
        assertEquals(PlayerColor.YELLOW, oneVsOne[1].color) // opposite corners
        assertTrue(oneVsOne[1].isBot)
    }

    @Test
    fun `seat planner preserves explicit bot difficulties`() {
        val seats = listOf(
            SeatConfig(0, SeatOccupancy.HUMAN, "Maya", PlayerColor.RED),
            SeatConfig(1, SeatOccupancy.BOT, "Brainy", PlayerColor.YELLOW, BotDifficulty.HARD),
            SeatConfig(2, SeatOccupancy.BOT, "Lucky", PlayerColor.GREEN, BotDifficulty.EASY),
            SeatConfig(3, SeatOccupancy.EMPTY, "Empty", PlayerColor.BLUE)
        )

        val setups = MatchSeatPlanner.buildMatchSetups(seats, targetPlayerCount = 3)
        assertEquals(3, setups.size)
        assertEquals(BotDifficulty.HARD, setups.first { it.nickname == "Brainy" }.botDifficulty)
        assertEquals(BotDifficulty.EASY, setups.first { it.nickname == "Lucky" }.botDifficulty)
    }

    @Test
    fun `easy bot always returns a valid legal move from stacked tray`() {
        val state = engine.createGame(
            listOf(
                PlayerSetup(0, "Bot", PlayerColor.RED, PlayerControllerType.BOT, BotDifficulty.EASY),
                PlayerSetup(1, "Opp", PlayerColor.YELLOW)
            ),
            mode = GameModeId.CLASSIC,
            matchFormat = MatchFormat.ONE_VS_ONE
        )
        // Roll 6 (stacks), then roll 2 -> tray [6, 2], all 4 tokens can exit base with 6
        val r1 = engine.applyDiceRoll(state, 6) as RollOutcome.RollAgainStacked
        val r2 = engine.applyDiceRoll(r1.state, 2) as RollOutcome.MultipleLegalMoves
        val chosen = botBrain.chooseMove(r2.state, classicRules, r2.moves, BotDifficulty.EASY)
        assertTrue(chosen in r2.moves)
    }

    @Test
    fun `medium bot prefers capture over ordinary advance`() {
        val base = engine.createGame(
            listOf(
                PlayerSetup(0, "MedBot", PlayerColor.RED, PlayerControllerType.BOT, BotDifficulty.MEDIUM),
                PlayerSetup(1, "Opp", PlayerColor.YELLOW)
            )
        )
        // Token 0 at progress 24 -> roll 3 lands on 27 (Yellow token at progress 1 -> trackIndex 27)
        // Token 1 at progress 10 -> roll 3 lands on 13 (safe start square!)
        val modified = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.RED, 24),
                            Token(1, PlayerColor.RED, 10),
                            Token(2, PlayerColor.RED, Token.BASE_PROGRESS),
                            Token(3, PlayerColor.RED, Token.BASE_PROGRESS)
                        )
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
        val roll = engine.applyDiceRoll(modified, 3) as RollOutcome.MultipleLegalMoves
        val chosen = botBrain.chooseMove(roll.state, classicRules, roll.moves, BotDifficulty.MEDIUM)
        assertEquals(0, chosen.tokenId)
        assertTrue(chosen.capturedTokens.isNotEmpty())
    }

    @Test
    fun `hard bot evaluates capture risk accurately and escapes danger to safe square`() {
        val base = engine.createGame(
            listOf(
                PlayerSetup(0, "HardBot", PlayerColor.RED, PlayerControllerType.BOT, BotDifficulty.HARD),
                PlayerSetup(1, "Opp", PlayerColor.YELLOW)
            )
        )
        // Red Token 0 at progress 5 (non-safe), threatened by Yellow at trackIndex 2 (Yellow progress 28 -> 3 steps behind!)
        // Red rolls 3:
        // - Token 0 moves 5 -> 8 (Star Safe Square -> risk drops from 1/6 to 0!)
        // - Token 1 moves 16 -> 19 (non-safe, no threat before or after)
        val modified = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.RED, 5),
                            Token(1, PlayerColor.RED, 16),
                            Token(2, PlayerColor.RED, Token.BASE_PROGRESS),
                            Token(3, PlayerColor.RED, Token.BASE_PROGRESS)
                        )
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t ->
                            if (idx == 0) t.copy(progress = 28) else t
                        }
                    )
                    else -> p
                }
            }
        )

        val riskBefore = botBrain.evaluateCaptureRisk(modified, PlayerColor.RED, 5, classicRules)
        val riskSafe = botBrain.evaluateCaptureRisk(modified, PlayerColor.RED, 8, classicRules)
        assertTrue(riskBefore > 0.0)
        assertEquals(0.0, riskSafe, 0.0001)

        val roll = engine.applyDiceRoll(modified, 3) as RollOutcome.MultipleLegalMoves
        val chosen = botBrain.chooseMove(roll.state, classicRules, roll.moves, BotDifficulty.HARD)
        assertEquals(0, chosen.tokenId)
        assertEquals(8, chosen.toProgress)
    }

    @Test
    fun `hard bot respects 2v2 team format and never captures partner`() {
        val teamRules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.TEAMS_2V2)
        val teamEngine = LudoEngine(teamRules)
        val base = teamEngine.createGame(
            listOf(
                PlayerSetup(0, "HardBot", PlayerColor.RED, PlayerControllerType.BOT, BotDifficulty.HARD),
                PlayerSetup(1, "Opp1", PlayerColor.GREEN),
                PlayerSetup(2, "Partner", PlayerColor.YELLOW),
                PlayerSetup(3, "Opp2", PlayerColor.BLUE)
            ),
            mode = GameModeId.CLASSIC,
            matchFormat = MatchFormat.TEAMS_2V2
        )
        // In 2v2 Team, YELLOW is RED's opposite-corner teammate!
        // Red Token 0 at progress 24 -> roll 3 lands on 27 alongside Yellow (no capture!)
        // Red Token 1 at progress 10 -> roll 3 lands on 13 (safe square)
        val modified = base.copy(
            players = base.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        tokens = listOf(
                            Token(0, PlayerColor.RED, 24),
                            Token(1, PlayerColor.RED, 10),
                            Token(2, PlayerColor.RED, Token.BASE_PROGRESS),
                            Token(3, PlayerColor.RED, Token.BASE_PROGRESS)
                        )
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
        val roll = teamEngine.applyDiceRoll(modified, 3) as RollOutcome.MultipleLegalMoves
        assertTrue("Partners must never capture each other", roll.moves.all { it.capturedTokens.isEmpty() })
        val chosen = botBrain.chooseMove(roll.state, teamRules, roll.moves, BotDifficulty.HARD)
        assertEquals(1, chosen.tokenId) // prefers safe square 13 over non-safe 27
    }
}
