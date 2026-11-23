package com.example.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.PlayerColor
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow

/**
 * Floating die-number selector popup themed via [LocalLudoTheme].
 */
@Composable
fun DieStepChoicePopup(
    visible: Boolean,
    playerName: String,
    rolledDice: List<Int>,
    remainingDice: List<Int>,
    selectedDieValue: Int?,
    playableDiceValues: List<Int>,
    activeColor: PlayerColor,
    onSelectDieValue: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val playerColor = theme.playerColor(activeColor)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(
            initialScale = 0.86f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.90f),
        modifier = modifier.fillMaxSize()
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val cardInteraction = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.36f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .ludoShadow(theme.cardShadow, 24.dp)
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = {}
                    )
                    .testTag("die_step_choice_popup"),
                shape = RoundedCornerShape(24.dp),
                color = theme.surface,
                border = BorderStroke(2.dp, playerColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.die_choice_popup_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = theme.surfaceText,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "$playerName · ${stringResource(R.string.die_choice_popup_subtitle)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.surfaceMutedText
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("die_choice_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_die_choice),
                                tint = theme.surfaceMutedText
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val remainingCounts = remainingDice.groupingBy { it }.eachCount().toMutableMap()
                    val slotRemaining = rolledDice.map { value ->
                        val left = remainingCounts[value] ?: 0
                        if (left > 0) {
                            remainingCounts[value] = left - 1
                            true
                        } else {
                            false
                        }
                    }
                    var highlightedSelectedOnce = false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rolledDice.forEachIndexed { idx, steps ->
                            val isRemaining = slotRemaining[idx]
                            val isPlayable = isRemaining && steps in playableDiceValues
                            val isSelected = isPlayable && selectedDieValue == steps && !highlightedSelectedOnce
                            if (isSelected) highlightedSelectedOnce = true

                            DieChoiceOptionCard(
                                dieValue = steps,
                                isRemaining = isRemaining,
                                isPlayable = isPlayable,
                                isSelected = isSelected,
                                activeColor = activeColor,
                                onClick = {
                                    onSelectDieValue(steps)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("die_step_chip_${idx}_$steps")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DieChoiceOptionCard(
    dieValue: Int,
    isRemaining: Boolean,
    isPlayable: Boolean,
    isSelected: Boolean,
    activeColor: PlayerColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val playerColor = theme.playerColor(activeColor)
    val containerColor = when {
        !isRemaining -> theme.surfaceVariant
        isSelected -> theme.primaryActionBg
        isPlayable -> theme.surface
        else -> theme.surfaceVariant
    }
    val borderColor = when {
        !isRemaining -> theme.chipLine
        isSelected -> theme.primaryActionBg
        isPlayable -> playerColor
        else -> theme.chipLine
    }
    val labelColor = when {
        !isRemaining -> theme.surfaceMutedText
        isSelected -> theme.onPrimaryAction
        isPlayable -> theme.surfaceText
        else -> theme.surfaceMutedText
    }

    Surface(
        onClick = { if (isPlayable) onClick() },
        enabled = isPlayable,
        modifier = modifier
            .heightIn(min = 122.dp)
            .alpha(if (isPlayable) 1f else 0.56f),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.5.dp else 1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                DieFaceGraphic(
                    value = dieValue,
                    dieFace = theme.dieFace,
                    pipColor = theme.pip,
                    borderColor = theme.chipLine,
                    modifier = Modifier.size(52.dp)
                )
                if (!isRemaining) {
                    Surface(
                        shape = CircleShape,
                        color = theme.green,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.die_choice_used_label),
                                tint = theme.contrastingTextOn(theme.green),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = if (dieValue == 1) "1 Step" else "$dieValue Steps",
                style = MaterialTheme.typography.titleMedium,
                color = labelColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (!isRemaining) {
                Text(
                    text = stringResource(R.string.die_choice_used_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accessibleAccentOn(theme.green, containerColor),
                    fontWeight = FontWeight.Bold
                )
            } else if (!isPlayable) {
                Text(
                    text = stringResource(R.string.die_choice_no_moves_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.surfaceMutedText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DieFaceGraphic(
    value: Int,
    dieFace: Color,
    pipColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    val safeVal = value.coerceIn(1, 6)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.24f

        drawRoundRect(
            color = dieFace,
            topLeft = Offset(w * 0.04f, h * 0.04f),
            size = Size(w * 0.92f, h * 0.92f),
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(w * 0.04f, h * 0.04f),
            size = Size(w * 0.92f, h * 0.92f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.5.dp.toPx())
        )

        val pipR = w * 0.082f
        val left = w * 0.28f
        val mid = w * 0.50f
        val right = w * 0.72f
        val top = h * 0.28f
        val center = h * 0.50f
        val bottom = h * 0.72f

        val pips = when (safeVal) {
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
        pips.forEach { pos ->
            drawCircle(color = pipColor, radius = pipR, center = pos)
        }
    }
}
