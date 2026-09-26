package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Pro

/**
 * v2.13 Locked In Pro (spec §1): benefits, "₹700 / month", the beta banner, and a disabled
 * "You're in the beta" button (payments aren't enabled).
 */
@Composable
fun ProScreen(pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    val accent = accentColor
    LaunchedEffect(Unit) { pvm.loadPlan() }
    val cfg = pvm.proConfig
    val plan = pvm.extras?.plan ?: Pro.BETA
    SubPage("Locked In Pro", onBack) {
        MotionScreen {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Entrance(0, key = "pro-hero") {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(64.dp).border(1.5.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(LineIcons.Crown, null, tint = accent, modifier = Modifier.size(30.dp))
                        }
                        Text("Locked In Pro", fontSize = 28.sp, fontWeight = FontWeight(700), letterSpacing = (-0.8).sp, color = p.ink)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("₹${cfg.priceInr}", fontSize = 34.sp, fontWeight = FontWeight(400), letterSpacing = (-1).sp, color = p.ink)
                            Text(" / ${cfg.period}", fontSize = 16.sp, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                }
                Entrance(1, key = "pro-beta") {
                    Row(
                        Modifier.fillMaxWidth().background(accent.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(LineIcons.Star, null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Beta tester: every Pro feature is free for you", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                    }
                }
                Entrance(2, key = "pro-list") {
                    Card {
                        Text("What's included", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(8.dp))
                        Pro.BENEFITS.forEach { (_, line) ->
                            Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                                Icon(CheckIcon, null, tint = accent, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(line, fontSize = 15.sp, color = p.ink, lineHeight = 20.sp)
                            }
                        }
                    }
                }
                Entrance(3, key = "pro-free") {
                    Card {
                        Text("Always free", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        Text("Logging, photo / barcode / label scans, squads, water, progress basics, body measurements and progress photos.", fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
                    }
                }
                Entrance(4, key = "pro-button") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PillButton(
                            Pro.buttonLabel(plan, pvm.hasPro, cfg), {}, enabled = Pro.buttonEnabled(pvm.hasPro, cfg),
                            bg = accent, fg = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Payments aren't switched on yet. You won't be charged.", fontSize = 12.sp, color = p.muted)
                    }
                }
            }
        }
    }
}
