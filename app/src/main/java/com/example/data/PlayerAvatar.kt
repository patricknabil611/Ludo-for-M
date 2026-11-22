package com.example.data

import com.example.engine.PlayerColor
import kotlin.random.Random

/**
 * Expressive cartoon characters available for player and bot profiles.
 */
enum class AvatarCharacter(
    val displayName: String,
    val tagline: String
) {
    FOX("Fox", "Clever & quick"),
    LION("Lion", "Board royalty"),
    PANDA("Panda", "Chill strategist"),
    FROG("Frog", "Big hopper"),
    CAT("Cat", "Lucky paws"),
    OWL("Owl", "Night tactician"),
    DRAGON("Dragon", "Fiery roller"),
    ASTRONAUT("Astro", "Star chaser"),
    KNIGHT("Knight", "Safe-star guard"),
    WIZARD("Wizard", "Six conjurer"),
    NINJA("Ninja", "Silent capturer"),
    ROBOT("Bot", "Precision dice")
}

/**
 * Vibrant cartoon backdrop palettes for avatars.
 */
enum class AvatarBackground(
    val displayName: String
) {
    COBALT("Cobalt"),
    SUNSET("Sunset"),
    MINT("Mint"),
    BERRY("Berry"),
    ROYAL("Royal"),
    OCEAN("Ocean"),
    GOLD("Gold"),
    CORAL("Coral")
}

/**
 * Decorative circular rim styles surrounding the avatar portrait.
 */
enum class AvatarFrame(
    val displayName: String
) {
    CLASSIC("Classic"),
    GOLD_RING("Gold"),
    DASHED("Stitched"),
    STAR_HALO("Starry"),
    NEON_RING("Glow"),
    DOUBLE_RIM("Double")
}

/**
 * Optional playful flair worn by the character.
 */
enum class AvatarAccessory(
    val displayName: String
) {
    NONE("None"),
    CROWN("Crown"),
    SUNGLASSES("Shades"),
    STAR_PIN("Star Pin"),
    HALO("Halo"),
    HEADPHONES("Beats")
}

/**
 * Immutable, Firestore-safe avatar configuration composed of 4 customizable layers.
 */
data class PlayerAvatarConfig(
    val character: AvatarCharacter = AvatarCharacter.FOX,
    val background: AvatarBackground = AvatarBackground.COBALT,
    val frame: AvatarFrame = AvatarFrame.CLASSIC,
    val accessory: AvatarAccessory = AvatarAccessory.NONE
) {
    /**
     * Compact deterministic string format (`v1:CHARACTER:BACKGROUND:FRAME:ACCESSORY`),
     * guaranteed to be <= 64 characters for Firestore validation rules.
     */
    fun encode(): String =
        "$VERSION_PREFIX:${character.name}:${background.name}:${frame.name}:${accessory.name}"

    fun accessibilityDescription(): String {
        val flair = if (accessory == AvatarAccessory.NONE) "" else " with ${accessory.displayName}"
        return "${character.displayName} avatar$flair"
    }

    companion object {
        const val VERSION_PREFIX = "v1"
        const val MAX_ENCODED_LENGTH = 64
    }
}

data class AvatarPreset(
    val id: String,
    val label: String,
    val config: PlayerAvatarConfig
)

/**
 * Serialization, fallback resolution, randomization, and curated presets for player avatars.
 */
object PlayerAvatarCodec {

    val DEFAULT_PLAYER_AVATAR = PlayerAvatarConfig(
        character = AvatarCharacter.FOX,
        background = AvatarBackground.COBALT,
        frame = AvatarFrame.GOLD_RING,
        accessory = AvatarAccessory.NONE
    )

