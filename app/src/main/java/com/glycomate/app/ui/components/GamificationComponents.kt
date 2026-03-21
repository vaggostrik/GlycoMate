package com.glycomate.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glycomate.app.gamification.Badge
import com.glycomate.app.ui.theme.*
import kotlinx.coroutines.delay

// ── XP Gain Toast ─────────────────────────────────────────────────────────────
@Composable
fun XpToast(amount: Int, reason: String, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { delay(2500); onDismiss() }
    AnimatedVisibility(
        visible = true,
        enter   = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit    = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 32.dp),
            shape    = RoundedCornerShape(24.dp),
            color    = GlycoPurple,
            tonalElevation = 4.dp
        ) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 18.sp)
                Column {
                    Text("+$amount XP",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                        color = MaterialTheme.colorScheme.onPrimary)
                    Text(reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ── Badge Unlocked Dialog ─────────────────────────────────────────────────────
@Composable
fun BadgeUnlockedDialog(badge: Badge, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                Text(badge.emoji, fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text("Νέο Badge!", style = MaterialTheme.typography.titleLarge
                    .copy(fontWeight = FontWeight.W700), color = GlycoAmber)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(badge.nameRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600))
                Text(stringResource(badge.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = GlycoPurple.copy(alpha = 0.15f)) {
                    Text("+${badge.xpReward} XP",
                        style = MaterialTheme.typography.labelLarge, color = GlycoPurple,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GlycoAmber)) {
                Text("Τέλεια! 🎉")
            }
        }
    )
}

// ── Level Up Dialog ───────────────────────────────────────────────────────────
@Composable
fun LevelUpDialog(level: Int, titleRes: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                Text("🎊", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text("Level Up!", style = MaterialTheme.typography.titleLarge
                    .copy(fontWeight = FontWeight.W700), color = GlycoGreen)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Level $level",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W700),
                    color = GlycoPurple)
                Text(stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GlycoGreen)) {
                Text("Συνέχεια! 💪")
            }
        }
    )
}

// ── Mini XP bar shown on Dashboard ───────────────────────────────────────────
@Composable
fun MiniXpBar(xp: Int, level: Int, progressFraction: Float, streakDays: Int) {
    val animProg by animateFloatAsState(progressFraction, animationSpec = tween(600), label = "xp")
    Surface(shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(20.dp), color = GlycoPurple.copy(alpha = 0.15f)) {
                    Text("Level $level  •  $xp XP",
                        style = MaterialTheme.typography.labelMedium, color = GlycoPurple,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (streakDays > 0)
                    Text("🔥 $streakDays", style = MaterialTheme.typography.labelMedium,
                        color = GlycoAmber)
            }
            LinearProgressIndicator(
                progress   = { animProg },
                modifier   = Modifier.fillMaxWidth().height(6.dp),
                color      = GlycoPurple,
                trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
