package com.example.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.PlayerColor
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.LudoTheme
import com.example.ui.theme.ThemeShadowSpec
import com.example.ui.theme.ludoShadow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure 3D projection math, face geometry, and 1..6 random roll generator for the
 * perspective-projected 6-face Ludo die.
 */
object DiceCube3DEngine {
    const val ROLL_DURATION_MS: Int = 680
    const val CAMERA_DISTANCE: Float = 5.4f
    const val FOCAL_LENGTH: Float = 4.75f
    const val REST_VIEW_TILT_X_DEG: Float = -18f
    const val REST_VIEW_TILT_Y_DEG: Float = 20f

    data class Vec3(val x: Float, val y: Float, val z: Float)

    data class FaceTargetAngles(val rotX: Float, val rotY: Float)

    data class CubeFaceDef(
        val value: Int,
        val normal: Vec3,
        val uAxis: Vec3,
        val vAxis: Vec3,
        val pips: List<Pair<Float, Float>>
    )

    private val lightDir: Vec3 = run {
        val lx = -0.38f
        val ly = -0.62f
        val lz = 0.85f
        val len = sqrt(lx * lx + ly * ly + lz * lz)
        Vec3(lx / len, ly / len, lz / len)
    }

    fun rollRandomFace(random: Random = Random.Default): Int = random.nextInt(1, 7)

    fun targetAnglesForFace(face: Int): FaceTargetAngles = when (face.coerceIn(1, 6)) {
        1 -> FaceTargetAngles(rotX = 0f, rotY = 0f)
        2 -> FaceTargetAngles(rotX = -90f, rotY = 0f)
        3 -> FaceTargetAngles(rotX = 0f, rotY = -90f)
        4 -> FaceTargetAngles(rotX = 0f, rotY = 90f)
        5 -> FaceTargetAngles(rotX = 90f, rotY = 0f)
        else -> FaceTargetAngles(rotX = 0f, rotY = 180f)
    }

