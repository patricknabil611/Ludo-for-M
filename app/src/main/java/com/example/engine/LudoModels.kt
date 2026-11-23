package com.example.engine

/**
 * Standard Ludo board colors ordered clockwise starting from Top-Left.
 * Opposite corners are (RED, YELLOW) and (GREEN, BLUE).
 */
enum class PlayerColor(
    val displayName: String,
    val startTrackIndex: Int,
    val cornerOrder: Int
) {
    RED("Red", startTrackIndex = 0, cornerOrder = 0),
    GREEN("Green", startTrackIndex = 13, cornerOrder = 1),
    YELLOW("Yellow", startTrackIndex = 26, cornerOrder = 2),
    BLUE("Blue", startTrackIndex = 39, cornerOrder = 3);

    fun opposite(): PlayerColor = when (this) {
        RED -> YELLOW
        GREEN -> BLUE
        YELLOW -> RED
        BLUE -> GREEN
    }
}

enum class PlayerControllerType {
    HUMAN_LOCAL,
    BOT,
    ONLINE_REMOTE
}

enum class BotDifficulty(val displayName: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard")
}

/**
 * Match formats selectable independently from the game mode.
 */
enum class MatchFormat(
    val displayName: String,
    val shortLabel: String,
    val playerCount: Int,
    val isTeamFormat: Boolean = false
) {
    ONE_VS_ONE("1v1", "1v1", playerCount = 2, isTeamFormat = false),
    FOUR_PLAYERS("4 Players", "4P", playerCount = 4, isTeamFormat = false),
    TEAMS_2V2("2v2 Team", "2v2", playerCount = 4, isTeamFormat = true);

    val label: String get() = displayName

    companion object {
        fun fromRaw(raw: String?, fallbackPlayerCount: Int = 4): MatchFormat {
            entries.firstOrNull { it.name == raw }?.let { return it }
            return when (raw?.uppercase()) {
                "1V1", "TWO_PLAYERS" -> ONE_VS_ONE
                "2V2", "TEAMS", "TEAMS_2V2" -> TEAMS_2V2
                "4P", "FOUR_PLAYERS" -> FOUR_PLAYERS
                else -> if (fallbackPlayerCount <= 2) ONE_VS_ONE else FOUR_PLAYERS
            }
        }
    }
}

/**
 * Turn timer setting (seconds per turn; 0 = Off). Default is 15 seconds.
 */
enum class TurnTimerOption(
    val seconds: Int,
    val label: String
) {
    OFF(0, "Off"),
    TEN(10, "10s"),
    FIFTEEN(15, "15s"),
    TWENTY(20, "20s");

    companion object {
        const val DEFAULT_SECONDS = 15

        fun fromSeconds(seconds: Int): TurnTimerOption =
            entries.firstOrNull { it.seconds == seconds } ?: FIFTEEN
    }
}

/**
 * All 9 game modes in Ludo for M v2.1:
 * - 6 Main Modes: Classic, Blockades, Master, Quick, Arrow, Magic
 * - 3 Extra Modes: Timed, Chaos, Elimination
 */
