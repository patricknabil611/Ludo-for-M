package com.example.bot

import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoEngine
import com.example.engine.LudoRuleSet
import com.example.engine.PlayerColor
import com.example.engine.PowerUpState
import com.example.engine.PowerUpType
import com.example.engine.Token
import kotlin.random.Random

/**
 * Action a bot may choose to take at the start of its turn before rolling (Power-Up mode only).
 */
sealed interface BotPowerUpAction {
    data class UseShield(val tokenId: Int) : BotPowerUpAction
    data object UseDoubleDice : BotPowerUpAction
    data class UseBombTrap(val trackIndex: Int) : BotPowerUpAction
}

/**
 * Mode-agnostic bot decision contract.
 */
interface BotDecisionEngine {
    fun chooseMove(
        state: GameState,
        ruleSet: LudoRuleSet,
        legalMoves: List<LegalMove>,
        difficulty: BotDifficulty
    ): LegalMove

    fun choosePowerUpBeforeRoll(
        state: GameState,
        ruleSet: LudoRuleSet,
        difficulty: BotDifficulty
    ): BotPowerUpAction? = null
}

/**
 * Mode-specific strategic adjustment hook used by bots.
 */
interface ModeBotHeuristic {
    fun scoreMoveForMode(
        state: GameState,
        move: LegalMove,
        ruleSet: LudoRuleSet
    ): Double
}

interface PowerUpBotAdvisor {
    fun powerUpBonusScore(
        state: GameState,
        move: LegalMove,
        difficulty: BotDifficulty
    ): Double = 0.0

    fun choosePreRollAction(
        state: GameState,
        ruleSet: LudoRuleSet,
        difficulty: BotDifficulty,
        riskEvaluator: (GameState, PlayerColor, Int, LudoRuleSet, Boolean) -> Double
    ): BotPowerUpAction? = null
}

