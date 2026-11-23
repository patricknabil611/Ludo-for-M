package com.example.engine

/**
 * Pluggable rule configuration interface powering all 9 game modes across all 3 match formats.
 */
interface LudoRuleSet {
    val modeId: GameModeId
    val matchFormat: MatchFormat
    val diceCount: Int
    val rollStackingEnabled: Boolean
    val tokensPerPlayer: Int
    val startingTokensOnTrack: Int
    val entryRolls: Set<Int>
    val extraRollTriggers: Set<Int>
    val maxConsecutiveSixes: Int
    val extraRollOnCapture: Boolean
    val extraRollOnHome: Boolean
    val blockadesEnabled: Boolean
    val jointTokensEnabled: Boolean
    val requiresCaptureBeforeHomeColumn: Boolean
    val arrowsEnabled: Boolean
    val magicItemsEnabled: Boolean
    val teamsMode: Boolean
    val tokensHomeToWin: Int
    val immediateWinOnFirstFinisher: Boolean
    val chaosEventsEveryNRounds: Int?
    val eliminationEveryNRounds: Int?
    val timedScoringEnabled: Boolean

    // Legacy compatibility alias
    val powerUpsEnabled: Boolean get() = magicItemsEnabled
}

data class ComposedRuleSet(
    override val modeId: GameModeId,
    override val matchFormat: MatchFormat = MatchFormat.FOUR_PLAYERS,
    override val diceCount: Int = 1,
    override val rollStackingEnabled: Boolean = true,
    override val tokensPerPlayer: Int = 4,
    override val startingTokensOnTrack: Int = 0,
    override val entryRolls: Set<Int> = setOf(6),
    override val extraRollTriggers: Set<Int> = setOf(6),
    override val maxConsecutiveSixes: Int = 3,
    override val extraRollOnCapture: Boolean = true,
    override val extraRollOnHome: Boolean = true,
    override val blockadesEnabled: Boolean = false,
    override val jointTokensEnabled: Boolean = false,
    override val requiresCaptureBeforeHomeColumn: Boolean = false,
    override val arrowsEnabled: Boolean = false,
    override val magicItemsEnabled: Boolean = false,
    override val teamsMode: Boolean = matchFormat.isTeamFormat,
    override val tokensHomeToWin: Int = 4,
    override val immediateWinOnFirstFinisher: Boolean = false,
    override val chaosEventsEveryNRounds: Int? = null,
    override val eliminationEveryNRounds: Int? = null,
    override val timedScoringEnabled: Boolean = false
) : LudoRuleSet

object RuleSetRegistry {
    fun forMode(
        mode: GameModeId,
        format: MatchFormat = MatchFormat.FOUR_PLAYERS
    ): LudoRuleSet {
        val teams = format.isTeamFormat
        return when (mode) {
            GameModeId.CLASSIC -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                teamsMode = teams
            )
            GameModeId.BLOCKADES -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                blockadesEnabled = true,
                teamsMode = teams
            )
            GameModeId.MASTER -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                jointTokensEnabled = true,
                requiresCaptureBeforeHomeColumn = true,
                teamsMode = teams
            )
            GameModeId.QUICK -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                startingTokensOnTrack = 1,
                tokensHomeToWin = 1,
                immediateWinOnFirstFinisher = !teams,
                teamsMode = teams
            )
            GameModeId.ARROW -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                arrowsEnabled = true,
                teamsMode = teams
            )
            GameModeId.MAGIC -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                magicItemsEnabled = true,
                teamsMode = teams
            )
            GameModeId.TIMED -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                immediateWinOnFirstFinisher = !teams,
                timedScoringEnabled = true,
                teamsMode = teams
            )
            GameModeId.CHAOS -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                chaosEventsEveryNRounds = 3,
                teamsMode = teams
            )
            GameModeId.ELIMINATION -> ComposedRuleSet(
                modeId = mode,
                matchFormat = format,
                immediateWinOnFirstFinisher = !teams,
                eliminationEveryNRounds = 5,
                teamsMode = teams
            )
        }
    }
}
