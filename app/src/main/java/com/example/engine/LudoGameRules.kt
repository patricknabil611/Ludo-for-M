package com.example.engine

/**
 * Pluggable rule configuration interface powering all game modes cleanly.
 */
interface LudoRuleSet {
    val modeId: GameModeId
    val tokensPerPlayer: Int
    val startOnTrack: Boolean
    val entryRolls: Set<Int>
    val extraRollTriggers: Set<Int>
    val maxConsecutiveSixes: Int
    val extraRollOnCapture: Boolean
    val extraRollOnHome: Boolean
    val requiresCaptureBeforeHomeColumn: Boolean
    val teamsMode: Boolean
    val tokensHomeToWin: Int
    val immediateWinOnFirstFinisher: Boolean
    val chaosEventsEveryNRounds: Int?
    val eliminationEveryNRounds: Int?
    val timedScoringEnabled: Boolean
    val powerUpsEnabled: Boolean
}

open class BaseRuleSet(
    override val modeId: GameModeId
) : LudoRuleSet {
    override val tokensPerPlayer: Int = 4
    override val startOnTrack: Boolean = false
    override val entryRolls: Set<Int> = setOf(6)
    override val extraRollTriggers: Set<Int> = setOf(6)
    override val maxConsecutiveSixes: Int = 3
    override val extraRollOnCapture: Boolean = false
    override val extraRollOnHome: Boolean = false
    override val requiresCaptureBeforeHomeColumn: Boolean = false
    override val teamsMode: Boolean = false
    override val tokensHomeToWin: Int = 4
    override val immediateWinOnFirstFinisher: Boolean = false
    override val chaosEventsEveryNRounds: Int? = null
    override val eliminationEveryNRounds: Int? = null
    override val timedScoringEnabled: Boolean = false
    override val powerUpsEnabled: Boolean = false
}

object ClassicRuleSet : BaseRuleSet(GameModeId.CLASSIC)

object QuickRuleSet : BaseRuleSet(GameModeId.QUICK) {
    override val startOnTrack: Boolean = true
}

object MasterRuleSet : BaseRuleSet(GameModeId.MASTER) {
    override val requiresCaptureBeforeHomeColumn: Boolean = true
}

object PowerUpRuleSet : BaseRuleSet(GameModeId.POWER_UP) {
    override val powerUpsEnabled: Boolean = true
}

object TimedRuleSet : BaseRuleSet(GameModeId.TIMED) {
    override val immediateWinOnFirstFinisher: Boolean = true
    override val timedScoringEnabled: Boolean = true
}

object Teams2v2RuleSet : BaseRuleSet(GameModeId.TEAMS_2V2) {
    override val teamsMode: Boolean = true
}

object OneTokenRaceRuleSet : BaseRuleSet(GameModeId.ONE_TOKEN_RACE) {
    override val tokensHomeToWin: Int = 1
    override val immediateWinOnFirstFinisher: Boolean = true
}

object ChaosRuleSet : BaseRuleSet(GameModeId.CHAOS) {
    override val chaosEventsEveryNRounds: Int = 3
}

object EliminationRuleSet : BaseRuleSet(GameModeId.ELIMINATION) {
    override val immediateWinOnFirstFinisher: Boolean = true
    override val eliminationEveryNRounds: Int = 5
}

object RuleSetRegistry {
    fun forMode(mode: GameModeId): LudoRuleSet = when (mode) {
        GameModeId.CLASSIC -> ClassicRuleSet
        GameModeId.QUICK -> QuickRuleSet
        GameModeId.MASTER -> MasterRuleSet
        GameModeId.POWER_UP -> PowerUpRuleSet
        GameModeId.TIMED -> TimedRuleSet
        GameModeId.TEAMS_2V2 -> Teams2v2RuleSet
        GameModeId.ONE_TOKEN_RACE -> OneTokenRaceRuleSet
        GameModeId.CHAOS -> ChaosRuleSet
        GameModeId.ELIMINATION -> EliminationRuleSet
    }
}
