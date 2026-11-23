package com.example.ui.game

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.engine.BombTrap
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LudoBoardGeometry
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.engine.PowerUpTile
import com.example.engine.PowerUpType
import com.example.engine.RuleSetRegistry
import com.example.engine.Token
import com.example.engine.TokenRef
import com.example.engine.TurnPhase
import com.example.ui.theme.BaseCornerStyle
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.LudoTheme
import com.example.ui.theme.ludoShadow
import com.example.ui.theme.powerUpAccentColor
import com.example.ui.theme.powerUpSoftBg
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private data class RenderToken(
    val ref: TokenRef,
    val row: Float,
    val col: Float,
    val radiusScale: Float,
    val isMovable: Boolean,
    val isSelected: Boolean,
    val isBaseExitMovable: Boolean,
    val isDimmed: Boolean,
    val isAnimating: Boolean,
    val shieldTurnsRemaining: Int,
    val isShieldTarget: Boolean,
    val isJoint: Boolean = false
)

private data class SquareStackBadge(
    val row: Float,
    val col: Float,
    val count: Int,
    val accentColor: Color
)

/**
 * Themed 15x15 interactive Ludo board matching Phase 3 Part 3 specifications:
 * - Borderless on Arcade Light (no frame, no border, no shadow, board == bg).
 * - Theme-framed with exact frame/padding/radius/shadow on Candy Pop, Night Arcade, Royal Lounge.
 * - Clean cells, corner bases (tint vs solid), outlined stars, center crown, crisp flat tokens
 *   with movable ring/arrow, lifted selected state, base "+" exit badge, and blue dashed ghost marker
 *   with capture crosshair.
 */
