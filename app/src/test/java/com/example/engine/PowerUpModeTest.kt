package com.example.engine

import com.example.bot.LudoBotBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests v2.1 Magic mode: instant item pickups (Rocket, Golden Dice, Shield, Bomb Trap),
 * 2-round item spawns (max 3 on board), 3-round bomb expiry, and bot heuristics.
 */
class PowerUpModeTest {

    private val magicRules = RuleSetRegistry.forMode(GameModeId.MAGIC, MatchFormat.ONE_VS_ONE)
    private val classicRules = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)

    private fun twoPlayers() = listOf(
        PlayerSetup(0, "Alice", PlayerColor.RED),
        PlayerSetup(1, "Bob", PlayerColor.YELLOW)
    )

    private fun twoPlayersWithBot(difficulty: BotDifficulty = BotDifficulty.HARD) = listOf(
        PlayerSetup(0, "BotRed", PlayerColor.RED, PlayerControllerType.BOT, difficulty),
        PlayerSetup(1, "HumanYellow", PlayerColor.YELLOW, PlayerControllerType.HUMAN_LOCAL)
    )

    @Test
    fun magicModeSpawnsUpTo3ItemsOnNonSafeSquares_andClassicSpawnsNone() {
        val magicEngine = LudoEngine(magicRules, eventRandom = Random(42))
        val magicState = magicEngine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE)

        assertEquals(PowerUpState.MAX_TILES_ON_BOARD, magicState.powerUpState.tiles.size)
        assertEquals(PowerUpState.MAX_TILES_ON_BOARD, magicState.powerUpState.tiles.map { it.trackIndex }.distinct().size)
        for (tile in magicState.powerUpState.tiles) {
            assertFalse(
                "Magic item must never spawn on safe or start squares",
                tile.trackIndex in LudoBoardGeometry.SAFE_TRACK_INDICES
            )
            assertTrue(tile.trackIndex in 0 until Token.TRACK_LENGTH)
        }

        val classicEngine = LudoEngine(classicRules, eventRandom = Random(42))
        val classicState = classicEngine.createGame(twoPlayers(), GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
        assertTrue(classicState.powerUpState.tiles.isEmpty())
    }

    @Test
    fun magicModeSpawnsNewItemEveryTwoFullRoundsUpToMaxThree() {
        val engine = LudoEngine(magicRules, eventRandom = Random(7))
        var state = engine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE).copy(
            players = engine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE).players.map { p ->
                p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
            },
            powerUpState = PowerUpState(tiles = emptyList(), nextEntityId = 10, roundsUntilNextSpawn = 2)
        )

        // Complete 1 full round (Red + Yellow) -> still 0 items
        val r1 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r1.state, r1.move).state
        val y1 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(y1.state, y1.move).state
        assertEquals(1, state.fullRoundsCompleted)
        assertTrue(state.powerUpState.tiles.isEmpty())

        // Complete 2nd full round -> 1 new Magic item spawns!
        val r2 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r2.state, r2.move).state
        val y2 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(y2.state, y2.move).state
        assertEquals(2, state.fullRoundsCompleted)
        assertEquals(1, state.powerUpState.tiles.size)
        assertFalse(state.powerUpState.tiles.first().trackIndex in LudoBoardGeometry.SAFE_TRACK_INDICES)
    }

    @Test
    fun instantShieldProtectsTokenForTwoTurnsThenExpires() {
        val engine = LudoEngine(magicRules, eventRandom = Random(11))
        var state = engine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE)

        // RED token 0 at progress 29 (track 29), Shield item at track 30; YELLOW token 0 at progress 2 (track 28)
        state = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 29) else it })
                    PlayerColor.YELLOW -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 2) else it })
                    else -> p
                }
            },
            powerUpState = PowerUpState(
                tiles = listOf(PowerUpTile(id = 1, trackIndex = 30, type = PowerUpType.SHIELD)),
                nextEntityId = 2
            )
        )

        // RED rolls 1 -> lands on track 30 -> Shield activates instantly for 2 turns!
        val r1 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        assertEquals(PowerUpType.SHIELD, r1.move.collectedPowerUp)
        state = engine.applyMove(r1.state, r1.move).state
        assertEquals(2, state.playerByColor(PlayerColor.RED)!!.tokens[0].shieldTurnsRemaining)
        assertEquals(1, state.playerByColor(PlayerColor.RED)!!.powerUpsUsed)

        // YELLOW rolls 2 -> lands on track 30 -> RED is shielded, NO capture!
        val y1 = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        assertTrue("Shielded token must not be capturable", y1.move.capturedTokens.isEmpty())
        state = engine.applyMove(y1.state, y1.move).state

        // RED's next turn: shield decremented from 2 -> 1
        assertEquals(1, state.playerByColor(PlayerColor.RED)!!.tokens[0].shieldTurnsRemaining)
        val r2 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r2.state, r2.move).state
        val y2 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        assertTrue("Shield still protects during 2nd turn", y2.move.capturedTokens.isEmpty())
        state = engine.applyMove(y2.state, y2.move).state

        // RED's 3rd turn: shield expired (0) -> YELLOW can capture!
        assertEquals(0, state.playerByColor(PlayerColor.RED)!!.tokens[0].shieldTurnsRemaining)
        val r3 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r3.state, r3.move).state
        val y3 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        assertEquals(listOf(TokenRef(PlayerColor.RED, 0)), y3.move.capturedTokens)
    }

    @Test
    fun goldenDiceGuaranteesNextRollIsSix() {
        val engine = LudoEngine(magicRules, eventRandom = Random(13))
        var state = engine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE)

        // RED token 0 at progress 1, Golden Dice tile at track 4
        state = state.copy(
            players = state.players.map { p ->
                p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
            },
            powerUpState = PowerUpState(
                tiles = listOf(PowerUpTile(id = 1, trackIndex = 4, type = PowerUpType.GOLDEN_DICE)),
                nextEntityId = 2
            )
        )

        val r1 = engine.applyDiceRoll(state, 3) as RollOutcome.SingleLegalMove
        state = engine.applyMove(r1.state, r1.move).state
        assertEquals(1, state.playerByColor(PlayerColor.RED)!!.goldenDiceCharges)

        // Yellow takes a turn
        val y1 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        state = engine.applyMove(y1.state, y1.move).state

        // Red rolls raw 2 -> Golden Dice forces it to 6 (stacks into tray!)
        val goldenRoll = engine.applyDiceRoll(state, 2)
        assertTrue("Golden Dice must force roll to 6 and stack", goldenRoll is RollOutcome.RollAgainStacked)
        assertEquals(6, goldenRoll.state.lastDiceValue)
        assertTrue(goldenRoll.state.wasGoldenDiceRoll)
        assertEquals(0, goldenRoll.state.playerByColor(PlayerColor.RED)!!.goldenDiceCharges)
    }

    @Test
    fun rocketLaunchesTokenForward1To8SquaresWithoutOvershootingHome() {
        val engine = LudoEngine(magicRules, eventRandom = Random(21))
        var state = engine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE)

        // Place RED token 0 at progress 48, Rocket at track 50 (only 6 steps left to Home = 56!)
        state = state.copy(
            players = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 48) else it })
                } else p
            },
            powerUpState = PowerUpState(
                tiles = listOf(PowerUpTile(id = 1, trackIndex = 50, type = PowerUpType.ROCKET)),
                nextEntityId = 2
            )
        )

        val r1 = engine.applyDiceRoll(state, 2) as RollOutcome.SingleLegalMove
        val outcome = engine.applyMove(r1.state, r1.move)
        assertEquals(PowerUpType.ROCKET, outcome.collectedPowerUp)
        assertTrue("Rocket must boost 1..8 steps", outcome.rocketBoostSteps in 1..8)
        val newProgress = outcome.state.playerByColor(PlayerColor.RED)!!.tokens[0].progress
        assertTrue("Rocket must stop at or before Home (56)", newProgress in 51..Token.HOME_PROGRESS)
    }

    @Test
    fun bombTrapEntersPlacementPhase_detonatesOnOpponent_immuneToOwner_andExpiresAfter3Rounds() {
        val engine = LudoEngine(magicRules, eventRandom = Random(19))
        var state = engine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE)

        // RED token 0 at progress 1, Bomb Trap item at track 2; YELLOW token 0 at progress 26 (track 0 -> start square)
        state = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
                    PlayerColor.YELLOW -> p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 26) else it })
                    else -> p
                }
            },
            powerUpState = PowerUpState(
                tiles = listOf(PowerUpTile(id = 5, trackIndex = 2, type = PowerUpType.BOMB_TRAP)),
                nextEntityId = 6
            )
        )

        // RED lands on track 2 -> enters AWAITING_BOMB_PLACEMENT with valid squares ahead (e.g., 3,4,5,6,7; NOT 8 which is star safe)
        val r1 = engine.applyDiceRoll(state, 1) as RollOutcome.SingleLegalMove
        val pickupOutcome = engine.applyMove(r1.state, r1.move)
        assertTrue(pickupOutcome.awaitingBombPlacement)
        assertEquals(TurnPhase.AWAITING_BOMB_PLACEMENT, pickupOutcome.state.phase)
        val pending = pickupOutcome.state.powerUpState.pendingBombPlacement
        assertNotNull(pending)
        assertTrue(4 in pending!!.validTrackIndices)
        assertFalse("Safe square 8 cannot hold a bomb", 8 in pending.validTrackIndices)

        // RED places hidden bomb on track 4 -> lifetime is 3 rounds, turn passes to YELLOW
        state = engine.placePendingBomb(pickupOutcome.state, trackIndex = 4)
        assertEquals(TurnPhase.AWAITING_ROLL, state.phase)
        assertEquals(PlayerColor.YELLOW, state.activePlayer.color)
        assertEquals(1, state.powerUpState.bombs.size)
        assertEquals(PowerUpState.BOMB_LIFETIME_ROUNDS, state.powerUpState.bombs.first().roundsRemaining)

        // YELLOW rolls 4 (track 0 -> track 4) -> EXPLODES back to base!
        val yHit = engine.applyDiceRoll(state, 4) as RollOutcome.SingleLegalMove
        assertNotNull(yHit.move.triggeredBomb)
        val afterBoom = engine.applyMove(yHit.state, yHit.move)
        assertTrue(afterBoom.state.playerByColor(PlayerColor.YELLOW)!!.tokens[0].isInBase)
        assertTrue(afterBoom.state.powerUpState.bombs.isEmpty())

        // Verify 3-round expiry for an untriggered bomb
        var expiryState = engine.createGame(twoPlayers(), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE).copy(
            players = state.players.map { p ->
                p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
            },
            powerUpState = PowerUpState(
                bombs = listOf(BombTrap(id = 99, ownerColor = PlayerColor.RED, trackIndex = 45, roundsRemaining = 3)),
                nextEntityId = 100
            )
        )
        repeat(3) {
            val r = engine.applyDiceRoll(expiryState, 1) as RollOutcome.SingleLegalMove
            expiryState = engine.applyMove(r.state, r.move).state
            val y = engine.applyDiceRoll(expiryState, 1) as RollOutcome.SingleLegalMove
            expiryState = engine.applyMove(y.state, y.move).state
        }
        assertEquals(3, expiryState.fullRoundsCompleted)
        assertTrue("Bomb must disappear after 3 full rounds", expiryState.powerUpState.bombs.isEmpty())
    }

    @Test
    fun hardBotPrioritizesMagicItemsAndPlacesPendingBombAheadOfOpponent() {
        val engine = LudoEngine(magicRules, eventRandom = Random(23))
        val brain = LudoBotBrain(Random(23))

        var state = engine.createGame(twoPlayersWithBot(BotDifficulty.HARD), GameModeId.MAGIC, MatchFormat.ONE_VS_ONE)
        state = state.copy(
            players = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(
                        tokens = p.tokens.map {
                            when (it.id) {
                                0 -> it.copy(progress = 2) // roll 3 lands on track 5 (Magic item!)
                                1 -> it.copy(progress = 15) // roll 3 lands on plain track 18
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
        val chosen = brain.chooseMove(state, magicRules, moves, BotDifficulty.HARD)
        assertEquals("Hard bot should prioritize landing on a Magic item", 0, chosen.tokenId)

        // Pending bomb placement: Yellow at track 27 (progress 1), valid squares = [29, 30, 31, 35, 36]
        val bombState = state.copy(
            players = state.players.map { p ->
                if (p.color == PlayerColor.YELLOW) {
                    p.copy(tokens = p.tokens.map { if (it.id == 0) it.copy(progress = 1) else it })
                } else p
            }
        )
        val validSquares = listOf(29, 30, 31, 35, 36)
        val pickedSquare = brain.choosePendingBombSquare(bombState, magicRules, validSquares, BotDifficulty.HARD)
        assertTrue(pickedSquare in validSquares)
        val distAheadOfYellow = (pickedSquare - 27 + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
        assertTrue("Hard bot should place bomb 1..6 squares ahead of opponent", distAheadOfYellow in 1..6)
    }
}
