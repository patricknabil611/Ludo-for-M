package com.example.bot

import com.example.engine.BotDifficulty
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoRuleSet
import com.example.engine.PlayerColor
import com.example.engine.Token
import kotlin.random.Random

/**
 * Pure Kotlin Ludo bot intelligence supporting Easy, Medium, and Hard difficulties.
 * Adapts to Power-Up mode (collecting tiles, shielding endangered tokens, placing bomb traps),
 * dynamic safe stars, portals, Teams 2v2, and Master locks.
 */
class LudoBotBrain(
    private val random: Random = Random.Default,
    private val modeHeuristic: ModeBotHeuristic = DefaultModeBotHeuristic(),
    private val powerUpAdvisor: PowerUpBotAdvisor = DefaultPowerUpBotAdvisor(random)
) : BotDecisionEngine {

    override fun chooseMove(
        state: GameState,
        ruleSet: LudoRuleSet,
        legalMoves: List<LegalMove>,
        difficulty: BotDifficulty
    ): LegalMove {
        require(legalMoves.isNotEmpty()) { "Bot cannot choose from empty legalMoves" }
        if (legalMoves.size == 1) return legalMoves.first()

        return when (difficulty) {
            BotDifficulty.EASY -> chooseEasyMove(legalMoves)
            BotDifficulty.MEDIUM -> chooseMediumMove(state, ruleSet, legalMoves)
            BotDifficulty.HARD -> chooseHardMove(state, ruleSet, legalMoves)
        }
    }

    override fun choosePowerUpBeforeRoll(
        state: GameState,
        ruleSet: LudoRuleSet,
        difficulty: BotDifficulty
    ): BotPowerUpAction? {
        return powerUpAdvisor.choosePreRollAction(
            state = state,
            ruleSet = ruleSet,
            difficulty = difficulty,
            riskEvaluator = ::evaluateCaptureRisk
        )
    }

    private fun chooseEasyMove(legalMoves: List<LegalMove>): LegalMove {
        return legalMoves[random.nextInt(legalMoves.size)]
    }

    private fun chooseMediumMove(
        state: GameState,
        ruleSet: LudoRuleSet,
        legalMoves: List<LegalMove>
    ): LegalMove {
        return legalMoves.maxBy { move ->
            scoreMediumMove(state, ruleSet, move) + random.nextDouble(0.0, 0.5)
        }
    }

    private fun chooseHardMove(
        state: GameState,
        ruleSet: LudoRuleSet,
        legalMoves: List<LegalMove>
    ): LegalMove {
        return legalMoves.maxBy { move ->
            scoreHardMove(state, ruleSet, move) + random.nextDouble(0.0, 0.05)
        }
    }

    internal fun scoreMediumMove(
        state: GameState,
        ruleSet: LudoRuleSet,
        move: LegalMove
    ): Double {
        var score = 0.0
        score += move.capturedTokens.size * 120.0

        if (move.reachesHome) {
            score += 95.0
        } else if (isSafeDestination(state, move.color, move.toProgress, move.toOuterWrap51)) {
            score += 65.0
        }

        if (move.leavesBase) {
            score += 75.0
        }

        score += move.toProgress * 0.6
        score += modeHeuristic.scoreMoveForMode(state, move, ruleSet) * 0.5
        score += powerUpAdvisor.powerUpBonusScore(state, move, BotDifficulty.MEDIUM)
        return score
    }

    internal fun scoreHardMove(
        state: GameState,
        ruleSet: LudoRuleSet,
        move: LegalMove
    ): Double {
        var score = 0.0
        val player = state.playerByColor(move.color)
        val activeOnBoardCount = player?.tokens?.count { !it.isInBase && !it.isHome } ?: 0
        val movingToken = player?.tokens?.firstOrNull { it.id == move.tokenId }

        // 1. Prioritize captures, especially high-progress opponent tokens
        if (move.capturedTokens.isNotEmpty()) {
            score += move.capturedTokens.size * 155.0
            for (victim in move.capturedTokens) {
                val victimProgress = state.playerByColor(victim.color)
                    ?.tokens
                    ?.firstOrNull { it.id == victim.tokenId }
                    ?.progress
                    ?.coerceAtLeast(0) ?: 0
                score += victimProgress * 1.4
            }
        }

        // 2. Risk evaluation (if token is currently shielded, capture risk is 0!)
        val riskBefore = if (movingToken?.isShielded == true) {
            0.0
        } else {
            evaluateCaptureRisk(
                state = state,
                tokenColor = move.color,
                progress = move.fromProgress,
                ruleSet = ruleSet,
                outerWrap51 = move.fromOuterWrap51
            )
        }
        val riskAfter = if (movingToken?.isShielded == true) {
            0.0
        } else {
            evaluateCaptureRisk(
                state = state,
                tokenColor = move.color,
                progress = move.toProgress,
                ruleSet = ruleSet,
                outerWrap51 = move.toOuterWrap51
            )
        }
        val riskDelta = riskBefore - riskAfter
        score += riskDelta * 85.0

        // 3. Reaching Home permanently secures the token
        if (move.reachesHome) {
            score += 135.0
        }

        // 4. Entering the safe private home column from the outer track
        val entersHomeColumn = (move.fromOuterWrap51 || move.fromProgress <= Token.MAX_MAIN_TRACK_PROGRESS) &&
            !move.toOuterWrap51 &&
            move.toProgress in (Token.MAX_MAIN_TRACK_PROGRESS + 1)..(Token.HOME_PROGRESS - 1)
        if (entersHomeColumn) {
            score += 88.0
        }

        // 5. Landing on a safe start or star square on the shared track
        if (isSafeOuterTrackSquare(state, move.color, move.toProgress, move.toOuterWrap51)) {
            score += 56.0
        } else if (isSafeOuterTrackSquare(state, move.color, move.fromProgress, move.fromOuterWrap51) && riskAfter > 0.0) {
            score -= 22.0
        }

        // 6. Leaving base wisely
        if (move.leavesBase) {
            score += when (activeOnBoardCount) {
                0 -> 95.0
                1 -> 70.0
                else -> 48.0
            }
        }

        // 7. Forward progress toward Home
        val effectiveProgress = if (move.toOuterWrap51) 51 else move.toProgress
        score += effectiveProgress * 1.15

        // 8. Mode-specific strategy and power-up collection scoring
        score += modeHeuristic.scoreMoveForMode(state, move, ruleSet)
        score += powerUpAdvisor.powerUpBonusScore(state, move, BotDifficulty.HARD)

        return score
    }

    /**
     * Computes how likely a token at [progress] is to be captured by any active opponent on their next turn.
     */
    fun evaluateCaptureRisk(
        state: GameState,
        tokenColor: PlayerColor,
        progress: Int,
        ruleSet: LudoRuleSet,
        outerWrap51: Boolean = false
    ): Double {
        val offset = when {
            outerWrap51 -> 51
            progress in 0..Token.MAX_MAIN_TRACK_PROGRESS -> progress
            else -> return 0.0
        }
        val targetTrackIdx = (tokenColor.startTrackIndex + offset) % Token.TRACK_LENGTH
        if (targetTrackIdx in state.activeSafeTrackIndices()) return 0.0

        val partnerColor = if (ruleSet.teamsMode) tokenColor.opposite() else null
        var risk = 0.0
        for (opponent in state.players) {
            if (opponent.isEliminated) continue
            if (opponent.color == tokenColor || opponent.color == partnerColor) continue
            if (opponent.color in state.finishOrder) continue

            for (oppToken in opponent.tokens) {
                if (!oppToken.isOnMainTrack) continue
                val oppTrackIdx = oppToken.mainTrackIndex() ?: continue
                val distance = (targetTrackIdx - oppTrackIdx + Token.TRACK_LENGTH) % Token.TRACK_LENGTH
                if (distance in 1..6) {
                    val oppOffset = oppToken.outerTrackOffset() ?: continue
                    val oppCanKeepCircling = ruleSet.requiresCaptureBeforeHomeColumn && !opponent.hasCapturedOpponent
                    if (oppCanKeepCircling || oppOffset + distance <= Token.MAX_MAIN_TRACK_PROGRESS) {
                        risk += 1.0 / 6.0
                    }
                }
            }
        }
        return risk
    }

    private fun isSafeOuterTrackSquare(
        state: GameState,
        color: PlayerColor,
        progress: Int,
        outerWrap51: Boolean
    ): Boolean {
        val offset = when {
            outerWrap51 -> 51
            progress in 0..Token.MAX_MAIN_TRACK_PROGRESS -> progress
            else -> return false
        }
        val trackIdx = (color.startTrackIndex + offset) % Token.TRACK_LENGTH
        return trackIdx in state.activeSafeTrackIndices()
    }

    private fun isSafeDestination(
        state: GameState,
        color: PlayerColor,
        progress: Int,
        outerWrap51: Boolean
    ): Boolean {
        return (!outerWrap51 && progress >= Token.MAX_MAIN_TRACK_PROGRESS + 1) ||
            isSafeOuterTrackSquare(state, color, progress, outerWrap51)
    }
}