    val CURATED_PRESETS: List<AvatarPreset> = listOf(
        AvatarPreset(
            id = "lucky_fox",
            label = "Lucky Fox",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.FOX,
                background = AvatarBackground.COBALT,
                frame = AvatarFrame.GOLD_RING,
                accessory = AvatarAccessory.STAR_PIN
            )
        ),
        AvatarPreset(
            id = "royal_lion",
            label = "Royal Lion",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.LION,
                background = AvatarBackground.SUNSET,
                frame = AvatarFrame.DOUBLE_RIM,
                accessory = AvatarAccessory.CROWN
            )
        ),
        AvatarPreset(
            id = "zen_panda",
            label = "Zen Panda",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.PANDA,
                background = AvatarBackground.MINT,
                frame = AvatarFrame.CLASSIC,
                accessory = AvatarAccessory.HEADPHONES
            )
        ),
        AvatarPreset(
            id = "cosmic_astro",
            label = "Cosmic Astro",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.ASTRONAUT,
                background = AvatarBackground.ROYAL,
                frame = AvatarFrame.STAR_HALO,
                accessory = AvatarAccessory.NONE
            )
        ),
        AvatarPreset(
            id = "cool_cat",
            label = "Cool Cat",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.CAT,
                background = AvatarBackground.CORAL,
                frame = AvatarFrame.NEON_RING,
                accessory = AvatarAccessory.SUNGLASSES
            )
        ),
        AvatarPreset(
            id = "magic_owl",
            label = "Magic Owl",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.OWL,
                background = AvatarBackground.OCEAN,
                frame = AvatarFrame.DASHED,
                accessory = AvatarAccessory.HALO
            )
        ),
        AvatarPreset(
            id = "blaze_dragon",
            label = "Blaze Dragon",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.DRAGON,
                background = AvatarBackground.GOLD,
                frame = AvatarFrame.GOLD_RING,
                accessory = AvatarAccessory.CROWN
            )
        ),
        AvatarPreset(
            id = "cyber_bot",
            label = "Cyber Bot",
            config = PlayerAvatarConfig(
                character = AvatarCharacter.ROBOT,
                background = AvatarBackground.BERRY,
                frame = AvatarFrame.NEON_RING,
                accessory = AvatarAccessory.HEADPHONES
            )
        )
    )

    /**
     * Parses an encoded `avatarId` string back into a [PlayerAvatarConfig].
     * Returns `null` if blank or malformed.
     */
    fun decode(raw: String?): PlayerAvatarConfig? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty() || cleaned.length > PlayerAvatarConfig.MAX_ENCODED_LENGTH) return null
        val parts = cleaned.split(":")
        if (parts.size != 5 || !parts[0].equals(PlayerAvatarConfig.VERSION_PREFIX, ignoreCase = true)) {
            return null
        }
        val character = AvatarCharacter.entries.firstOrNull { it.name.equals(parts[1], ignoreCase = true) }
            ?: return null
        val background = AvatarBackground.entries.firstOrNull { it.name.equals(parts[2], ignoreCase = true) }
            ?: return null
        val frame = AvatarFrame.entries.firstOrNull { it.name.equals(parts[3], ignoreCase = true) }
            ?: return null
        val accessory = AvatarAccessory.entries.firstOrNull { it.name.equals(parts[4], ignoreCase = true) }
            ?: return null
        return PlayerAvatarConfig(
            character = character,
            background = background,
            frame = frame,
            accessory = accessory
        )
    }

    /**
     * Sanitizes a user-provided or stored avatar string for safe persistence.
     * Returns empty string when invalid so callers can fall back cleanly.
     */
    fun sanitize(raw: String?): String = decode(raw)?.encode().orEmpty()

    /**
     * Resolves an encoded [raw] avatar string or generates a vibrant, deterministic fallback
     * based on the player's board [fallbackColor], [isBot] flag, and [seed].
     */
    fun resolve(
        raw: String?,
        fallbackColor: PlayerColor? = null,
        isBot: Boolean = false,
        seed: Int = 0
    ): PlayerAvatarConfig {
        decode(raw)?.let { return it }

        if (isBot) {
            val botBg = when (fallbackColor) {
                PlayerColor.RED -> AvatarBackground.CORAL
                PlayerColor.GREEN -> AvatarBackground.MINT
                PlayerColor.YELLOW -> AvatarBackground.GOLD
                PlayerColor.BLUE -> AvatarBackground.OCEAN
                null -> AvatarBackground.entries[Math.floorMod(seed, AvatarBackground.entries.size)]
            }
            val botFrame = when (Math.floorMod(seed, 3)) {
                0 -> AvatarFrame.NEON_RING
                1 -> AvatarFrame.DOUBLE_RIM
                else -> AvatarFrame.DASHED
            }
            val botAcc = when (Math.floorMod(seed, 4)) {
                0 -> AvatarAccessory.HEADPHONES
                1 -> AvatarAccessory.STAR_PIN
                2 -> AvatarAccessory.SUNGLASSES
                else -> AvatarAccessory.NONE
            }
            return PlayerAvatarConfig(
                character = AvatarCharacter.ROBOT,
                background = botBg,
                frame = botFrame,
                accessory = botAcc
            )
        }

        if (fallbackColor != null) {
            return when (fallbackColor) {
                PlayerColor.RED -> PlayerAvatarConfig(
                    character = AvatarCharacter.FOX,
                    background = AvatarBackground.CORAL,
                    frame = AvatarFrame.CLASSIC,
                    accessory = AvatarAccessory.NONE
                )
                PlayerColor.GREEN -> PlayerAvatarConfig(
                    character = AvatarCharacter.FROG,
                    background = AvatarBackground.MINT,
                    frame = AvatarFrame.CLASSIC,
                    accessory = AvatarAccessory.NONE
                )
                PlayerColor.YELLOW -> PlayerAvatarConfig(
                    character = AvatarCharacter.LION,
                    background = AvatarBackground.GOLD,
                    frame = AvatarFrame.GOLD_RING,
                    accessory = AvatarAccessory.NONE
                )
                PlayerColor.BLUE -> PlayerAvatarConfig(
                    character = AvatarCharacter.ASTRONAUT,
                    background = AvatarBackground.COBALT,
                    frame = AvatarFrame.CLASSIC,
                    accessory = AvatarAccessory.NONE
                )
            }
        }

        if (seed != 0) {
            return CURATED_PRESETS[Math.floorMod(seed, CURATED_PRESETS.size)].config
        }
        return DEFAULT_PLAYER_AVATAR
    }

    /**
     * Generates a fun, varied random avatar combination.
     */
    fun randomize(random: Random = Random.Default): PlayerAvatarConfig {
        val chars = AvatarCharacter.entries
        val bgs = AvatarBackground.entries
        val frames = AvatarFrame.entries
        val accessories = AvatarAccessory.entries
        return PlayerAvatarConfig(
            character = chars[random.nextInt(chars.size)],
            background = bgs[random.nextInt(bgs.size)],
            frame = frames[random.nextInt(frames.size)],
            accessory = accessories[random.nextInt(accessories.size)]
        )
    }
}
