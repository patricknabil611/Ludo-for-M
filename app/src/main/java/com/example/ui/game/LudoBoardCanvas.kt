package com.example.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.example.engine.PlayerColor
import com.example.engine.PowerUpTile
import com.example.engine.PowerUpType
import com.example.engine.Token
import com.example.engine.TokenRef
import com.example.engine.TurnPhase
import com.example.ui.theme.BoardCellBorder
import com.example.ui.theme.BoardWoodRim
import com.example.ui.theme.InkDark
import com.example.ui.theme.SafeStarGold
import com.example.ui.theme.palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private data class RenderToken(
    val ref: TokenRef,
    val row: Float,
    val col: Float,
    val radiusScale: Float,
    val isMovable: Boolean,
    val isAnimating: Boolean,
    val shieldTurnsRemaining: Int,
    val isShieldTarget: Boolean
)

/**
 * Interactive 15x15 cross-shaped Ludo board with glossy pawns, dynamic safe stars,
 * Chaos portals, Master home-column locks, Power-Up tiles, glowing Shield bubbles with
 * turn counters, owner-only hidden Bomb Traps, targeting highlights, and explosion bursts.
 */
@Composable
fun LudoBoardCanvas(
    uiState: GameUiState,
    onTokenTapped: (TokenRef) -> Unit,
    onShieldTokenSelected: (Int) -> Unit = {},
    onBombSquareSelected: (Int) -> Unit = {},
    isLocalPlayersTurn: Boolean = true,
    onOutOfTurnTap: ((TokenRef?) -> Unit)? = null,
    localViewerColor: PlayerColor? = null,
    modifier: Modifier = Modifier
) {
    val gameState = uiState.gameState
    val textMeasurer = rememberTextMeasurer()

    val needsPulse = (isLocalPlayersTurn &&
        gameState.phase == TurnPhase.AWAITING_MOVE &&
        !gameState.activePlayer.setup.isBot) ||
        (gameState.mode == GameModeId.POWER_UP &&
            (gameState.powerUpState.tiles.isNotEmpty() || gameState.powerUpState.bombs.isNotEmpty())) ||
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

    val renderTokens = remember(
        gameState,
        isLocalPlayersTurn,
        uiState.animatingToken,
        uiState.animatingRow,
        uiState.animatingCol,
        uiState.powerUpTargeting
    ) {
        buildRenderTokens(gameState, uiState, isLocalPlayersTurn).sortedWith(
            compareBy<RenderToken> { it.isAnimating }
                .thenBy { it.isMovable || it.isShieldTarget }
                .thenBy { it.ref.color.cornerOrder }
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val boardSide = min(maxWidth, maxHeight)
        val outerCorner = (boardSide * 0.042f).coerceIn(14.dp, 24.dp)
        val innerCorner = (outerCorner - 4.dp).coerceAtLeast(10.dp)
        val rimPadding = (boardSide * 0.011f).coerceIn(3.dp, 6.dp)
        val borderStroke = (boardSide * 0.006f).coerceIn(2.dp, 3.5.dp)

        Box(
            modifier = Modifier
                .size(boardSide)
                .shadow(10.dp, RoundedCornerShape(outerCorner))
                .clip(RoundedCornerShape(outerCorner))
                .background(BoardWoodRim)
                .border(borderStroke, InkDark.copy(alpha = 0.22f), RoundedCornerShape(outerCorner))
                .padding(rimPadding)
                .clip(RoundedCornerShape(innerCorner))
                .background(Color.White)
                .testTag("ludo_board")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                .pointerInput(
                    renderTokens,
                    gameState.phase,
                    isLocalPlayersTurn,
                    uiState.isAnimatingMove,
                    uiState.isPaused,
                    uiState.powerUpTargeting
                ) {
                    detectTapGestures { tapOffset ->
                        if (uiState.isPaused || uiState.isAnimatingMove || gameState.isMatchFinished) {
                            return@detectTapGestures
                        }

                        val cell = size.width / LudoBoardGeometry.GRID_SIZE.toFloat()
                        val tapRow = tapOffset.y / cell
                        val tapCol = tapOffset.x / cell

                        // Strict turn-ownership gate: block taps during bot turns or another online player's turn
                        if (!isLocalPlayersTurn || gameState.activePlayer.setup.isBot) {
                            val tappedAny = renderTokens.minByOrNull { token ->
                                hypot(token.row - tapRow, token.col - tapCol)
                            }?.takeIf { hypot(it.row - tapRow, it.col - tapCol) <= 1.15f }
                            if (tappedAny != null) {
                                onOutOfTurnTap?.invoke(tappedAny.ref)
                            }
                            return@detectTapGestures
                        }

                        // 1. Power-up targeting mode intercepts board taps before rolling
                        when (val targeting = uiState.powerUpTargeting) {
                            is PowerUpTargetingState.ShieldTarget -> {
                                val candidates = renderTokens.filter { it.isShieldTarget }
                                val closest = candidates.minByOrNull { token ->
                                    hypot(token.row - tapRow, token.col - tapCol)
                                }
                                if (closest != null && hypot(closest.row - tapRow, closest.col - tapCol) <= 1.35f) {
                                    onShieldTokenSelected(closest.ref.tokenId)
                                }
                                return@detectTapGestures
                            }

                            is PowerUpTargetingState.BombPlacement -> {
                                val closestSq = targeting.validTrackIndices.minByOrNull { sq ->
                                    val (sqR, sqC) = LudoBoardGeometry.outerTrackCenter(sq)
                                    hypot(sqR - tapRow, sqC - tapCol)
                                }
                                if (closestSq != null) {
                                    val (sqR, sqC) = LudoBoardGeometry.outerTrackCenter(closestSq)
                                    if (hypot(sqR - tapRow, sqC - tapCol) <= 0.90f) {
                                        onBombSquareSelected(closestSq)
                                    }
                                }
                                return@detectTapGestures
                            }

                            null -> Unit
                        }

                        // 2. Normal move selection (only active player's legal moves during AWAITING_MOVE)
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
                            onTokenTapped(closest.ref)
                        } else {
                            // User tapped an opponent's token or an immovable token — give clear turn feedback
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

            // 1. Draw outer track with dynamic safe squares (supports Chaos Moving Stars)
            drawOuterTrackCells(cell, gameState.activeSafeTrackIndices())

            // 2. Draw 4 colored home columns (+ Master mode lock icon when not yet unlocked)
            drawHomeColumns(cell, gameState)

            // 3. Draw Chaos Portals if active
            gameState.chaosState.portalPair?.let { (pA, pB) ->
                drawPortalAtTrackIndex(pA, cell, portalSpin)
                drawPortalAtTrackIndex(pB, cell, -portalSpin)
            }

            // 4. Draw Power-Up Mode board tiles, owner-visible hidden bombs, and bomb target highlights
            if (gameState.mode == GameModeId.POWER_UP) {
                for (tile in gameState.powerUpState.tiles) {
                    drawPowerUpTile(tile, cell, highlightPulse)
                }

                // Only the owning player can see their own hidden Bomb Traps
                val visibleBombOwner = localViewerColor
                    ?: gameState.activePlayer.takeIf { !it.setup.isBot }?.color
                if (visibleBombOwner != null) {
                    for (bomb in gameState.powerUpState.bombs) {
                        if (bomb.ownerColor == visibleBombOwner) {
                            drawOwnerHiddenBomb(bomb, cell, highlightPulse, textMeasurer)
                        }
                    }
                }

                // Highlight valid squares when placing a Bomb Trap
                val bombTargeting = uiState.powerUpTargeting as? PowerUpTargetingState.BombPlacement
                if (bombTargeting != null) {
                    for (sq in bombTargeting.validTrackIndices) {
                        drawBombTargetSquare(sq, cell, highlightPulse)
                    }
                }
            }

            // 5. Draw 4 corner 6x6 bases with active turn pulse
            drawCornerBase(PlayerColor.RED, 0, 0, cell, gameState.activePlayer.color == PlayerColor.RED, highlightPulse)
            drawCornerBase(PlayerColor.GREEN, 0, 9, cell, gameState.activePlayer.color == PlayerColor.GREEN, highlightPulse)
            drawCornerBase(PlayerColor.YELLOW, 9, 9, cell, gameState.activePlayer.color == PlayerColor.YELLOW, highlightPulse)
            drawCornerBase(PlayerColor.BLUE, 9, 0, cell, gameState.activePlayer.color == PlayerColor.BLUE, highlightPulse)

            // 6. Draw center 3x3 home triangles
            drawCenterHomeTriangles(cell)

            // 7. Draw pre-sorted tokens (non-movable first, then targets/movable, then actively hopping token on top)
            for (token in renderTokens) {
                val scale = if (token.isAnimating) uiState.hopScale else 1f
                drawCartoonToken(
                    token = token,
                    cell = cell,
                    hopScale = scale,
                    highlightPulse = highlightPulse,
                    textMeasurer = textMeasurer
                )
            }

            // 8. Draw capture starburst if active
            val burst = uiState.captureBurst
            if (burst != null && burstProgress.value < 1f) {
                drawCaptureBurst(
                    center = Offset(burst.col * cell, burst.row * cell),
                    cell = cell,
                    color = burst.color.palette().primary,
                    progress = burstProgress.value
                )
            }

            // 9. Draw bomb explosion blast if active
            val boom = uiState.bombExplosionBurst
            if (boom != null && explosionProgress.value < 1f) {
                drawBombExplosionBurst(
                    center = Offset(boom.col * cell, boom.row * cell),
                    cell = cell,
                    progress = explosionProgress.value
                )
            }
        }
        }
    }
}

private fun buildRenderTokens(
    gameState: GameState,
    uiState: GameUiState,
    isLocalPlayersTurn: Boolean
): List<RenderToken> {
    val allowInteractiveHighlights = isLocalPlayersTurn && !gameState.activePlayer.setup.isBot
    val movableSet = if (allowInteractiveHighlights && gameState.phase == TurnPhase.AWAITING_MOVE) {
        gameState.legalMovesForSelectedDie().map { TokenRef(it.color, it.tokenId) }.toSet()
    } else {
        emptySet()
    }

    val shieldTargetSet = if (allowInteractiveHighlights) {
        when (val t = uiState.powerUpTargeting) {
            is PowerUpTargetingState.ShieldTarget ->
                t.validTokenIds.map { TokenRef(gameState.activePlayer.color, it) }.toSet()
            else -> emptySet()
        }
    } else {
        emptySet()
    }

    val allTokens = gameState.players
        .filter { !it.isEliminated }
        .flatMap { it.tokens }

    val coordGroups = mutableMapOf<Pair<Int, Int>, MutableList<Token>>()

    for (token in allTokens) {
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

    for (token in allTokens) {
        val ref = TokenRef(token.color, token.id)
        if (uiState.animatingToken == ref && uiState.isAnimatingMove) {
            result += RenderToken(
                ref = ref,
                row = uiState.animatingRow,
                col = uiState.animatingCol,
                radiusScale = 1.05f,
                isMovable = false,
                isAnimating = true,
                shieldTurnsRemaining = token.shieldTurnsRemaining,
                isShieldTarget = false
            )
            continue
        }

        if (token.isInBase || token.isHome) {
            val (r, c) = LudoBoardGeometry.tokenBoardPosition(
                token.color, token.id, token.progress, token.outerWrap51
            )
            result += RenderToken(
                ref = ref,
                row = r,
                col = c,
                radiusScale = if (token.isHome) 0.62f else 1f,
                isMovable = ref in movableSet,
                isAnimating = false,
                shieldTurnsRemaining = token.shieldTurnsRemaining,
                isShieldTarget = ref in shieldTargetSet
            )
        }
    }

    for ((_, group) in coordGroups) {
        val count = group.size
        group.forEachIndexed { idx, token ->
            val ref = TokenRef(token.color, token.id)
            val (baseR, baseC) = LudoBoardGeometry.tokenBoardPosition(
                token.color, token.id, token.progress, token.outerWrap51
            )
            if (count == 1) {
                result += RenderToken(
                    ref = ref,
                    row = baseR,
                    col = baseC,
                    radiusScale = 0.92f,
                    isMovable = ref in movableSet,
                    isAnimating = false,
                    shieldTurnsRemaining = token.shieldTurnsRemaining,
                    isShieldTarget = ref in shieldTargetSet
                )
            } else {
                val angle = (2.0 * PI * idx / count) - PI / 4.0
                val spread = 0.22f
                result += RenderToken(
                    ref = ref,
                    row = baseR + (sin(angle) * spread).toFloat(),
                    col = baseC + (cos(angle) * spread).toFloat(),
                    radiusScale = if (count <= 2) 0.72f else 0.60f,
                    isMovable = ref in movableSet,
                    isAnimating = false,
                    shieldTurnsRemaining = token.shieldTurnsRemaining,
                    isShieldTarget = ref in shieldTargetSet
                )
            }
        }
    }

    return result
}

private val START_COLOR_MAP = mapOf(
    0 to PlayerColor.RED,
    13 to PlayerColor.GREEN,
    26 to PlayerColor.YELLOW,
    39 to PlayerColor.BLUE
)

// Bridge squares where each player turns into their colored home column (index -> color & angle degrees)
private val HOME_ENTRY_ARROW_MAP = mapOf(
    50 to (PlayerColor.RED to 0f),      // (7, 0) pointing Right
    11 to (PlayerColor.GREEN to 90f),   // (0, 7) pointing Down
    24 to (PlayerColor.YELLOW to 180f), // (7, 14) pointing Left
    37 to (PlayerColor.BLUE to 270f)    // (14, 7) pointing Up
)

private fun DrawScope.drawOuterTrackCells(cell: Float, activeSafeIndices: Set<Int>) {
    val gridStroke = (cell * 0.052f).coerceAtLeast(1.1f)
    LudoBoardGeometry.OUTER_TRACK.forEachIndexed { idx, coord ->
        val topLeft = Offset(coord.col * cell, coord.row * cell)
        val cellSize = Size(cell, cell)

        val startColor = START_COLOR_MAP[idx]
        val entryInfo = HOME_ENTRY_ARROW_MAP[idx]
        val isSafeStar = idx in activeSafeIndices && startColor == null

        val fill = when {
            startColor != null -> startColor.palette().primary
            isSafeStar -> Color(0xFFFFF4CC)
            entryInfo != null -> entryInfo.first.palette().light.copy(alpha = 0.72f)
            else -> Color(0xFFFCFAF5)
        }

        drawRect(color = fill, topLeft = topLeft, size = cellSize)
        drawRect(
            color = BoardCellBorder,
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
                color = if (idx in activeSafeIndices) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.50f)
            )
        } else if (isSafeStar) {
            drawStar(
                center = center,
                outerRadius = cell * 0.35f,
                innerRadius = cell * 0.165f,
                color = SafeStarGold
            )
        } else if (entryInfo != null) {
            drawDirectionArrow(
                center = center,
                cell = cell,
                angleDeg = entryInfo.second,
                color = entryInfo.first.palette().dark.copy(alpha = 0.78f)
            )
        }
    }
}

private fun DrawScope.drawDirectionArrow(
    center: Offset,
    cell: Float,
    angleDeg: Float,
    color: Color
) {
    val shaft = cell * 0.24f
    val wing = cell * 0.16f
    val strokeW = (cell * 0.095f).coerceAtLeast(1.8f)
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

private fun DrawScope.drawPowerUpTile(tile: PowerUpTile, cell: Float, pulse: Float) {
    val coord = LudoBoardGeometry.OUTER_TRACK[tile.trackIndex.mod(Token.TRACK_LENGTH)]
    val topLeft = Offset(coord.col * cell, coord.row * cell)
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    val accent = powerUpAccentColor(tile.type)
    val softBg = powerUpSoftBg(tile.type)

    // Glowing tinted cell fill
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

    // Crisp custom vector glyph per power-up type
    when (tile.type) {
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

        PowerUpType.DOUBLE_DICE -> {
            val dSize = cell * 0.30f
            // Back die
            drawRoundRect(
                color = accent,
                topLeft = Offset(center.x - dSize * 0.88f, center.y - dSize * 0.85f),
                size = Size(dSize, dSize),
                cornerRadius = CornerRadius(cell * 0.07f, cell * 0.07f)
            )
            drawCircle(
                color = Color.White,
                radius = cell * 0.045f,
                center = Offset(center.x - dSize * 0.38f, center.y - dSize * 0.35f)
            )
            // Front die
            drawRoundRect(
                color = Color(0xFFFFB703),
                topLeft = Offset(center.x - dSize * 0.12f, center.y - dSize * 0.15f),
                size = Size(dSize, dSize),
                cornerRadius = CornerRadius(cell * 0.07f, cell * 0.07f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(center.x - dSize * 0.12f, center.y - dSize * 0.15f),
                size = Size(dSize, dSize),
                cornerRadius = CornerRadius(cell * 0.07f, cell * 0.07f),
                style = Stroke(width = cell * 0.04f)
            )
            drawCircle(
                color = InkDark,
                radius = cell * 0.045f,
                center = Offset(center.x + dSize * 0.38f, center.y + dSize * 0.35f)
            )
        }

        PowerUpType.BOMB_TRAP -> {
            val bombR = cell * 0.22f
            drawCircle(
                color = InkDark,
                radius = bombR,
                center = Offset(center.x - cell * 0.03f, center.y + cell * 0.06f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = bombR * 0.28f,
                center = Offset(center.x - bombR * 0.45f, center.y - bombR * 0.10f)
            )
            // Fuse line + glowing spark
            val fuseStart = Offset(center.x + bombR * 0.45f, center.y - bombR * 0.55f)
            val fuseEnd = Offset(center.x + bombR * 1.05f, center.y - bombR * 1.15f)
            drawLine(color = Color(0xFF9C6644), start = fuseStart, end = fuseEnd, strokeWidth = cell * 0.07f)
            drawCircle(
                color = Color(0xFFFF9F1C),
                radius = cell * (0.09f + pulse * 0.04f),
                center = fuseEnd
            )
            drawCircle(
                color = Color(0xFFFFE66D),
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
    textMeasurer: TextMeasurer
) {
    val coord = LudoBoardGeometry.OUTER_TRACK[bomb.trackIndex.mod(Token.TRACK_LENGTH)]
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    val crimson = Color(0xFFE63946)

    // Dashed stealth ring visible only to the owner
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

    // Cartoon bomb core
    val bombR = cell * 0.20f
    drawCircle(
        color = InkDark.copy(alpha = 0.88f),
        radius = bombR,
        center = Offset(center.x - cell * 0.03f, center.y + cell * 0.04f)
    )
    val sparkCenter = Offset(center.x + bombR * 0.85f, center.y - bombR * 0.90f)
    drawLine(
        color = Color(0xFF9C6644),
        start = Offset(center.x + bombR * 0.35f, center.y - bombR * 0.40f),
        end = sparkCenter,
        strokeWidth = cell * 0.06f
    )
    drawCircle(color = Color(0xFFFF9F1C), radius = cell * 0.08f, center = sparkCenter)

    // Small rounds-left badge in bottom-right corner of the cell
    val badgeCenter = Offset(center.x + cell * 0.27f, center.y + cell * 0.27f)
    val badgeR = cell * 0.17f
    drawCircle(color = crimson, radius = badgeR, center = badgeCenter)
    val measured = textMeasurer.measure(
        text = bomb.roundsRemaining.toString(),
        style = TextStyle(
            color = Color.White,
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

private fun DrawScope.drawBombTargetSquare(trackIndex: Int, cell: Float, pulse: Float) {
    val coord = LudoBoardGeometry.OUTER_TRACK[trackIndex.mod(Token.TRACK_LENGTH)]
    val topLeft = Offset(coord.col * cell, coord.row * cell)
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    val crimson = Color(0xFFE63946)

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

private fun DrawScope.drawHomeColumns(cell: Float, gameState: GameState) {
    val gridStroke = (cell * 0.055f).coerceAtLeast(1.2f)
    for (color in PlayerColor.entries) {
        val palette = color.palette()
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
            val fillAlpha = if (isMasterLocked) 0.55f else 0.88f + (idx * 0.03f)
            drawRect(
                color = palette.primary.copy(alpha = fillAlpha.coerceAtMost(1f)),
                topLeft = topLeft,
                size = Size(cell, cell)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.62f),
                topLeft = topLeft,
                size = Size(cell, cell),
                style = Stroke(width = gridStroke)
            )
            val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
            if (isMasterLocked && idx == 2) {
                drawLockBadge(center = center, cell = cell)
            } else if (idx == 4) {
                // Subtle entry chevron on final home-column tile leading into the finish crown
                drawDirectionArrow(
                    center = center,
                    cell = cell * 0.84f,
                    angleDeg = arrowAngle,
                    color = Color.White.copy(alpha = 0.42f)
                )
            }
        }
    }
}

private fun DrawScope.drawLockBadge(center: Offset, cell: Float) {
    drawCircle(color = Color.White, radius = cell * 0.38f, center = center)
    val bodyW = cell * 0.40f
    val bodyH = cell * 0.30f
    drawRoundRect(
        color = InkDark,
        topLeft = Offset(center.x - bodyW / 2f, center.y - bodyH * 0.15f),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawArc(
        color = InkDark,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - bodyW * 0.30f, center.y - bodyH * 0.72f),
        size = Size(bodyW * 0.60f, bodyH * 0.85f),
        style = Stroke(width = cell * 0.08f)
    )
}

private fun DrawScope.drawPortalAtTrackIndex(trackIndex: Int, cell: Float, spinDegrees: Float) {
    val coord = LudoBoardGeometry.OUTER_TRACK[trackIndex.mod(Token.TRACK_LENGTH)]
    val center = Offset((coord.col + 0.5f) * cell, (coord.row + 0.5f) * cell)
    rotate(degrees = spinDegrees, pivot = center) {
        drawCircle(
            color = Color(0xFF7B2CBF).copy(alpha = 0.28f),
            radius = cell * 0.44f,
            center = center
        )
        drawCircle(
            color = Color(0xFF00D2FF),
            radius = cell * 0.34f,
            center = center,
            style = Stroke(width = cell * 0.10f)
        )
        drawArc(
            color = Color(0xFFFF4D6D),
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
    pulse: Float
) {
    val palette = color.palette()
    val x = startCol * cell
    val y = startRow * cell
    val boxSize = 6f * cell

    // Rich directional gradient yard background
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(palette.highlight, palette.primary, palette.dark.copy(alpha = 0.92f)),
            start = Offset(x, y),
            end = Offset(x + boxSize, y + boxSize)
        ),
        topLeft = Offset(x, y),
        size = Size(boxSize, boxSize)
    )

    // Subtle crisp border + pulsing active-player highlight frame
    val borderW = if (isActive) (cell * (0.16f + pulse * 0.07f)).coerceAtLeast(3.5f) else (cell * 0.065f).coerceAtLeast(1.5f)
    drawRect(
        color = if (isActive) Color.White.copy(alpha = 0.85f + pulse * 0.15f) else palette.dark.copy(alpha = 0.32f),
        topLeft = Offset(x + borderW * 0.5f, y + borderW * 0.5f),
        size = Size(boxSize - borderW, boxSize - borderW),
        style = Stroke(width = borderW)
    )

    // Elevated white inner yard card with soft drop shadow
    val pad = cell * 0.86f
    val innerSize = boxSize - pad * 2f
    val cornerR = CornerRadius(cell * 0.78f, cell * 0.78f)
    drawRoundRect(
        color = palette.dark.copy(alpha = 0.24f),
        topLeft = Offset(x + pad, y + pad + cell * 0.11f),
        size = Size(innerSize, innerSize),
        cornerRadius = cornerR
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFFDFBF7)),
            startY = y + pad,
            endY = y + pad + innerSize
        ),
        topLeft = Offset(x + pad, y + pad),
        size = Size(innerSize, innerSize),
        cornerRadius = cornerR
    )
    drawRoundRect(
        color = palette.primary.copy(alpha = 0.28f),
        topLeft = Offset(x + pad, y + pad),
        size = Size(innerSize, innerSize),
        cornerRadius = cornerR,
        style = Stroke(width = (cell * 0.085f).coerceAtLeast(1.8f))
    )

    // 4 recessed token launch pads with glossy double-ring detail
    for (tokenId in 0..3) {
        val (tr, tc) = LudoBoardGeometry.baseTokenCenter(color, tokenId)
        val center = Offset(tc * cell, tr * cell)
        val padR = cell * 0.54f
        drawCircle(
            color = palette.light,
            radius = padR,
            center = center
        )
        drawCircle(
            color = palette.primary.copy(alpha = 0.55f),
            radius = padR,
            center = center,
            style = Stroke(width = (cell * 0.09f).coerceAtLeast(1.8f))
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = padR * 0.72f,
            center = center,
            style = Stroke(width = (cell * 0.045f).coerceAtLeast(1.1f))
        )
    }
}

private fun DrawScope.drawCenterHomeTriangles(cell: Float) {
    val left = 6f * cell
    val top = 6f * cell
    val right = 9f * cell
    val bottom = 9f * cell
    val cx = 7.5f * cell
    val cy = 7.5f * cell

    drawTriangle(Offset(left, top), Offset(left, bottom), Offset(cx, cy), PlayerColor.RED.palette().primary)
    drawTriangle(Offset(left, top), Offset(right, top), Offset(cx, cy), PlayerColor.GREEN.palette().primary)
    drawTriangle(Offset(right, top), Offset(right, bottom), Offset(cx, cy), PlayerColor.YELLOW.palette().primary)
    drawTriangle(Offset(left, bottom), Offset(right, bottom), Offset(cx, cy), PlayerColor.BLUE.palette().primary)

    drawRect(
        color = InkDark.copy(alpha = 0.30f),
        topLeft = Offset(left, top),
        size = Size(3f * cell, 3f * cell),
        style = Stroke(width = 2.5f)
    )

    drawCircle(color = Color.White, radius = cell * 0.42f, center = Offset(cx, cy))
    drawStar(
        center = Offset(cx, cy),
        outerRadius = cell * 0.32f,
        innerRadius = cell * 0.15f,
        color = SafeStarGold
    )
}

private fun DrawScope.drawTriangle(p1: Offset, p2: Offset, p3: Offset, color: Color) {
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }
    drawPath(path = path, color = color)
    drawPath(path = path, color = Color.White.copy(alpha = 0.65f), style = Stroke(width = 2f))
}

private fun DrawScope.drawCartoonToken(
    token: RenderToken,
    cell: Float,
    hopScale: Float,
    highlightPulse: Float,
    textMeasurer: TextMeasurer
) {
    val palette = token.ref.color.palette()
    val center = Offset(token.col * cell, token.row * cell)
    val baseRadius = cell * 0.38f * token.radiusScale * hopScale

    // Pulsing cyan target halo when choosing a token to Shield
    if (token.isShieldTarget) {
        val cyan = Color(0xFF00B4D8)
        val haloRadius = baseRadius * (1.42f + highlightPulse * 0.30f)
        drawCircle(
            color = cyan.copy(alpha = 0.30f),
            radius = haloRadius,
            center = center
        )
        drawCircle(
            color = cyan,
            radius = haloRadius,
            center = center,
            style = Stroke(width = 4f)
        )
    } else if (token.isMovable) {
        val haloRadius = baseRadius * (1.35f + highlightPulse * 0.28f)
        drawCircle(
            color = Color.White.copy(alpha = 0.90f),
            radius = haloRadius,
            center = center
        )
        drawCircle(
            color = InkDark.copy(alpha = 0.85f),
            radius = haloRadius,
            center = center,
            style = Stroke(width = 3.2f)
        )
    }

    val shadowYOffset = baseRadius * (0.20f + (hopScale - 1f) * 0.85f)
    drawCircle(
        color = Color.Black.copy(alpha = 0.24f),
        radius = baseRadius * 0.96f,
        center = Offset(center.x, center.y + shadowYOffset)
    )

    drawCircle(color = palette.dark, radius = baseRadius, center = center)
    drawCircle(
        color = palette.primary,
        radius = baseRadius * 0.84f,
        center = Offset(center.x, center.y - baseRadius * 0.05f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = baseRadius * 0.50f,
        center = Offset(center.x, center.y - baseRadius * 0.06f),
        style = Stroke(width = baseRadius * 0.16f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.65f),
        radius = baseRadius * 0.20f,
        center = Offset(center.x - baseRadius * 0.26f, center.y - baseRadius * 0.30f)
    )

    // Glowing Shield energy bubble + turn counter badge
    if (token.shieldTurnsRemaining > 0) {
        val cyan = Color(0xFF00D2FF)
        val bubbleR = baseRadius * (1.28f + highlightPulse * 0.09f)
        drawCircle(
            color = cyan.copy(alpha = 0.22f),
            radius = bubbleR,
            center = center
        )
        drawCircle(
            color = cyan.copy(alpha = 0.92f),
            radius = bubbleR,
            center = center,
            style = Stroke(width = cell * 0.08f)
        )
        // Specular highlight arc on top-left of bubble
        drawArc(
            color = Color.White.copy(alpha = 0.85f),
            startAngle = 205f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(center.x - bubbleR * 0.82f, center.y - bubbleR * 0.82f),
            size = Size(bubbleR * 1.64f, bubbleR * 1.64f),
            style = Stroke(width = cell * 0.055f)
        )
        // Turn counter pill at top-right of the bubble
        val badgeCenter = Offset(center.x + bubbleR * 0.72f, center.y - bubbleR * 0.72f)
        val badgeR = cell * 0.20f
        drawCircle(color = Color(0xFF0077B6), radius = badgeR, center = badgeCenter)
        drawCircle(color = Color.White, radius = badgeR, center = badgeCenter, style = Stroke(width = 1.8f))
        val measured = textMeasurer.measure(
            text = token.shieldTurnsRemaining.toString(),
            style = TextStyle(
                color = Color.White,
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

private fun DrawScope.drawStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color
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
    drawPath(path = path, color = color)
}

private fun DrawScope.drawCaptureBurst(
    center: Offset,
    cell: Float,
    color: Color,
    progress: Float
) {
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val maxRadius = cell * 1.45f * progress + cell * 0.3f
    drawCircle(
        color = color.copy(alpha = alpha * 0.55f),
        radius = maxRadius,
        center = center,
        style = Stroke(width = cell * 0.18f * alpha)
    )
    for (i in 0 until 8) {
        val angle = (i * PI / 4.0)
        val dist = maxRadius * 0.9f
        val end = Offset(
            center.x + (cos(angle) * dist).toFloat(),
            center.y + (sin(angle) * dist).toFloat()
        )
        drawLine(
            color = Color(0xFFFFB800).copy(alpha = alpha),
            start = center,
            end = end,
            strokeWidth = 5f * alpha
        )
    }
}

private fun DrawScope.drawBombExplosionBurst(
    center: Offset,
    cell: Float,
    progress: Float
) {
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val outerRadius = cell * 2.1f * progress + cell * 0.35f
    // Fiery shockwave fill
    drawCircle(
        color = Color(0xFFFF595E).copy(alpha = alpha * 0.42f),
        radius = outerRadius * 0.72f,
        center = center
    )
    drawCircle(
        color = Color(0xFFFF9F1C).copy(alpha = alpha * 0.85f),
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
            color = if (i % 2 == 0) Color(0xFFFFCA3A).copy(alpha = alpha) else Color(0xFFE63946).copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = 6f * alpha
        )
    }
}
