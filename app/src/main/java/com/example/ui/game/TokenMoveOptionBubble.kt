package com.example.ui.game

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoBoardGeometry
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.engine.PowerUpType
import com.example.engine.RuleSetRegistry
import com.example.engine.TokenRef
import com.example.engine.TurnPhase
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ThemeShadowSpec
import com.example.ui.theme.ludoShadow
import kotlin.math.roundToInt

/**
 * A selectable move option shown in the per-token bubble above a tapped movable token.
 * Either a single-die move (e.g., [6] or [3]) or a legal sequential multi-die move (e.g., [9] = 6 then 3).
 */
data class TokenMoveOption(
    val totalSteps: Int,
    val moves: List<LegalMove>,
    val isCombined: Boolean,
    val destinationRow: Float,
    val destinationCol: Float,
    val color: PlayerColor,
    val isCapture: Boolean = false
) {
    val label: String get() = totalSteps.toString()
}

/**
 * Ghost destination marker rendered on the board while the user holds a chip in [TokenMoveOptionBubble].
 */
data class GhostDestinationState(
    val row: Float,
    val col: Float,
    val color: PlayerColor,
    val stepsLabel: String,
    val isCapture: Boolean = false
)

/**
 * Pure UI helper resolving per-token move chips (including legal sequential combined values)
 * and automatic-move eligibility without altering any core [LudoEngine] rules.
 */
object TokenMoveOptionResolver {

    /**
     * Resolves all distinct legal move options for [tokenRef] from the current roll tray.
     */
    fun optionsForToken(state: GameState, tokenRef: TokenRef): List<TokenMoveOption> {
        if (state.phase != TurnPhase.AWAITING_MOVE || state.legalMoves.isEmpty()) {
            return emptyList()
        }

        val matchingMoves = state.legalMoves.filter {
            it.color == tokenRef.color && it.includesToken(tokenRef.tokenId)
        }
        if (matchingMoves.isEmpty()) return emptyList()

        // Preserve roll-tray ordering for distinct single-die values
        val singleByDie = LinkedHashMap<Int, LegalMove>()
        for (die in state.remainingDice) {
            if (die !in singleByDie) {
                matchingMoves.firstOrNull { it.dieValueUsed == die }?.let { singleByDie[die] = it }
            }
        }
        for (m in matchingMoves) {
            if (m.dieValueUsed !in singleByDie) {
                singleByDie[m.dieValueUsed] = m
            }
        }

        val singleOptions = singleByDie.values.map { move ->
            val (destR, destC) = destinationCoordsFor(move)
            TokenMoveOption(
                totalSteps = move.dieValueUsed,
                moves = listOf(move),
                isCombined = false,
                destinationRow = destR,
                destinationCol = destC,
                color = move.color,
                isCapture = move.capturedTokens.isNotEmpty()
            )
        }

        // If only one die value is usable on this token right now, no bubble needed
        if (singleOptions.size <= 1 || state.remainingDice.size < 2) {
            return singleOptions
        }

        // Check legal sequential 2-die combinations (e.g., 6 then 3 = [9]) on the same token
        val ruleSet = RuleSetRegistry.forMode(state.mode, state.matchFormat)
        val simEngine = LudoEngine(ruleSet)
        val combinedBySteps = LinkedHashMap<Int, TokenMoveOption>()
        val existingSingleTotals = singleOptions.map { it.totalSteps }.toSet()

        for (firstOpt in singleOptions) {
            val firstMove = firstOpt.moves.first()
            // Skip combining through non-deterministic Rocket boosts or Bomb placement pauses
            if (firstMove.collectedPowerUp == PowerUpType.ROCKET ||
                firstMove.collectedPowerUp == PowerUpType.BOMB_TRAP ||
                firstMove.triggeredBomb != null ||
                firstMove.reachesHome
            ) {
                continue
            }

            val outcome1 = simEngine.applyMove(state, firstMove)
            val after1 = outcome1.state
            if (outcome1.awaitingBombPlacement ||
                after1.isMatchFinished ||
                after1.phase != TurnPhase.AWAITING_MOVE ||
                after1.activePlayerIndex != state.activePlayerIndex
            ) {
                continue
            }

            val followUps = after1.legalMoves.filter {
                it.color == tokenRef.color && it.includesToken(tokenRef.tokenId)
            }
            for (secondMove in followUps) {
                val combinedSteps = firstMove.dieValueUsed + secondMove.dieValueUsed
                if (combinedSteps in existingSingleTotals || combinedSteps in combinedBySteps) {
                    continue
                }
                val (destR, destC) = destinationCoordsFor(secondMove)
                combinedBySteps[combinedSteps] = TokenMoveOption(
                    totalSteps = combinedSteps,
                    moves = listOf(firstMove, secondMove),
                    isCombined = true,
                    destinationRow = destR,
                    destinationCol = destC,
                    color = secondMove.color,
                    isCapture = firstMove.capturedTokens.isNotEmpty() || secondMove.capturedTokens.isNotEmpty()
                )
            }
        }

        return singleOptions + combinedBySteps.values.sortedBy { it.totalSteps }
    }

    /**
     * Returns an automatic [LegalMove] to play after ~300 ms when:
     * 1. There is exactly ONE legal move for the entire roll tray, OR
     * 2. All tokens of the controlled player are in base and a 6 is playable (equivalent base exits).
     */
    fun findAutoMove(state: GameState): LegalMove? {
        if (state.isMatchFinished || state.phase != TurnPhase.AWAITING_MOVE || state.legalMoves.isEmpty()) {
            return null
        }

        if (state.legalMoves.size == 1) {
            return state.legalMoves.first()
        }

        val controlledColor = state.legalMoves.first().color
        val controlledPlayer = state.playerByColor(controlledColor) ?: return null
        val allInBase = controlledPlayer.tokens.all { it.isInBase }
        if (allInBase && state.legalMoves.all { it.leavesBase }) {
            return state.legalMoves.first()
        }

        return null
    }

