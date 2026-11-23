package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.GameModeId
import com.example.engine.PlayerColor
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.ConfettiOverlay
import com.example.ui.game.DiceCube3DEngine
import com.example.ui.game.LudoDice3D
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.LudoTheme
import com.example.ui.theme.modeAccentColor
import com.example.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class TutorialLesson(
    val badge: String,
    val title: String,
    val instruction: String,
    val ruleSummary: String
) {
    ROLL_DIE(
        badge = "1 OF 8 • ROLLING",
        title = "Roll the Die",
        instruction = "Tap the 3D die below to roll!",
        ruleSummary = "Each turn starts by rolling the die. Players take turns clockwise around the board."
    ),
    GET_OUT_WITH_SIX(
        badge = "2 OF 8 • LEAVING BASE",
        title = "Roll a 6 to Enter",
        instruction = "Roll a 6, then tap your Red token in Base to enter!",
        ruleSummary = "Tokens start in your corner base. You must roll a 6 to move a token onto your colored Start square."
    ),
    MOVE_TOKEN(
        badge = "3 OF 8 • MOVING",
        title = "Move Along the Track",
        instruction = "Roll a 4, then tap the track to hop 4 spaces forward!",
        ruleSummary = "Tokens travel clockwise around the 52-square track before turning into your private colored home column."
    ),
    CAPTURE_OPPONENT(
        badge = "4 OF 8 • CAPTURING",
        title = "Capture Rivals",
        instruction = "Tap the glowing Red token to land on Blue!",
        ruleSummary = "Landing on an opponent's token on a regular square sends it back to base! Captures do not give an extra roll."
    ),
    SAFE_STARS(
        badge = "5 OF 8 • SAFE STARS",
        title = "Safe Star Squares",
        instruction = "Tap to land on the Star shared with Green!",
        ruleSummary = "8 Safe Squares (4 Start squares + 4 Star squares) protect every token on them — rivals share safely with no capture."
    ),
    EXACT_ROLL_HOME(
        badge = "6 OF 8 • REACHING HOME",
        title = "Exact Roll to Finish",
        instruction = "Try rolling! Only an exact 2 reaches the Home triangle.",
        ruleSummary = "A token must land in the center Home triangle with the exact roll. Overshooting can't move that token!"
    ),
    EXTRA_ROLL(
        badge = "7 OF 8 • BONUS ROLLS",
        title = "6 Gives a Bonus Roll",
        instruction = "Tap the die to roll a 6 and earn a Bonus Roll!",
        ruleSummary = "Whenever you roll a 6 and play your move, you immediately get another roll."
    ),
    TRIPLE_SIX(
        badge = "8 OF 8 • TRIPLE-6 RULE",
        title = "Watch Out for Triple 6s!",
        instruction = "Tap the die 3 times to see what happens on three 6s in a row!",
        ruleSummary = "Rolling three 6s in a row cancels your turn immediately — the third 6 is not played and the next player goes."
    );

    fun accentColor(theme: LudoTheme): Color = when (this) {
        ROLL_DIE -> theme.blue
        GET_OUT_WITH_SIX -> theme.red
        MOVE_TOKEN -> theme.green
        CAPTURE_OPPONENT -> theme.red
        SAFE_STARS -> theme.yellow
        EXACT_ROLL_HOME -> modeAccentColor(GameModeId.MAGIC, theme)
        EXTRA_ROLL -> theme.green
        TRIPLE_SIX -> theme.red
    }
}

/**
 * Interactive 8-step hands-on Ludo tutorial shown automatically on first launch
 * (skippable anytime) and replayable from Settings or the Home "?" button.
 */
