package com.glycomate.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.glycomate.app.R
import com.glycomate.app.gamification.*
import com.glycomate.app.ui.theme.*
import com.glycomate.app.viewmodel.GlycoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddyScreen(viewModel: GlycoViewModel) {
    val gamState by viewModel.gamificationState.collectAsState()
    val buddyMsg by produceState(initialValue = BuddyMessage(R.string.buddy_greeting, mood = BuddyMood.HAPPY)) {
        value = viewModel.gamif.getBuddyMessage()
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    BuddyAvatar(
                        mood        = gamState.buddyMood,
                        accessories = gamState.buddyAccessories
                    )

                    Text(stringResource(R.string.buddy_name), style = MaterialTheme.typography.headlineSmall
                        .copy(fontWeight = FontWeight.W700))

                    val (moodRes, moodColor) = when (gamState.buddyMood) {
                        BuddyMood.VERY_HAPPY -> R.string.mood_very_happy to GlycoGreen
                        BuddyMood.HAPPY      -> R.string.mood_happy to GlycoGreen
                        BuddyMood.NEUTRAL    -> R.string.mood_neutral to GlycoAmber
                        BuddyMood.SAD        -> R.string.mood_sad to GlycoAmber
                        BuddyMood.WORRIED    -> R.string.mood_worried to GlycoRed
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = moodColor.copy(alpha = 0.15f)) {
                        Text(stringResource(moodRes), style = MaterialTheme.typography.labelLarge, color = moodColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }

                    Surface(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        val msgText = if (buddyMsg.args.isEmpty()) {
                            stringResource(buddyMsg.resId)
                        } else {
                            stringResource(buddyMsg.resId, *buddyMsg.args.toTypedArray())
                        }
                        Text(msgText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp))
                    }
                }
            }
        }

        item {
            XpLevelCard(state = gamState)
        }

        item {
            StreakCard(streakDays = gamState.streakDays)
        }

        item {
            Text(stringResource(R.string.badges_title), style = MaterialTheme.typography.labelSmall
                .copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            BadgesGrid(state = gamState)
        }
    }
}

@Composable
private fun BuddyAvatar(mood: BuddyMood, accessories: Set<String>) {
    val scale by rememberInfiniteTransition(label = "buddy").animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale")

    val emoji = when (mood) {
        BuddyMood.VERY_HAPPY -> "🦸"
        BuddyMood.HAPPY      -> "🦸"
        BuddyMood.NEUTRAL    -> "🤔"
        BuddyMood.SAD        -> "😔"
        BuddyMood.WORRIED    -> "😰"
    }

    Box(contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.size(100.dp).scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 52.sp)
            }
        }
        if ("crown" in accessories) {
            Text("👑", fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-4).dp))
        } else if ("hat" in accessories) {
            Text("🎩", fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-2).dp))
        }
        if ("cape" in accessories) {
            Text("🦸", fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun XpLevelCard(state: GamificationState) {
    val progress by animateFloatAsState(
        targetValue = state.progressFraction,
        animationSpec = tween(800),
        label = "xp_progress")

    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = GlycoPurple.copy(alpha = 0.15f)) {
                        Text(stringResource(R.string.level_label, state.level, stringResource(state.levelTitleRes)),
                            style = MaterialTheme.typography.labelLarge,
                            color = GlycoPurple,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                    Text(stringResource(R.string.xp_label, state.xp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))
                }
                if (state.streakDays > 0) {
                    Text(stringResource(R.string.streak_days_label, state.streakDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = GlycoAmber)
                }
            }

            LinearProgressIndicator(
                progress      = { progress },
                modifier      = Modifier.fillMaxWidth().height(8.dp),
                color         = GlycoPurple,
                trackColor    = MaterialTheme.colorScheme.surfaceVariant)

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.xp_progress, state.xpProgressInLevel, state.xpNeededForNextLevel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.next_level, state.level + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = GlycoPurple)
            }
        }
    }
}

@Composable
private fun StreakCard(streakDays: Int) {
    Card(modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.streak_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600))
                Text(stringResource(R.string.streak_days_label, streakDays),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (streakDays > 0) GlycoAmber else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val displayDays = 14
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(displayDays) { i ->
                    Surface(modifier = Modifier.weight(1f).height(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            i < streakDays  -> GlycoGreen
                            i == streakDays -> MaterialTheme.colorScheme.primary
                            else            -> MaterialTheme.colorScheme.surfaceVariant
                        }) {}
                }
            }
            val nextMilestone = listOf(7, 14, 30).firstOrNull { it > streakDays }
            if (nextMilestone != null) {
                Text(stringResource(R.string.next_milestone, nextMilestone - streakDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.legendary_streak),
                    style = MaterialTheme.typography.labelSmall, color = GlycoAmber)
            }
        }
    }
}

@Composable
private fun BadgesGrid(state: GamificationState) {
    val allBadges = ALL_BADGES

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val earned = allBadges.filter { it.id in state.earnedBadgeIds }
        if (earned.isNotEmpty()) {
            Text(stringResource(R.string.earned_badges, earned.size),
                style = MaterialTheme.typography.labelSmall,
                color = GlycoAmber)
            BadgeRow(badges = earned, earned = true)
        }

        val unearned = allBadges.filter { it.id !in state.earnedBadgeIds }
        if (unearned.isNotEmpty()) {
            Text(stringResource(R.string.locked_badges, unearned.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            BadgeRow(badges = unearned, earned = false)
        }
    }
}

@Composable
private fun BadgeRow(badges: List<Badge>, earned: Boolean) {
    val rows = badges.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { badge ->
                    BadgeItem(badge = badge, earned = earned, modifier = Modifier.weight(1f))
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BadgeItem(badge: Badge, earned: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (earned) GlycoAmber.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(badge.emoji, fontSize = 24.sp)
            Text(stringResource(badge.nameRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (earned) GlycoAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2)
            if (earned) {
                Text(stringResource(R.string.xp_reward, badge.xpReward),
                    style = MaterialTheme.typography.labelSmall,
                    color = GlycoPurple)
            }
        }
    }
}
