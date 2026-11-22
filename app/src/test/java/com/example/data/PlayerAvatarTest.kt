package com.example.data

import com.example.bot.MatchSeatPlanner
import com.example.bot.SeatConfig
import com.example.bot.SeatOccupancy
import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.online.GameStateFirestoreMapper
import com.example.online.OnlineRoomSeat
import com.example.online.SeatControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlayerAvatarTest {

    @Test
    fun encodeAndDecodeRoundTripAllEnumValuesWithinFirestoreLimit() {
        for (character in AvatarCharacter.entries) {
            for (background in AvatarBackground.entries) {
                for (frame in AvatarFrame.entries) {
                    for (accessory in AvatarAccessory.entries) {
                        val config = PlayerAvatarConfig(
                            character = character,
                            background = background,
                            frame = frame,
                            accessory = accessory
                        )
                        val encoded = config.encode()
                        assertTrue(
                            "Encoded avatar '$encoded' exceeds max length ${PlayerAvatarConfig.MAX_ENCODED_LENGTH}",
                            encoded.length <= PlayerAvatarConfig.MAX_ENCODED_LENGTH
                        )
                        assertEquals(config, PlayerAvatarCodec.decode(encoded))
                    }
                }
            }
        }
    }

    @Test
    fun decodeHandlesCaseInsensitivityAndWhitespace() {
        val raw = "  V1:astronaut:sunset:double_rim:headphones  "
        val decoded = PlayerAvatarCodec.decode(raw)
        assertEquals(
            PlayerAvatarConfig(
                character = AvatarCharacter.ASTRONAUT,
                background = AvatarBackground.SUNSET,
                frame = AvatarFrame.DOUBLE_RIM,
                accessory = AvatarAccessory.HEADPHONES
            ),
            decoded
        )
    }

    @Test
    fun decodeRejectsInvalidInputsGracefully() {
        assertNull(PlayerAvatarCodec.decode(null))
        assertNull(PlayerAvatarCodec.decode(""))
        assertNull(PlayerAvatarCodec.decode("   "))
        assertNull(PlayerAvatarCodec.decode("v2:FOX:COBALT:CLASSIC:NONE"))
        assertNull(PlayerAvatarCodec.decode("v1:FOX:COBALT:CLASSIC"))
        assertNull(PlayerAvatarCodec.decode("v1:UNICORN:COBALT:CLASSIC:NONE"))
        assertNull(PlayerAvatarCodec.decode("v1:FOX:RAINBOW:CLASSIC:NONE"))
        assertNull(PlayerAvatarCodec.decode("v1:FOX:COBALT:SQUARE:NONE"))
        assertNull(PlayerAvatarCodec.decode("v1:FOX:COBALT:CLASSIC:JETPACK"))
        assertNull(PlayerAvatarCodec.decode("v1:" + "A".repeat(70)))
    }

    @Test
    fun sanitizeReturnsCanonicalEncodedStringOrEmpty() {
        assertEquals(
            "v1:LION:GOLD:STAR_HALO:CROWN",
            PlayerAvatarCodec.sanitize(" v1:lion:gold:star_halo:crown ")
        )
        assertEquals("", PlayerAvatarCodec.sanitize("malformed"))
        assertEquals("", PlayerAvatarCodec.sanitize(null))
    }

    @Test
    fun resolveUsesValidAvatarOrColorHarmonizedAndBotFallbacks() {
        val custom = PlayerAvatarConfig(
            character = AvatarCharacter.WIZARD,
            background = AvatarBackground.ROYAL,
            frame = AvatarFrame.STAR_HALO,
            accessory = AvatarAccessory.HALO
        )
        assertEquals(
            custom,
            PlayerAvatarCodec.resolve(custom.encode(), fallbackColor = PlayerColor.RED, isBot = false)
        )

        val botFallback = PlayerAvatarCodec.resolve("", fallbackColor = PlayerColor.GREEN, isBot = true, seed = 2)
        assertEquals(AvatarCharacter.ROBOT, botFallback.character)
        assertEquals(AvatarBackground.MINT, botFallback.background)

        val colorDefaults = PlayerColor.entries.map { color ->
            PlayerAvatarCodec.resolve("", fallbackColor = color, isBot = false)
        }
        assertEquals(4, colorDefaults.map { it.character }.toSet().size)
        assertEquals(4, colorDefaults.map { it.background }.toSet().size)
    }

    @Test
    fun curatedPresetsHaveUniqueIdsAndValidRoundTrips() {
        val presets = PlayerAvatarCodec.CURATED_PRESETS
        assertTrue(presets.size >= 8)
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        presets.forEach { preset ->
            val encoded = preset.config.encode()
            assertTrue(encoded.length <= PlayerAvatarConfig.MAX_ENCODED_LENGTH)
            assertEquals(preset.config, PlayerAvatarCodec.decode(encoded))
        }
    }

    @Test
    fun randomizeProducesValidVariedAvatars() {
        val first = PlayerAvatarCodec.randomize(Random(101))
        val second = PlayerAvatarCodec.randomize(Random(202))
        assertEquals(first, PlayerAvatarCodec.decode(first.encode()))
        assertEquals(second, PlayerAvatarCodec.decode(second.encode()))
        assertNotEquals(first, second)
    }

    @Test
    fun seatPlannerOnlineSeatAndFirestoreMapperPreserveAvatarId() {
        val customAvatar = PlayerAvatarConfig(
            character = AvatarCharacter.DRAGON,
            background = AvatarBackground.BERRY,
            frame = AvatarFrame.NEON_RING,
            accessory = AvatarAccessory.CROWN
        ).encode()

        val botAvatar = PlayerAvatarConfig(
            character = AvatarCharacter.NINJA,
            background = AvatarBackground.OCEAN,
            frame = AvatarFrame.DASHED,
            accessory = AvatarAccessory.SUNGLASSES
        ).encode()

        val setups = MatchSeatPlanner.buildMatchSetups(
            seats = listOf(
                SeatConfig(0, SeatOccupancy.HUMAN, "Maya", PlayerColor.RED, avatarId = customAvatar),
                SeatConfig(1, SeatOccupancy.BOT, "Shadow Bot", PlayerColor.YELLOW, BotDifficulty.HARD, avatarId = botAvatar),
                SeatConfig(2, SeatOccupancy.EMPTY, "Player 3", PlayerColor.GREEN),
                SeatConfig(3, SeatOccupancy.EMPTY, "Player 4", PlayerColor.BLUE)
            ),
            targetPlayerCount = 2,
            mode = GameModeId.CLASSIC
        )

        assertEquals(2, setups.size)
        assertEquals(customAvatar, setups[0].avatarId)
        assertEquals(botAvatar, setups[1].avatarId)

        // OnlineRoomSeat Firestore map round-trip
        val seat = OnlineRoomSeat(
            seatIndex = 0,
            color = PlayerColor.RED,
            uid = "user_1",
            nickname = "Maya",
            avatarId = customAvatar,
            controllerType = SeatControl.HUMAN,
            connected = true
        )
        val roundTrippedSeat = OnlineRoomSeat.fromFirestoreMap(seat.toFirestoreMap())
        assertEquals(customAvatar, roundTrippedSeat.avatarId)
        assertEquals(customAvatar, roundTrippedSeat.toPlayerSetup().avatarId)

        // GameStateFirestoreMapper (used by both online rooms and local resume) round-trip
        val state = LudoEngine().createGame(playerSetups = setups, mode = GameModeId.CLASSIC)
        val restored = GameStateFirestoreMapper.fromFirestoreMap(GameStateFirestoreMapper.toFirestoreMap(state))
        assertNotNull(restored)
        assertEquals(customAvatar, restored!!.players[0].avatarId)
        assertEquals(botAvatar, restored.players[1].avatarId)
    }
}