@Composable
fun TutorialScreen(
    isFirstLaunch: Boolean = false,
    onCompleteOrSkip: () -> Unit,
    onPlayNowClick: () -> Unit = onCompleteOrSkip,
    onBackClick: () -> Unit = onCompleteOrSkip
) {
    val theme = LocalLudoTheme.current
    val lessons = remember { TutorialLesson.entries }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val lesson = lessons[stepIndex]
    val lessonAccent = lesson.accentColor(theme)
    val isFirst = stepIndex == 0
    val isLast = stepIndex == lessons.lastIndex

    // Interactive state reset on each lesson change
    var subStage by remember(stepIndex) { mutableIntStateOf(0) }
    var diceVal by remember(stepIndex) { mutableIntStateOf(1) }
    var rollTrigger by remember(stepIndex) { mutableIntStateOf(0) }
    var isRolling by remember(stepIndex) { mutableStateOf(false) }
    var feedbackText by remember(stepIndex) { mutableStateOf(lesson.instruction) }
    var lessonDone by remember(stepIndex) { mutableStateOf(false) }
    var confettiKey by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    fun triggerInteractiveRoll(targetValue: Int, onLanded: () -> Unit) {
        if (isRolling) return
        scope.launch {
            isRolling = true
            diceVal = targetValue
            rollTrigger += 1
            delay(DiceCube3DEngine.ROLL_DURATION_MS.toLong() + 30L)
            isRolling = false
            onLanded()
        }
    }

    LaunchedEffect(stepIndex) {
        when (lesson) {
            TutorialLesson.CAPTURE_OPPONENT -> diceVal = 3
            TutorialLesson.SAFE_STARS -> diceVal = 2
            else -> diceVal = 6
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("tutorial_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Back (if replay) + Title + Skip button (always accessible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isFirstLaunch) {
                        CartoonIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            onClick = onBackClick,
                            modifier = Modifier.testTag("tutorial_back_button")
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = stringResource(R.string.tutorial_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.text
                    )
                }

                TextButton(
                    onClick = onCompleteOrSkip,
                    modifier = Modifier.testTag("tutorial_skip_button")
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.accessibleAccentOn(theme.blue, theme.bg),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / lessons.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = lessonAccent,
                trackColor = theme.timerTrack
            )

            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = lesson,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tutorialLessonCard"
            ) { activeLesson ->
                val activeAccent = activeLesson.accentColor(theme)
                val badgeTextAccent = theme.accessibleAccentOn(activeAccent, theme.surface)
                val promptBg = lerp(theme.surface, activeAccent, if (theme.isDarkSurface) 0.16f else 0.09f)
                val doneAccent = theme.accessibleAccentOn(theme.green, promptBg)
                val promptLabelAccent = theme.accessibleAccentOn(activeAccent, promptBg)

                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = activeAccent.copy(alpha = 0.55f),
                    cornerRadius = 26.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeLesson.badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeTextAccent,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = activeLesson.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = theme.surfaceText,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(12.dp))

                        // Interactive Mini-Board / Track Playground
                        InteractiveLessonStage(
                            lesson = activeLesson,
                            lessonAccent = activeAccent,
                            subStage = subStage,
                            onBoardTapped = {
                                when (activeLesson) {
                                    TutorialLesson.GET_OUT_WITH_SIX -> {
                                        if (subStage == 1) {
                                            subStage = 2
                                            lessonDone = true
                                            feedbackText = "Nice! Your Red token entered the Start square!"
                                        }
                                    }
                                    TutorialLesson.MOVE_TOKEN -> {
                                        if (subStage == 1) {
                                            subStage = 2
                                            lessonDone = true
                                            feedbackText = "Great! Red hopped 4 squares clockwise!"
                                        }
                                    }
                                    TutorialLesson.CAPTURE_OPPONENT -> {
                                        if (subStage == 0) {
                                            subStage = 1
                                            lessonDone = true
                                            feedbackText = "BOOM! Blue sent back to base! (No extra roll for captures.)"
                                        }
                                    }
                                    TutorialLesson.SAFE_STARS -> {
                                        if (subStage == 0) {
                                            subStage = 1
                                            lessonDone = true
                                            feedbackText = "Safe! Red & Green share the Star peacefully."
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Interactive Die + Prompt Box
                        val dieNeedsTap = when (activeLesson) {
                            TutorialLesson.ROLL_DIE -> subStage == 0
                            TutorialLesson.GET_OUT_WITH_SIX -> subStage == 0
                            TutorialLesson.MOVE_TOKEN -> subStage == 0
                            TutorialLesson.EXACT_ROLL_HOME -> subStage < 2
                            TutorialLesson.EXTRA_ROLL -> subStage == 0
                            TutorialLesson.TRIPLE_SIX -> subStage < 3
                            TutorialLesson.CAPTURE_OPPONENT, TutorialLesson.SAFE_STARS -> false
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(promptBg)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LudoDice3D(
                                value = diceVal,
                                rollTrigger = rollTrigger,
                                isRolling = isRolling,
                                canRoll = dieNeedsTap && !isRolling,
                                activeColor = PlayerColor.RED,
                                size = 58.dp,
                                testTag = "tutorial_dice_button",
                                onRollClick = {
                                    when (activeLesson) {
                                        TutorialLesson.ROLL_DIE -> {
                                            triggerInteractiveRoll(4) {
                                                subStage = 1
                                                lessonDone = true
                                                feedbackText = "You rolled a 4! That's how every turn begins."
                                            }
                                        }
                                        TutorialLesson.GET_OUT_WITH_SIX -> {
                                            triggerInteractiveRoll(6) {
                                                subStage = 1
                                                feedbackText = "You rolled a 6! Now tap the glowing Red token above!"
                                            }
                                        }
                                        TutorialLesson.MOVE_TOKEN -> {
                                            triggerInteractiveRoll(4) {
                                                subStage = 1
                                                feedbackText = "Rolled 4! Now tap your glowing Red token to hop 4 spaces!"
                                            }
                                        }
                                        TutorialLesson.EXACT_ROLL_HOME -> {
                                            if (subStage == 0) {
                                                triggerInteractiveRoll(5) {
                                                    subStage = 1
                                                    feedbackText = "Rolled 5 — too high (needs exact 2)! Tap the die again!"
                                                }
                                            } else if (subStage == 1) {
                                                triggerInteractiveRoll(2) {
                                                    subStage = 2
                                                    lessonDone = true
                                                    confettiKey += 1
                                                    feedbackText = "Exact 2! Red glides straight into Home!"
                                                }
                                            }
                                        }
                                        TutorialLesson.EXTRA_ROLL -> {
                                            triggerInteractiveRoll(6) {
                                                subStage = 1
                                                lessonDone = true
                                                feedbackText = "Rolled a 6! BONUS ROLL unlocked — you get to roll again!"
                                            }
                                        }
                                        TutorialLesson.TRIPLE_SIX -> {
                                            val next = subStage + 1
                                            triggerInteractiveRoll(6) {
                                                subStage = next
                                                feedbackText = when (next) {
                                                    1 -> "First 6! (1/3) Tap again for your bonus roll..."
                                                    2 -> "Second 6! (2/3) One more 6 will cancel the turn..."
                                                    else -> {
                                                        lessonDone = true
                                                        "TRIPLE 6! Turn cancelled — 3rd roll is forfeited!"
                                                    }
                                                }
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (lessonDone) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = doneAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = if (lessonDone) "Completed!" else "Try It Live",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (lessonDone) doneAccent else promptLabelAccent,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = feedbackText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.surfaceText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = theme.surfaceVariant,
                            border = BorderStroke(1.dp, theme.chipLine)
                        ) {
                            Text(
                                text = activeLesson.ruleSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.surfaceMutedText,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isFirst) {
                    CartoonButton(
                        text = "Back",
                        onClick = { stepIndex -= 1 },
                        tag = "tutorial_prev_button",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        containerColor = theme.surface,
                        lipColor = theme.chipLine,
                        contentColor = theme.surfaceText,
                        minHeight = 54.dp,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isLast) {
                    CartoonButton(
                        text = stringResource(R.string.tutorial_play_now),
                        onClick = onPlayNowClick,
                        tag = "tutorial_play_now_button",
                        icon = Icons.Filled.PlayArrow,
                        containerColor = theme.green,
                        minHeight = 54.dp,
                        modifier = Modifier.weight(1.4f)
                    )
                } else {
                    CartoonButton(
                        text = if (lessonDone) "Next Lesson" else "Next",
                        onClick = { stepIndex += 1 },
                        tag = "tutorial_next_button",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        containerColor = if (lessonDone) theme.green else theme.primaryActionBg,
                        contentColor = if (lessonDone) theme.contrastingTextOn(theme.green) else theme.onPrimaryAction,
                        minHeight = 54.dp,
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        ConfettiOverlay(
            triggerKey = confettiKey,
            burstFromCenter = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun InteractiveLessonStage(
    lesson: TutorialLesson,
    lessonAccent: Color,
    subStage: Int,
    onBoardTapped: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val redPalette = PlayerColor.RED.palette(theme)
    val bluePalette = PlayerColor.BLUE.palette(theme)
    val greenPalette = PlayerColor.GREEN.palette(theme)

    val isBoardActionReady = when (lesson) {
        TutorialLesson.GET_OUT_WITH_SIX -> subStage == 1
        TutorialLesson.MOVE_TOKEN -> subStage == 1
        TutorialLesson.CAPTURE_OPPONENT -> subStage == 0
        TutorialLesson.SAFE_STARS -> subStage == 0
        else -> false
    }

    val targetPos = when (lesson) {
        TutorialLesson.GET_OUT_WITH_SIX -> if (subStage >= 2) 1f else 0f
        TutorialLesson.MOVE_TOKEN -> if (subStage >= 2) 4f else 0f
        TutorialLesson.CAPTURE_OPPONENT -> if (subStage >= 1) 3f else 0f
        TutorialLesson.SAFE_STARS -> if (subStage >= 1) 2f else 0f
        TutorialLesson.EXACT_ROLL_HOME -> if (subStage >= 2) 4f else 2f
        else -> 0f
    }
    val animPos by animateFloatAsState(
        targetValue = targetPos,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 260f),
        label = "tutorialTokenSlide"
    )

    val stageBg = theme.board
    val stageIconAccent = theme.accessibleAccentOn(lessonAccent, stageBg)
    val stageDoneAccent = theme.accessibleAccentOn(theme.green, stageBg)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = isBoardActionReady, onClick = onBoardTapped)
            .testTag("tutorial_interactive_stage"),
        shape = RoundedCornerShape(20.dp),
        color = stageBg,
        border = BorderStroke(
            width = if (isBoardActionReady) 2.5.dp else 1.dp,
            color = if (isBoardActionReady) lessonAccent else theme.gridLine
        )
    ) {
        when (lesson) {
            TutorialLesson.ROLL_DIE,
            TutorialLesson.EXTRA_ROLL,
            TutorialLesson.TRIPLE_SIX -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (lesson == TutorialLesson.TRIPLE_SIX) {
                        for (i in 1..3) {
                            val active = subStage >= i
                            val cancelled = subStage >= 3 && i == 3
                            val tileBg = when {
                                cancelled -> theme.red
                                active -> theme.yellow
                                else -> theme.cell
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = tileBg,
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = if (active) tileBg else theme.gridLine
                                ),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (cancelled) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = "Turn cancelled",
                                            tint = theme.contrastingTextOn(theme.red),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    } else {
                                        Text(
                                            text = if (active) "6" else "?",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = if (active) theme.contrastingTextOn(theme.yellow) else theme.surfaceMutedText,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(lessonAccent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (lesson == TutorialLesson.EXTRA_ROLL) Icons.Filled.Star else Icons.Filled.Casino,
                                contentDescription = null,
                                tint = stageIconAccent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column {
                            Text(
                                text = when {
                                    lesson == TutorialLesson.EXTRA_ROLL && subStage >= 1 -> "BONUS ROLL!"
                                    lesson == TutorialLesson.ROLL_DIE && subStage >= 1 -> "ROLLED A 4!"
                                    else -> "TAP THE DIE BELOW"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = if (subStage >= 1) stageDoneAccent else theme.surfaceText,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = when {
                                    lesson == TutorialLesson.EXTRA_ROLL && subStage >= 1 -> "6 unlocks another roll right away!"
                                    lesson == TutorialLesson.ROLL_DIE && subStage >= 1 -> "Smooth & bouncy dice every turn."
                                    else -> "Give the 3D die a tap!"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.surfaceMutedText
                            )
                        }
                    }
                }
            }

            else -> {
                val starCellTint = lerp(theme.cell, theme.yellow, if (theme.isDarkSurface) 0.24f else 0.16f)
                // 5-cell interactive mini-track canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    val cellCount = 5
                    val cellW = size.width / cellCount
                    val cellH = minOf(size.height * 0.72f, cellW * 0.92f)
                    val topY = (size.height - cellH) / 2f

                    for (i in 0 until cellCount) {
                        val left = i * cellW + 4f
                        val w = cellW - 8f
                        val isBaseCell = lesson == TutorialLesson.GET_OUT_WITH_SIX && i == 0
                        val isStartCell = lesson == TutorialLesson.GET_OUT_WITH_SIX && i == 1
                        val isStarCell = lesson == TutorialLesson.SAFE_STARS && i == 2
                        val isHomeCell = lesson == TutorialLesson.EXACT_ROLL_HOME && i == 4
                        val isHomeLane = lesson == TutorialLesson.EXACT_ROLL_HOME && i in 1..3

                        val fill = when {
                            isBaseCell -> redPalette.light
                            isStartCell -> redPalette.primary
                            isStarCell -> starCellTint
                            isHomeCell -> theme.crownIcon.copy(alpha = 0.22f)
                            isHomeLane -> redPalette.primary.copy(alpha = 0.24f)
                            else -> theme.cell
                        }
                        drawRoundRect(
                            color = fill,
                            topLeft = Offset(left, topY),
                            size = Size(w, cellH),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        drawRoundRect(
                            color = theme.gridLine,
                            topLeft = Offset(left, topY),
                            size = Size(w, cellH),
                            cornerRadius = CornerRadius(14f, 14f),
                            style = Stroke(width = 2.5f)
                        )

                        val center = Offset(left + w / 2f, topY + cellH / 2f)
                        if (isStarCell || isStartCell) {
                            drawMiniStar(
                                center = center,
                                radius = cellH * 0.27f,
                                color = if (isStartCell) Color.White else theme.starIcon
                            )
                        } else if (isHomeCell) {
                            val tri = Path().apply {
                                moveTo(center.x, center.y - cellH * 0.26f)
                                lineTo(center.x + cellH * 0.26f, center.y + cellH * 0.22f)
                                lineTo(center.x - cellH * 0.26f, center.y + cellH * 0.22f)
                                close()
                            }
                            drawPath(tri, color = redPalette.primary)
                        }
                    }

                    val tokenR = cellH * 0.27f

                    // Opponent token for Capture or Safe Star lessons
                    if (lesson == TutorialLesson.CAPTURE_OPPONENT && subStage == 0) {
                        val oppCenter = Offset(3 * cellW + cellW / 2f, topY + cellH / 2f)
                        drawMiniPawn(oppCenter, tokenR, bluePalette.primary, theme.tokenEdge, theme.blue, glowing = false)
                    } else if (lesson == TutorialLesson.SAFE_STARS) {
                        val shiftX = if (subStage >= 1) cellW * 0.16f else 0f
                        val oppCenter = Offset(2 * cellW + cellW / 2f + shiftX, topY + cellH / 2f)
                        drawMiniPawn(oppCenter, tokenR * 0.9f, greenPalette.primary, theme.tokenEdge, theme.green, glowing = false)
                    }

                    // Player's Red token (animated slide)
                    val redShiftX = if (lesson == TutorialLesson.SAFE_STARS && subStage >= 1) -cellW * 0.16f else 0f
                    val redCenter = Offset(animPos * cellW + cellW / 2f + redShiftX, topY + cellH / 2f)
                    drawMiniPawn(
                        center = redCenter,
                        radius = if (lesson == TutorialLesson.SAFE_STARS && subStage >= 1) tokenR * 0.9f else tokenR,
                        fill = redPalette.primary,
                        edge = theme.tokenEdge,
                        ringColor = redPalette.primary,
                        glowing = isBoardActionReady
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMiniPawn(
    center: Offset,
    radius: Float,
    fill: Color,
    edge: Color,
    ringColor: Color,
    glowing: Boolean
) {
    if (glowing) {
        drawCircle(
            color = ringColor.copy(alpha = 0.26f),
            radius = radius * 1.48f,
            center = center
        )
        drawCircle(
            color = ringColor,
            radius = radius * 1.48f,
            center = center,
            style = Stroke(width = 3.5f)
        )
    }
    drawCircle(color = Color.Black.copy(alpha = 0.18f), radius = radius, center = Offset(center.x, center.y + 3f))
    drawCircle(color = fill, radius = radius, center = center)
    drawCircle(color = edge, radius = radius, center = center, style = Stroke(width = 4.5f))
    drawCircle(
        color = Color.White.copy(alpha = 0.52f),
        radius = radius * 0.42f,
        center = center
    )
}

private fun DrawScope.drawMiniStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val inner = radius * 0.45f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else inner
        val angle = (i * 36.0 - 90.0) * PI / 180.0
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3f, join = StrokeJoin.Round)
    )
}
