package com.example.online

import com.example.engine.BombTrap
import com.example.engine.BotDifficulty
import com.example.engine.ChaosEventType
import com.example.engine.ChaosState
import com.example.engine.GameModeId
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.engine.PlayerControllerType
import com.example.engine.PlayerSetup
import com.example.engine.PowerUpState
import com.example.engine.PowerUpTile
import com.example.engine.PowerUpType
import com.example.engine.RuleSetRegistry
import com.example.engine.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineMultiplayerUnitTest {

    @Test
    fun fairDiceRoller_isDeterministicAndBounded1To6() {
        val rollA = FairDiceRoller.computeSingleRoll(
            roomCode = "ABC234",
            diceSeed = 987654321L,
            rollSeq = 7,
            turnStartedAtSeconds = 1_700_000_000L,
            activeColor = PlayerColor.RED
        )
        val rollB = FairDiceRoller.computeSingleRoll(
            roomCode = "ABC234",
            diceSeed = 987654321L,
            rollSeq = 7,
            turnStartedAtSeconds = 1_700_000_000L,
            activeColor = PlayerColor.RED
        )
        assertEquals(rollA, rollB)
        assertTrue(rollA in 1..6)

        val seen = mutableSetOf<Int>()
        for (seq in 1..200) {
            val v = FairDiceRoller.computeSingleRoll(
                roomCode = "ROOM99",
                diceSeed = 42L,
                rollSeq = seq,
                turnStartedAtSeconds = 1_700_000_000L + seq,
                activeColor = PlayerColor.GREEN
            )
            assertTrue("Roll out of bounds: $v", v in 1..6)
            seen += v
        }
        assertEquals(setOf(1, 2, 3, 4, 5, 6), seen)
    }

    @Test
    fun fairDiceRoller_roomCodeMatchesRulesRegex() {
        val regex = Regex("^[A-Z0-9]{6}$")
        repeat(50) {
            val code = FairDiceRoller.generateRoomCode(Random(it))
            assertTrue("Invalid room code $code", regex.matches(code))
        }
    }

    @Test
    fun gameStateFirestoreMapper_roundTripsPowerUpAndChaosStateLosslessly() {
        val setups = listOf(
            PlayerSetup(0, "Alice", PlayerColor.RED, PlayerControllerType.ONLINE_REMOTE),
            PlayerSetup(1, "Bot", PlayerColor.YELLOW, PlayerControllerType.BOT, BotDifficulty.HARD)
        )
        val engine = LudoEngine(RuleSetRegistry.forMode(GameModeId.POWER_UP), Random(123))
        val initial = engine.createGame(setups, GameModeId.POWER_UP)
        val modified = initial.copy(
            phase = TurnPhase.AWAITING_MOVE,
            lastDiceValue = 6,
            lastRawDiceValue = 6,
            players = initial.players.mapIndexed { idx, p ->
                if (idx == 0) {
                    p.copy(
                        heldPowerUps = listOf(PowerUpType.SHIELD, PowerUpType.BOMB_TRAP),
                        tokens = p.tokens.mapIndexed { tIdx, t ->
                            if (tIdx == 0) t.copy(progress = 12, shieldTurnsRemaining = 2) else t
                        }
                    )
                } else p
            },
            powerUpState = PowerUpState(
                tiles = listOf(PowerUpTile(1, 15, PowerUpType.DOUBLE_DICE)),
                bombs = listOf(BombTrap(2, PlayerColor.RED, 18, 4)),
                nextEntityId = 9
            ),
            chaosState = ChaosState(
                activeSafeTrackIndices = setOf(2, 14, 28),
                safeStarsRoundsRemaining = 2,
                portalPair = 7 to 33,
                portalsRoundsRemaining = 3,
                lastTriggeredEvent = ChaosEventType.PORTALS,
                announcementDetail = "Portals open!",
                announcementId = 4
            )
        )

        val serialized = GameStateFirestoreMapper.toFirestoreMap(modified)
        val restored = GameStateFirestoreMapper.fromFirestoreMap(serialized)

        assertNotNull(restored)
        restored!!
        assertEquals(GameModeId.POWER_UP, restored.mode)
        assertEquals(TurnPhase.AWAITING_MOVE, restored.phase)
        assertEquals(listOf(PowerUpType.SHIELD, PowerUpType.BOMB_TRAP), restored.players[0].heldPowerUps)
        assertEquals(2, restored.players[0].tokens[0].shieldTurnsRemaining)
        assertEquals(1, restored.powerUpState.tiles.size)
        assertEquals(18, restored.powerUpState.bombs.first().trackIndex)
        assertEquals(PlayerColor.RED, restored.powerUpState.bombs.first().ownerColor)
        assertEquals(7 to 33, restored.chaosState.portalPair)
        assertEquals(setOf(2, 14, 28), restored.chaosState.activeSafeTrackIndices)
        assertTrue("Recomputed legal moves should not be empty on roll 6", restored.legalMoves.isNotEmpty())
    }

    @Test
    fun onlineRoom_botTakeoverAndCoordinatorSelectionWorkCorrectly() {
        val seat0 = OnlineRoomSeat(
            seatIndex = 0,
            color = PlayerColor.RED,
            uid = "u1",
            nickname = "Host",
            controllerType = SeatControl.HUMAN,
            connected = false,
            botTakeover = true,
            consecutiveMisses = 3
        )
        val seat1 = OnlineRoomSeat(
            seatIndex = 1,
            color = PlayerColor.YELLOW,
            uid = "u2",
            nickname = "Friend",
            controllerType = SeatControl.HUMAN,
            connected = true,
            botTakeover = false
        )
        val room = OnlineRoom(
            roomCode = "PLAY42",
            hostUid = "u1",
            mode = GameModeId.CLASSIC,
            targetPlayerCount = 2,
            timedDurationSeconds = 300,
            status = RoomStatus.PLAYING,
            publicMatchmaking = false,
            memberUids = listOf("u1", "u2"),
            seats = listOf(seat0, seat1),
            diceSeed = 111L,
            rollSeq = 0,
            turnSeq = 1,
            lastActionType = OnlineActionType.BOT_TAKEOVER,
            lastActionSummary = "Host missed 3 turns",
            lastMovedColor = null,
            lastMovedTokenId = -1,
            lastMovedFromProgress = -1,
            lastMovedToProgress = -1,
            gameState = null,
            turnStartedAt = null,
            createdAt = null,
            updatedAt = null
        )

        assertTrue(seat0.isEffectiveBot)
        assertEquals(BotDifficulty.MEDIUM, seat0.effectiveBotDifficulty)
        assertEquals(PlayerControllerType.BOT, seat0.toPlayerSetup().controllerType)
        assertFalse(room.isBotCoordinator("u1"))
        assertTrue(room.isBotCoordinator("u2"))
    }

    @Test
    fun weekKeyUtil_formatsIsoStyleWeek() {
        val key = WeekKeyUtil.currentWeekKey(1_700_000_000_000L)
        assertTrue("Unexpected week key format: $key", Regex("^\\d{4}-W\\d{2}$").matches(key))
    }

    @Test
    fun savedGameJson_roundTripsPowerUpGameStateLosslessly() {
        val setups = listOf(
            PlayerSetup(0, "Player 1", PlayerColor.RED, PlayerControllerType.HUMAN_LOCAL),
            PlayerSetup(1, "Bot 2", PlayerColor.YELLOW, PlayerControllerType.BOT, BotDifficulty.MEDIUM)
        )
        val engine = LudoEngine(RuleSetRegistry.forMode(GameModeId.POWER_UP), Random(99))
        val initial = engine.createGame(setups, GameModeId.POWER_UP).copy(
            turnNumber = 14,
            powerUpState = PowerUpState(
                tiles = listOf(PowerUpTile(1, 22, PowerUpType.SHIELD)),
                bombs = listOf(BombTrap(2, PlayerColor.RED, 19, 3)),
                nextEntityId = 5
            )
        )

        val json = com.example.data.SavedGameRepository.encodeGameStateToJson(initial)
        val restored = com.example.data.SavedGameRepository.decodeGameStateFromJson(json)

        assertNotNull(restored)
        assertEquals(GameModeId.POWER_UP, restored!!.mode)
        assertEquals(14, restored.turnNumber)
        assertEquals(1, restored.powerUpState.tiles.size)
        assertEquals(19, restored.powerUpState.bombs.first().trackIndex)
    }

    @Test
    fun matchHistory_buildsAndRoundTripsLocalJson() {
        val setups = listOf(
            PlayerSetup(0, "M", PlayerColor.RED, PlayerControllerType.HUMAN_LOCAL),
            PlayerSetup(1, "Bot", PlayerColor.YELLOW, PlayerControllerType.BOT, BotDifficulty.HARD)
        )
        val engine = LudoEngine(RuleSetRegistry.forMode(GameModeId.CLASSIC), Random(7))
        val finished = engine.createGame(setups, GameModeId.CLASSIC).copy(
            phase = TurnPhase.MATCH_FINISHED,
            finishOrder = listOf(PlayerColor.RED, PlayerColor.YELLOW),
            turnNumber = 42
        )

        val entry = com.example.data.MatchHistoryRepository.buildEntryFromGameState(
            finalState = finished,
            onlineMatch = true,
            roomCode = "LUDO99",
            localColor = PlayerColor.RED
        )
        assertEquals("M", entry.winnerName)
        assertTrue(entry.won)
        assertEquals(1, entry.rank)
        assertEquals(42, entry.turnsPlayed)

        val encoded = com.example.data.MatchHistoryRepository.encodeLocalList(listOf(entry))
        val decoded = com.example.data.MatchHistoryRepository.decodeLocalList(encoded)
        assertEquals(1, decoded.size)
        assertEquals("LUDO99", decoded.first().roomCode)
        assertEquals(GameModeId.CLASSIC, decoded.first().mode)
    }

    @Test
    fun engineTracksCapturesAndPowerUpsUsedAndMapperPreservesThem() {
        val setups = listOf(
            PlayerSetup(0, "Alice", PlayerColor.RED, PlayerControllerType.HUMAN_LOCAL),
            PlayerSetup(1, "Bob", PlayerColor.YELLOW, PlayerControllerType.HUMAN_LOCAL)
        )
        val engine = LudoEngine(RuleSetRegistry.forMode(GameModeId.POWER_UP), Random(77))
        var state = engine.createGame(setups, GameModeId.POWER_UP)
        // Red trackIndex(2) = 2; Red trackIndex(5) = 5. Yellow start = 26 -> progress for track 5 = 31.
        state = state.copy(
            players = state.players.map { p ->
                when (p.color) {
                    PlayerColor.RED -> p.copy(
                        heldPowerUps = listOf(PowerUpType.DOUBLE_DICE, PowerUpType.SHIELD),
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 2) else t }
                    )
                    PlayerColor.YELLOW -> p.copy(
                        tokens = p.tokens.mapIndexed { idx, t -> if (idx == 0) t.copy(progress = 31) else t }
                    )
                    else -> p
                }
            }
        )

        state = engine.activateShield(state, 0)
        assertEquals(1, state.playerByColor(PlayerColor.RED)!!.powerUpsUsed)

        state = engine.activateDoubleDice(state)
        assertEquals(2, state.playerByColor(PlayerColor.RED)!!.powerUpsUsed)

        // Roll 1 + 2 = 3 -> Red token 0 moves from progress 2 to 5 (track 5, capturing Yellow token 0!)
        val rollOutcome = engine.applyDiceRoll(state, 1, 2)
        val singleMove = rollOutcome as com.example.engine.RollOutcome.SingleLegalMove
        val moveOutcome = engine.applyMove(singleMove.state, singleMove.move)
        val afterCapture = moveOutcome.state

        assertEquals(1, moveOutcome.move.capturedTokens.size)
        assertEquals(1, afterCapture.playerByColor(PlayerColor.RED)!!.capturesMade)
        assertEquals(2, afterCapture.playerByColor(PlayerColor.RED)!!.powerUpsUsed)

        // Verify Firestore mapper preserves capturesMade & powerUpsUsed
        val mapped = GameStateFirestoreMapper.toFirestoreMap(afterCapture)
        val restored = GameStateFirestoreMapper.fromFirestoreMap(mapped)!!
        assertEquals(1, restored.playerByColor(PlayerColor.RED)!!.capturesMade)
        assertEquals(2, restored.playerByColor(PlayerColor.RED)!!.powerUpsUsed)
    }

    @Test
    fun matchHistory_preservesDetailedStatsAndPlayersRoundTrip() {
        val setups = listOf(
            PlayerSetup(0, "M", PlayerColor.RED, PlayerControllerType.HUMAN_LOCAL),
            PlayerSetup(1, "Bot", PlayerColor.YELLOW, PlayerControllerType.BOT, BotDifficulty.HARD)
        )
        val engine = LudoEngine(RuleSetRegistry.forMode(GameModeId.POWER_UP), Random(11))
        val base = engine.createGame(setups, GameModeId.POWER_UP)
        val finished = base.copy(
            phase = TurnPhase.MATCH_FINISHED,
            finishOrder = listOf(PlayerColor.RED, PlayerColor.YELLOW),
            turnNumber = 36,
            players = base.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(
                        capturesMade = 4,
                        powerUpsUsed = 3,
                        tokens = p.tokens.map { it.copy(progress = 56) }
                    )
                } else {
                    p.copy(capturesMade = 1, powerUpsUsed = 2)
                }
            }
        )

        val entry = com.example.data.MatchHistoryRepository.buildEntryFromGameState(
            finalState = finished,
            onlineMatch = false,
            localColor = PlayerColor.RED
        )
        assertEquals(4, entry.capturesMade)
        assertEquals(4, entry.tokensHome)
        assertEquals(3, entry.powerUpsUsed)
        assertEquals(2, entry.playerDetails.size)
        assertEquals("M", entry.playerDetails[0].nickname)
        assertEquals(4, entry.playerDetails[0].capturesMade)
        assertEquals(3, entry.playerDetails[0].powerUpsUsed)

        val encoded = com.example.data.MatchHistoryRepository.encodeLocalList(listOf(entry))
        val decoded = com.example.data.MatchHistoryRepository.decodeLocalList(encoded)
        assertEquals(1, decoded.size)
        val restored = decoded.first()
        assertEquals(4, restored.capturesMade)
        assertEquals(4, restored.tokensHome)
        assertEquals(3, restored.powerUpsUsed)
        assertEquals(2, restored.playerDetails.size)
        assertEquals(4, restored.playerDetails[0].capturesMade)
        assertEquals(2, restored.playerDetails[1].powerUpsUsed)
    }

    @Test
    fun statsHistoryOverview_computesOverallPerModeFavoriteAndBestWinRate() {
        val matches = listOf(
            com.example.data.MatchHistoryEntry(
                id = "1",
                mode = GameModeId.CLASSIC,
                onlineMatch = false,
                roomCode = "",
                winnerName = "M",
                winnerColor = PlayerColor.RED,
                won = true,
                rank = 1,
                playerSummary = "M vs Bot",
                turnsPlayed = 30,
                playedAtMs = 1000L,
                capturesMade = 3,
                tokensHome = 4,
                powerUpsUsed = 0
            ),
            com.example.data.MatchHistoryEntry(
                id = "2",
                mode = GameModeId.CLASSIC,
                onlineMatch = false,
                roomCode = "",
                winnerName = "Bot",
                winnerColor = PlayerColor.YELLOW,
                won = false,
                rank = 2,
                playerSummary = "M vs Bot",
                turnsPlayed = 28,
                playedAtMs = 2000L,
                capturesMade = 1,
                tokensHome = 2,
                powerUpsUsed = 0
            ),
            com.example.data.MatchHistoryEntry(
                id = "3",
                mode = GameModeId.POWER_UP,
                onlineMatch = true,
                roomCode = "ROOM88",
                winnerName = "M",
                winnerColor = PlayerColor.RED,
                won = true,
                rank = 1,
                playerSummary = "M, Alex, Sam, Jo",
                turnsPlayed = 40,
                playedAtMs = 3000L,
                capturesMade = 5,
                tokensHome = 4,
                powerUpsUsed = 4
            )
        )

        val overview = com.example.ui.online.StatsHistoryViewModel.computeOverview(matches)
        assertEquals(3, overview.overall.gamesPlayed)
        assertEquals(2, overview.overall.wins)
        assertEquals(66, overview.overall.winRatePercent)
        assertEquals(9, overview.overall.capturesMade)
        assertEquals(10, overview.overall.tokensHome)
        assertEquals(4, overview.overall.powerUpsUsed)
        assertEquals(GameModeId.CLASSIC, overview.favoriteMode)
        assertEquals(GameModeId.POWER_UP to 100, overview.bestWinRateMode)

        val classic = overview.byMode[GameModeId.CLASSIC]!!
        assertEquals(2, classic.gamesPlayed)
        assertEquals(1, classic.wins)
        assertEquals(50, classic.winRatePercent)
        assertEquals(4, classic.capturesMade)
        assertEquals(6, classic.tokensHome)

        val powerUp = overview.byMode[GameModeId.POWER_UP]!!
        assertEquals(1, powerUp.gamesPlayed)
        assertEquals(1, powerUp.wins)
        assertEquals(100, powerUp.winRatePercent)
        assertEquals(4, powerUp.powerUpsUsed)
    }
}