@Composable
fun LudoBoardCanvas(
    uiState: GameUiState,
    onTokenTapped: (TokenRef) -> Unit,
    onTokenTappedWithAnchor: ((TokenRef, Offset) -> Unit)? = null,
    onEmptyBoardTap: (() -> Unit)? = null,
    selectedTokenRef: TokenRef? = null,
    ghostDestination: GhostDestinationState? = null,
    onShieldTokenSelected: (Int) -> Unit = {},
    onBombSquareSelected: (Int) -> Unit = {},
    isLocalPlayersTurn: Boolean = true,
    onOutOfTurnTap: ((TokenRef?) -> Unit)? = null,
    localViewerColor: PlayerColor? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val gameState = uiState.gameState
    val textMeasurer = rememberTextMeasurer()

    val activeBlockades = remember(gameState.mode, gameState.matchFormat, gameState.players, gameState.chaosState) {
        if (gameState.mode == GameModeId.BLOCKADES) {
            LudoEngine(RuleSetRegistry.forMode(gameState.mode, gameState.matchFormat)).activeBlockades(gameState)
        } else {
            emptyMap()
        }
    }

    val activeBombTargetIndices = remember(
        uiState.powerUpTargeting,
        gameState.phase,
        gameState.powerUpState.pendingBombPlacement,
        isLocalPlayersTurn,
        gameState.activePlayerIndex
    ) {
        val fromTargeting = (uiState.powerUpTargeting as? PowerUpTargetingState.BombPlacement)?.validTrackIndices
        val fromPhase = if (isLocalPlayersTurn &&
            !gameState.activePlayer.setup.isBot &&
            gameState.phase == TurnPhase.AWAITING_BOMB_PLACEMENT
        ) {
            gameState.powerUpState.pendingBombPlacement?.validTrackIndices?.toSet()
        } else null
        fromTargeting ?: fromPhase ?: emptySet()
    }

    val needsPulse = (isLocalPlayersTurn &&
        gameState.phase == TurnPhase.AWAITING_MOVE &&
        !gameState.activePlayer.setup.isBot) ||
        gameState.mode == GameModeId.ARROW ||
        (gameState.mode == GameModeId.MAGIC &&
            (gameState.powerUpState.tiles.isNotEmpty() || gameState.powerUpState.bombs.isNotEmpty())) ||
        activeBombTargetIndices.isNotEmpty() ||
        (isLocalPlayersTurn && uiState.powerUpTargeting != null)

    val highlightPulse = if (needsPulse) {
        val pulseTransition = rememberInfiniteTransition(label = "movablePulse")
        val animated by pulseTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(650),
                repeatMode = RepeatMode.Reverse
            ),
            label = "movablePulseValue"
        )
        animated
    } else {
        0.5f
    }

    val hasPortals = gameState.chaosState.portalPair != null
    val portalSpin = if (hasPortals) {
        val spinTransition = rememberInfiniteTransition(label = "portalSpinTransition")
        val animated by spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "portalSpin"
        )
        animated
    } else {
        0f
    }

    val burstProgress = remember { Animatable(1f) }
    LaunchedEffect(uiState.captureBurst?.triggerKey) {
        if (uiState.captureBurst != null) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(1f, tween(durationMillis = 480))
        }
    }

    val explosionProgress = remember { Animatable(1f) }
    LaunchedEffect(uiState.bombExplosionBurst?.triggerKey) {
        if (uiState.bombExplosionBurst != null) {
            explosionProgress.snapTo(0f)
            explosionProgress.animateTo(1f, tween(durationMillis = 620))
        }
    }

    val lockBreakProgress = remember { Animatable(1f) }
    LaunchedEffect(gameState.lastHomeUnlockEventId) {
        if (gameState.lastHomeUnlockEventId > 0 && gameState.lastHomeUnlockedColor != null) {
            lockBreakProgress.snapTo(0f)
            lockBreakProgress.animateTo(1f, tween(durationMillis = 720))
        }
    }

    val (renderTokens, stackBadges) = remember(
        gameState,
        theme,
        isLocalPlayersTurn,
        selectedTokenRef,
        uiState.animatingToken,
        uiState.animatingRow,
        uiState.animatingCol,
        uiState.flyingCapturedTokens,
        uiState.powerUpTargeting
    ) {
        val (tokens, badges) = buildRenderTokensAndBadges(
            gameState = gameState,
            uiState = uiState,
            theme = theme,
            selectedTokenRef = selectedTokenRef,
            isLocalPlayersTurn = isLocalPlayersTurn
        )
        tokens.sortedWith(
            compareBy<RenderToken> { it.isAnimating }
                .thenBy { it.isSelected }
                .thenBy { it.isMovable || it.isShieldTarget }
                .thenBy { it.ref.color.cornerOrder }
        ) to badges
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val boardSide = min(maxWidth, maxHeight)
        val hasFrame = theme.frame != null && theme.framePadding > 0.dp

        val outerModifier = if (hasFrame) {
            Modifier
                .size(boardSide)
                .ludoShadow(theme.boardShadow, theme.frameRadius)
                .clip(RoundedCornerShape(theme.frameRadius))
                .background(theme.frame!!)
                .padding(theme.framePadding)
                .clip(RoundedCornerShape(theme.boardRadius))
                .background(theme.board)
                .testTag("ludo_board")
        } else {
            // Borderless board (Arcade Light): NO frame, NO border, NO shadow; board == bg
            Modifier
                .size(boardSide)
                .clip(RoundedCornerShape(theme.boardRadius))
                .background(theme.board)
                .testTag("ludo_board")
        }

        Box(modifier = outerModifier) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(
                        renderTokens,
                        gameState.phase,
                        isLocalPlayersTurn,
                        uiState.isAnimatingMove,
                        uiState.isPaused,
                        uiState.powerUpTargeting,
                        activeBombTargetIndices
                    ) {
                        detectTapGestures { tapOffset ->
                            if (uiState.isPaused || uiState.isAnimatingMove || gameState.isMatchFinished) {
                                return@detectTapGestures
                            }

                            val cell = size.width / LudoBoardGeometry.GRID_SIZE.toFloat()
                            val tapRow = tapOffset.y / cell
                            val tapCol = tapOffset.x / cell

                            if (!isLocalPlayersTurn || gameState.activePlayer.setup.isBot) {
                                val tappedAny = renderTokens.minByOrNull { token ->
                                    hypot(token.row - tapRow, token.col - tapCol)
                                }?.takeIf { hypot(it.row - tapRow, it.col - tapCol) <= 1.15f }
                                if (tappedAny != null) {
                                    onOutOfTurnTap?.invoke(tappedAny.ref)
                                }
                                return@detectTapGestures
                            }

                            if (activeBombTargetIndices.isNotEmpty()) {
                                val closestSq = activeBombTargetIndices.minByOrNull { sq ->
                                    val (sqR, sqC) = LudoBoardGeometry.outerTrackCenter(sq)
                                    hypot(sqR - tapRow, sqC - tapCol)
                                }
                                if (closestSq != null) {
                                    val (sqR, sqC) = LudoBoardGeometry.outerTrackCenter(closestSq)
                                    if (hypot(sqR - tapRow, sqC - tapCol) <= 0.95f) {
                                        onBombSquareSelected(closestSq)
                                    }
                                }
                                return@detectTapGestures
                            }

                            if (uiState.powerUpTargeting is PowerUpTargetingState.ShieldTarget) {
                                val candidates = renderTokens.filter { it.isShieldTarget }
                                val closest = candidates.minByOrNull { token ->
                                    hypot(token.row - tapRow, token.col - tapCol)
                                }
                                if (closest != null && hypot(closest.row - tapRow, closest.col - tapCol) <= 1.35f) {
                                    onShieldTokenSelected(closest.ref.tokenId)
                                }
                                return@detectTapGestures
                            }

                            if (gameState.phase != TurnPhase.AWAITING_MOVE) {
                                val tappedToken = renderTokens.minByOrNull { token ->
                                    hypot(token.row - tapRow, token.col - tapCol)
                                }?.takeIf { hypot(it.row - tapRow, it.col - tapCol) <= 1.15f }
                                if (tappedToken != null) {
                                    onOutOfTurnTap?.invoke(tappedToken.ref)
                                }
                                return@detectTapGestures
                            }

                            val movableTokens = renderTokens.filter { it.isMovable }
                            val closest = movableTokens.minByOrNull { token ->
                                hypot(token.row - tapRow, token.col - tapCol)
                            }
                            if (closest != null && hypot(closest.row - tapRow, closest.col - tapCol) <= 1.25f) {
                                val anchorPx = Offset(closest.col * cell, closest.row * cell)
                                if (onTokenTappedWithAnchor != null) {
                                    onTokenTappedWithAnchor(closest.ref, anchorPx)
                                } else {
                                    onTokenTapped(closest.ref)
                                }
                            } else {
                                onEmptyBoardTap?.invoke()
                                val tappedOther = renderTokens.minByOrNull { token ->
                                    hypot(token.row - tapRow, token.col - tapCol)
                                }?.takeIf { hypot(it.row - tapRow, it.col - tapCol) <= 1.10f }
                                if (tappedOther != null) {
                                    onOutOfTurnTap?.invoke(tappedOther.ref)
                                }
                            }
                        }
                    }
            ) {
                val cell = size.width / LudoBoardGeometry.GRID_SIZE.toFloat()

                // 1. Outer track squares with Start squares + outlined Star safe squares
                drawOuterTrackCells(cell, gameState.activeSafeTrackIndices(), theme)

                // 2. 4 colored home columns (+ Master mode lock icon & break animation)
                drawHomeColumns(
                    cell = cell,
                    gameState = gameState,
                    theme = theme,
                    unlockAnimColor = gameState.lastHomeUnlockedColor,
                    unlockAnimProgress = lockBreakProgress.value
                )

                // 3. Arrow Mode curved shortcut arrows
                if (gameState.mode == GameModeId.ARROW) {
                    drawArrowModeShortcuts(cell = cell, pulse = highlightPulse, theme = theme)
                }

                // 4. Chaos Portals
                gameState.chaosState.portalPair?.let { (pA, pB) ->
                    drawPortalAtTrackIndex(pA, cell, portalSpin, theme)
                    drawPortalAtTrackIndex(pB, cell, -portalSpin, theme)
                }

                // 5. Magic Mode item tiles, owner-visible hidden bombs, and bomb target highlights
                if (gameState.mode == GameModeId.MAGIC) {
                    for (tile in gameState.powerUpState.tiles) {
                        drawPowerUpTile(tile, cell, highlightPulse, theme)
                    }

                    val visibleBombOwner = localViewerColor
                        ?: gameState.activePlayer.takeIf { !it.setup.isBot }?.color
                    if (visibleBombOwner != null) {
                        for (bomb in gameState.powerUpState.bombs) {
                            if (bomb.ownerColor == visibleBombOwner) {
                                drawOwnerHiddenBomb(bomb, cell, highlightPulse, textMeasurer, theme)
                            }
                        }
                    }

                    for (sq in activeBombTargetIndices) {
                        drawBombTargetSquare(sq, cell, highlightPulse, theme)
                    }
                }

                // 6. 4 corner 6x6 bases
                drawCornerBase(PlayerColor.RED, 0, 0, cell, gameState.activePlayer.color == PlayerColor.RED, theme)
                drawCornerBase(PlayerColor.GREEN, 0, 9, cell, gameState.activePlayer.color == PlayerColor.GREEN, theme)
                drawCornerBase(PlayerColor.YELLOW, 9, 9, cell, gameState.activePlayer.color == PlayerColor.YELLOW, theme)
                drawCornerBase(PlayerColor.BLUE, 9, 0, cell, gameState.activePlayer.color == PlayerColor.BLUE, theme)

                // 7. Center 3x3 home triangles + center circle with outlined crown
                drawCenterHomeTriangles(cell, theme)

                // 8. Blockades wall emblem under stacked blockade tokens
                if (activeBlockades.isNotEmpty()) {
                    for ((trackIdx, ownerColor) in activeBlockades) {
                        drawBlockadeWallEmblem(trackIdx, ownerColor, cell, theme)
                    }
                }

                // 9. Ghost landing marker while holding a move option chip
                if (ghostDestination != null) {
                    drawGhostDestinationMarker(
                        ghost = ghostDestination,
                        cell = cell,
                        theme = theme
                    )
                }

                // 10. Tokens (dimmed non-movable -> normal -> movable -> selected -> animating)
                for (token in renderTokens) {
                    val scale = if (token.isAnimating) uiState.hopScale else 1f
                    drawThemedToken(
                        token = token,
                        cell = cell,
                        hopScale = scale,
                        highlightPulse = highlightPulse,
                        textMeasurer = textMeasurer,
                        theme = theme
                    )
                }

                // 11. Stack count badges (3+ tokens on one square)
                for (badge in stackBadges) {
                    drawStackCountBadge(
                        badge = badge,
                        cell = cell,
                        textMeasurer = textMeasurer,
                        theme = theme
                    )
                }

                // 12. Captured tokens flying back to base
                for (flying in uiState.flyingCapturedTokens) {
                    drawFlyingCapturedToken(flying = flying, cell = cell, theme = theme)
                }

                // 13. Capture impact burst
                val burst = uiState.captureBurst
                if (burst != null && burstProgress.value < 1f) {
                    drawCaptureBurst(
                        center = Offset(burst.col * cell, burst.row * cell),
                        cell = cell,
                        color = theme.playerColor(burst.color),
                        sparkColor = theme.yellow,
                        progress = burstProgress.value
                    )
                }

                // 14. Bomb explosion blast
                val boom = uiState.bombExplosionBurst
                if (boom != null && explosionProgress.value < 1f) {
                    drawBombExplosionBurst(
                        center = Offset(boom.col * cell, boom.row * cell),
                        cell = cell,
                        theme = theme,
                        progress = explosionProgress.value
                    )
                }
            }
        }
    }
}