enum class GameModeId(
    val title: String,
    val subtitle: String,
    val isMainMode: Boolean,
    val availableNow: Boolean = true,
    val minPlayers: Int = 2,
    val maxPlayers: Int = 4
) {
    // Main Modes
    CLASSIC(
        title = "Classic",
        subtitle = "The original race. Bring all 4 tokens home.",
        isMainMode = true
    ),
    BLOCKADES(
        title = "Blockades",
        subtitle = "Pair up your tokens to build walls nobody can pass.",
        isMainMode = true
    ),
    MASTER(
        title = "Master",
        subtitle = "Capture first to unlock home. Merge tokens into Joint Tokens.",
        isMainMode = true
    ),
    QUICK(
        title = "Quick",
        subtitle = "Start with a token out. First token home wins.",
        isMainMode = true
    ),
    ARROW(
        title = "Arrow",
        subtitle = "Land on arrows to shoot ahead.",
        isMainMode = true
    ),
    MAGIC(
        title = "Magic",
        subtitle = "Grab Rockets, Golden Dice, Shields and Bombs.",
        isMainMode = true
    ),

    // Extra Modes
    TIMED(
        title = "Timed",
        subtitle = "Score points for moves, captures & homes before the clock runs out.",
        isMainMode = false
    ),
    CHAOS(
        title = "Chaos",
        subtitle = "Wild board events shake up the track every 3 full rounds.",
        isMainMode = false
    ),
    ELIMINATION(
        title = "Elimination",
        subtitle = "Every 5 rounds, the lowest-progress player is knocked out.",
        isMainMode = false
    );

    companion object {
        val mainModes: List<GameModeId> = entries.filter { it.isMainMode }
        val extraModes: List<GameModeId> = entries.filter { !it.isMainMode }

        /**
         * Migrates legacy saved mode IDs (v1/v2.0) to v2.1 modes + optional implied format:
         * - POWER_UP -> MAGIC
         * - ONE_TOKEN_RACE -> QUICK
         * - TEAMS / TEAMS_2V2 -> CLASSIC with MatchFormat.TEAMS_2V2
         * - TIMED, CHAOS, ELIMINATION keep their IDs
         */
        fun fromLegacyName(raw: String?): Pair<GameModeId, MatchFormat?> {
            if (raw.isNullOrBlank()) return CLASSIC to null
            entries.firstOrNull { it.name == raw }?.let { return it to null }
            return when (raw.trim().uppercase()) {
                "POWER_UP" -> MAGIC to null
                "ONE_TOKEN_RACE" -> QUICK to null
                "TEAMS", "TEAMS_2V2" -> CLASSIC to MatchFormat.TEAMS_2V2
                else -> CLASSIC to null
            }
        }
    }
}

/**
 * Magic items exclusive to Magic mode (collected and used instantly on landing).
 */
enum class PowerUpType(
    val title: String,
    val shortLabel: String,
    val description: String
) {
    ROCKET(
        title = "Rocket",
        shortLabel = "Rocket",
        description = "Launches this token forward a random 1–8 squares."
    ),
    GOLDEN_DICE(
        title = "Golden Dice",
        shortLabel = "Golden 6",
        description = "Your next dice roll is a guaranteed 6!"
    ),
    SHIELD(
        title = "Shield",
        shortLabel = "Shield",
        description = "Protects this token from capture for your next 2 turns."
    ),
    BOMB_TRAP(
        title = "Bomb Trap",
        shortLabel = "Bomb",
        description = "Hide a bomb within 6 squares ahead; opponents who land on it return to base."
    );

    companion object {
        fun fromLegacyName(raw: String?): PowerUpType? {
            if (raw.isNullOrBlank()) return null
            entries.firstOrNull { it.name == raw }?.let { return it }
            return when (raw.trim().uppercase()) {
                "DOUBLE_DICE" -> GOLDEN_DICE
                else -> null
            }
        }
    }
}

data class PowerUpTile(
    val id: Int,
    val trackIndex: Int,
    val type: PowerUpType
)

data class BombTrap(
    val id: Int,
    val ownerColor: PlayerColor,
    val trackIndex: Int,
    val roundsRemaining: Int = PowerUpState.BOMB_LIFETIME_ROUNDS
)

data class PendingPowerUpRespawn(
    val roundsRemaining: Int = PowerUpState.RESPAWN_ROUNDS
)

/**
 * Set transiently in Magic mode right after a token lands on a Bomb Trap item,
 * prompting the player (or bot) to pick any empty non-safe square within 6 squares ahead.
 */
data class PendingBombPlacement(
    val ownerColor: PlayerColor,
    val sourceTokenColor: PlayerColor,
    val sourceTokenId: Int,
    val validTrackIndices: List<Int>
)