    val faces: List<CubeFaceDef> = run {
        val p = 0.44f
        val pipsByValue = mapOf(
            1 to listOf(0f to 0f),
            2 to listOf(-p to -p, p to p),
            3 to listOf(-p to -p, 0f to 0f, p to p),
            4 to listOf(-p to -p, p to -p, -p to p, p to p),
            5 to listOf(-p to -p, p to -p, 0f to 0f, -p to p, p to p),
            6 to listOf(-p to -p, p to -p, -p to 0f, p to 0f, -p to p, p to p)
        )
        listOf(
            CubeFaceDef(1, Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), pipsByValue.getValue(1)),
            CubeFaceDef(6, Vec3(0f, 0f, -1f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), pipsByValue.getValue(6)),
            CubeFaceDef(2, Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f), pipsByValue.getValue(2)),
            CubeFaceDef(5, Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), pipsByValue.getValue(5)),
            CubeFaceDef(3, Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f), pipsByValue.getValue(3)),
            CubeFaceDef(4, Vec3(-1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f), pipsByValue.getValue(4))
        )
    }

    val outerFaceUv: List<Pair<Float, Float>> = buildRoundedSquareUv(halfExtent = 1.0f, cornerRadius = 0.24f)
    val innerFaceUv: List<Pair<Float, Float>> = buildRoundedSquareUv(halfExtent = 0.87f, cornerRadius = 0.19f)
    val pipCircleUv: List<Pair<Float, Float>> = List(16) { i ->
        val angle = (2.0 * PI * i) / 16.0
        cos(angle).toFloat() to sin(angle).toFloat()
    }

    private fun buildRoundedSquareUv(halfExtent: Float, cornerRadius: Float): List<Pair<Float, Float>> {
        val inner = halfExtent - cornerRadius
        val corners = listOf(
            Triple(inner, -inner, -90f to 0f),
            Triple(inner, inner, 0f to 90f),
            Triple(-inner, inner, 90f to 180f),
            Triple(-inner, -inner, 180f to 270f)
        )
        val result = ArrayList<Pair<Float, Float>>(16)
        for ((cx, cy, range) in corners) {
            val (startDeg, endDeg) = range
            for (step in 0..3) {
                val deg = startDeg + (endDeg - startDeg) * (step / 3f)
                val rad = deg * (PI / 180.0)
                result += (cx + cornerRadius * cos(rad).toFloat()) to
                    (cy + cornerRadius * sin(rad).toFloat())
            }
        }
        return result
    }

    fun transformPoint(
        p: Vec3,
        rotXDeg: Float,
        rotYDeg: Float,
        rotZDeg: Float,
        viewTiltXDeg: Float = REST_VIEW_TILT_X_DEG,
        viewTiltYDeg: Float = REST_VIEW_TILT_Y_DEG
    ): Vec3 {
        val rx = rotXDeg * (PI / 180.0)
        val cosX = cos(rx).toFloat()
        val sinX = sin(rx).toFloat()
        val y1 = p.y * cosX - p.z * sinX
        val z1 = p.y * sinX + p.z * cosX
        val x1 = p.x

        val ry = rotYDeg * (PI / 180.0)
        val cosY = cos(ry).toFloat()
        val sinY = sin(ry).toFloat()
        val x2 = x1 * cosY + z1 * sinY
        val z2 = -x1 * sinY + z1 * cosY
        val y2 = y1

        val rz = rotZDeg * (PI / 180.0)
        val cosZ = cos(rz).toFloat()
        val sinZ = sin(rz).toFloat()
        val x3 = x2 * cosZ - y2 * sinZ
        val y3 = x2 * sinZ + y2 * cosZ
        val z3 = z2

        val vx = viewTiltXDeg * (PI / 180.0)
        val cosVx = cos(vx).toFloat()
        val sinVx = sin(vx).toFloat()
        val y4 = y3 * cosVx - z3 * sinVx
        val z4 = y3 * sinVx + z3 * cosVx
        val x4 = x3

        val vy = viewTiltYDeg * (PI / 180.0)
        val cosVy = cos(vy).toFloat()
        val sinVy = sin(vy).toFloat()
        val x5 = x4 * cosVy + z4 * sinVy
        val z5 = -x4 * sinVy + z4 * cosVy
        val y5 = y4

        return Vec3(x5, y5, z5)
    }

    fun frontFacingValue(rotXDeg: Float, rotYDeg: Float, rotZDeg: Float): Int {
        var bestFace = 1
        var bestZ = Float.NEGATIVE_INFINITY
        for (face in faces) {
            val n = transformPoint(
                face.normal,
                rotXDeg = rotXDeg,
                rotYDeg = rotYDeg,
                rotZDeg = rotZDeg,
                viewTiltXDeg = 0f,
                viewTiltYDeg = 0f
            )
            if (n.z > bestZ) {
                bestZ = n.z
                bestFace = face.value
            }
        }
        return bestFace
    }

    fun lightIntensity(transformedNormal: Vec3): Float {
        val dot = transformedNormal.x * lightDir.x +
            transformedNormal.y * lightDir.y +
            transformedNormal.z * lightDir.z
        return (0.64f + 0.36f * max(0f, dot)).coerceIn(0.55f, 1.0f)
    }

    fun isFaceVisible(transformedNormal: Vec3): Boolean {
        return (CAMERA_DISTANCE * transformedNormal.z - 1f) > 0.015f
    }

    fun project(point: Vec3, centerX: Float, centerY: Float, cubeHalfPx: Float): Offset {
        val factor = FOCAL_LENGTH / (CAMERA_DISTANCE - point.z)
        return Offset(
            x = centerX + point.x * cubeHalfPx * factor,
            y = centerY + point.y * cubeHalfPx * factor
        )
    }

    fun pointOnFace(face: CubeFaceDef, u: Float, v: Float): Vec3 = Vec3(
        x = face.normal.x + face.uAxis.x * u + face.vAxis.x * v,
        y = face.normal.y + face.uAxis.y * u + face.vAxis.y * v,
        z = face.normal.z + face.uAxis.z * u + face.vAxis.z * v
    )
}

