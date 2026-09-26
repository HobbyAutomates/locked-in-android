package com.sohum.bandlog.ui.milestone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.motion.riseIn
import com.sohum.bandlog.ui.platform.ShareCards
import com.sohum.bandlog.ui.theme.AccentStyle
import com.sohum.bandlog.ui.theme.Brand
import com.sohum.bandlog.ui.theme.Bricolage
import com.sohum.bandlog.ui.theme.EyebrowStyle
import com.sohum.bandlog.util.Milestones

/**
 * The full-screen ember takeover for a real milestone: the eyebrow, a big Bricolage number, one
 * Fraunces line, and Share (the v2.13 share card) + Done. Ember is earned: nothing else in the app
 * paints a whole screen this colour.
 */
@Composable
fun MilestoneFlood(m: Milestones.Milestone, vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    BackHandler(onBack = onDone)
    LaunchedEffect(m.key) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val flood = rememberMotion("flood-${m.key}", 0, 700)
    val rise = rememberMotion("rise-${m.key}", 250, PremiumMotion.ENTRANCE_MS)
    val ink = Brand.Ink
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = PremiumMotion.eased(flood.value); val s = 0.96f + 0.04f * PremiumMotion.eased(flood.value); scaleX = s; scaleY = s }
            .background(Brand.Ember)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp).riseIn(rise), verticalArrangement = Arrangement.Center) {
            Text(m.eyebrow.uppercase(), style = EyebrowStyle.copy(fontSize = 13.sp), color = ink.copy(alpha = 0.7f))
            Text(
                m.big, fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = if (m.big.length <= 3) 168.sp else 120.sp,
                lineHeight = if (m.big.length <= 3) 160.sp else 118.sp, letterSpacing = (-6).sp, color = ink, modifier = Modifier.semantics { heading() },
            )
            Text(m.line, fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp, color = ink)
            Text(m.sub, style = AccentStyle, fontSize = 30.sp, lineHeight = 36.sp, color = ink, modifier = Modifier.padding(top = 10.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.weight(1f).height(56.dp).background(ink, CircleShape).clickable {
                    val unit = when (m.share) { "streak" -> "days"; "goal" -> "kg"; else -> if (m.line.startsWith("kg")) "kg" else "reps" }
                    ShareCards.share(ctx, ShareCards.Spec(eyebrow = m.eyebrow.uppercase(), big = m.big, bigUnit = unit, title = m.sub, lines = emptyList()), hideNumbers = vm.profile.hideNumbers == true)
                },
                contentAlignment = Alignment.Center,
            ) { Text("Share", fontSize = 17.sp, fontWeight = FontWeight(700), color = Brand.Bone) }
            Box(Modifier.weight(1f).height(56.dp).border(2.dp, ink, CircleShape).clickable(onClick = onDone), contentAlignment = Alignment.Center) {
                Text("Done", fontSize = 17.sp, fontWeight = FontWeight(700), color = ink)
            }
        }
    }
}