data class PowerUpState(
    val tiles: List<PowerUpTile> = emptyList(),
    val pendingRespawns: List<PendingPowerUpRespawn> = emptyList(),
    val bombs: List<BombTrap> = emptyList(),
    val nextEntityId: Int = 1,
    val pendingBombPlacement: PendingBombPlacement? = null,
    val roundsUntilNextSpawn: Int = RESPAWN_ROUNDS
) {
    companion object {
        const val MAX_TILES_ON_BOARD = 3
        const val MAX_HELD_PER_PLAYER = 2
        const val RESPAWN_ROUNDS = 2
        const val BOMB_LIFETIME_ROUNDS = 3
        const val SHIELD_DURATION_TURNS = 2
        const val BOMB_PLACEMENT_RANGE = 6
    }
}

enum class ChaosEventType(
    val title: String,
    val description: String
) {
    MOVING_STARS("Moving Stars!", "Safe star squares jumped to new random spots for 3 rounds!"),
    PORTALS("Portal Rift!", "Two linked portals appeared — land on one to teleport!"),
    DICE_STORM("Dice Storm!", "Every player's next roll is doubled (max 12)!"),
    EARTHQUAKE("Earthquake!", "A random token on the board shook back 3 squares!"),
    LUCKY_WIND("Lucky Wind!", "Every token in a home column glides forward 1 square!")
}

data class ChaosState(
    val activeSafeTrackIndices: Set<Int>? = null,
    val safeStarsRoundsRemaining: Int = 0,
    val portalPair: Pair<Int, Int>? = null,
    val portalsRoundsRemaining: Int = 0,
    val lastTriggeredEvent: ChaosEventType? = null,
    val activeAnnouncement: ChaosEventType? = null,
    val announcementDetail: String = "",
    val announcementId: Int = 0
)

data class PlayerSetup(
    val seatIndex: Int,
    val nickname: String,
    val color: PlayerColor,
    val controllerType: PlayerControllerType = PlayerControllerType.HUMAN_LOCAL,
    val botDifficulty: BotDifficulty = BotDifficulty.MEDIUM,
    val avatarId: String = ""
) {
    val isBot: Boolean get() = controllerType == PlayerControllerType.BOT
}

/**
 * Represents a single Ludo token.
 *
 * progress:
 * -1     -> In home base
 * 0..50  -> On the 52-cell outer shared track (0 is the player's start square)
 * 51..55 -> On the player's private colored home column (5 squares)
 * 56     -> Reached the center home triangle (finished, 57 steps from base / 56 from start)
 *
 * outerWrap51:
 * Used only in Master mode when a token circles past progress 50 onto the 52nd outer
 * track square ((start + 51) % 52) before wrapping back to progress 0.
 *
 * jointPartnerId:
 * Used only in Master mode when two tokens of the same color merge into a Joint Token
 * that moves as one unit using a single die value.
 */
data class Token(
    val id: Int,
    val color: PlayerColor,
    val progress: Int = BASE_PROGRESS,
    val outerWrap51: Boolean = false,
    val shieldTurnsRemaining: Int = 0,
    val jointPartnerId: Int? = null
) {
    val isInBase: Boolean get() = progress == BASE_PROGRESS && !outerWrap51
    val isHome: Boolean get() = progress == HOME_PROGRESS && !outerWrap51
    val isOnMainTrack: Boolean get() = outerWrap51 || (progress in 0..MAX_MAIN_TRACK_PROGRESS)
    val isInHomeColumn: Boolean
        get() = !outerWrap51 && progress in (MAX_MAIN_TRACK_PROGRESS + 1)..(HOME_PROGRESS - 1)
    val isShielded: Boolean get() = shieldTurnsRemaining > 0
    val isJoint: Boolean get() = jointPartnerId != null && !isInBase && !isHome

    fun outerTrackOffset(): Int? = when {
        outerWrap51 -> 51
        progress in 0..MAX_MAIN_TRACK_PROGRESS -> progress
        else -> null
    }

    fun mainTrackIndex(): Int? =
        outerTrackOffset()?.let { (color.startTrackIndex + it) % TRACK_LENGTH }

    companion object {
        const val BASE_PROGRESS = -1
        const val MAX_MAIN_TRACK_PROGRESS = 50
        const val HOME_PROGRESS = 56
        const val TRACK_LENGTH = 52
    }
}