private val DiceTumbleEasing = CubicBezierEasing(0.16f, 0.88f, 0.24f, 1.0f)

/**
 * Themed 3D animated Ludo die:
 * - While tumbling: full perspective-projected 6-face 3D cube with bounce.
 * - When settled flat: crisp 58dp rounded square (16dp corners), theme dieFace, 9dp pips in theme.pip,
 *   and exact theme dieShadow.
 */
@Composable
fun LudoDice3D(
    value: Int,
    rollTrigger: Int,
    isRolling: Boolean,
    canRoll: Boolean,
    activeColor: PlayerColor,
    isGoldenDice: Boolean = false,
    showTapHint: Boolean = false,
    onRollClick: () -> Unit = {},
    onRollStarted: (() -> Unit)? = null,
    onRollResult: ((Int) -> Unit)? = null,
    random: Random = Random.Default,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    testTag: String = "dice_roll_button"
) {
    val theme = LocalLudoTheme.current
    val playerColor = theme.playerColor(activeColor)
    val playerDark = lerp(playerColor, Color.Black, 0.32f)
    val safeValue = value.coerceIn(1, 6)
    val initialAngles = remember { DiceCube3DEngine.targetAnglesForFace(safeValue) }

    val rotX = remember { Animatable(initialAngles.rotX) }
    val rotY = remember { Animatable(initialAngles.rotY) }
    val rotZ = remember { Animatable(0f) }
    val jumpArc = remember { Animatable(0f) }
    val squashScale = remember { Animatable(1f) }
    val tumbleTiltFactor = remember { Animatable(0f) }
    val sixRingProgress = remember { Animatable(0f) }

    var internalRolling by remember { mutableStateOf(false) }
    var lastExternalTrigger by remember { mutableIntStateOf(rollTrigger) }
    var spinParity by remember { mutableIntStateOf(0) }
    var shownFaceValue by remember { mutableIntStateOf(safeValue) }

    val coroutineScope = rememberCoroutineScope()
    val latestOnRollStarted by rememberUpdatedState(onRollStarted)
    val latestOnRollResult by rememberUpdatedState(onRollResult)
    val latestOnRollClick by rememberUpdatedState(onRollClick)

    suspend fun animateTumbleToFace(targetFace: Int) {
        val cleanFace = targetFace.coerceIn(1, 6)
        val target = DiceCube3DEngine.targetAnglesForFace(cleanFace)
        spinParity += 1
        val yDir = if (spinParity % 2 == 0) 1f else -1f
        val zDir = if (spinParity % 3 == 0) -1f else 1f

        val startX = rotX.value
        val startY = rotY.value
        val startZ = rotZ.value

        val endX = startX + 720f + normalizeSignedDelta(target.rotX - startX)
        val endY = startY + (yDir * 720f) + normalizeSignedDelta(target.rotY - startY)
        val endZ = startZ + (zDir * 360f) + normalizeSignedDelta(0f - startZ)

        sixRingProgress.snapTo(0f)

        coroutineScope {
            launch {
                rotX.animateTo(
                    targetValue = endX,
                    animationSpec = tween(
                        durationMillis = DiceCube3DEngine.ROLL_DURATION_MS,
                        easing = DiceTumbleEasing
                    )
                )
            }
            launch {
                rotY.animateTo(
                    targetValue = endY,
                    animationSpec = tween(
                        durationMillis = DiceCube3DEngine.ROLL_DURATION_MS,
                        easing = DiceTumbleEasing
                    )
                )
            }
            launch {
                rotZ.animateTo(
                    targetValue = endZ,
                    animationSpec = tween(
                        durationMillis = DiceCube3DEngine.ROLL_DURATION_MS,
                        easing = DiceTumbleEasing
                    )
                )
            }
            launch {
                tumbleTiltFactor.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
                tumbleTiltFactor.animateTo(0.85f, tween(280))
                tumbleTiltFactor.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            }
            launch {
                jumpArc.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
                jumpArc.animateTo(0f, tween(210, easing = CubicBezierEasing(0.45f, 0f, 0.85f, 0.65f)))
                jumpArc.animateTo(0.28f, tween(115, easing = FastOutSlowInEasing))
                jumpArc.animateTo(0f, tween(115, easing = FastOutSlowInEasing))
            }
            launch {
                squashScale.animateTo(1.12f, tween(230))
                squashScale.animateTo(0.90f, tween(200))
                squashScale.animateTo(1.04f, tween(110))
                squashScale.animateTo(1.0f, spring(dampingRatio = 0.52f, stiffness = 480f))
            }
        }

        rotX.snapTo(target.rotX)
        rotY.snapTo(target.rotY)
        rotZ.snapTo(0f)
        tumbleTiltFactor.snapTo(0f)
        shownFaceValue = cleanFace

        if (cleanFace == 6) {
            sixRingProgress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            sixRingProgress.snapTo(0f)
        }
    }

    LaunchedEffect(rollTrigger) {
        if (rollTrigger > lastExternalTrigger) {
            lastExternalTrigger = rollTrigger
            if (!internalRolling) {
                animateTumbleToFace(safeValue)
            }
        }
    }

    LaunchedEffect(safeValue, isRolling, internalRolling) {
        if (!isRolling && !internalRolling && rollTrigger == lastExternalTrigger && shownFaceValue != safeValue) {
            val target = DiceCube3DEngine.targetAnglesForFace(safeValue)
            rotX.snapTo(target.rotX)
            rotY.snapTo(target.rotY)
            rotZ.snapTo(0f)
            tumbleTiltFactor.snapTo(0f)
            shownFaceValue = safeValue
        }
    }

    val effectiveRolling = isRolling || internalRolling
    val show3DCube = effectiveRolling || tumbleTiltFactor.value > 0.02f || jumpArc.value > 0.02f

    val idlePulse = if ((canRoll || isGoldenDice) && !effectiveRolling) {
        val pulseTransition = rememberInfiniteTransition(label = "diceReadyPulse")
        val anim by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(720),
                repeatMode = RepeatMode.Reverse
            ),
            label = "diceIdleScale"
        )
        anim
    } else {
        1f
    }

    val interactionSource = remember { MutableInteractionSource() }
    val reusableOuterPath = remember { Path() }
    val reusableInnerPath = remember { Path() }
    val reusablePipPath = remember { Path() }

    val activeDieShadow = remember(theme, isGoldenDice, playerColor) {
        when {
            isGoldenDice -> ThemeShadowSpec.Glow(
                blur = 20.dp,
                color = theme.yellow.copy(alpha = 0.65f),
                outlineColor = theme.yellow,
                outlineWidth = 1.5.dp
            )
            else -> theme.dieShadow
        }
    }

    val restingFaceColor = if (isGoldenDice) {
        lerp(theme.dieFace, theme.yellow, 0.24f)
    } else {
        theme.dieFace
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTag)
            .semantics {
                contentDescription = if (canRoll) {
                    if (isGoldenDice) "Golden Dice ready. Tap to roll guaranteed 6"
                    else "3D Dice showing $shownFaceValue. Tap to roll"
                } else {
                    "3D Dice showing $shownFaceValue"
                }
            }
            .clickable(
                enabled = canRoll && !effectiveRolling,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = false,
                    radius = size * 0.56f,
                    color = (if (isGoldenDice) theme.yellow else playerColor).copy(alpha = 0.32f)
                ),
                role = Role.Button,
                onClick = {
                    val resultCallback = latestOnRollResult
                    if (resultCallback != null) {
                        if (internalRolling) return@clickable
                        internalRolling = true
                        val rolledValue = if (isGoldenDice) 6 else DiceCube3DEngine.rollRandomFace(random)
                        latestOnRollStarted?.invoke()
                        coroutineScope.launch {
                            try {
                                animateTumbleToFace(rolledValue)
                            } finally {
                                internalRolling = false
                            }
                            resultCallback(rolledValue)
                        }
                    } else {
                        latestOnRollClick()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (show3DCube) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                draw3DCubeDie(
                    rotXDeg = rotX.value,
                    rotYDeg = rotY.value,
                    rotZDeg = rotZ.value,
                    viewTiltXDeg = DiceCube3DEngine.REST_VIEW_TILT_X_DEG * tumbleTiltFactor.value,
                    viewTiltYDeg = DiceCube3DEngine.REST_VIEW_TILT_Y_DEG * tumbleTiltFactor.value,
                    jumpFactor = jumpArc.value,
                    scaleFactor = squashScale.value * idlePulse,
                    sixRingProgress = sixRingProgress.value,
                    canRoll = canRoll && !effectiveRolling,
                    isGoldenDice = isGoldenDice,
                    theme = theme,
                    primaryColor = playerColor,
                    darkColor = playerDark,
                    outerPath = reusableOuterPath,
                    innerPath = reusableInnerPath,
                    pipPath = reusablePipPath
                )
            }
        } else {
            // Flat resting die matching exact Part 5 spec (58dp, 16dp corners, dieFace, 9dp pips, dieShadow)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = idlePulse * squashScale.value
                        scaleY = idlePulse * squashScale.value
                    }
                    .ludoShadow(activeDieShadow, 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(restingFaceColor)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawFlatRestingPips(
                        value = shownFaceValue,
                        pipColor = if (isGoldenDice) theme.contrastingTextOn(restingFaceColor) else theme.pip,
                        sixRingProgress = sixRingProgress.value,
                        sixRingColor = theme.yellow
                    )
                }
            }
        }

        if (showTapHint && canRoll && !effectiveRolling) {
            val hintBg = if (isGoldenDice) theme.yellow else theme.primaryActionBg
            val hintFg = if (isGoldenDice) theme.contrastingTextOn(theme.yellow) else theme.onPrimaryAction
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .testTag("dice_tap_hint"),
                shape = RoundedCornerShape(999.dp),
                color = hintBg
            ) {
                Text(
                    text = if (isGoldenDice) "6!" else "Tap",
                    fontFamily = theme.font,
                    fontSize = 10.sp,
                    color = hintFg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Synchronized pair of 3D animated dice (retained for any multi-die callers).
 */
@Composable
fun LudoDoubleDice3D(
    firstValue: Int,
    secondValue: Int,
    rollTrigger: Int,
    isRolling: Boolean,
    canRoll: Boolean,
    activeColor: PlayerColor,
    rolledDice: List<Int> = emptyList(),
    remainingDice: List<Int> = emptyList(),
    selectedDieValue: Int? = null,
    playableDiceValues: List<Int> = emptyList(),
    canSelectDie: Boolean = false,
    onSelectDieValue: ((Int) -> Unit)? = null,
    onRollClick: () -> Unit = {},
    onRollStarted: (() -> Unit)? = null,
    onDoubleRollResult: ((Int, Int) -> Unit)? = null,
    random: Random = Random.Default,
    dieSize: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val playerColor = theme.playerColor(activeColor)
    var localTrigger by remember { mutableIntStateOf(0) }
    var localFirst by remember { mutableIntStateOf(firstValue.coerceIn(1, 6)) }
    var localSecond by remember { mutableIntStateOf(secondValue.coerceIn(1, 6)) }
    var localRolling by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val latestOnStarted by rememberUpdatedState(onRollStarted)
    val latestOnResult by rememberUpdatedState(onDoubleRollResult)
    val latestOnClick by rememberUpdatedState(onRollClick)

    val triggerDoubleRoll: () -> Unit = {
        val resultCallback = latestOnResult
        if (resultCallback != null) {
            if (!localRolling && !isRolling && canRoll) {
                localRolling = true
                val v1 = DiceCube3DEngine.rollRandomFace(random)
                val v2 = DiceCube3DEngine.rollRandomFace(random)
                localFirst = v1
                localSecond = v2
                localTrigger += 1
                latestOnStarted?.invoke()
                coroutineScope.launch {
                    kotlinx.coroutines.delay(DiceCube3DEngine.ROLL_DURATION_MS.toLong() + 30L)
                    localRolling = false
                    resultCallback(v1, v2)
                }
            }
        } else {
            latestOnClick()
        }
    }

    val effectiveTrigger = rollTrigger + localTrigger
    val shownFirst = if (localRolling) localFirst else firstValue.coerceIn(1, 6)
    val shownSecond = if (localRolling) localSecond else secondValue.coerceIn(1, 6)

    val die0Effective = rolledDice.getOrNull(0) ?: shownFirst
    val die1Effective = rolledDice.getOrNull(1) ?: shownSecond
    val (die0Remaining, die1Remaining) = if (rolledDice.size >= 2) {
        val remCounts = remainingDice.groupingBy { it }.eachCount().toMutableMap()
        val firstRem = (remCounts[die0Effective] ?: 0) > 0
        if (firstRem) remCounts[die0Effective] = (remCounts[die0Effective] ?: 1) - 1
        val secondRem = (remCounts[die1Effective] ?: 0) > 0
        firstRem to secondRem
    } else {
        true to true
    }

    val isMovePhaseVisual = canSelectDie && !isRolling && !localRolling && rolledDice.size >= 2
    val die0Playable = isMovePhaseVisual && die0Remaining && die0Effective in playableDiceValues
    val die1Playable = isMovePhaseVisual && die1Remaining && die1Effective in playableDiceValues
    val die0Selected = die0Playable && selectedDieValue == die0Effective
    val die1Selected = die1Playable && selectedDieValue == die1Effective &&
        !(die0Selected && die0Effective == die1Effective)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (die0Selected) {
                        Modifier.border(2.5.dp, playerColor, RoundedCornerShape(16.dp))
                    } else Modifier
                )
                .padding(2.dp)
                .alpha(if (isMovePhaseVisual && !die0Remaining) 0.38f else 1f)
                .then(
                    if (die0Playable && onSelectDieValue != null) {
                        Modifier.clickable { onSelectDieValue(die0Effective) }
                    } else Modifier
                )
        ) {
            LudoDice3D(
                value = shownFirst,
                rollTrigger = effectiveTrigger,
                isRolling = isRolling || localRolling,
                canRoll = canRoll,
                activeColor = activeColor,
                onRollClick = triggerDoubleRoll,
                size = dieSize,
                testTag = "dice_roll_button"
            )
        }
        Box(
            modifier = Modifier
                .then(
                    if (die1Selected) {
                        Modifier.border(2.5.dp, playerColor, RoundedCornerShape(16.dp))
                    } else Modifier
                )
                .padding(2.dp)
                .alpha(if (isMovePhaseVisual && !die1Remaining) 0.38f else 1f)
                .then(
                    if (die1Playable && onSelectDieValue != null) {
                        Modifier.clickable { onSelectDieValue(die1Effective) }
                    } else Modifier
                )
        ) {
            LudoDice3D(
                value = shownSecond,
                rollTrigger = effectiveTrigger,
                isRolling = isRolling || localRolling,
                canRoll = canRoll,
                activeColor = activeColor,
                onRollClick = triggerDoubleRoll,
                size = dieSize,
                testTag = "dice_roll_button_2"
            )
        }
    }
}