class DefaultPowerUpBotAdvisor(
    private val random: Random = Random.Default
) : PowerUpBotAdvisor {

    override fun powerUpBonusScore(
        state: GameState,
        move: LegalMove,
        difficulty: BotDifficulty
    ): Double {
        if (state.mode != GameModeId.POWER_UP) return 0.0
        val player = state.playerByColor(move.color) ?: return 0.0
        if (player.heldPowerUps.size >= PowerUpState.MAX_HELD_PER_PLAYER) return 0.0
        val pickup = move.collectedPowerUp ?: return 0.0

        val basePickupValue = when (pickup) {
            PowerUpType.SHIELD -> 68.0
            PowerUpType.DOUBLE_DICE -> 64.0
            PowerUpType.BOMB_TRAP -> 60.0
        }
        return when (difficulty) {
            BotDifficulty.EASY -> 10.0
            BotDifficulty.MEDIUM -> basePickupValue * 0.85
            BotDifficulty.HARD -> basePickupValue * 1.1
        }
    }

    override fun choosePreRollAction(
        state: GameState,
        ruleSet: LudoRuleSet,
        difficulty: BotDifficulty,
        riskEvaluator: (GameState, PlayerColor, Int, LudoRuleSet, Boolean) -> Double
    ): BotPowerUpAction? {
        if (!ruleSet.powerUpsEnabled) return null
        val player = state.activePlayer
        if (player.heldPowerUps.isEmpty()) return null

        val engine = LudoEngine(ruleSet)

        if (difficulty == BotDifficulty.EASY) {
            if (random.nextFloat() > 0.35f) return null
            val shuffled = player.heldPowerUps.shuffled(random)
            for (pu in shuffled) {
                when (pu) {
                    PowerUpType.SHIELD -> {
                        val validTokens = engine.computeValidShieldTokenIds(state, player.color)
                        if (validTokens.isNotEmpty()) {
                            return BotPowerUpAction.UseShield(validTokens[random.nextInt(validTokens.size)])
                        }
                    }
                    PowerUpType.DOUBLE_DICE -> {
                        if (!player.doubleDiceActive && player.tokens.any { it.isOnMainTrack }) {
                            return BotPowerUpAction.UseDoubleDice
                        }
                    }
                    PowerUpType.BOMB_TRAP -> {
                        val validSquares = engine.computeValidBombSquares(state, player.color).toList()
                        if (validSquares.isNotEmpty()) {
                            return BotPowerUpAction.UseBombTrap(validSquares[random.nextInt(validSquares.size)])
                        }
                    }
                }
            }
            return null
        }

        // Medium & Hard: evaluate Shield, Bomb Trap, and Double Dice sensibly

        // 1. SHIELD: prioritize shielding an endangered token (highest risk * value)
        if (PowerUpType.SHIELD in player.heldPowerUps) {
            val unshieldedMainTrack = player.tokens.filter { it.isOnMainTrack && !it.isShielded }
            val bestShieldTarget = unshieldedMainTrack
                .map { token ->
                    val risk = riskEvaluator(state, player.color, token.progress, ruleSet, token.outerWrap51)
                    val valueWeight = 1.0 + (token.progress.coerceAtLeast(0) / 30.0)
                    token to (risk * valueWeight)
                }
                .maxByOrNull { it.second }

            val dangerThreshold = if (difficulty == BotDifficulty.HARD) 0.12 else 0.15
            if (bestShieldTarget != null && bestShieldTarget.second >= dangerThreshold) {
                return BotPowerUpAction.UseShield(bestShieldTarget.first.id)
            }

            // Proactive shield if inventory is full and an advanced token is exposed on a non-safe square
            if (player.heldPowerUps.size >= PowerUpState.MAX_HELD_PER_PLAYER) {
                val exposedAdvanced = unshieldedMainTrack
                    .filter { t ->
                        val trackIdx = t.mainTrackIndex()
                        t.progress >= 24 && trackIdx != null && trackIdx !in state.activeSafeTrackIndices()
                    }
                    .maxByOrNull { it.progress }
                if (exposedAdvanced != null) {
                    return BotPowerUpAction.UseShield(exposedAdvanced.id)
                }
            }
        }

        // 2. BOMB TRAP: place right in front of an opponent token's likely path (1..6 steps ahead)
        if (PowerUpType.BOMB_TRAP in player.heldPowerUps) {
            val validSquares = engine.computeValidBombSquares(state, player.color)
            if (validSquares.isNotEmpty()) {
                val bestBombSquare = validSquares
                    .map { sq -> sq to scoreBombSquare(state, player.color, sq) }
                    .maxByOrNull { it.second }

                val minScore = if (difficulty == BotDifficulty.HARD) 1.0 else 0.9
                if (bestBombSquare != null && bestBombSquare.second >= minScore) {
                    return BotPowerUpAction.UseBombTrap(bestBombSquare.first)
                }
            }
        }

        // 3. DOUBLE DICE: use when at least one token can make a big forward leap without overshooting
        if (PowerUpType.DOUBLE_DICE in player.heldPowerUps && !player.doubleDiceActive) {
            val goodRunner = player.tokens.any { it.isOnMainTrack && it.progress <= 44 }
            if (goodRunner) {
                return BotPowerUpAction.UseDoubleDice
            }
        }

        return null
    }

    private fun scoreBombSquare(
        state: GameState,
        ownerColor: PlayerColor,
        candidateTrackIndex: Int
    ): Double {
        var score = 0.0
        for (opp in state.players) {
            if (opp.isEliminated || opp.color == ownerColor) continue
            for (oppToken in opp.tokens) {
                if (!oppToken.isOnMainTrack) continue
                val oppTrack = oppToken.mainTrackIndex() ?: continue
                val distAhead = (candidateTrackIndex - oppTrack + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
                val oppOffset = oppToken.outerTrackOffset() ?: continue
                if (distAhead in 1..6 && oppOffset + distAhead <= Token.MAX_MAIN_TRACK_PROGRESS) {
                    // Direct 1-turn hit probability! Weight higher for advanced opponent tokens
                    val progressFactor = 1.0 + (oppToken.progress.coerceAtLeast(0) / 40.0)
                    score += 1.2 * progressFactor
                } else if (distAhead in 7..10 && oppOffset + distAhead <= Token.MAX_MAIN_TRACK_PROGRESS) {
                    score += 0.35
                }
            }
        }
        return score
    }
}

class DefaultModeBotHeuristic : ModeBotHeuristic {
    override fun scoreMoveForMode(
        state: GameState,
        move: LegalMove,
        ruleSet: LudoRuleSet
    ): Double {
        return when (state.mode) {
            GameModeId.CLASSIC -> 0.0
            GameModeId.QUICK -> {
                val stepDelta = move.stepPath.size * 2.5
                stepDelta + if (move.reachesHome) 35.0 else 0.0
            }
            GameModeId.MASTER -> {
                val player = state.playerByColor(move.color)
                val needsUnlock = player?.hasCapturedOpponent == false
                if (needsUnlock && move.capturedTokens.isNotEmpty()) 180.0 else 0.0
            }
            GameModeId.TIMED -> {
                (move.stepPath.size * 2.0) +
                    (move.capturedTokens.size * 25.0) +
                    if (move.reachesHome) 60.0 else 0.0
            }
            GameModeId.TEAMS_2V2 -> {
                val teammateColor = move.color.opposite()
                val hitsTeammate = move.capturedTokens.any { it.color == teammateColor }
                if (hitsTeammate) -500.0 else 0.0
            }
            GameModeId.ONE_TOKEN_RACE -> {
                val leadBonus = if (move.fromProgress >= 0) move.fromProgress * 2.2 else 0.0
                val winNow = if (move.reachesHome) 500.0 else 0.0
                leadBonus + winNow
            }
            GameModeId.CHAOS -> {
                val portalBonus = if (move.teleportedToTrackIndex != null) 12.0 else 0.0
                portalBonus + if (move.reachesHome) 25.0 else 0.0
            }
            GameModeId.ELIMINATION -> {
                val progressGain = if (!move.leavesBase) move.stepPath.size * 3.0 else 10.0
                progressGain + if (move.reachesHome) 45.0 else 0.0
            }
            GameModeId.POWER_UP -> {
                if (move.reachesHome) 25.0 else 0.0
            }
        }
    }
}
