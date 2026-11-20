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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.PlayerColor
import com.example.ui.theme.InkDark
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.palette
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
    const val ROLL_DURATION_MS: Int = 540
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

    // Top-left-front directional light for clean 3D Lambertian face shading
    private val lightDir: Vec3 = run {
        val lx = -0.38f
        val ly = -0.62f
        val lz = 0.85f
        val len = sqrt(lx * lx + ly * ly + lz * lz)
        Vec3(lx / len, ly / len, lz / len)
    }

    /**
     * Generates a fair uniform random die roll between 1 and 6 inclusive.
     */
    fun rollRandomFace(random: Random = Random.Default): Int = random.nextInt(1, 7)

    /**
     * Returns the exact cube-local (rotX, rotY) angles in degrees that bring [face] (1..6)
     * directly forward onto +Z before applying the fixed 3/4 resting view tilt.
     */
    fun targetAnglesForFace(face: Int): FaceTargetAngles = when (face.coerceIn(1, 6)) {
        1 -> FaceTargetAngles(rotX = 0f, rotY = 0f)
        2 -> FaceTargetAngles(rotX = -90f, rotY = 0f)
        3 -> FaceTargetAngles(rotX = 0f, rotY = -90f)
        4 -> FaceTargetAngles(rotX = 0f, rotY = 90f)
        5 -> FaceTargetAngles(rotX = 90f, rotY = 0f)
        else -> FaceTargetAngles(rotX = 0f, rotY = 180f)
    }

    /**
     * Standard 6-face die layout where opposite faces sum to 7 (1-6, 2-5, 3-4).
     */
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

    // Pre-computed rounded-square UV contour (16 vertices) so draw loop stays allocation-light
    val outerFaceUv: List<Pair<Float, Float>> = buildRoundedSquareUv(halfExtent = 1.0f, cornerRadius = 0.24f)
    val innerFaceUv: List<Pair<Float, Float>> = buildRoundedSquareUv(halfExtent = 0.87f, cornerRadius = 0.19f)
    val pipCircleUv: List<Pair<Float, Float>> = List(10) { i ->
        val angle = (2.0 * PI * i) / 10.0
        cos(angle).toFloat() to sin(angle).toFloat()
    }

    private fun buildRoundedSquareUv(halfExtent: Float, cornerRadius: Float): List<Pair<Float, Float>> {
        val inner = halfExtent - cornerRadius
        val corners = listOf(
            Triple(inner, -inner, -90f to 0f),     // Top-right
            Triple(inner, inner, 0f to 90f),       // Bottom-right
            Triple(-inner, inner, 90f to 180f),    // Bottom-left
            Triple(-inner, -inner, 180f to 270f)   // Top-left
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

    /**
     * Transforms a 3D point by cube-local Rx -> Ry -> Rz, then fixed world 3/4 view tilt.
     */
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

    /**
     * Computes which face (1..6) is most facing the camera for the given cube rotation.
     */
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
        // Exact perspective backface culling for a unit cube centered at origin with camera at +Z
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
 * True 3D perspective-projected 6-face animated Ludo die.
 *
 * Supports two seamless modes:
 * 1. **Self-rolling mode** (`onRollResult != null`): Tapping the die generates a random
 *    number between 1 and 6, notifies `onRollStarted`, animates the 3D cube tumbling and
 *    bouncing onto that exact face, and invokes `onRollResult(rolledValue)` to update game state.
 * 2. **Controlled mode** (`onRollResult == null`): Driven externally via `value` and
 *    `rollTrigger` (used for Bots, synced Online rolls, and scripted Tutorial lessons),
 *    while still rendering the exact same 3D cube tumble onto `value`.
 */
@Composable
fun LudoDice3D(
    value: Int,
    rollTrigger: Int,
    isRolling: Boolean,
    canRoll: Boolean,
    activeColor: PlayerColor,
    onRollClick: () -> Unit = {},
    onRollStarted: (() -> Unit)? = null,
    onRollResult: ((Int) -> Unit)? = null,
    random: Random = Random.Default,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    testTag: String = "dice_roll_button"
) {
    val palette = activeColor.palette()
    val safeValue = value.coerceIn(1, 6)
    val initialAngles = remember { DiceCube3DEngine.targetAnglesForFace(safeValue) }

    val rotX = remember { Animatable(initialAngles.rotX) }
    val rotY = remember { Animatable(initialAngles.rotY) }
    val rotZ = remember { Animatable(0f) }
    val jumpArc = remember { Animatable(0f) }
    val squashScale = remember { Animatable(1f) }
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

        // Normalize angle deltas so we always do a lively ~2 full 3D spins and land cleanly on targetFace
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
                // Main toss arc + crisp landing mini-hop
                jumpArc.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                jumpArc.animateTo(0f, tween(175, easing = CubicBezierEasing(0.45f, 0f, 0.85f, 0.65f)))
                jumpArc.animateTo(0.24f, tween(80, easing = FastOutSlowInEasing))
                jumpArc.animateTo(0f, tween(75, easing = FastOutSlowInEasing))
            }
            launch {
                squashScale.animateTo(1.10f, tween(200))
                squashScale.animateTo(0.92f, tween(170))
                squashScale.animateTo(1.0f, spring(dampingRatio = 0.45f, stiffness = 460f))
            }
        }

        // Snap modulo 360 cleanly at exact resting angles without visual jump
        rotX.snapTo(target.rotX)
        rotY.snapTo(target.rotY)
        rotZ.snapTo(0f)
        shownFaceValue = cleanFace

        if (cleanFace == 6) {
            sixRingProgress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            sixRingProgress.snapTo(0f)
        }
    }

    // Controlled / external roll trigger (bots, online synced rolls, tutorial lessons)
    LaunchedEffect(rollTrigger) {
        if (rollTrigger > lastExternalTrigger) {
            lastExternalTrigger = rollTrigger
            if (!internalRolling) {
                animateTumbleToFace(safeValue)
            }
        }
    }

    // Keep resting cube orientation synced when value changes while idle (e.g., resuming a saved match)
    LaunchedEffect(safeValue, isRolling, internalRolling) {
        if (!isRolling && !internalRolling && rollTrigger == lastExternalTrigger && shownFaceValue != safeValue) {
            val target = DiceCube3DEngine.targetAnglesForFace(safeValue)
            rotX.snapTo(target.rotX)
            rotY.snapTo(target.rotY)
            rotZ.snapTo(0f)
            shownFaceValue = safeValue
        }
    }

    val effectiveRolling = isRolling || internalRolling

    // Only run idle breathing animation when the die is actively ready to roll (saves battery on mid-range phones)
    val idlePulse = if (canRoll && !effectiveRolling) {
        val pulseTransition = rememberInfiniteTransition(label = "diceReadyPulse")
        val anim by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
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

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTag)
            .semantics {
                contentDescription = if (canRoll) {
                    "3D Dice showing $shownFaceValue. Tap to roll"
                } else {
                    "3D Dice showing $shownFaceValue"
                }
            }
            .clickable(
                enabled = canRoll && !effectiveRolling,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = false,
                    radius = size * 0.54f,
                    color = palette.primary.copy(alpha = 0.30f)
                ),
                role = Role.Button,
                onClick = {
                    val resultCallback = latestOnRollResult
                    if (resultCallback != null) {
                        if (internalRolling) return@clickable
                        internalRolling = true
                        val rolledValue = DiceCube3DEngine.rollRandomFace(random)
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            draw3DCubeDie(
                rotXDeg = rotX.value,
                rotYDeg = rotY.value,
                rotZDeg = rotZ.value,
                jumpFactor = jumpArc.value,
                scaleFactor = squashScale.value * idlePulse,
                sixRingProgress = sixRingProgress.value,
                canRoll = canRoll && !effectiveRolling,
                primaryColor = palette.primary,
                darkColor = palette.dark,
                outerPath = reusableOuterPath,
                innerPath = reusableInnerPath,
                pipPath = reusablePipPath
            )
        }
    }
}