private fun normalizeSignedDelta(degDelta: Float): Float {
    var d = degDelta % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun DrawScope.drawFlatRestingPips(
    value: Int,
    pipColor: Color,
    sixRingProgress: Float,
    sixRingColor: Color
) {
    val w = size.width
    val h = size.height
    val minDim = minOf(w, h)
    // 9dp pips on a 58dp die (scales proportionally with die size)
    val pipRadius = (9.dp.toPx() / 2f).coerceAtMost(minDim * 0.088f)
    val left = w * 0.27f
    val mid = w * 0.50f
    val right = w * 0.73f
    val top = h * 0.27f
    val center = h * 0.50f
    val bottom = h * 0.73f

    val pips = when (value.coerceIn(1, 6)) {
        1 -> listOf(Offset(mid, center))
        2 -> listOf(Offset(left, top), Offset(right, bottom))
        3 -> listOf(Offset(left, top), Offset(mid, center), Offset(right, bottom))
        4 -> listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom))
        5 -> listOf(
            Offset(left, top), Offset(right, top),
            Offset(mid, center),
            Offset(left, bottom), Offset(right, bottom)
        )
        else -> listOf(
            Offset(left, top), Offset(right, top),
            Offset(left, center), Offset(right, center),
            Offset(left, bottom), Offset(right, bottom)
        )
    }

    for (pos in pips) {
        drawCircle(
            color = pipColor,
            radius = pipRadius,
            center = pos
        )
    }

    if (sixRingProgress > 0.01f && sixRingProgress < 0.99f) {
        val ringRadius = minDim * (0.30f + 0.20f * sixRingProgress)
        drawCircle(
            color = sixRingColor.copy(alpha = (1f - sixRingProgress) * 0.85f),
            radius = ringRadius,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = (1f - sixRingProgress) * minDim * 0.07f)
        )
    }
}

