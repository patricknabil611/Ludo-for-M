package com.example.bot

import com.example.engine.BotDifficulty
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoRuleSet
import com.example.engine.PlayerColor
import com.example.engine.Token
import kotlin.random.Random

/**
 * Pure Kotlin Ludo bot intelligence supporting Easy, Medium, and Hard difficulties across
 * all 9 v2.1 modes and all 3 match formats.
 *
 * Core move priority order:
 *   1. Finish (bring a token Home)
 *   2. Capture opponent token(s)
 *   3. Escape danger (reduce capture risk)
 *   4. Land on safe / star square (or enter private home column)
 *   5. Leave base with a 6
 *   6. Advance furthest token
 *
 * Hard bots additionally chase Master unlock, form/preserve Blockades, aim for Arrow shortcuts,
 * prioritize Magic item pickups, and place instant Bomb Traps directly ahead of rival tokens.
 * Easy bots play more randomly.
 */
class LudoBotBrain(
    private val random: Random = Random.Default,
    private val modeHeuristic: ModeBotHeuristic = DefaultModeBotHeuristic(),
    private val magicAdvisor: MagicItemBotAdvisor = DefaultMagicItemBotAdvisor(random)
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

    override fun choosePendingBombSquare(
        state: GameState,
        ruleSet: LudoRuleSet,
        validTrackIndices: List<Int>,
        difficulty: BotDifficulty
    ): Int {
        return magicAdvisor.chooseBombSquare(
            state = state,
            ownerColor = state.activePlayer.color,
            ruleSet = ruleSet,
            validTrackIndices = validTrackIndices,
            difficulty = difficulty
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
            scoreMediumMove(state, ruleSet, move) + random.nextDouble(0.0, 2.5)
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

        // 1. Finish
        if (move.reachesHome) {
            score += 240.0
        }

        // 2. Capture
        if (move.capturedTokens.isNotEmpty()) {
            score += 185.0 + (move.capturedTokens.size - 1) * 45.0
        }

        // 3. Escape danger
        val riskBefore = evaluateCaptureRisk(state, move.color, move.fromProgress, ruleSet, move.fromOuterWrap51)
        val riskAfter = evaluateCaptureRisk(state, move.color, move.toProgress, ruleSet, move.toOuterWrap51)
        score += (riskBefore - riskAfter) * 115.0

        // 4. Safe / star square or home column entry
        if (!move.reachesHome && isSafeDestination(state, move.color, move.toProgress, move.toOuterWrap51)) {
            score += 85.0
        }

        // 5. Leave base with 6
        if (move.leavesBase) {
            score += 65.0
        }

        // 6. Advance furthest token
        val effectiveProgress = if (move.toOuterWrap51) 51 else move.toProgress.coerceAtLeast(0)
        score += effectiveProgress * 0.9

        // Mode & Magic bonuses (moderate weight)
        score += modeHeuristic.scoreMoveForMode(state, move, ruleSet, BotDifficulty.MEDIUM)
        score += magicAdvisor.magicPickupScore(state, move, BotDifficulty.MEDIUM)
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

        // 1. Finish (highest priority — permanently secures token + bonus roll)
        if (move.reachesHome) {
            score += 310.0 * move.tokenIds.size
        }

        // 2. Capture opponent token(s), valuing high-progress victims + bonus roll
        if (move.capturedTokens.isNotEmpty()) {
            score += 230.0 + (move.capturedTokens.size - 1) * 65.0
            for (victim in move.capturedTokens) {
                val victimProgress = state.playerByColor(victim.color)
                    ?.tokens
                    ?.firstOrNull { it.id == victim.tokenId }
                    ?.progress
                    ?.coerceAtLeast(0) ?: 0
                score += victimProgress * 1.4
            }
        }

        // 3. Escape danger (shielded tokens have 0 capture risk)
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
        score += (riskBefore - riskAfter) * 145.0

        // 4. Safe / star square or entering private home column
        val entersHomeColumn = (move.fromOuterWrap51 || move.fromProgress <= Token.MAX_MAIN_TRACK_PROGRESS) &&
            !move.toOuterWrap51 &&
            move.toProgress in (Token.MAX_MAIN_TRACK_PROGRESS + 1)..(Token.HOME_PROGRESS - 1)
        if (entersHomeColumn) {
            score += 112.0
        } else if (isSafeOuterTrackSquare(state, move.color, move.toProgress, move.toOuterWrap51)) {
            score += 92.0
        } else if (isSafeOuterTrackSquare(state, move.color, move.fromProgress, move.fromOuterWrap51) && riskAfter > 0.0) {
            score -= 26.0
        }

        // 5. Leave base with 6
        if (move.leavesBase) {
            score += when (activeOnBoardCount) {
                0 -> 96.0
                1 -> 76.0
                else -> 58.0
            }
        }

        // 6. Advance furthest token toward Home
        val effectiveProgress = if (move.toOuterWrap51) 51 else move.toProgress.coerceAtLeast(0)
        score += effectiveProgress * 1.25

        // Mode-specific strategies (Blockades, Master unlock/Joint, Quick, Arrow, Timed, Chaos, Elimination)
        score += modeHeuristic.scoreMoveForMode(state, move, ruleSet, BotDifficulty.HARD)
        score += magicAdvisor.magicPickupScore(state, move, BotDifficulty.HARD)

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
            if (!ruleSet.teamsMode && opponent.color in state.finishOrder) continue

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
