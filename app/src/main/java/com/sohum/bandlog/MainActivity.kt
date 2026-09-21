package com.sohum.bandlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.UpdateDialog
import com.sohum.bandlog.ui.UpdateViewModel
import com.sohum.bandlog.ui.calendar.CalendarScreen
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.log.LogScreen
import com.sohum.bandlog.ui.login.LoginScreen
import com.sohum.bandlog.ui.progress.ProgressScreen
import com.sohum.bandlog.ui.settings.SettingsScreen
import com.sohum.bandlog.ui.theme.BandLogTheme
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.TodayScreen
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.ThemeMode
import com.sohum.bandlog.util.ThemePrefs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.get(this)) }
            val dark = when (themeMode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.AUTO -> isSystemInDarkTheme() }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            }
            BandLogTheme(dark = dark) {
                val vm: AppViewModel = viewModel()
                val updateVm: UpdateViewModel = viewModel()
                LaunchedEffect(Unit) { updateVm.checkOnce(); vm.addBurnedBack = ThemePrefs.burned(this@MainActivity); if (vm.signedIn) { vm.refresh(); vm.refreshHealth(this@MainActivity) } }
                Surface(Modifier.fillMaxSize(), color = palette.bg) {
                    if (!vm.signedIn) LoginScreen(onSignedIn = { vm.onSignedIn() })
                    else MainShell(vm, updateVm, themeMode) { themeMode = it; ThemePrefs.set(this, it) }
                }
                UpdateDialog(updateVm)
            }
        }
    }
}

/** What the Log page was opened with. */
private data class LogRequest(val workout: Workout?, val date: String, val meal: Boolean)

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun MainShell(vm: AppViewModel, updateVm: UpdateViewModel, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val p = palette
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var log by remember { mutableStateOf<LogRequest?>(null) }
    val tabs = listOf(Tab("Home", Icons.Outlined.Home), Tab("Calendar", Icons.Outlined.CalendarMonth), Tab("Scan", com.sohum.bandlog.ui.components.ScanIcon), Tab("Progress", Icons.Outlined.SignalCellularAlt), Tab("Settings", Icons.Outlined.Settings))

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayScreen(vm) { w -> log = LogRequest(w, Dates.today(), false) }
                    1 -> CalendarScreen(vm) { w, d -> log = LogRequest(w, d, false) }
                    2 -> com.sohum.bandlog.ui.scan.ScanTab()
                    3 -> ProgressScreen(vm)
                    else -> SettingsScreen(vm, updateVm, themeMode, onThemeMode)
                }
            }
        }
        // Bottom bar + FAB, overlaid so screens scroll under it.
        Column(Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().background(p.card)) {
                    Hair()
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 76.dp, top = 10.dp).navigationBarsPadding().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        tabs.forEachIndexed { i, t ->
                            val sel = tab == i
                            Column(Modifier.clickable { tab = i }.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(t.icon, t.label, tint = if (sel) p.ink else p.muted, modifier = Modifier.size(24.dp))
                                Text(t.label, fontSize = 11.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted)
                            }
                        }
                    }
                }
                Fab(Modifier.align(Alignment.TopEnd).offset(x = (-20).dp, y = (-30).dp)) { log = LogRequest(null, Dates.today(), false) }
            }
        }

        vm.celebrate?.let { c -> CelebrationModal(c) { vm.dismissCelebration() } }

        AnimatedContent(
            targetState = log, label = "log",
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
        ) { req ->
            if (req != null) {
                BackHandler { log = null }
                LogScreen(vm, req.workout, req.date, req.meal, onClose = { log = null })
            }
        }
    }
}

/** Cal AI-style streak celebration after a saved session: flame, count, this week's dots. */
@Composable
private fun CelebrationModal(c: AppViewModel.Celebration, onDismiss: () -> Unit) {
    val p = palette
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(p.card, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                com.sohum.bandlog.ui.components.Flame(p.flame, 96.dp)
                Text("${c.thisWeek}", fontSize = 26.sp, fontWeight = FontWeight(800), color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(top = 18.dp))
            }
            Text(
                if (c.hitTarget) "Week target hit!" else "Session ${c.thisWeek} of ${c.target}",
                fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.flame, modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                if (c.hitTarget) "${c.streakWeeks}-week streak. You're on fire — keep it rolling." else "${c.target - c.thisWeek} more this week keeps the ${c.streakWeeks}-week streak alive.",
                fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(c.target) { i ->
                    Box(Modifier.size(14.dp).let { m -> if (i < c.thisWeek) m.background(p.flame, CircleShape) else m.border(2.dp, p.flame, CircleShape) })
                }
            }
            Spacer(Modifier.height(20.dp))
            com.sohum.bandlog.ui.components.PillButton("Continue", onDismiss)
        }
    }
}

@Composable
private fun Fab(modifier: Modifier, onClick: () -> Unit) {
    val p = palette
    val t = rememberInfiniteTransition(label = "fab")
    val glow by t.animateFloat(10f, 16f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "fabGlow")
    Box(
        modifier.size(60.dp).shadow(glow.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.btn, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, "Log", tint = p.btnInk, modifier = Modifier.size(28.dp)) }
}