private fun buildRenderTokensAndBadges(
    gameState: GameState,
    uiState: GameUiState,
    theme: LudoTheme,
    selectedTokenRef: TokenRef?,
    isLocalPlayersTurn: Boolean
): Pair<List<RenderToken>, List<SquareStackBadge>> {
    val allowInteractiveHighlights = isLocalPlayersTurn &&
        !gameState.activePlayer.setup.isBot &&
        !uiState.isAnimatingMove &&
        !uiState.isDiceRolling

    val movableSet = if (allowInteractiveHighlights && gameState.phase == TurnPhase.AWAITING_MOVE) {
        gameState.legalMoves.flatMap { m ->
            m.tokenIds.map { tid -> TokenRef(m.color, tid) }
        }.toSet()
    } else {
        emptySet()
    }
    val shouldDimUnmovable = allowInteractiveHighlights &&
        gameState.phase == TurnPhase.AWAITING_MOVE &&
        movableSet.isNotEmpty()

    val shieldTargetSet = if (allowInteractiveHighlights) {
        when (val t = uiState.powerUpTargeting) {
            is PowerUpTargetingState.ShieldTarget ->
                t.validTokenIds.map { TokenRef(gameState.activePlayer.color, it) }.toSet()
            else -> emptySet()
        }
    } else {
        emptySet()
    }

    val flyingVictimRefs = uiState.flyingCapturedTokens
        .map { TokenRef(it.color, it.tokenId) }
        .toSet()

    val allTokens = gameState.players
        .filter { !it.isEliminated }
        .flatMap { it.tokens }
        .filter { TokenRef(it.color, it.id) !in flyingVictimRefs }

    val hiddenJointPartners = mutableSetOf<TokenRef>()
    for (token in allTokens) {
        val partnerId = token.jointPartnerId ?: continue
        if (!token.isJoint) continue
        val ref = TokenRef(token.color, token.id)
        val partnerRef = TokenRef(token.color, partnerId)
        val animatingThis = uiState.isAnimatingMove && uiState.animatingToken == ref
        val animatingPartner = uiState.isAnimatingMove && uiState.animatingToken == partnerRef
        when {
            animatingThis -> hiddenJointPartners += partnerRef
            animatingPartner -> hiddenJointPartners += ref
            token.id < partnerId -> hiddenJointPartners += partnerRef
        }
    }

    val visibleTokens = allTokens.filter { TokenRef(it.color, it.id) !in hiddenJointPartners }
    val coordGroups = mutableMapOf<Pair<Int, Int>, MutableList<Token>>()

    for (token in visibleTokens) {
        val ref = TokenRef(token.color, token.id)
        if (uiState.animatingToken == ref && uiState.isAnimatingMove) continue
        if (token.isInBase || token.isHome) continue

        val (r, c) = LudoBoardGeometry.tokenBoardPosition(
            token.color, token.id, token.progress, token.outerWrap51
        )
        val key = (r * 10f).toInt() to (c * 10f).toInt()
        coordGroups.getOrPut(key) { mutableListOf() }.add(token)
    }

    val result = mutableListOf<RenderToken>()
    val badges = mutableListOf<SquareStackBadge>()

    for (token in visibleTokens) {
        val ref = TokenRef(token.color, token.id)
        if (uiState.animatingToken == ref && uiState.isAnimatingMove) {
            result += RenderToken(
                ref = ref,
                row = uiState.animatingRow,
                col = uiState.animatingCol,
                radiusScale = 1.04f,
                isMovable = false,
                isSelected = false,
                isBaseExitMovable = false,
                isDimmed = false,
                isAnimating = true,
                shieldTurnsRemaining = token.shieldTurnsRemaining,
                isShieldTarget = false,
                isJoint = token.isJoint
            )
            continue
        }

        if (token.isInBase || token.isHome) {
            val (r, c) = LudoBoardGeometry.tokenBoardPosition(
                token.color, token.id, token.progress, token.outerWrap51
            )
            val isMovable = ref in movableSet
            result += RenderToken(
                ref = ref,
                row = r,
                col = c,
                radiusScale = if (token.isHome) 0.65f else 1f,
                isMovable = isMovable,
                isSelected = isMovable && ref == selectedTokenRef,
                isBaseExitMovable = isMovable && token.isInBase,
                isDimmed = shouldDimUnmovable && !isMovable && !token.isHome,
                isAnimating = false,
                shieldTurnsRemaining = token.shieldTurnsRemaining,
                isShieldTarget = ref in shieldTargetSet,
                isJoint = false
            )
        }
    }

    for ((_, group) in coordGroups) {
        val count = group.size
        val (centerR, centerC) = LudoBoardGeometry.tokenBoardPosition(
            group.first().color, group.first().id, group.first().progress, group.first().outerWrap51
        )
        if (count >= 3) {
            badges += SquareStackBadge(
                row = centerR,
                col = centerC,
                count = count,
                accentColor = theme.playerColor(group.first().color)
            )
        }
        group.forEachIndexed { idx, token ->
            val ref = TokenRef(token.color, token.id)
            val isMovable = ref in movableSet
            if (count == 1) {
                result += RenderToken(
                    ref = ref,
                    row = centerR,
                    col = centerC,
                    radiusScale = if (token.isJoint) 1.05f else 1.0f,
                    isMovable = isMovable,
                    isSelected = isMovable && ref == selectedTokenRef,
                    isBaseExitMovable = false,
                    isDimmed = shouldDimUnmovable && !isMovable,
                    isAnimating = false,
                    shieldTurnsRemaining = token.shieldTurnsRemaining,
                    isShieldTarget = ref in shieldTargetSet,
                    isJoint = token.isJoint
                )
            } else {
                val angle = (2.0 * PI * idx / count) - PI / 4.0
                val spread = if (count == 2) 0.21f else 0.25f
                result += RenderToken(
                    ref = ref,
                    row = centerR + (sin(angle) * spread).toFloat(),
                    col = centerC + (cos(angle) * spread).toFloat(),
                    radiusScale = if (count == 2) 0.78f else 0.64f,
                    isMovable = isMovable,
                    isSelected = isMovable && ref == selectedTokenRef,
                    isBaseExitMovable = false,
                    isDimmed = shouldDimUnmovable && !isMovable,
                    isAnimating = false,
                    shieldTurnsRemaining = token.shieldTurnsRemaining,
                    isShieldTarget = ref in shieldTargetSet,
                    isJoint = token.isJoint
                )
            }
        }
    }

    return result to badges
}

private val START_COLOR_MAP = mapOf(
    0 to PlayerColor.RED,
    13 to PlayerColor.GREEN,
    26 to PlayerColor.YELLOW,
    39 to PlayerColor.BLUE
)

private val HOME_ENTRY_ARROW_MAP = mapOf(
    50 to (PlayerColor.RED to 0f),
    11 to (PlayerColor.GREEN to 90f),
    24 to (PlayerColor.YELLOW to 180f),
    37 to (PlayerColor.BLUE to 270f)
)

