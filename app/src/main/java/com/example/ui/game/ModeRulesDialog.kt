package com.example.ui.game

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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.engine.GameModeId
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.SunnyGoldDark

private data class ModeRulesSpec(
    val icon: ImageVector,
    val accent: Color,
    val tagline: String,
    val bullets: List<String>
)

private fun rulesSpecFor(mode: GameModeId): ModeRulesSpec = when (mode) {
    GameModeId.CLASSIC -> ModeRulesSpec(
        icon = Icons.Filled.Casino,
        accent = CobaltPrimary,
        tagline = "Timeless 15×15 Ludo rules.",
        bullets = listOf(
            "Roll a 6 to bring a token out of your base onto your Start square.",
            "8 safe Star squares protect tokens from capture. Capturing does not grant an extra roll.",
            "Rolling a 6 gives a bonus roll, but three 6s in a row cancels your turn.",
            "Enter the center Home triangle with an exact roll. First to get all 4 tokens home wins!"
        )
    )
    GameModeId.QUICK -> ModeRulesSpec(
        icon = Icons.Filled.Bolt,
        accent = Color(0xFF00A878),
        tagline = "Jump straight into the action!",
        bullets = listOf(
            "All 4 tokens start right on your colored Start square — no 6 needed to begin.",
            "Race clockwise around the board and into your home column as fast as possible.",
            "All standard safe stars, captures, bonus 6s, and triple-6 rules apply."
        )
    )
    GameModeId.MASTER -> ModeRulesSpec(
        icon = Icons.Filled.LockOpen,
        accent = Color(0xFFE63946),
        tagline = "Earn your entry into the home lane!",
        bullets = listOf(
            "Your colored home column starts locked (🔒).",
            "Capture at least 1 opponent token to unlock your home column for all your tokens.",
            "Until your first capture, your tokens keep looping around the outer track."
        )
    )
    GameModeId.POWER_UP -> ModeRulesSpec(
        icon = Icons.Filled.AutoAwesome,
        accent = Color(0xFF8338EC),
        tagline = "Collect glowing board tiles and outplay rivals!",
        bullets = listOf(
            "3 power-up tiles sit on the track. Land on one to hold up to 2 power-ups.",
            "Tap a held power-up next to your avatar before rolling to activate it."
        )
    )
    GameModeId.TIMED -> ModeRulesSpec(
        icon = Icons.Filled.Timer,
        accent = CobaltPrimary,
        tagline = "Score the most points before the clock hits 00:00!",
        bullets = listOf(
            "+1 point per square moved, +10 per capture, −5 when captured, and +25 per token home.",
            "Highest score wins when time expires.",
            "Get all 4 tokens home before the clock runs out to win immediately!"
        )
    )
    GameModeId.TEAMS_2V2 -> ModeRulesSpec(
        icon = Icons.Filled.Groups,
        accent = SunnyGoldDark,
        tagline = "Red & Yellow vs Green & Blue!",
        bullets = listOf(
            "Partners sit in opposite corners, share squares safely, and never capture each other.",
            "Once you finish all 4 of your own tokens, you keep rolling to move your partner's tokens!",
            "First team to get all 8 combined tokens home wins!"
        )
    )
    GameModeId.ONE_TOKEN_RACE -> ModeRulesSpec(
        icon = Icons.Filled.Flag,
        accent = Color(0xFFFF6B35),
        tagline = "Lightning-fast sprint to the center!",
        bullets = listOf(
            "All Classic Ludo rules apply — including safe stars, captures, and exact rolls.",
            "The very first player to get ANY single token into the center Home triangle wins instantly!"
        )
    )
    GameModeId.CHAOS -> ModeRulesSpec(
        icon = Icons.Filled.Whatshot,
        accent = CoralPink,
        tagline = "Expect the unexpected every 3 full rounds!",
        bullets = listOf(
            "Moving Stars relocate safe squares; Linked Portals warp tokens across the track.",
            "Dice Storm doubles every player's next roll (up to 12)!",
            "Earthquake pushes a random token back 3 squares; Lucky Wind boosts home-column tokens +1."
        )
    )
    GameModeId.ELIMINATION -> ModeRulesSpec(
        icon = Icons.Filled.Gavel,
        accent = Color(0xFFB5179E),
        tagline = "Stay ahead or get knocked out!",
        bullets = listOf(
            "Every 5 full rounds, the player with the lowest total token progress is eliminated.",
            "Eliminated players' tokens leave the board and play continues.",
            "Be the last player standing — or the first to get all 4 tokens home — to win!"
        )
    )
}

/**
 * Short mode rules popup shown automatically the first time a player plays any mode,
 * and reopenable anytime via the "?" button on the game board.
 */
@Composable
fun ModeRulesDialog(
    mode: GameModeId,
    onDismiss: () -> Unit
) {
    if (mode == GameModeId.POWER_UP) {
        PowerUpGuideDialog(onDismiss = onDismiss)
        return
    }

    val spec = rulesSpecFor(mode)
    Dialog(onDismissRequest = onDismiss) {
        CartoonCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mode_rules_dialog"),
            borderColor = spec.accent,
            containerColor = Color.White,
            cornerRadius = 28.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(spec.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = spec.accent,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "${mode.title} Rules",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkDark,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = spec.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spec.bullets.forEachIndexed { idx, bullet ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = spec.accent.copy(alpha = 0.08f)
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
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkDark,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                CartoonButton(
                    text = stringResource(R.string.powerup_guide_got_it),
                    onClick = onDismiss,
                    tag = "mode_rules_dismiss_button",
                    icon = Icons.Filled.Check,
                    containerColor = MintGreen,
                    lipColor = MintGreenDark,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