private fun DrawScope.draw3DCubeDie(
    rotXDeg: Float,
    rotYDeg: Float,
    rotZDeg: Float,
    viewTiltXDeg: Float,
    viewTiltYDeg: Float,
    jumpFactor: Float,
    scaleFactor: Float,
    sixRingProgress: Float,
    canRoll: Boolean,
    isGoldenDice: Boolean,
    theme: LudoTheme,
    primaryColor: Color,
    darkColor: Color,
    outerPath: Path,
    innerPath: Path,
    pipPath: Path
) {
    val w = size.width
    val h = size.height
    val minDim = minOf(w, h)
    val centerX = w * 0.5f
    val baseCenterY = h * 0.48f
    val liftPx = jumpFactor * (minDim * 0.14f)
    val centerY = baseCenterY - liftPx

    if (isGoldenDice) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    theme.yellow.copy(alpha = 0.52f),
                    theme.yellow.copy(alpha = 0.18f),
                    theme.yellow.copy(alpha = 0f)
                ),
                center = Offset(centerX, baseCenterY),
                radius = minDim * 0.54f
            ),
            radius = minDim * 0.54f,
            center = Offset(centerX, baseCenterY)
        )
    } else if (canRoll) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.28f),
                    primaryColor.copy(alpha = 0f)
                ),
                center = Offset(centerX, baseCenterY),
                radius = minDim * 0.52f
            ),
            radius = minDim * 0.52f,
            center = Offset(centerX, baseCenterY)
        )
    }

    if (sixRingProgress > 0.01f && sixRingProgress < 0.99f) {
        val ringRadius = minDim * (0.34f + 0.19f * sixRingProgress)
        drawCircle(
            color = theme.yellow.copy(alpha = (1f - sixRingProgress) * 0.85f),
            radius = ringRadius,
            center = Offset(centerX, baseCenterY),
            style = Stroke(width = (1f - sixRingProgress) * minDim * 0.07f)
        )
    }

    val shadowWidth = minDim * (0.60f - jumpFactor * 0.18f)
    val shadowHeight = minDim * (0.14f - jumpFactor * 0.04f)
    val shadowAlpha = (0.24f - jumpFactor * 0.11f).coerceAtLeast(0.06f)
    drawOval(
        color = Color.Black.copy(alpha = shadowAlpha),
        topLeft = Offset(centerX - shadowWidth * 0.5f, h * 0.78f),
        size = Size(shadowWidth, shadowHeight)
    )

    val cubeHalfPx = minDim * 0.30f * scaleFactor

    val visibleFaces = ArrayList<Pair<DiceCube3DEngine.CubeFaceDef, DiceCube3DEngine.Vec3>>(4)
    for (face in DiceCube3DEngine.faces) {
        val transformedNormal = DiceCube3DEngine.transformPoint(
            p = face.normal,
            rotXDeg = rotXDeg,
            rotYDeg = rotYDeg,
            rotZDeg = rotZDeg,
            viewTiltXDeg = viewTiltXDeg,
            viewTiltYDeg = viewTiltYDeg
        )
        if (DiceCube3DEngine.isFaceVisible(transformedNormal)) {
            visibleFaces.add(face to transformedNormal)
        }
    }
    visibleFaces.sortBy { it.second.z }

    val rimTintBase = when {
        isGoldenDice -> lerp(theme.dieFace, theme.yellow, 0.38f)
        else -> lerp(theme.dieFace, theme.chipLine, 0.45f)
    }
    val edgeStrokeColor = when {
        isGoldenDice -> lerp(theme.yellow, Color.Black, 0.30f)
        else -> theme.chipLine
    }
    val edgeStrokeWidth = (minDim * 0.026f).coerceAtLeast(1.4f)
    val faceBaseColor = if (isGoldenDice) {
        lerp(theme.dieFace, theme.yellow, 0.20f)
    } else {
        theme.dieFace
    }

    for ((face, transformedNormal) in visibleFaces) {
        val light = DiceCube3DEngine.lightIntensity(transformedNormal)

        outerPath.reset()
        DiceCube3DEngine.outerFaceUv.forEachIndexed { idx, (u, v) ->
            val p3 = DiceCube3DEngine.pointOnFace(face, u, v)
            val rot = DiceCube3DEngine.transformPoint(p3, rotXDeg, rotYDeg, rotZDeg, viewTiltXDeg, viewTiltYDeg)
            val pt = DiceCube3DEngine.project(rot, centerX, centerY, cubeHalfPx)
            if (idx == 0) outerPath.moveTo(pt.x, pt.y) else outerPath.lineTo(pt.x, pt.y)
        }
        outerPath.close()

        val outerShade = shadeColor(rimTintBase, light * 0.96f)
        drawPath(path = outerPath, color = outerShade)
        drawPath(
            path = outerPath,
            color = edgeStrokeColor,
            style = Stroke(width = edgeStrokeWidth, join = StrokeJoin.Round)
        )

        innerPath.reset()
        DiceCube3DEngine.innerFaceUv.forEachIndexed { idx, (u, v) ->
            val p3 = DiceCube3DEngine.pointOnFace(face, u, v)
            val rot = DiceCube3DEngine.transformPoint(p3, rotXDeg, rotYDeg, rotZDeg, viewTiltXDeg, viewTiltYDeg)
            val pt = DiceCube3DEngine.project(rot, centerX, centerY, cubeHalfPx)
            if (idx == 0) innerPath.moveTo(pt.x, pt.y) else innerPath.lineTo(pt.x, pt.y)
        }
        innerPath.close()

        val faceColor = shadeColor(faceBaseColor, (0.88f + 0.12f * light).coerceAtMost(1f))
        drawPath(path = innerPath, color = faceColor)

        val basePipColor = if (isGoldenDice) {
            theme.contrastingTextOn(faceBaseColor)
        } else {
            theme.pip
        }
        val shadedPipColor = shadeColor(basePipColor, (0.82f + 0.18f * light).coerceAtMost(1f))
        val pipRadiusUv = 0.155f

        for ((pu, pv) in face.pips) {
            pipPath.reset()
            DiceCube3DEngine.pipCircleUv.forEachIndexed { idx, (cu, cv) ->
                val p3 = DiceCube3DEngine.pointOnFace(
                    face,
                    u = pu + cu * pipRadiusUv,
                    v = pv + cv * pipRadiusUv
                )
                val rot = DiceCube3DEngine.transformPoint(p3, rotXDeg, rotYDeg, rotZDeg, viewTiltXDeg, viewTiltYDeg)
                val pt = DiceCube3DEngine.project(rot, centerX, centerY, cubeHalfPx)
                if (idx == 0) pipPath.moveTo(pt.x, pt.y) else pipPath.lineTo(pt.x, pt.y)
            }
            pipPath.close()
            drawPath(path = pipPath, color = shadedPipColor)
        }
    }
}

private fun shadeColor(color: Color, factor: Float): Color {
    val f = factor.coerceIn(0.45f, 1.0f)
    return Color(
        red = (color.red * f).coerceIn(0f, 1f),
        green = (color.green * f).coerceIn(0f, 1f),
        blue = (color.blue * f).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}
