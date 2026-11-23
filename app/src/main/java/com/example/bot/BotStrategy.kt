package com.example.bot

import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoRuleSet
import com.example.engine.PlayerColor
import com.example.engine.PowerUpType
import com.example.engine.Token
import kotlin.random.Random

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

    fun choosePendingBombSquare(
        state: GameState,
        ruleSet: LudoRuleSet,
        validTrackIndices: List<Int>,
        difficulty: BotDifficulty
    ): Int
}

/**
 * Mode-specific strategic adjustment hook used by bots across all 9 modes.
 */
interface ModeBotHeuristic {
    fun scoreMoveForMode(
        state: GameState,
        move: LegalMove,
        ruleSet: LudoRuleSet,
        difficulty: BotDifficulty
    ): Double
}

interface MagicItemBotAdvisor {
    fun magicPickupScore(
        state: GameState,
        move: LegalMove,
        difficulty: BotDifficulty
    ): Double

    fun chooseBombSquare(
        state: GameState,
        ownerColor: PlayerColor,
        ruleSet: LudoRuleSet,
        validTrackIndices: List<Int>,
        difficulty: BotDifficulty
    ): Int
}

class DefaultMagicItemBotAdvisor(
    private val random: Random = Random.Default
) : MagicItemBotAdvisor {

    override fun magicPickupScore(
        state: GameState,
        move: LegalMove,
        difficulty: BotDifficulty
    ): Double {
        if (state.mode != GameModeId.MAGIC) return 0.0
        val pickup = move.collectedPowerUp ?: return 0.0
        val baseValue = when (pickup) {
            PowerUpType.ROCKET -> 88.0
            PowerUpType.GOLDEN_DICE -> 84.0
            PowerUpType.SHIELD -> 74.0
            PowerUpType.BOMB_TRAP -> 68.0
        }
        return when (difficulty) {
            BotDifficulty.EASY -> 8.0
            BotDifficulty.MEDIUM -> baseValue * 0.75
            BotDifficulty.HARD -> baseValue * 1.15
        }
    }

    override fun chooseBombSquare(
        state: GameState,
        ownerColor: PlayerColor,
        ruleSet: LudoRuleSet,
        validTrackIndices: List<Int>,
        difficulty: BotDifficulty
    ): Int {
        require(validTrackIndices.isNotEmpty()) { "validTrackIndices cannot be empty" }
        if (difficulty == BotDifficulty.EASY || validTrackIndices.size == 1) {
            return validTrackIndices[random.nextInt(validTrackIndices.size)]
        }
        val partnerColor = if (ruleSet.teamsMode) ownerColor.opposite() else null
        return validTrackIndices.maxBy { sq ->
            scoreBombSquare(state, ownerColor, partnerColor, sq) + random.nextDouble(0.0, 0.05)
        }
    }

    private fun scoreBombSquare(
        state: GameState,
        ownerColor: PlayerColor,
        partnerColor: PlayerColor?,
        candidateTrackIndex: Int
    ): Double {
        var score = 0.0
        for (opp in state.players) {
            if (opp.isEliminated || opp.color == ownerColor || opp.color == partnerColor) continue
            for (oppToken in opp.tokens) {
                if (!oppToken.isOnMainTrack) continue
                val oppTrack = oppToken.mainTrackIndex() ?: continue
                val distAhead = (candidateTrackIndex - oppTrack + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
                val oppOffset = oppToken.outerTrackOffset() ?: continue
                if (distAhead in 1..6 && oppOffset + distAhead <= Token.MAX_MAIN_TRACK_PROGRESS) {
                    val progressFactor = 1.0 + (oppToken.progress.coerceAtLeast(0) / 35.0)
                    score += 1.5 * progressFactor
                } else if (distAhead in 7..12 && oppOffset + distAhead <= Token.MAX_MAIN_TRACK_PROGRESS) {
                    score += 0.45
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
        ruleSet: LudoRuleSet,
        difficulty: BotDifficulty
    ): Double {
        val hardBoost = if (difficulty == BotDifficulty.HARD) 1.0 else 0.55
        var score = 0.0

        // Team awareness applies in every mode when 2v2 Team format is active
        if (ruleSet.teamsMode) {
            val teammateColor = move.color.opposite()
            if (move.capturedTokens.any { it.color == teammateColor }) {
                score -= 500.0
            }
        }

        score += when (state.mode) {
            GameModeId.CLASSIC -> 0.0

            GameModeId.BLOCKADES -> {
                var bScore = 0.0
                if (move.formsBlockade) {
                    // Hard bots love forming blockades on strategic non-safe squares
                    bScore += 92.0 * hardBoost
                }
                // Check if this move breaks an existing blockade (penalize unless capturing or finishing)
                val player = state.playerByColor(move.color)
                val fromTrack = if (move.fromOuterWrap51 || move.fromProgress in 0..Token.MAX_MAIN_TRACK_PROGRESS) {
                    val off = if (move.fromOuterWrap51) 51 else move.fromProgress
                    (move.color.startTrackIndex + off) % Token.TRACK_LENGTH
                } else null
                if (fromTrack != null && fromTrack !in state.activeSafeTrackIndices()) {
                    val sameSquareCount = player?.tokens?.count { it.mainTrackIndex() == fromTrack } ?: 0
                    if (sameSquareCount == 2 && !move.reachesHome && move.capturedTokens.isEmpty()) {
                        bScore -= 48.0 * hardBoost
                    }
                }
                bScore
            }

            GameModeId.MASTER -> {
                var mScore = 0.0
                val player = state.playerByColor(move.color)
                val needsUnlock = player?.hasCapturedOpponent == false
                if (needsUnlock && move.capturedTokens.isNotEmpty()) {
                    // Chase first capture aggressively to unlock home column!
                    mScore += 220.0 * hardBoost
                }
                // Encourage forming a Joint Token when landing on own unpaired token on outer track
                if (!move.isJointMove && !move.reachesHome && !move.leavesBase && player != null) {
                    val hasOwnUnpairedAtDest = player.tokens.any { t ->
                        t.id != move.tokenId &&
                            !t.isInBase && !t.isHome && !t.isJoint &&
                            t.progress == move.toProgress &&
                            t.outerWrap51 == move.toOuterWrap51
                    }
                    if (hasOwnUnpairedAtDest) {
                        mScore += 75.0 * hardBoost
                    }
                }
                // Moving a Joint Token moves 2 tokens at once for 1 die — huge tempo!
                if (move.isJointMove) {
                    mScore += 42.0 * hardBoost
                }
                mScore
            }

            GameModeId.QUICK -> {
                // First token home wins — prioritize advancing the leading token and finishing!
                val leadBonus = if (move.fromProgress >= 0) move.fromProgress * 2.4 else 0.0
                val winNow = if (move.reachesHome) 550.0 else 0.0
                leadBonus + winNow
            }

            GameModeId.ARROW -> {
                // Actively aim for arrow shortcuts (+6 free squares!)
                if (move.arrowToTrackIndex != null) 115.0 * hardBoost else 0.0
            }

            GameModeId.MAGIC -> {
                if (move.triggeredBomb != null) -240.0 else 0.0
            }

            GameModeId.TIMED -> {
                (move.stepPath.size * 2.5) +
                    (move.capturedTokens.size * 30.0) +
                    if (move.reachesHome) 75.0 else 0.0
            }

            GameModeId.CHAOS -> {
                val portalBonus = if (move.teleportedToTrackIndex != null) 24.0 * hardBoost else 0.0
                portalBonus + if (move.reachesHome) 30.0 else 0.0
            }

            GameModeId.ELIMINATION -> {
                val progressGain = if (!move.leavesBase) move.stepPath.size * 3.5 else 15.0
                progressGain + if (move.reachesHome) 55.0 else 0.0
            }
        }

        return score
    }
}
