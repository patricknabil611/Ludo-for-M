package com.example.engine

import com.example.bot.BotPowerUpAction
import com.example.bot.LudoBotBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PowerUpModeTest {

    private fun twoPlayers() = listOf(
        PlayerSetup(0, "Alice", PlayerColor.RED),
        PlayerSetup(1, "Bob", PlayerColor.YELLOW)
    )

    private fun twoPlayersWithBot(difficulty: BotDifficulty = BotDifficulty.HARD) = listOf(
        PlayerSetup(0, "BotRed", PlayerColor.RED, PlayerControllerType.BOT, difficulty),
        PlayerSetup(1, "HumanYellow", PlayerColor.YELLOW, PlayerControllerType.HUMAN_LOCAL)
    )

    @Test
    fun powerUpModeSpawns3TilesNotOnSafeOrStartSquares_andClassicSpawnsNone() {
        val powerEngine = LudoEngine(PowerUpRuleSet, eventRandom = Random(42))
        val powerState = powerEngine.createGame(twoPlayers(), GameModeId.POWER_UP)

        assertEquals(3, powerState.powerUpState.tiles.size)
        assertEquals(3, powerState.powerUpState.tiles.map { it.trackIndex }.distinct().size)
        for (tile in powerState.powerUpState.tiles) {
            assertFalse(
                "Power-up tile must never spawn on safe or start squares",
                tile.trackIndex in LudoBoardGeometry.SAFE_TRACK_INDICES
            )
            assertTrue(tile.trackIndex in 0 until Token.TRACK_LENGTH)
        }

        val classicEngine = LudoEngine(ClassicRuleSet, eventRandom = Random(42))
        val classicState = classicEngine.createGame(twoPlayers(), GameModeId.CLASSIC)
        assertTrue(classicState.powerUpState.tiles.isEmpty())
    }

    @Test
    fun collectingTileAddsPowerUpAndRespawnsAfterTwoFullRounds() {
        val engine = LudoEngine(PowerUpRuleSet, eventRandom = Random(7))
        var state = engine.createGame(twoPlayers(), GameModeId.POWER_UP)

        // Place RED token 0 at progress 1 (track 1), tile at track 4 (not safe)
        val tile = PowerUpTile(id = 10, trackIndex = 4, type = PowerUpType.SHIELD)
        state = state.copy(
            players = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
                } else {
                    p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
                }
            },
            powerUpState = PowerUpState(tiles = listOf(tile), nextEntityId = 11)
        )

        // RED rolls 3 -> lands on track 4 -> collects Shield
        val roll = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        assertEquals(PowerUpType.SHIELD, roll.move.collectedPowerUp)
        val afterMove = engine.applyMove(roll.state, roll.move)
        state = afterMove.state

        assertEquals(listOf(PowerUpType.SHIELD), state.playerByColor(PlayerColor.RED)!!.heldPowerUps)
        assertTrue(state.powerUpState.tiles.isEmpty())
        assertEquals(1, state.powerUpState.pendingRespawns.size)
        assertEquals(2, state.powerUpState.pendingRespawns.first().roundsRemaining)

        // Complete Round 1 (YELLOW moves -> wraps back to RED)
        val yRoll1 = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        state = engine.applyMove(yRoll1.state, yRoll1.move).state
        assertEquals(1, state.fullRoundsCompleted)
        assertTrue("Tile should not respawn after only 1 round", state.powerUpState.tiles.isEmpty())
        assertEquals(1, state.powerUpState.pendingRespawns.first().roundsRemaining)

        // Complete Round 2 (RED moves, YELLOW moves -> wraps back to RED)
        val rRoll2 = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        state = engine.applyMove(rRoll2.state, rRoll2.move).state
        val yRoll2 = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        state = engine.applyMove(yRoll2.state, yRoll2.move).state

        assertEquals(2, state.fullRoundsCompleted)
        assertEquals(1, state.powerUpState.tiles.size)
        assertTrue(state.powerUpState.pendingRespawns.isEmpty())
        assertFalse(state.powerUpState.tiles.first().trackIndex in LudoBoardGeometry.SAFE_TRACK_INDICES)
    }

    @Test
    fun holdingTwoPowerUpsIgnoresThirdTileAndLeavesItOnBoard() {
        val engine = LudoEngine(PowerUpRuleSet, eventRandom = Random(9))
        var state = engine.createGame(twoPlayers(), GameModeId.POWER_UP)

        val tile = PowerUpTile(id = 10, trackIndex = 4, type = PowerUpType.BOMB_TRAP)
        state = state.copy(
            players = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(
                        heldPowerUps = listOf(PowerUpType.SHIELD, PowerUpType.DOUBLE_DICE),
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it }
                    )
                } else p
            },
            powerUpState = PowerUpState(tiles = listOf(tile), nextEntityId = 11)
        )

        val roll = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        assertNull(roll.move.collectedPowerUp)
        val after = engine.applyMove(roll.state, roll.move).state

        assertEquals(2, after.playerByColor(PlayerColor.RED)!!.heldPowerUps.size)
        assertEquals(1, after.powerUpState.tiles.size)
        assertEquals(4, after.powerUpState.tiles.first().trackIndex)
    }

    @Test
    fun shieldProtectsFromCaptureForTwoTurnsThenExpires() {
        val engine = LudoEngine(PowerUpRuleSet, eventRandom = Random(11))
        var state = engine.createGame(twoPlayers(), GameModeId.POWER_UP)

        // RED token 0 at progress 29 (track 29), YELLOW token 0 at progress 1 (track 27), token 1 at progress 5
        state = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        heldPowerUps = listOf(PowerUpType.SHIELD),
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 29) else it }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 2) else it }
                    )
                    else -> p
                }
            },
            powerUpState = PowerUpState()
        )

        // RED activates Shield on token 0 (shieldTurnsRemaining = 2)
        state = engine.activateShield(state, tokenId = 0)
        assertEquals(2, state.playerByColor(PlayerColor.RED)!!.tokens[0].shieldTurnsRemaining)
        assertTrue(state.playerByColor(PlayerColor.RED)!!.heldPowerUps.isEmpty())

        // RED rolls 1 -> token 0 moves to progress 30 (track 30), still shielded (2)
        val r1 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r1.state, r1.move).state

        // YELLOW token 0 is at track 28 (progress 2). Rolls 2 -> lands on track 30 where RED token 0 sits!
        // Because RED token 0 is shielded, YELLOW must NOT capture it.
        val y1 = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        assertTrue("Shielded token must not be capturable", y1.move.capturedTokens.isEmpty())
        state = engine.applyMove(y1.state, y1.move).state

        // Now it is RED's next turn: shield decremented from 2 -> 1!
        val redTokenAfterTurn1 = state.playerByColor(PlayerColor.RED)!!.tokens[0]
        assertEquals(30, redTokenAfterTurn1.progress)
        assertEquals(1, redTokenAfterTurn1.shieldTurnsRemaining)

        // RED rolls 1 -> token 0 moves to 31; YELLOW rolls 1 -> lands on 31, still shielded (1)!
        val r2 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r2.state, r2.move).state
        val y2 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        assertTrue("Shield should still protect during turn 2", y2.move.capturedTokens.isEmpty())
        state = engine.applyMove(y2.state, y2.move).state

        // Now it is RED's 3rd turn: shield decremented 1 -> 0 (expired)!
        assertEquals(0, state.playerByColor(PlayerColor.RED)!!.tokens[0].shieldTurnsRemaining)

        // RED rolls 1 -> moves to 32; YELLOW rolls 1 -> lands on 32 -> NOW captures RED token 0!
        val r3 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r3.state, r3.move).state
        val y3 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        assertEquals(listOf(TokenRef(PlayerColor.RED, 0)), y3.move.capturedTokens)
    }

    @Test
    fun doubleDiceSumsBothDice_doubleSixGivesExtraRoll_andIgnoresTripleSixRule() {
        val engine = LudoEngine(PowerUpRuleSet, eventRandom = Random(13))
        var state = engine.createGame(twoPlayers(), GameModeId.POWER_UP)

        state = state.copy(
            consecutiveSixes = 2, // Normally a 3rd 6 would cancel the turn!
            players = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(
                        heldPowerUps = listOf(PowerUpType.DOUBLE_DICE),
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 10) else it }
                    )
                } else p
            },
            powerUpState = PowerUpState()
        )

        state = engine.activateDoubleDice(state)
        assertTrue(state.activePlayer.doubleDiceActive)

        // Roll 6 + 6 = 12 -> must NOT cancel turn, moves token 0 by 12 (to progress 22), and grants extra roll!
        val outcome = engine.applyDiceRoll(state, rawDiceValue = 6, secondDiceValue = 6)
        assertFalse("Triple-6 rule must not apply to Double Dice", outcome is RollOutcome.TripleSixCancelled)
        assertTrue(outcome.state.wasDoubleDiceRoll)
        assertEquals(12, outcome.state.lastDiceValue)

        // Token 0 moves by 12; tokens 1..3 can also leave base since doubled 6s count as a 6!
        val moves = outcome.state.legalMoves
        val token0Move = moves.first { it.tokenId == 0 }
        assertEquals(22, token0Move.toProgress)
        assertEquals(12, token0Move.stepPath.size)
        assertTrue(moves.any { it.leavesBase })

        val moveOutcome = engine.applyMove(outcome.state, token0Move)
        assertTrue("Doubled 6s must grant an extra roll", moveOutcome.grantedExtraRoll)
        assertEquals(PlayerColor.RED, moveOutcome.state.activePlayer.color)
        assertFalse(moveOutcome.state.activePlayer.doubleDiceActive)
    }

    @Test
    fun bombTrapPlacementValidation_opponentDetonation_ownerImmunity_and5RoundExpiry() {
        val engine = LudoEngine(PowerUpRuleSet, eventRandom = Random(19))
        var state = engine.createGame(twoPlayers(), GameModeId.POWER_UP)

        // RED token 0 at progress 1 (track 1); YELLOW token 0 at progress 26 (track 0 -> start square)
        state = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        heldPowerUps = listOf(PowerUpType.BOMB_TRAP),
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 26) else it } // track 0
                    )
                    else -> p
                }
            },
            powerUpState = PowerUpState()
        )

        val validSquares = engine.computeValidBombSquares(state, PlayerColor.RED)
        // Track 1 is occupied by RED; track 0 & 8 & 47 are safe squares
        assertFalse(0 in validSquares)
        assertFalse(1 in validSquares)
        assertFalse(8 in validSquares)
        assertFalse(47 in validSquares)
        assertTrue(4 in validSquares) // 3 steps ahead of track 1
        assertFalse(10 in validSquares) // > 6 steps away

        // RED hides a bomb on track 4
        state = engine.placeBombTrap(state, trackIndex = 4)
        assertEquals(1, state.powerUpState.bombs.size)
        assertEquals(5, state.powerUpState.bombs.first().roundsRemaining)

        // RED rolls 3 -> lands on track 4 (own bomb!) -> MUST NOT trigger!
        val redOwnLand = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        assertNull("Owner cannot trigger own bomb", redOwnLand.move.triggeredBomb)
        state = engine.applyMove(redOwnLand.state, redOwnLand.move).state
        assertEquals(4, state.playerByColor(PlayerColor.RED)!!.tokens[0].progress)
        assertEquals(1, state.powerUpState.bombs.size)

        // YELLOW rolls 4 -> lands on track 4 (RED's bomb!) -> EXPLODES and returns to base!
        val yellowHit = engine.applyDiceRoll(state, 4) as RollOutcome.SingleLegalMove
        assertNotNull("Opponent landing on bomb must trigger it", yellowHit.move.triggeredBomb)
        val afterBoom = engine.applyMove(yellowHit.state, yellowHit.move)
        assertNotNull(afterBoom.triggeredBomb)
        assertTrue(afterBoom.state.playerByColor(PlayerColor.YELLOW)!!.tokens[0].isInBase)
        assertTrue("Bomb must disappear after triggering", afterBoom.state.powerUpState.bombs.isEmpty())

        // Verify 5-round expiry for an untriggered bomb
        var expiryState = engine.createGame(twoPlayers(), GameModeId.POWER_UP).copy(
            players = state.players.map { p ->
                p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
            },
            powerUpState = PowerUpState(
                bombs = listOf(BombTrap(id = 99, ownerColor = PlayerColor.RED, trackIndex = 45, roundsRemaining = 5)),
                nextEntityId = 100
            )
        )
        // Advance 5 full rounds (10 single-turn moves)
        repeat(5) {
            val r = engine.applyDiceRoll(expiryState, 1) as RollOutcome.SingleLegalMove
            expiryState = engine.applyMove(r.state, r.move).state
            val y = engine.applyDiceRoll(expiryState, 1) as RollOutcome.SingleLegalMove
            expiryState = engine.applyMove(y.state, y.move).state
        }
        assertEquals(5, expiryState.fullRoundsCompleted)
        assertTrue("Bomb must disappear after 5 full rounds", expiryState.powerUpState.bombs.isEmpty())
    }

    @Test
    fun mediumAndHardBotsCollectPowerUpsAndUseShieldAndBombSensibly() {
        val engine = LudoEngine(PowerUpRuleSet, eventRandom = Random(23))
        val brain = LudoBotBrain(Random(23))

        // 1. Bot prefers collecting a Power-Up tile over an ordinary move
        var state = engine.createGame(twoPlayersWithBot(BotDifficulty.HARD), GameModeId.POWER_UP)
        state = state.copy(
            players = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(
                        tokens = p.tokens.map {
                            when (it.id) {
                                0 -> it.copy(progress = 2) // rolling 3 lands on track 5 (Power-Up tile!)
                                1 -> it.copy(progress = 15) // rolling 3 lands on plain track 18
                                else -> it
                            }
                        }
                    )
                } else p
            },
            powerUpState = PowerUpState(
                tiles = listOf(PowerUpTile(id = 1, trackIndex = 5, type = PowerUpType.SHIELD)),
                nextEntityId = 2
            )
        )
        val moves = engine.computeLegalMoves(state, 3)
        val chosen = brain.chooseMove(state, PowerUpRuleSet, moves, BotDifficulty.HARD)
        assertEquals("Hard bot should prioritize landing on a power-up tile", 0, chosen.tokenId)

        // 2. Bot uses Shield on an endangered token before rolling
        val endangeredState = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        heldPowerUps = listOf(PowerUpType.SHIELD),
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 29) else it.copy(progress = Token.BASE_PROGRESS) }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        // YELLOW token 0 at progress 1 (track 27) — 2 steps behind RED token 0 at track 29!
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it.copy(progress = Token.BASE_PROGRESS) }
                    )
                    else -> p
                }
            }
        )
        val shieldAction = brain.choosePowerUpBeforeRoll(endangeredState, PowerUpRuleSet, BotDifficulty.HARD)
        assertEquals(BotPowerUpAction.UseShield(0), shieldAction)

        // 3. Bot places Bomb Trap in front of an opponent's likely path (1..6 steps ahead)
        val bombState = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        heldPowerUps = listOf(PowerUpType.BOMB_TRAP),
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 30) else it.copy(progress = Token.BASE_PROGRESS) }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        // YELLOW token 0 at progress 1 (track 27): squares 28, 29, 31, 32, 33 are 1..6 ahead of YELLOW and within 6 of RED (30)
                        tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it.copy(progress = Token.BASE_PROGRESS) }
                    )
                    else -> p
                }
            },
            powerUpState = PowerUpState()
        )
        val bombAction = brain.choosePowerUpBeforeRoll(bombState, PowerUpRuleSet, BotDifficulty.MEDIUM)
        assertTrue("Medium bot should place a bomb trap ahead of opponent", bombAction is BotPowerUpAction.UseBombTrap)
        val placedSq = (bombAction as BotPowerUpAction.UseBombTrap).trackIndex
        val distAheadOfYellow = (placedSq - 27 + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
        assertTrue("Bomb should be placed 1..6 squares ahead of opponent", distAheadOfYellow in 1..6)
    }
}
