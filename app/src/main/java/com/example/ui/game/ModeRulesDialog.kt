package com.example.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.GameModeId
import com.example.engine.MatchFormat
import com.example.engine.PowerUpType
import com.example.ui.components.CartoonButton
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.LudoTheme
import com.example.ui.theme.ludoShadow
import com.example.ui.theme.modeAccentColor
import com.example.ui.theme.powerUpAccentColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class ModeRulesSpec(
    val icon: ImageVector,
    val accent: Color,
    val tagline: String,
    val bullets: List<String>
)

private fun rulesSpecFor(mode: GameModeId, theme: LudoTheme): ModeRulesSpec {
    val accent = modeAccentColor(mode, theme)
    return when (mode) {
        GameModeId.CLASSIC -> ModeRulesSpec(
            icon = Icons.Filled.Casino,
            accent = accent,
            tagline = "The original race. Bring all 4 tokens home.",
            bullets = listOf(
                "Roll a 6 to bring a token out of base. Every 6 stacks in your roll tray and lets you roll again right away — spend stacked values in any order!",
                "Three 6s in a row cancels the entire tray and passes your turn.",
                "8 safe squares (4 Start squares + 4 Star squares) protect tokens from capture. Capturing an opponent or getting a token Home awards +1 bonus roll.",
                "No blockades: landing on stacked non-safe opponent tokens captures all of them. Enter Home with an exact roll to get all 4 tokens home!"
            )
        )
        GameModeId.BLOCKADES -> ModeRulesSpec(
            icon = Icons.Filled.Shield,
            accent = accent,
            tagline = "Pair up your tokens to build walls nobody can pass.",
            bullets = listOf(
                "All Classic core rules apply — roll stacking, safe squares, exact finish, and capture/home bonus rolls.",
                "Place 2 or more of your own tokens on the same non-safe square to form a Blockade wall.",
                "Opponents cannot land on, capture, or pass through your Blockade until one of your tokens moves away.",
                "Build strategic walls to stall rivals while bringing all 4 tokens home!"
            )
        )
        GameModeId.MASTER -> ModeRulesSpec(
            icon = Icons.Filled.LockOpen,
            accent = accent,
            tagline = "Capture first to unlock home. Merge tokens into Joint Tokens.",
            bullets = listOf(
                "Your colored home column starts LOCKED. Capture at least 1 opponent token to break the lock open — until then, tokens keep looping around the outer track.",
                "Joint Token: land two of your own tokens on the same square to merge them into a paired Joint Token (shown with a \"2\" badge).",
                "A Joint Token moves together as one unit using a single die value. Only an opponent Joint Token can capture it — single tokens cannot land on or capture it.",
                "Unlock your home lane and bring all 4 tokens home to win!"
            )
        )
        GameModeId.QUICK -> ModeRulesSpec(
            icon = Icons.Filled.Bolt,
            accent = accent,
            tagline = "Start with a token out. First token home wins.",
            bullets = listOf(
                "Every player begins with 1 token already out on their Start square and 3 waiting in base.",
                "No capture requirement — race straight for your home column right away.",
                "The very first player to bring ANY 1 token into Home wins immediately!",
                "Standard roll stacking, triple-6 rule, safe squares, and exact finish all apply."
            )
        )
        GameModeId.ARROW -> ModeRulesSpec(
            icon = Icons.Filled.FastForward,
            accent = accent,
            tagline = "Land on arrows to shoot ahead.",
            bullets = listOf(
                "4 glowing curved shortcut arrows sit on the outer track, each leaping 6 squares forward.",
                "Land EXACTLY on an arrow tail to slide straight along the arrow to its tip!",
                "If opponent tokens sit on the arrow's tip (non-safe square), sliding onto them captures them and grants a bonus roll.",
                "Passing over an arrow tail does nothing — aim your stacked dice carefully!"
            )
        )
        GameModeId.MAGIC -> ModeRulesSpec(
            icon = Icons.Filled.AutoAwesome,
            accent = accent,
            tagline = "Grab Rockets, Golden Dice, Shields and Bombs.",
            bullets = listOf(
                "Magic items spawn on random empty non-safe squares every 2 rounds (max 3 on the board) and trigger instantly on pickup.",
                "Rocket: blasts that token forward a random 1–8 squares (stops before overshooting Home).",
                "Golden Dice: guarantees your very next dice roll is a 6!",
                "Shield: protects that token in a bubble from capture for your next 2 turns.",
                "Bomb Trap: pick any empty non-safe square within 6 steps ahead to hide a bomb for 3 rounds — the next opponent to land on it blasts back to base!"
            )
        )
        GameModeId.TIMED -> ModeRulesSpec(
            icon = Icons.Filled.Timer,
            accent = accent,
            tagline = "Score the most points before the clock hits 00:00.",
            bullets = listOf(
                "Play against a 5-minute or 10-minute match countdown using all core roll-stacking and bonus-roll rules.",
                "Earn +1 point per square moved, +10 for each capture, −5 when captured, and +25 for each token reaching Home.",
                "When the match clock expires, the highest-scoring player (or combined team in 2v2) wins!",
                "Bringing all 4 tokens home before the clock runs out wins the match immediately."
            )
        )
        GameModeId.CHAOS -> ModeRulesSpec(
            icon = Icons.Filled.Whatshot,
            accent = accent,
            tagline = "Wild board events shake up the track every 3 rounds.",
            bullets = listOf(
                "Every 3 full rounds, a random Chaos Event alters the entire board.",
                "Moving Stars relocate safe squares; Linked Portals warp any token that lands on one to its twin portal.",
                "Dice Storm doubles every player's next roll (up to 12) for explosive dashes across the board.",
                "Earthquake pushes a random token back 3 squares, while Lucky Wind boosts home-column tokens +1 step!"
            )
        )
        GameModeId.ELIMINATION -> ModeRulesSpec(
            icon = Icons.Filled.Gavel,
            accent = accent,
            tagline = "Stay ahead or get knocked out every 5 rounds.",
            bullets = listOf(
                "Every 5 full rounds, the active player with the lowest combined token progress is eliminated from the match.",
                "Eliminated players' tokens are removed from the board and the survivors keep racing.",
                "Uses all core roll-stacking, bonus-roll, safe-square, and exact-finish rules.",
                "Be the last player standing — or the first to bring all 4 tokens home — to claim victory!"
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeRulesDialog(
    mode: GameModeId,
    matchFormat: MatchFormat? = null,
    onDismiss: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val spec = rulesSpecFor(mode, theme)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val readableAccent = theme.accessibleAccentOn(spec.accent, theme.surface)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.surface,
        contentColor = theme.surfaceText,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("mode_rules_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = lerp(theme.surface, spec.accent, if (theme.isDarkSurface) 0.16f else 0.10f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModeRulesIllustration(
                        mode = mode,
                        accent = spec.accent,
                        modifier = Modifier.size(64.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "How to play ${mode.title}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = theme.surfaceText,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = spec.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.surfaceMutedText
                        )
                        if (matchFormat != null) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = spec.accent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Format: ${matchFormat.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = readableAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                spec.bullets.forEachIndexed { idx, bullet ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = lerp(theme.surface, spec.accent, if (theme.isDarkSurface) 0.12f else 0.07f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(spec.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.contrastingTextOn(spec.accent),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.surfaceText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (matchFormat == MatchFormat.TEAMS_2V2) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = lerp(theme.surface, theme.yellow, if (theme.isDarkSurface) 0.18f else 0.16f)
                ) {
                    Text(
                        text = "2v2 Team Rule: Opposite corners are partners and never capture each other. Once your 4 tokens are home, keep rolling to move your partner's tokens!",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.surfaceText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            CartoonButton(
                text = stringResource(R.string.powerup_guide_got_it),
                onClick = onDismiss,
                tag = "mode_rules_dismiss_button",
                icon = Icons.Filled.Check,
                containerColor = theme.primaryActionBg,
                contentColor = theme.onPrimaryAction,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ModeRulesIllustration(
    mode: GameModeId,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current

    Surface(
        modifier = modifier.ludoShadow(theme.cardShadow, 18.dp),
        shape = RoundedCornerShape(18.dp),
        color = theme.surface
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            when (mode) {
                GameModeId.CLASSIC -> {
                    drawCircle(color = accent.copy(alpha = 0.16f), radius = w * 0.36f, center = Offset(cx, cy))
                    drawMiniStar(Offset(cx, cy), w * 0.28f, w * 0.13f, theme.crownIcon)
                    drawCircle(color = theme.red, radius = w * 0.10f, center = Offset(w * 0.24f, h * 0.26f))
                    drawCircle(color = theme.green, radius = w * 0.10f, center = Offset(w * 0.76f, h * 0.26f))
                    drawCircle(color = theme.yellow, radius = w * 0.10f, center = Offset(w * 0.76f, h * 0.74f))
                    drawCircle(color = theme.blue, radius = w * 0.10f, center = Offset(w * 0.24f, h * 0.74f))
                }
                GameModeId.BLOCKADES -> {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(w * 0.14f, h * 0.62f),
                        size = Size(w * 0.72f, h * 0.20f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawCircle(color = theme.red, radius = w * 0.16f, center = Offset(w * 0.36f, h * 0.42f))
                    drawCircle(color = theme.tokenEdge, radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.42f), style = Stroke(2.5f))
                    drawCircle(color = theme.red, radius = w * 0.16f, center = Offset(w * 0.64f, h * 0.42f))
                    drawCircle(color = theme.tokenEdge, radius = w * 0.08f, center = Offset(w * 0.64f, h * 0.42f), style = Stroke(2.5f))
                }
                GameModeId.MASTER -> {
                    drawCircle(color = accent.copy(alpha = 0.16f), radius = w * 0.38f, center = Offset(cx, cy))
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(cx - w * 0.18f, cy - h * 0.02f),
                        size = Size(w * 0.36f, h * 0.28f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawArc(
                        color = theme.surfaceText,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - w * 0.12f, cy - h * 0.24f),
                        size = Size(w * 0.24f, h * 0.28f),
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                }
                GameModeId.QUICK -> {
                    val bolt = Path().apply {
                        moveTo(w * 0.56f, h * 0.14f)
                        lineTo(w * 0.28f, h * 0.52f)
                        lineTo(w * 0.48f, h * 0.52f)
                        lineTo(w * 0.40f, h * 0.86f)
                        lineTo(w * 0.72f, h * 0.44f)
                        lineTo(w * 0.52f, h * 0.44f)
                        close()
                    }
                    drawPath(bolt, color = accent)
                }
                GameModeId.ARROW -> {
                    val curve = Path().apply {
                        moveTo(w * 0.18f, h * 0.72f)
                        quadraticTo(w * 0.32f, h * 0.18f, w * 0.78f, h * 0.30f)
                    }
                    drawPath(curve, color = accent, style = Stroke(width = 7f, cap = StrokeCap.Round))
                    val head = Path().apply {
                        moveTo(w * 0.64f, h * 0.18f)
                        lineTo(w * 0.82f, h * 0.30f)
                        lineTo(w * 0.66f, h * 0.44f)
                    }
                    drawPath(head, color = accent, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                GameModeId.MAGIC -> {
                    drawCircle(color = powerUpAccentColor(PowerUpType.ROCKET, theme), radius = w * 0.15f, center = Offset(w * 0.30f, h * 0.30f))
                    drawCircle(color = powerUpAccentColor(PowerUpType.GOLDEN_DICE, theme), radius = w * 0.15f, center = Offset(w * 0.70f, h * 0.30f))
                    drawCircle(color = powerUpAccentColor(PowerUpType.SHIELD, theme), radius = w * 0.15f, center = Offset(w * 0.30f, h * 0.70f))
                    drawCircle(color = powerUpAccentColor(PowerUpType.BOMB_TRAP, theme), radius = w * 0.15f, center = Offset(w * 0.70f, h * 0.70f))
                }
                GameModeId.TIMED -> {
                    drawCircle(color = accent.copy(alpha = 0.16f), radius = w * 0.34f, center = Offset(cx, cy + 3f))
                    drawCircle(color = accent, radius = w * 0.30f, center = Offset(cx, cy + 3f), style = Stroke(5f))
                    drawLine(color = theme.surfaceText, start = Offset(cx, cy + 3f), end = Offset(cx, cy - h * 0.16f), strokeWidth = 4.5f, cap = StrokeCap.Round)
                    drawLine(color = theme.red, start = Offset(cx, cy + 3f), end = Offset(cx + w * 0.16f, cy + 3f), strokeWidth = 4f, cap = StrokeCap.Round)
                }
                GameModeId.CHAOS -> {
                    val violet = lerp(theme.blue, theme.red, 0.48f)
                    drawCircle(color = violet.copy(alpha = 0.22f), radius = w * 0.36f, center = Offset(cx, cy))
                    drawCircle(color = theme.blue, radius = w * 0.28f, center = Offset(cx, cy), style = Stroke(5.5f))
                    drawArc(
                        color = theme.red,
                        startAngle = 20f,
                        sweepAngle = 200f,
                        useCenter = false,
                        topLeft = Offset(cx - w * 0.18f, cy - h * 0.18f),
                        size = Size(w * 0.36f, h * 0.36f),
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                }
                GameModeId.ELIMINATION -> {
                    drawCircle(color = accent.copy(alpha = 0.16f), radius = w * 0.36f, center = Offset(cx, cy))
                    drawCircle(color = accent, radius = w * 0.28f, center = Offset(cx, cy), style = Stroke(5f))
                    drawLine(color = theme.red, start = Offset(w * 0.34f, h * 0.34f), end = Offset(w * 0.66f, h * 0.66f), strokeWidth = 5.5f, cap = StrokeCap.Round)
                    drawLine(color = theme.red, start = Offset(w * 0.66f, h * 0.34f), end = Offset(w * 0.34f, h * 0.66f), strokeWidth = 5.5f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.drawMiniStar(
    center: Offset,
    outerR: Float,
    innerR: Float,
    color: Color
) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = (i * PI / 5.0) - (PI / 2.0)
        val px = center.x + (cos(angle) * r).toFloat()
        val py = center.y + (sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = color)
}