    private fun destinationCoordsFor(move: LegalMove): Pair<Float, Float> {
        val arrowDest = move.arrowToTrackIndex
        if (arrowDest != null) {
            return LudoBoardGeometry.outerTrackCenter(arrowDest)
        }
        val portalDest = move.teleportedToTrackIndex
        if (portalDest != null) {
            return LudoBoardGeometry.outerTrackCenter(portalDest)
        }
        return LudoBoardGeometry.tokenBoardPosition(
            color = move.color,
            tokenId = move.tokenId,
            progress = move.toProgress,
            outerWrap51 = move.toOuterWrap51
        )
    }
}

/**
 * Clean themed floating move bubble anchored directly above a tapped token.
 * Matches Part 4 visual spec: theme surface, 14dp corners, 6dp padding/gaps,
 * soft shadow (y=6dp, blur=18dp, 30% black), 8dp pointer triangle, and 36dp chips.
 */
@Composable
fun TokenMoveOptionBubble(
    options: List<TokenMoveOption>,
    anchorPx: Offset,
    containerSizePx: IntSize,
    onPreviewOption: (TokenMoveOption?) -> Unit,
    onSelectOption: (TokenMoveOption) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty() || containerSizePx.width <= 0 || containerSizePx.height <= 0) return

    val theme = LocalLudoTheme.current
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.toPx() }
    val verticalGapPx = with(density) { 26.dp.toPx() }

    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

    val showBelow = anchorPx.y - bubbleSize.height - verticalGapPx < marginPx
    val desiredX = (anchorPx.x - bubbleSize.width / 2f)
        .coerceIn(marginPx, (containerSizePx.width - bubbleSize.width - marginPx).coerceAtLeast(marginPx))
    val desiredY = if (showBelow) {
        (anchorPx.y + verticalGapPx).coerceAtMost(
            (containerSizePx.height - bubbleSize.height - marginPx).coerceAtLeast(marginPx)
        )
    } else {
        (anchorPx.y - bubbleSize.height - verticalGapPx).coerceAtLeast(marginPx)
    }

    val pointerLocalX = (anchorPx.x - desiredX)
        .coerceIn(
            with(density) { 16.dp.toPx() },
            (bubbleSize.width - with(density) { 16.dp.toPx() }).coerceAtLeast(16f)
        )

    val bubbleShadow = remember {
        ThemeShadowSpec.Soft(
            offsetY = 6.dp,
            blur = 18.dp,
            color = Color.Black.copy(alpha = 0.30f)
        )
    }

    Column(
        modifier = modifier
            .offset { IntOffset(desiredX.roundToInt(), desiredY.roundToInt()) }
            .onSizeChanged { bubbleSize = it }
            .testTag("token_move_option_bubble"),
        horizontalAlignment = Alignment.Start
    ) {
        if (showBelow) {
            BubblePointerArrow(
                pointingUp = true,
                offsetX = pointerLocalX,
                fillColor = theme.surface
            )
        }

        Surface(
            modifier = Modifier.ludoShadow(bubbleShadow, 14.dp),
            shape = RoundedCornerShape(14.dp),
            color = theme.surface
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { option ->
                    MoveOptionChip(
                        option = option,
                        onPreviewChange = { active -> onPreviewOption(if (active) option else null) },
                        onConfirm = { onSelectOption(option) }
                    )
                }
            }
        }

        if (!showBelow) {
            BubblePointerArrow(
                pointingUp = false,
                offsetX = pointerLocalX,
                fillColor = theme.surface
            )
        }
    }
}

@Composable
private fun MoveOptionChip(
    option: TokenMoveOption,
    onPreviewChange: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    val theme = LocalLudoTheme.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "moveChipScale"
    )

    val isHighlighted = isPressed
    val chipBg = if (isHighlighted) theme.primaryActionBg else theme.dieFace
    val chipTextColor = if (isHighlighted) theme.onPrimaryAction else theme.pip
    val chipBorder = if (isHighlighted) null else BorderStroke(2.dp, theme.chipLine)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = chipBg,
        border = chipBorder,
        modifier = Modifier
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("move_option_chip_${option.totalSteps}")
            .semantics {
                role = Role.Button
                contentDescription = if (option.isCombined) {
                    "Move ${option.totalSteps} spaces using combined dice"
                } else {
                    "Move ${option.totalSteps} spaces"
                }
                onClick {
                    onConfirm()
                    true
                }
            }
            .pointerInput(option) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onPreviewChange(true)
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    onPreviewChange(false)
                    if (up != null) {
                        val pos = up.position
                        val inside = pos.x in -16f..(size.width + 16f) &&
                            pos.y in -16f..(size.height + 16f)
                        if (inside) {
                            onConfirm()
                        }
                    }
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = option.label,
                fontFamily = theme.font,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = chipTextColor
            )
        }
    }
}

@Composable
private fun BubblePointerArrow(
    pointingUp: Boolean,
    offsetX: Float,
    fillColor: Color
) {
    val density = LocalDensity.current
    val arrowW = with(density) { 14.dp.toPx() }
    val offsetDp = with(density) { (offsetX - arrowW / 2f).coerceAtLeast(6f).toDp() }

    Canvas(
        modifier = Modifier
            .padding(start = offsetDp)
            .width(14.dp)
            .height(8.dp)
    ) {
        val path = Path().apply {
            if (pointingUp) {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
        }
        drawPath(path = path, color = fillColor)
    }
}
