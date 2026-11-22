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
 * All 9 game modes in Ludo for M.
 */
enum class GameModeId(
    val title: String,
    val subtitle: String,
    val availableNow: Boolean,
    val minPlayers: Int = 2,
    val maxPlayers: Int = 4
) {
    CLASSIC("Classic", "Standard 15×15 Ludo with 8 safe stars", true, 2, 4),
    QUICK("Quick", "Tokens start on the board for fast games", true, 2, 4),
    MASTER("Master", "Capture an opponent to unlock your home lane", true, 2, 4),
    POWER_UP("Power-Up", "Collect Shields, Double Dice & Bomb Traps", true, 2, 4),
    TIMED("Timed", "Score points in 5 or 10 minute matches", true, 2, 4),
    TEAMS_2V2("Teams 2v2", "Red & Yellow vs Green & Blue (4 players)", true, 4, 4),
    ONE_TOKEN_RACE("One Token Race", "First player to get 1 token home wins", true, 2, 4),
    CHAOS("Chaos", "Wild board events trigger every 3 rounds", true, 2, 4),
    ELIMINATION("Elimination", "Lowest progress knocked out every 5 rounds", true, 3, 4)
}

/**
 * Power-ups exclusive to Power-Up mode.
 */
enum class PowerUpType(
    val title: String,
    val shortLabel: String,
    val description: String
) {
    SHIELD(
        title = "Shield",
        shortLabel = "Shield",
        description = "Protect one token from capture for your next 2 turns."
    ),
    DOUBLE_DICE(
        title = "Double Dice",
        shortLabel = "2× Dice",
        description = "Roll two dice this turn and move one token by the total."
    ),
    BOMB_TRAP(
        title = "Bomb Trap",
        shortLabel = "Bomb",
        description = "Hide a trap within 6 squares; opponents who land on it return to base."
    )
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
    val roundsRemaining: Int = 5
)

data class PendingPowerUpRespawn(
    val roundsRemaining: Int = 2
)

data class PowerUpState(
    val tiles: List<PowerUpTile> = emptyList(),
    val pendingRespawns: List<PendingPowerUpRespawn> = emptyList(),
    val bombs: List<BombTrap> = emptyList(),
    val nextEntityId: Int = 1
) {
    companion object {
        const val MAX_TILES_ON_BOARD = 3
        const val MAX_HELD_PER_PLAYER = 2
        const val RESPAWN_ROUNDS = 2
        const val BOMB_LIFETIME_ROUNDS = 5
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
    val botDifficulty: BotDifficulty = BotDifficulty.MEDIUM
) {
    val isBot: Boolean get() = controllerType == PlayerControllerType.BOT
}

/**
 * Represents a single Ludo token.
 *
 * progress:
 * -1     -> In home base
 * 0..50  -> On the 52-cell outer shared track (0 is the player's start square)
 * 51..55 -> On the player's private colored home column
 * 56     -> Reached the center home triangle (finished)
 *
 * outerWrap51:
 * Used only in Master mode when a token circles past progress 50 onto the 52nd outer
 * track square ((start + 51) % 52) before wrapping back to progress 0.
 */
data class Token(
    val id: Int,
    val color: PlayerColor,
    val progress: Int = BASE_PROGRESS,
    val outerWrap51: Boolean = false,
    val shieldTurnsRemaining: Int = 0
) {
    val isInBase: Boolean get() = progress == BASE_PROGRESS && !outerWrap51
    val isHome: Boolean get() = progress == HOME_PROGRESS && !outerWrap51
    val isOnMainTrack: Boolean get() = outerWrap51 || (progress in 0..MAX_MAIN_TRACK_PROGRESS)
    val isInHomeColumn: Boolean
        get() = !outerWrap51 && progress in (MAX_MAIN_TRACK_PROGRESS + 1)..(HOME_PROGRESS - 1)
    val isShielded: Boolean get() = shieldTurnsRemaining > 0

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
    val heldPowerUps: List<PowerUpType> = emptyList(),
    val doubleDiceActive: Boolean = false,
    val capturesMade: Int = 0,
    val powerUpsUsed: Int = 0
) {
    val color: PlayerColor get() = setup.color
    val nickname: String get() = setup.nickname
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
    val collectedPowerUp: PowerUpType? = null,
    val collectedTileId: Int? = null,
    val triggeredBomb: BombTrap? = null,
    val dieValueUsed: Int = stepPath.size
)

data class TokenRef(
    val color: PlayerColor,
    val tokenId: Int
)

enum class TurnPhase {
    AWAITING_ROLL,
    AWAITING_MOVE,
    NO_LEGAL_MOVES,
    TURN_CANCELLED_TRIPLE_SIX,
    MATCH_FINISHED
}

data class GameState(
    val mode: GameModeId = GameModeId.CLASSIC,
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
    val wasDiceDoubled: Boolean = false,
    val wasDoubleDiceRoll: Boolean = false,
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
    val lastEventMessage: String = ""
) {
    val activePlayer: PlayerState get() = players[activePlayerIndex]
    val hasWinner: Boolean get() = finishOrder.isNotEmpty()
    val winner: PlayerColor? get() = finishOrder.firstOrNull()
    val isMatchFinished: Boolean get() = phase == TurnPhase.MATCH_FINISHED

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
     */
    fun resolveMoveForToken(tokenRef: TokenRef): LegalMove? {
        val chosenDie = effectiveSelectedDieValue()
        if (chosenDie != null) {
            legalMoves.firstOrNull {
                it.color == tokenRef.color && it.tokenId == tokenRef.tokenId && it.dieValueUsed == chosenDie
            }?.let { return it }
        }
        return legalMoves.firstOrNull {
            it.color == tokenRef.color && it.tokenId == tokenRef.tokenId
        }
    }
}

sealed interface RollOutcome {
    val state: GameState

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
    val playerFinishedRank: Int? = null,
    val triggeredChaosEvent: ChaosEventType? = null,
    val eliminatedPlayer: PlayerColor? = null,
    val collectedPowerUp: PowerUpType? = null,
    val triggeredBomb: BombTrap? = null
)