/**
 * Synchronized pair of 3D animated dice for Power-Up mode's Double Dice ability.
 * Generates two random 1..6 values in self-rolling mode and commits both together,
 * or animates synced externally provided values for bots & online matches.
 */
@Composable
fun LudoDoubleDice3D(
    firstValue: Int,
    secondValue: Int,
    rollTrigger: Int,
    isRolling: Boolean,
    canRoll: Boolean,
    activeColor: PlayerColor,
    onRollClick: () -> Unit = {},
    onRollStarted: (() -> Unit)? = null,
    onDoubleRollResult: ((Int, Int) -> Unit)? = null,
    random: Random = Random.Default,
    dieSize: Dp = 58.dp,
    modifier: Modifier = Modifier
) {
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

private fun normalizeSignedDelta(degDelta: Float): Float {
    var d = degDelta % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun DrawScope.draw3DCubeDie(
    rotXDeg: Float,
    rotYDeg: Float,
    rotZDeg: Float,
    jumpFactor: Float,
    scaleFactor: Float,
    sixRingProgress: Float,
    canRoll: Boolean,
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
    val baseCenterY = h * 0.51f
    val liftPx = jumpFactor * (minDim * 0.12f)
    val centerY = baseCenterY - liftPx

    // Subtle glowing readiness halo when ready to roll
    if (canRoll) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.24f),
                    primaryColor.copy(alpha = 0f)
                ),
                center = Offset(centerX, baseCenterY),
                radius = minDim * 0.52f
            ),
            radius = minDim * 0.52f,
            center = Offset(centerX, baseCenterY)
        )
    }

    // Celebratory golden burst ring when landing on a 6
    if (sixRingProgress > 0.01f && sixRingProgress < 0.99f) {
        val ringRadius = minDim * (0.34f + 0.19f * sixRingProgress)
        drawCircle(
            color = SunnyGold.copy(alpha = (1f - sixRingProgress) * 0.85f),
            radius = ringRadius,
            center = Offset(centerX, baseCenterY),
            style = Stroke(width = (1f - sixRingProgress) * minDim * 0.07f)
        )
    }

    // Dynamic elliptical floor shadow that shrinks & softens as the die leaps upward
    val shadowWidth = minDim * (0.62f - jumpFactor * 0.18f)
    val shadowHeight = minDim * (0.16f - jumpFactor * 0.05f)
    val shadowAlpha = (0.28f - jumpFactor * 0.13f).coerceAtLeast(0.08f)
    drawOval(
        color = darkColor.copy(alpha = shadowAlpha),
        topLeft = Offset(centerX - shadowWidth * 0.5f, h * 0.80f),
        size = Size(shadowWidth, shadowHeight)
    )

    val cubeHalfPx = minDim * 0.265f * scaleFactor

    // Transform all 6 face normals, cull backfaces, and painter-sort back-to-front
    val visibleFaces = ArrayList<Pair<DiceCube3DEngine.CubeFaceDef, DiceCube3DEngine.Vec3>>(4)
    for (face in DiceCube3DEngine.faces) {
        val transformedNormal = DiceCube3DEngine.transformPoint(
            p = face.normal,
            rotXDeg = rotXDeg,
            rotYDeg = rotYDeg,
            rotZDeg = rotZDeg
        )
        if (DiceCube3DEngine.isFaceVisible(transformedNormal)) {
            visibleFaces.add(face to transformedNormal)
        }
    }
    visibleFaces.sortBy { it.second.z }

    val rimTintBase = lerp(Color(0xFFEAEEF8), primaryColor, if (canRoll) 0.24f else 0.14f)
    val edgeStrokeColor = if (canRoll) primaryColor.copy(alpha = 0.88f) else darkColor.copy(alpha = 0.42f)
    val edgeStrokeWidth = (minDim * 0.024f).coerceAtLeast(1.4f)

    for ((face, transformedNormal) in visibleFaces) {
        val light = DiceCube3DEngine.lightIntensity(transformedNormal)

        // Outer beveled face polygon
        outerPath.reset()
        DiceCube3DEngine.outerFaceUv.forEachIndexed { idx, (u, v) ->
            val p3 = DiceCube3DEngine.pointOnFace(face, u, v)
            val rot = DiceCube3DEngine.transformPoint(p3, rotXDeg, rotYDeg, rotZDeg)
            val pt = DiceCube3DEngine.project(rot, centerX, centerY, cubeHalfPx)
            if (idx == 0) outerPath.moveTo(pt.x, pt.y) else outerPath.lineTo(pt.x, pt.y)
        }
        outerPath.close()

        val outerShade = shadeColor(rimTintBase, light * 0.94f)
        drawPath(path = outerPath, color = outerShade)
        drawPath(
            path = outerPath,
            color = edgeStrokeColor,
            style = Stroke(width = edgeStrokeWidth, join = StrokeJoin.Round)
        )

        // Inner glossy ivory face inset
        innerPath.reset()
        DiceCube3DEngine.innerFaceUv.forEachIndexed { idx, (u, v) ->
            val p3 = DiceCube3DEngine.pointOnFace(face, u, v)
            val rot = DiceCube3DEngine.transformPoint(p3, rotXDeg, rotYDeg, rotZDeg)
            val pt = DiceCube3DEngine.project(rot, centerX, centerY, cubeHalfPx)
            if (idx == 0) innerPath.moveTo(pt.x, pt.y) else innerPath.lineTo(pt.x, pt.y)
        }
        innerPath.close()

        val faceColor = shadeColor(Color(0xFFFFFFFF), light)
        drawPath(path = innerPath, color = faceColor)

        // Perspective-projected 3D recessed pips on this face
        val basePipColor = if (face.value == 6 || face.value == 1) primaryColor else InkDark
        val shadedPipColor = shadeColor(basePipColor, (0.72f + 0.28f * light).coerceAtMost(1f))
        val pipRadiusUv = if (face.value == 1) 0.19f else 0.145f

        for ((pu, pv) in face.pips) {
            // Subtle bottom highlight for recessed dimple look
            pipPath.reset()
            DiceCube3DEngine.pipCircleUv.forEachIndexed { idx, (cu, cv) ->
                val p3 = DiceCube3DEngine.pointOnFace(
                    face,
                    u = pu + cu * pipRadiusUv,
                    v = pv + cv * pipRadiusUv + 0.025f
                )
                val rot = DiceCube3DEngine.transformPoint(p3, rotXDeg, rotYDeg, rotZDeg)
                val pt = DiceCube3DEngine.project(rot, centerX, centerY, cubeHalfPx)
                if (idx == 0) pipPath.moveTo(pt.x, pt.y) else pipPath.lineTo(pt.x, pt.y)
            }
            pipPath.close()
            drawPath(path = pipPath, color = Color.White.copy(alpha = 0.55f * light))

            // Main pip ellipse projected onto the 3D face plane
            pipPath.reset()
            DiceCube3DEngine.pipCircleUv.forEachIndexed { idx, (cu, cv) ->
                val p3 = DiceCube3DEngine.pointOnFace(
                    face,
                    u = pu + cu * pipRadiusUv,
                    v = pv + cv * pipRadiusUv
                )
                val rot = DiceCube3DEngine.transformPoint(p3, rotXDeg, rotYDeg, rotZDeg)
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