private fun DrawScope.drawOuterTrackCells(
    cell: Float,
    activeSafeIndices: Set<Int>,
    theme: LudoTheme
) {
    val gridStroke = 0.5.dp.toPx().coerceAtLeast(0.8f)
    LudoBoardGeometry.OUTER_TRACK.forEachIndexed { idx, coord ->
        val topLeft = Offset(coord.col * cell, coord.row * cell)
        val cellSize = Size(cell, cell)

        val startColor = START_COLOR_MAP[idx]
        val entryInfo = HOME_ENTRY_ARROW_MAP[idx]
        val isSafeStar = idx in activeSafeIndices && startColor == null

        val fill = when {
            startColor != null -> theme.playerColor(startColor)
            else -> theme.cell
        }

        drawRect(color = fill, topLeft = topLeft, size = cellSize)
        drawRect(
            color = theme.gridLine,
            topLeft = topLeft,
            size = cellSize,
            style = Stroke(width = gridStroke)
        )

        val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
        if (startColor != null) {
            drawStar(
                center = center,
                outerRadius = cell * 0.34f,
                innerRadius = cell * 0.16f,
                color = if (idx in activeSafeIndices) Color.White else Color.White.copy(alpha = 0.55f),
                outlined = false
            )
        } else if (isSafeStar) {
            // Safe star square: theme.cell fill + outlined star in theme.starIcon
            drawStar(
                center = center,
                outerRadius = cell * 0.34f,
                innerRadius = cell * 0.16f,
                color = theme.starIcon,
                outlined = true,
                strokeWidth = 1.5.dp.toPx()
            )
        } else if (entryInfo != null) {
            drawDirectionArrow(
                center = center,
                cell = cell,
                angleDeg = entryInfo.second,
                color = theme.playerColor(entryInfo.first).copy(alpha = 0.72f)
            )
        }
    }
}