data class PlayerState(
    val setup: PlayerSetup,
    val tokens: List<Token>,
    val hasCapturedOpponent: Boolean = false,
    val score: Int = 0,
    val isEliminated: Boolean = false,
    val diceStormCharges: Int = 0,
    val goldenDiceCharges: Int = 0,
    val heldPowerUps: List<PowerUpType> = emptyList(),
    val doubleDiceActive: Boolean = false,
    val capturesMade: Int = 0,
    val powerUpsUsed: Int = 0
) {
    val color: PlayerColor get() = setup.color
    val nickname: String get() = setup.nickname
    val avatarId: String get() = setup.avatarId
    val finishedTokenCount: Int get() = tokens.count { it.isHome }
    val allTokensHome: Boolean get() = tokens.all { it.isHome }
    val totalProgress: Int
        get() = tokens.sumOf {
            when {
                it.outerWrap51 -> 51
                it.progress > 0 -> it.progress
                else -> 0
            }
        }

    fun hasReachedGoal(tokensHomeToWin: Int): Boolean =
        finishedTokenCount >= tokensHomeToWin
}

data class StepTarget(
    val progress: Int,
    val outerWrap51: Boolean = false
)

data class LegalMove(
    val tokenId: Int,
    val color: PlayerColor,
    val fromProgress: Int,
    val fromOuterWrap51: Boolean = false,
    val toProgress: Int,
    val toOuterWrap51: Boolean = false,
    val stepPath: List<StepTarget>,
    val capturedTokens: List<TokenRef> = emptyList(),
    val reachesHome: Boolean = false,
    val leavesBase: Boolean = false,
    val teleportedFromTrackIndex: Int? = null,
    val teleportedToTrackIndex: Int? = null,
    val arrowFromTrackIndex: Int? = null,
    val arrowToTrackIndex: Int? = null,
    val collectedPowerUp: PowerUpType? = null,
    val collectedTileId: Int? = null,
    val triggeredBomb: BombTrap? = null,
    val dieValueUsed: Int = stepPath.size,
    val tokenIds: List<Int> = listOf(tokenId),
    val isJointMove: Boolean = false,
    val formsBlockade: Boolean = false,
    val rocketBoostSteps: Int = 0
) {
    fun includesToken(id: Int): Boolean = id == tokenId || id in tokenIds
}

data class TokenRef(
    val color: PlayerColor,
    val tokenId: Int
)

enum class TurnPhase {
    AWAITING_ROLL,
    AWAITING_MOVE,
    AWAITING_BOMB_PLACEMENT,
    NO_LEGAL_MOVES,
    TURN_CANCELLED_TRIPLE_SIX,
    MATCH_FINISHED
}