private fun DrawScope.drawArrowModeShortcuts(
    cell: Float,
    pulse: Float,
    theme: LudoTheme
) {
    val boardCenter = Offset(7.5f * cell, 7.5f * cell)
    val glowColor = theme.green
    val coreColor = lerp(theme.green, Color.Black, 0.18f)
    val highlightColor = lerp(theme.green, Color.White, 0.65f)

    for ((tailIdx, tipIdx) in LudoBoardGeometry.ARROW_SHORTCUTS) {
        val (tailR, tailC) = LudoBoardGeometry.outerTrackCenter(tailIdx)
        val (tipR, tipC) = LudoBoardGeometry.outerTrackCenter(tipIdx)
        val start = Offset(tailC * cell, tailR * cell)
        val end = Offset(tipC * cell, tipR * cell)

        drawCircle(
            color = glowColor.copy(alpha = 0.22f + pulse * 0.16f),
            radius = cell * 0.42f,
            center = start
        )
        drawCircle(
            color = glowColor.copy(alpha = 0.88f),
            radius = cell * 0.26f,
            center = start,
            style = Stroke(width = cell * 0.08f)
        )

        val midX = (start.x + end.x) * 0.5f
        val midY = (start.y + end.y) * 0.5f
        val ctrlX = midX + (boardCenter.x - midX) * 0.22f
        val ctrlY = midY + (boardCenter.y - midY) * 0.22f
        val control = Offset(ctrlX, ctrlY)

        val curvePath = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(control.x, control.y, end.x, end.y)
        }

        drawPath(
            path = curvePath,
            color = glowColor.copy(alpha = 0.28f + pulse * 0.22f),
            style = Stroke(width = cell * 0.28f, cap = StrokeCap.Round)
        )
        drawPath(
            path = curvePath,
            color = coreColor.copy(alpha = 0.92f),
            style = Stroke(width = cell * 0.14f, cap = StrokeCap.Round)
        )
        drawPath(
            path = curvePath,
            color = highlightColor,
            style = Stroke(width = cell * 0.05f, cap = StrokeCap.Round)
        )

        val angleRad = atan2((end.y - control.y).toDouble(), (end.x - control.x).toDouble())
        val headLen = cell * 0.34f
        val spreadRad = 0.52
        val leftWing = Offset(
            end.x - (headLen * cos(angleRad - spreadRad)).toFloat(),
            end.y - (headLen * sin(angleRad - spreadRad)).toFloat()
        )
        val rightWing = Offset(
            end.x - (headLen * cos(angleRad + spreadRad)).toFloat(),
            end.y - (headLen * sin(angleRad + spreadRad)).toFloat()
        )
        val headPath = Path().apply {
            moveTo(end.x, end.y)
            lineTo(leftWing.x, leftWing.y)
            lineTo(rightWing.x, rightWing.y)
            close()
        }
        drawPath(path = headPath, color = coreColor)
        drawPath(
            path = headPath,
            color = theme.tokenEdge,
            style = Stroke(width = cell * 0.045f, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawBlockadeWallEmblem(
    trackIndex: Int,
    ownerColor: PlayerColor,
    cell: Float,
    theme: LudoTheme
) {
    val coord = LudoBoardGeometry.OUTER_TRACK[trackIndex.mod(Token.TRACK_LENGTH)]
    val topLeft = Offset(coord.col * cell, coord.row * cell)
    val palette = theme.playerPalette(ownerColor)

    drawRoundRect(
        color = palette.primary.copy(alpha = 0.18f),
        topLeft = Offset(topLeft.x + cell * 0.04f, topLeft.y + cell * 0.04f),
        size = Size(cell * 0.92f, cell * 0.92f),
        cornerRadius = CornerRadius(cell * 0.16f, cell * 0.16f)
    )
    drawRoundRect(
        color = palette.dark.copy(alpha = 0.90f),
        topLeft = Offset(topLeft.x + cell * 0.05f, topLeft.y + cell * 0.05f),
        size = Size(cell * 0.90f, cell * 0.90f),
        cornerRadius = CornerRadius(cell * 0.16f, cell * 0.16f),
        style = Stroke(width = cell * 0.09f)
    )

    val wallW = cell * 0.60f
    val wallH = cell * 0.22f
    val wallLeft = topLeft.x + (cell - wallW) * 0.5f
    val wallTop = topLeft.y + cell * 0.72f
    drawRoundRect(
        color = palette.dark,
        topLeft = Offset(wallLeft, wallTop),
        size = Size(wallW, wallH),
        cornerRadius = CornerRadius(cell * 0.06f, cell * 0.06f)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(wallLeft + wallW * 0.33f, wallTop),
        end = Offset(wallLeft + wallW * 0.33f, wallTop + wallH),
        strokeWidth = (cell * 0.04f).coerceAtLeast(1.2f)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(wallLeft + wallW * 0.67f, wallTop),
        end = Offset(wallLeft + wallW * 0.67f, wallTop + wallH),
        strokeWidth = (cell * 0.04f).coerceAtLeast(1.2f)
    )
}

private fun DrawScope.drawDirectionArrow(
    center: Offset,
    cell: Float,
    angleDeg: Float,
    color: Color
) {
    val shaft = cell * 0.22f
    val wing = cell * 0.15f
    val strokeW = (cell * 0.085f).coerceAtLeast(1.6f)
    rotate(degrees = angleDeg, pivot = center) {
        drawLine(
            color = color,
            start = Offset(center.x - shaft, center.y),
            end = Offset(center.x + shaft, center.y),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        val chevron = Path().apply {
            moveTo(center.x + shaft - wing, center.y - wing)
            lineTo(center.x + shaft, center.y)
            lineTo(center.x + shaft - wing, center.y + wing)
        }
        drawPath(
            path = chevron,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawPowerUpTile(
    tile: PowerUpTile,
    cell: Float,
    pulse: Float,
    theme: LudoTheme
) {
    val coord = LudoBoardGeometry.OUTER_TRACK[tile.trackIndex.mod(Token.TRACK_LENGTH)]
    val topLeft = Offset(coord.col * cell, coord.row * cell)
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    val accent = powerUpAccentColor(tile.type, theme)
    val softBg = powerUpSoftBg(tile.type, theme)

    drawRoundRect(
        color = softBg,
        topLeft = Offset(topLeft.x + cell * 0.06f, topLeft.y + cell * 0.06f),
        size = Size(cell * 0.88f, cell * 0.88f),
        cornerRadius = CornerRadius(cell * 0.22f, cell * 0.22f)
    )
    drawRoundRect(
        color = accent.copy(alpha = 0.55f + pulse * 0.40f),
        topLeft = Offset(topLeft.x + cell * 0.06f, topLeft.y + cell * 0.06f),
        size = Size(cell * 0.88f, cell * 0.88f),
        cornerRadius = CornerRadius(cell * 0.22f, cell * 0.22f),
        style = Stroke(width = cell * 0.09f)
    )

    when (tile.type) {
        PowerUpType.ROCKET -> {
            rotate(degrees = 45f, pivot = center) {
                val bodyW = cell * 0.22f
                val bodyH = cell * 0.44f
                val nose = Path().apply {
                    moveTo(center.x, center.y - bodyH * 0.68f)
                    lineTo(center.x + bodyW * 0.65f, center.y - bodyH * 0.15f)
                    lineTo(center.x + bodyW * 0.55f, center.y + bodyH * 0.42f)
                    lineTo(center.x - bodyW * 0.55f, center.y + bodyH * 0.42f)
                    lineTo(center.x - bodyW * 0.65f, center.y - bodyH * 0.15f)
                    close()
                }
                drawPath(nose, color = accent)
                drawPath(nose, color = Color.White, style = Stroke(width = cell * 0.045f))
                drawCircle(color = Color.White, radius = cell * 0.075f, center = Offset(center.x, center.y - bodyH * 0.12f))
                drawCircle(
                    color = theme.yellow,
                    radius = cell * (0.10f + pulse * 0.03f),
                    center = Offset(center.x, center.y + bodyH * 0.56f)
                )
            }
        }

        PowerUpType.GOLDEN_DICE -> {
            val dSize = cell * 0.48f
            val dLeft = center.x - dSize * 0.5f
            val dTop = center.y - dSize * 0.5f
            drawRoundRect(
                color = theme.yellow,
                topLeft = Offset(dLeft, dTop),
                size = Size(dSize, dSize),
                cornerRadius = CornerRadius(cell * 0.10f, cell * 0.10f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(dLeft, dTop),
                size = Size(dSize, dSize),
                cornerRadius = CornerRadius(cell * 0.10f, cell * 0.10f),
                style = Stroke(width = cell * 0.055f)
            )
            val pipR = cell * 0.042f
            val dx = dSize * 0.24f
            val dy = dSize * 0.26f
            val pipColor = theme.contrastingTextOn(theme.yellow)
            for (sx in listOf(-1f, 1f)) {
                for (sy in listOf(-1f, 0f, 1f)) {
                    drawCircle(
                        color = pipColor,
                        radius = pipR,
                        center = Offset(center.x + sx * dx, center.y + sy * dy)
                    )
                }
            }
        }

        PowerUpType.SHIELD -> {
            val r = cell * 0.28f
            val shieldPath = Path().apply {
                moveTo(center.x, center.y - r)
                lineTo(center.x + r * 0.85f, center.y - r * 0.55f)
                lineTo(center.x + r * 0.72f, center.y + r * 0.28f)
                lineTo(center.x, center.y + r)
                lineTo(center.x - r * 0.72f, center.y + r * 0.28f)
                lineTo(center.x - r * 0.85f, center.y - r * 0.55f)
                close()
            }
            drawPath(shieldPath, color = accent)
            drawPath(shieldPath, color = Color.White, style = Stroke(width = cell * 0.05f))
        }

        PowerUpType.BOMB_TRAP -> {
            val bombR = cell * 0.22f
            drawCircle(
                color = theme.pip,
                radius = bombR,
                center = Offset(center.x - cell * 0.03f, center.y + cell * 0.06f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = bombR * 0.28f,
                center = Offset(center.x - bombR * 0.45f, center.y - bombR * 0.10f)
            )
            val fuseStart = Offset(center.x + bombR * 0.45f, center.y - bombR * 0.55f)
            val fuseEnd = Offset(center.x + bombR * 1.05f, center.y - bombR * 1.15f)
            drawLine(color = theme.starIcon, start = fuseStart, end = fuseEnd, strokeWidth = cell * 0.07f)
            drawCircle(
                color = theme.red,
                radius = cell * (0.09f + pulse * 0.04f),
                center = fuseEnd
            )
            drawCircle(
                color = theme.yellow,
                radius = cell * 0.05f,
                center = fuseEnd
            )
        }
    }
}

private fun DrawScope.drawOwnerHiddenBomb(
    bomb: BombTrap,
    cell: Float,
    pulse: Float,
    textMeasurer: TextMeasurer,
    theme: LudoTheme
) {
    val coord = LudoBoardGeometry.OUTER_TRACK[bomb.trackIndex.mod(Token.TRACK_LENGTH)]
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    val crimson = theme.red

    drawCircle(
        color = crimson.copy(alpha = 0.18f),
        radius = cell * 0.43f,
        center = center
    )
    drawCircle(
        color = crimson.copy(alpha = 0.70f + pulse * 0.25f),
        radius = cell * 0.41f,
        center = center,
        style = Stroke(
            width = cell * 0.07f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.16f, cell * 0.12f))
        )
    )

    val bombR = cell * 0.20f
    drawCircle(
        color = theme.pip.copy(alpha = 0.90f),
        radius = bombR,
        center = Offset(center.x - cell * 0.03f, center.y + cell * 0.04f)
    )
    val sparkCenter = Offset(center.x + bombR * 0.85f, center.y - bombR * 0.90f)
    drawLine(
        color = theme.starIcon,
        start = Offset(center.x + bombR * 0.35f, center.y - bombR * 0.40f),
        end = sparkCenter,
        strokeWidth = cell * 0.06f
    )
    drawCircle(color = theme.yellow, radius = cell * 0.08f, center = sparkCenter)

    val badgeCenter = Offset(center.x + cell * 0.27f, center.y + cell * 0.27f)
    val badgeR = cell * 0.17f
    drawCircle(color = crimson, radius = badgeR, center = badgeCenter)
    val measured = textMeasurer.measure(
        text = bomb.roundsRemaining.toString(),
        style = TextStyle(
            color = theme.contrastingTextOn(crimson),
            fontFamily = theme.font,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold
        )
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            badgeCenter.x - measured.size.width / 2f,
            badgeCenter.y - measured.size.height / 2f
        )
    )
}

private fun DrawScope.drawBombTargetSquare(
    trackIndex: Int,
    cell: Float,
    pulse: Float,
    theme: LudoTheme
) {
    val coord = LudoBoardGeometry.OUTER_TRACK[trackIndex.mod(Token.TRACK_LENGTH)]
    val topLeft = Offset(coord.col * cell, coord.row * cell)
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    val crimson = theme.red

    drawRect(
        color = crimson.copy(alpha = 0.18f + pulse * 0.16f),
        topLeft = topLeft,
        size = Size(cell, cell)
    )
    drawRoundRect(
        color = crimson,
        topLeft = Offset(topLeft.x + cell * 0.08f, topLeft.y + cell * 0.08f),
        size = Size(cell * 0.84f, cell * 0.84f),
        cornerRadius = CornerRadius(cell * 0.18f, cell * 0.18f),
        style = Stroke(width = cell * (0.08f + pulse * 0.04f))
    )
    drawCircle(
        color = crimson.copy(alpha = 0.85f),
        radius = cell * (0.14f + pulse * 0.05f),
        center = center
    )
}

private fun DrawScope.drawHomeColumns(
    cell: Float,
    gameState: GameState,
    theme: LudoTheme,
    unlockAnimColor: PlayerColor?,
    unlockAnimProgress: Float
) {
    val gridStroke = 0.5.dp.toPx().coerceAtLeast(0.8f)
    for (color in PlayerColor.entries) {
        val playerColor = theme.playerColor(color)
        val coords = LudoBoardGeometry.homeColumnCoords(color)
        val player = gameState.playerByColor(color)
        val isMasterLocked = gameState.mode == GameModeId.MASTER &&
            player != null && !player.hasCapturedOpponent
        val arrowAngle = when (color) {
            PlayerColor.RED -> 0f
            PlayerColor.GREEN -> 90f
            PlayerColor.YELLOW -> 180f
            PlayerColor.BLUE -> 270f
        }

        coords.forEachIndexed { idx, coord ->
            val topLeft = Offset(coord.col * cell, coord.row * cell)
            val fillAlpha = if (isMasterLocked) 0.52f else 1f
            drawRect(
                color = playerColor.copy(alpha = fillAlpha),
                topLeft = topLeft,
                size = Size(cell, cell)
            )
            drawRect(
                color = theme.board.copy(alpha = 0.45f),
                topLeft = topLeft,
                size = Size(cell, cell),
                style = Stroke(width = gridStroke)
            )
            val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
            if (isMasterLocked && idx == 2) {
                drawLockBadge(center = center, cell = cell, theme = theme)
            } else if (!isMasterLocked && idx == 2 && unlockAnimColor == color && unlockAnimProgress < 1f) {
                drawLockBreakBurst(center = center, cell = cell, theme = theme, progress = unlockAnimProgress)
            } else if (idx == 4) {
                drawDirectionArrow(
                    center = center,
                    cell = cell * 0.82f,
                    angleDeg = arrowAngle,
                    color = Color.White.copy(alpha = 0.48f)
                )
            }
        }
    }
}

private fun DrawScope.drawLockBadge(center: Offset, cell: Float, theme: LudoTheme) {
    drawCircle(color = theme.surface, radius = cell * 0.38f, center = center)
    val bodyW = cell * 0.40f
    val bodyH = cell * 0.30f
    drawRoundRect(
        color = theme.surfaceText,
        topLeft = Offset(center.x - bodyW / 2f, center.y - bodyH * 0.15f),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawArc(
        color = theme.surfaceText,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - bodyW * 0.30f, center.y - bodyH * 0.72f),
        size = Size(bodyW * 0.60f, bodyH * 0.85f),
        style = Stroke(width = cell * 0.08f)
    )
}

private fun DrawScope.drawLockBreakBurst(
    center: Offset,
    cell: Float,
    theme: LudoTheme,
    progress: Float
) {
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val spread = cell * 0.95f * progress
    drawCircle(
        color = theme.yellow.copy(alpha = alpha * 0.85f),
        radius = cell * 0.36f + spread,
        center = center,
        style = Stroke(width = cell * 0.14f * alpha)
    )
    val halfW = cell * 0.18f
    val halfH = cell * 0.26f
    drawRoundRect(
        color = theme.surfaceText.copy(alpha = alpha),
        topLeft = Offset(center.x - halfW - spread * 0.48f, center.y - halfH * 0.4f - spread * 0.20f),
        size = Size(halfW, halfH),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = theme.surfaceText.copy(alpha = alpha),
        topLeft = Offset(center.x + spread * 0.48f, center.y - halfH * 0.4f + spread * 0.20f),
        size = Size(halfW, halfH),
        cornerRadius = CornerRadius(3f, 3f)
    )
    for (i in 0 until 6) {
        val angle = i * PI / 3.0
        val start = Offset(
            center.x + (cos(angle) * spread * 0.35f).toFloat(),
            center.y + (sin(angle) * spread * 0.35f).toFloat()
        )
        val end = Offset(
            center.x + (cos(angle) * (cell * 0.35f + spread)).toFloat(),
            center.y + (sin(angle) * (cell * 0.35f + spread)).toFloat()
        )
        drawLine(
            color = theme.yellow.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = 4.5f * alpha,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawPortalAtTrackIndex(
    trackIndex: Int,
    cell: Float,
    spinDegrees: Float,
    theme: LudoTheme
) {
    val coord = LudoBoardGeometry.OUTER_TRACK[trackIndex.mod(Token.TRACK_LENGTH)]
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    val violet = lerp(theme.blue, theme.red, 0.48f)
    rotate(degrees = spinDegrees, pivot = center) {
        drawCircle(
            color = violet.copy(alpha = 0.26f),
            radius = cell * 0.44f,
            center = center
        )
        drawCircle(
            color = theme.blue,
            radius = cell * 0.34f,
            center = center,
            style = Stroke(width = cell * 0.10f)
        )
        drawArc(
            color = theme.red,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - cell * 0.24f, center.y - cell * 0.24f),
            size = Size(cell * 0.48f, cell * 0.48f),
            style = Stroke(width = cell * 0.08f)
        )
        drawCircle(
            color = Color.White,
            radius = cell * 0.11f,
            center = center
        )
    }
}

private fun DrawScope.drawCornerBase(
    color: PlayerColor,
    startRow: Int,
    startCol: Int,
    cell: Float,
    isActive: Boolean,
    theme: LudoTheme
) {
    val playerColor = theme.playerColor(color)
    val x = startCol * cell
    val y = startRow * cell
    val boxSize = 6f * cell
    val baseFill = when (theme.baseStyle) {
        BaseCornerStyle.SOLID -> playerColor
        BaseCornerStyle.TINT -> playerColor.copy(alpha = 0.18f)
    }

    drawRect(
        color = baseFill,
        topLeft = Offset(x, y),
        size = Size(boxSize, boxSize)
    )

    if (isActive) {
        val strokeW = 2.dp.toPx()
        drawRect(
            color = playerColor.copy(alpha = if (theme.baseStyle == BaseCornerStyle.TINT) 0.45f else 0.90f),
            topLeft = Offset(x + strokeW / 2f, y + strokeW / 2f),
            size = Size(boxSize - strokeW, boxSize - strokeW),
            style = Stroke(width = strokeW)
        )
    }

    // Inner rounded square (14dp corners) in theme.baseInner inset by 1 cell
    val pad = cell
    val innerSize = boxSize - pad * 2f
    val cornerRadiusPx = 14.dp.toPx().coerceAtMost(innerSize * 0.28f)
    val cornerR = CornerRadius(cornerRadiusPx, cornerRadiusPx)
    drawRoundRect(
        color = theme.baseInner,
        topLeft = Offset(x + pad, y + pad),
        size = Size(innerSize, innerSize),
        cornerRadius = cornerR
    )

    // 4 token spots: 7.5% of board size diameter, 15% player fill + 2dp ring at 40%
    val spotRadius = size.width * 0.0375f
    val spotRingWidth = 2.dp.toPx()
    for (tokenId in 0..3) {
        val (tr, tc) = LudoBoardGeometry.baseTokenCenter(color, tokenId)
        val center = Offset(tc * cell, tr * cell)
        drawCircle(
            color = playerColor.copy(alpha = 0.15f),
            radius = spotRadius,
            center = center
        )
        drawCircle(
            color = playerColor.copy(alpha = 0.40f),
            radius = spotRadius,
            center = center,
            style = Stroke(width = spotRingWidth)
        )
    }
}

private fun DrawScope.drawCenterHomeTriangles(cell: Float, theme: LudoTheme) {
    val left = 6f * cell
    val top = 6f * cell
    val right = 9f * cell
    val bottom = 9f * cell
    val cx = 7.5f * cell
    val cy = 7.5f * cell

    drawTriangle(Offset(left, top), Offset(left, bottom), Offset(cx, cy), theme.red, theme.board)
    drawTriangle(Offset(left, top), Offset(right, top), Offset(cx, cy), theme.green, theme.board)
    drawTriangle(Offset(right, top), Offset(right, bottom), Offset(cx, cy), theme.yellow, theme.board)
    drawTriangle(Offset(left, bottom), Offset(right, bottom), Offset(cx, cy), theme.blue, theme.board)

    // Center circle in theme.board holding an outlined crown in theme.crownIcon
    val centerRadius = cell * 0.54f
    drawCircle(
        color = theme.board,
        radius = centerRadius,
        center = Offset(cx, cy)
    )
    drawOutlinedCrown(
        center = Offset(cx, cy),
        width = centerRadius * 1.18f,
        height = centerRadius * 0.88f,
        color = theme.crownIcon
    )
}

private fun DrawScope.drawOutlinedCrown(
    center: Offset,
    width: Float,
    height: Float,
    color: Color
) {
    val left = center.x - width / 2f
    val right = center.x + width / 2f
    val bottom = center.y + height * 0.42f
    val top = center.y - height * 0.48f
    val valleyY = center.y + height * 0.04f

    val path = Path().apply {
        moveTo(left + width * 0.10f, bottom)
        lineTo(left, top + height * 0.22f)
        lineTo(left + width * 0.28f, valleyY)
        lineTo(center.x, top)
        lineTo(right - width * 0.28f, valleyY)
        lineTo(right, top + height * 0.22f)
        lineTo(right - width * 0.10f, bottom)
        close()
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawTriangle(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    color: Color,
    seamColor: Color
) {
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }
    drawPath(path = path, color = color)
    drawPath(path = path, color = seamColor.copy(alpha = 0.35f), style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawThemedToken(
    token: RenderToken,
    cell: Float,
    hopScale: Float,
    highlightPulse: Float,
    textMeasurer: TextMeasurer,
    theme: LudoTheme
) {
    val playerColor = theme.playerColor(token.ref.color)
    val alphaFactor = if (token.isDimmed) 0.44f else 1f
    val groundCenter = Offset(token.col * cell, token.row * cell)

    // Normal token diameter = 5.6% of board size; selected scales 115% and lifts 14%
    val normalRadius = size.width * 0.028f * token.radiusScale * hopScale
    val selectedScale = if (token.isSelected) 1.15f else 1f
    val radius = normalRadius * selectedScale
    val liftPx = when {
        token.isSelected -> (normalRadius * 2f) * 0.14f
        token.isMovable && !token.isAnimating -> cell * (0.04f + highlightPulse * 0.06f)
        else -> 0f
    }
    val center = Offset(groundCenter.x, groundCenter.y - liftPx)

    // Shield target halo
    if (token.isShieldTarget) {
        val ringR = radius * (1.55f + highlightPulse * 0.18f)
        drawCircle(
            color = theme.blue.copy(alpha = 0.18f),
            radius = ringR,
            center = groundCenter
        )
        drawCircle(
            color = theme.blue,
            radius = ringR,
            center = groundCenter,
            style = Stroke(width = 2.5.dp.toPx())
        )
    } else if (token.isSelected) {
        // Selected token ring: 170% of token size, 3dp stroke, 20% fill
        val ringR = normalRadius * 1.70f
        drawCircle(
            color = playerColor.copy(alpha = 0.20f),
            radius = ringR,
            center = groundCenter
        )
        drawCircle(
            color = playerColor,
            radius = ringR,
            center = groundCenter,
            style = Stroke(width = 3.dp.toPx())
        )
    } else if (token.isMovable) {
        // Movable token ring: 170% of token size, 2dp stroke, 12% fill
        val ringR = radius * (1.66f + highlightPulse * 0.08f)
        drawCircle(
            color = playerColor.copy(alpha = 0.12f),
            radius = ringR,
            center = groundCenter
        )
        drawCircle(
            color = playerColor,
            radius = ringR,
            center = groundCenter,
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // Ground shadow: normal y=+2dp 25% black; selected y=+7dp blur 8dp 35% black
    if (token.isSelected) {
        val shadowOffsetY = 7.dp.toPx()
        val blurPx = 8.dp.toPx()
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = Color.Black.copy(alpha = 0.35f * alphaFactor).toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawCircle(
                groundCenter.x,
                groundCenter.y + shadowOffsetY,
                radius * 0.92f,
                paint
            )
        }
    } else {
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f * alphaFactor),
            radius = radius * 0.96f,
            center = Offset(groundCenter.x, groundCenter.y + 2.dp.toPx())
        )
    }

    // Master Joint Token twin rim
    if (token.isJoint) {
        val lowerCenter = Offset(center.x + radius * 0.18f, center.y + radius * 0.18f)
        drawCircle(color = playerColor.copy(alpha = alphaFactor), radius = radius, center = lowerCenter)
        drawCircle(
            color = theme.tokenEdge.copy(alpha = alphaFactor),
            radius = radius,
            center = lowerCenter,
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // Main token disc: player color + 2.5dp theme.tokenEdge border + 50% white center dot (38% diameter)
    drawCircle(
        color = playerColor.copy(alpha = alphaFactor),
        radius = radius,
        center = center
    )
    val edgeWidth = 2.5.dp.toPx()
    drawCircle(
        color = theme.tokenEdge.copy(alpha = alphaFactor),
        radius = (radius - edgeWidth / 2f).coerceAtLeast(1f),
        center = center,
        style = Stroke(width = edgeWidth)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.50f * alphaFactor),
        radius = radius * 0.38f,
        center = center
    )

    // Base token that can exit: 14dp "+" badge in player color at top-right
    if (token.isBaseExitMovable) {
        val badgeRadius = 7.dp.toPx()
        val badgeCenter = Offset(center.x + radius * 0.78f, center.y - radius * 0.78f)
        drawCircle(color = playerColor, radius = badgeRadius, center = badgeCenter)
        drawCircle(color = theme.tokenEdge, radius = badgeRadius, center = badgeCenter, style = Stroke(width = 1.4.dp.toPx()))
        val plusArm = badgeRadius * 0.48f
        val plusStroke = 1.8.dp.toPx()
        val plusColor = theme.contrastingTextOn(playerColor)
        drawLine(
            color = plusColor,
            start = Offset(badgeCenter.x - plusArm, badgeCenter.y),
            end = Offset(badgeCenter.x + plusArm, badgeCenter.y),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = plusColor,
            start = Offset(badgeCenter.x, badgeCenter.y - plusArm),
            end = Offset(badgeCenter.x, badgeCenter.y + plusArm),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
    }

    // Down-pointing triangle (10x7dp) in player color above movable tokens (hide when bubble is open on selected token)
    if (token.isMovable && !token.isSelected && !token.isAnimating) {
        val triW = 10.dp.toPx()
        val triH = 7.dp.toPx()
        val arrowTipY = center.y - radius - 4.dp.toPx() - highlightPulse * 3.dp.toPx()
        val arrowPath = Path().apply {
            moveTo(center.x, arrowTipY)
            lineTo(center.x - triW / 2f, arrowTipY - triH)
            lineTo(center.x + triW / 2f, arrowTipY - triH)
            close()
        }
        drawPath(path = arrowPath, color = playerColor)
    }

    // Master Joint Token "2" badge
    if (token.isJoint) {
        val badgeCenter = Offset(center.x + radius * 0.72f, center.y - radius * 0.72f)
        val badgeR = 7.dp.toPx()
        drawCircle(color = theme.yellow.copy(alpha = alphaFactor), radius = badgeR, center = badgeCenter)
        drawCircle(color = theme.tokenEdge.copy(alpha = alphaFactor), radius = badgeR, center = badgeCenter, style = Stroke(width = 1.5.dp.toPx()))
        val measured = textMeasurer.measure(
            text = "2",
            style = TextStyle(
                color = theme.contrastingTextOn(theme.yellow).copy(alpha = alphaFactor),
                fontFamily = theme.font,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                badgeCenter.x - measured.size.width / 2f,
                badgeCenter.y - measured.size.height / 2f
            )
        )
    }

    // Magic Shield protective ring + turn counter
    if (token.shieldTurnsRemaining > 0) {
        val bubbleR = radius * (1.36f + highlightPulse * 0.08f)
        drawCircle(
            color = theme.blue.copy(alpha = 0.18f * alphaFactor),
            radius = bubbleR,
            center = center
        )
        drawCircle(
            color = theme.blue.copy(alpha = 0.90f * alphaFactor),
            radius = bubbleR,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        val badgeCenter = Offset(center.x + bubbleR * 0.72f, center.y - bubbleR * 0.72f)
        val badgeR = 7.dp.toPx()
        drawCircle(color = theme.blue.copy(alpha = alphaFactor), radius = badgeR, center = badgeCenter)
        drawCircle(color = theme.tokenEdge.copy(alpha = alphaFactor), radius = badgeR, center = badgeCenter, style = Stroke(width = 1.5.dp.toPx()))
        val measured = textMeasurer.measure(
            text = token.shieldTurnsRemaining.toString(),
            style = TextStyle(
                color = theme.contrastingTextOn(theme.blue).copy(alpha = alphaFactor),
                fontFamily = theme.font,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                badgeCenter.x - measured.size.width / 2f,
                badgeCenter.y - measured.size.height / 2f
            )
        )
    }
}

private fun DrawScope.drawGhostDestinationMarker(
    ghost: GhostDestinationState,
    cell: Float,
    theme: LudoTheme
) {
    val center = Offset(ghost.col * cell, ghost.row * cell)
    // Ghost landing marker: dashed 2.5dp ring in theme.blue, 9.5% of board size diameter, 13% fill
    val radius = size.width * 0.0475f
    val strokePx = 2.5.dp.toPx()

    drawCircle(
        color = theme.blue.copy(alpha = 0.13f),
        radius = radius,
        center = center
    )
    drawCircle(
        color = theme.blue,
        radius = radius,
        center = center,
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
    )

    // If the move captures, show an 18dp theme.blue circle badge with a white crosshair at top-right
    if (ghost.isCapture) {
        val badgeRadius = 9.dp.toPx()
        val badgeCenter = Offset(center.x + radius * 0.78f, center.y - radius * 0.78f)
        drawCircle(color = theme.blue, radius = badgeRadius, center = badgeCenter)
        val iconColor = Color.White
        val iconStroke = 1.5.dp.toPx()
        val targetR = badgeRadius * 0.44f
        drawCircle(
            color = iconColor,
            radius = targetR,
            center = badgeCenter,
            style = Stroke(width = iconStroke)
        )
        val crossOuter = badgeRadius * 0.68f
        drawLine(
            color = iconColor,
            start = Offset(badgeCenter.x - crossOuter, badgeCenter.y),
            end = Offset(badgeCenter.x + crossOuter, badgeCenter.y),
            strokeWidth = iconStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = iconColor,
            start = Offset(badgeCenter.x, badgeCenter.y - crossOuter),
            end = Offset(badgeCenter.x, badgeCenter.y + crossOuter),
            strokeWidth = iconStroke,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawStackCountBadge(
    badge: SquareStackBadge,
    cell: Float,
    textMeasurer: TextMeasurer,
    theme: LudoTheme
) {
    val badgeCenter = Offset(badge.col * cell + cell * 0.34f, badge.row * cell - cell * 0.34f)
    val badgeR = cell * 0.21f
    drawCircle(color = badge.accentColor, radius = badgeR, center = badgeCenter)
    drawCircle(color = theme.tokenEdge, radius = badgeR, center = badgeCenter, style = Stroke(width = 1.5.dp.toPx()))
    val measured = textMeasurer.measure(
        text = badge.count.toString(),
        style = TextStyle(
            color = theme.contrastingTextOn(badge.accentColor),
            fontFamily = theme.font,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold
        )
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            badgeCenter.x - measured.size.width / 2f,
            badgeCenter.y - measured.size.height / 2f
        )
    )
}

private fun DrawScope.drawFlyingCapturedToken(
    flying: FlyingCapturedToken,
    cell: Float,
    theme: LudoTheme
) {
    val playerColor = theme.playerColor(flying.color)
    val center = Offset(flying.col * cell, flying.row * cell)
    val radius = size.width * 0.028f * flying.scale

    drawCircle(
        color = Color.Black.copy(alpha = 0.20f),
        radius = radius * 0.85f,
        center = Offset(center.x, center.y + 4.dp.toPx())
    )
    drawCircle(color = playerColor, radius = radius, center = center)
    drawCircle(
        color = theme.tokenEdge,
        radius = radius - 1.dp.toPx(),
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.50f),
        radius = radius * 0.38f,
        center = center
    )
}

private fun DrawScope.drawStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color,
    outlined: Boolean,
    strokeWidth: Float = 2f
) {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = (i * PI / points) - (PI / 2.0)
        val px = center.x + (cos(angle) * r).toFloat()
        val py = center.y + (sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    if (outlined) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    } else {
        drawPath(path = path, color = color)
    }
}

private fun DrawScope.drawCaptureBurst(
    center: Offset,
    cell: Float,
    color: Color,
    sparkColor: Color,
    progress: Float
) {
    val alpha = (1f - progress).coerceIn(0f, 1f)
    if (progress < 0.38f) {
        val flashAlpha = (1f - progress / 0.38f) * 0.85f
        drawCircle(
            color = Color.White.copy(alpha = flashAlpha),
            radius = cell * (0.48f + progress * 0.8f),
            center = center
        )
    }
    val maxRadius = cell * 1.45f * progress + cell * 0.3f
    drawCircle(
        color = color.copy(alpha = alpha * 0.65f),
        radius = maxRadius,
        center = center,
        style = Stroke(width = cell * 0.20f * alpha)
    )
    for (i in 0 until 8) {
        val angle = (i * PI / 4.0)
        val dist = maxRadius * 0.9f
        val end = Offset(
            center.x + (cos(angle) * dist).toFloat(),
            center.y + (sin(angle) * dist).toFloat()
        )
        drawLine(
            color = sparkColor.copy(alpha = alpha),
            start = center,
            end = end,
            strokeWidth = 5f * alpha
        )
    }
}

private fun DrawScope.drawBombExplosionBurst(
    center: Offset,
    cell: Float,
    theme: LudoTheme,
    progress: Float
) {
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val outerRadius = cell * 2.1f * progress + cell * 0.35f
    val orange = lerp(theme.red, theme.yellow, 0.45f)
    drawCircle(
        color = theme.red.copy(alpha = alpha * 0.42f),
        radius = outerRadius * 0.72f,
        center = center
    )
    drawCircle(
        color = orange.copy(alpha = alpha * 0.85f),
        radius = outerRadius,
        center = center,
        style = Stroke(width = cell * 0.24f * alpha)
    )
    for (i in 0 until 12) {
        val angle = i * PI / 6.0
        val startDist = outerRadius * 0.25f
        val endDist = outerRadius * 1.05f
        val start = Offset(
            center.x + (cos(angle) * startDist).toFloat(),
            center.y + (sin(angle) * startDist).toFloat()
        )
        val end = Offset(
            center.x + (cos(angle) * endDist).toFloat(),
            center.y + (sin(angle) * endDist).toFloat()
        )
        drawLine(
            color = if (i % 2 == 0) theme.yellow.copy(alpha = alpha) else theme.red.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = 6f * alpha
        )
    }
}