data class GameState(
    val mode: GameModeId = GameModeId.CLASSIC,
    val matchFormat: MatchFormat = MatchFormat.FOUR_PLAYERS,
    val turnTimerSeconds: Int = TurnTimerOption.DEFAULT_SECONDS,
    val players: List<PlayerState>,
    val activePlayerIndex: Int = 0,
    val phase: TurnPhase = TurnPhase.AWAITING_ROLL,
    val lastDiceValue: Int? = null,
    val lastRawDiceValue: Int? = null,
    val lastSecondDiceValue: Int? = null,
    val rolledDice: List<Int> = emptyList(),
    val remainingDice: List<Int> = emptyList(),
    val selectedDieValue: Int? = null,
    val earnedBonusRollThisTurn: Boolean = false,
    val pendingBonusRolls: Int = 0,
    val wasDiceDoubled: Boolean = false,
    val wasDoubleDiceRoll: Boolean = false,
    val wasGoldenDiceRoll: Boolean = false,
    val consecutiveSixes: Int = 0,
    val legalMoves: List<LegalMove> = emptyList(),
    val finishOrder: List<PlayerColor> = emptyList(),
    val eliminatedOrder: List<PlayerColor> = emptyList(),
    val turnNumber: Int = 1,
    val fullRoundsCompleted: Int = 0,
    val timedDurationSeconds: Int = 300,
    val timedRemainingSeconds: Int = 300,
    val winningTeamLabel: String? = null,
    val chaosState: ChaosState = ChaosState(),
    val powerUpState: PowerUpState = PowerUpState(),
    val lastEventMessage: String = "",
    val lastHomeUnlockedColor: PlayerColor? = null,
    val lastHomeUnlockEventId: Int = 0
) {
    val activePlayer: PlayerState get() = players[activePlayerIndex]
    val hasWinner: Boolean get() = finishOrder.isNotEmpty()
    val winner: PlayerColor? get() = finishOrder.firstOrNull()
    val isMatchFinished: Boolean get() = phase == TurnPhase.MATCH_FINISHED
    val isTeamMatch: Boolean get() = matchFormat.isTeamFormat

    fun playerByColor(color: PlayerColor): PlayerState? =
        players.firstOrNull { it.color == color }

    fun activeSafeTrackIndices(): Set<Int> =
        chaosState.activeSafeTrackIndices ?: LudoBoardGeometry.SAFE_TRACK_INDICES

    /**
     * Distinct unspent die values that currently have at least one legal token move.
     */
    fun playableDiceValues(): List<Int> {
        if (legalMoves.isEmpty()) return emptyList()
        val playableSet = legalMoves.map { it.dieValueUsed }.toSet()
        val ordered = remainingDice.distinct().filter { it in playableSet }
        return ordered.ifEmpty { playableSet.toList() }
    }

    /**
     * Active die value currently targeted by the player for their next token tap.
     */
    fun effectiveSelectedDieValue(): Int? {
        val playable = playableDiceValues()
        if (playable.isEmpty()) return null
        return if (selectedDieValue != null && selectedDieValue in playable) {
            selectedDieValue
        } else {
            playable.first()
        }
    }

    /**
     * Legal moves filtered to the currently selected die number (for board highlighting).
     */
    fun legalMovesForSelectedDie(): List<LegalMove> {
        val chosenDie = effectiveSelectedDieValue() ?: return legalMoves
        return legalMoves.filter { it.dieValueUsed == chosenDie }
    }

    /**
     * Resolves the legal move for [tokenRef], preferring the currently selected die value
     * and gracefully falling back to another remaining die if only that die can move [tokenRef].
     * Also matches either token ID of a Master Joint Token.
     */
    fun resolveMoveForToken(tokenRef: TokenRef): LegalMove? {
        val chosenDie = effectiveSelectedDieValue()
        if (chosenDie != null) {
            legalMoves.firstOrNull {
                it.color == tokenRef.color && it.includesToken(tokenRef.tokenId) && it.dieValueUsed == chosenDie
            }?.let { return it }
        }
        return legalMoves.firstOrNull {
            it.color == tokenRef.color && it.includesToken(tokenRef.tokenId)
        }
    }
}

sealed interface RollOutcome {
    val state: GameState

    /**
     * Player rolled a 6 and stacked it into the roll tray; they must roll again immediately.
     */
    data class RollAgainStacked(
        override val state: GameState,
        val rolledValue: Int
    ) : RollOutcome

    data class SingleLegalMove(
        override val state: GameState,
        val move: LegalMove
    ) : RollOutcome

    data class MultipleLegalMoves(
        override val state: GameState,
        val moves: List<LegalMove>
    ) : RollOutcome

    data class NoLegalMoves(
        override val state: GameState
    ) : RollOutcome

    data class TripleSixCancelled(
        override val state: GameState
    ) : RollOutcome
}

data class MoveOutcome(
    val state: GameState,
    val move: LegalMove,
    val grantedExtraRoll: Boolean,
    val hasRemainingDiceMoves: Boolean = false,
    val awaitingBombPlacement: Boolean = false,
    val playerFinishedRank: Int? = null,
    val triggeredChaosEvent: ChaosEventType? = null,
    val eliminatedPlayer: PlayerColor? = null,
    val collectedPowerUp: PowerUpType? = null,
    val triggeredBomb: BombTrap? = null,
    val rocketBoostSteps: Int = 0,
    val unlockedMasterHome: Boolean = false
)
