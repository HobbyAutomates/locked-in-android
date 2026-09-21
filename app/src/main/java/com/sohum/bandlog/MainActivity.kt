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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
                LaunchedEffect(Unit) { updateVm.checkOnce(); if (vm.signedIn) vm.refresh() }
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
    val tabs = listOf(Tab("Home", Icons.Outlined.Home), Tab("Calendar", Icons.Outlined.CalendarMonth), Tab("Progress", Icons.Outlined.SignalCellularAlt), Tab("Settings", Icons.Outlined.Settings))

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayScreen(vm) { w -> log = LogRequest(w, Dates.today(), false) }
                    1 -> CalendarScreen(vm) { w, d -> log = LogRequest(w, d, false) }
                    2 -> ProgressScreen(vm)
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
                            Column(Modifier.clickable { tab = i }.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(t.icon, t.label, tint = if (sel) p.ink else p.muted, modifier = Modifier.size(24.dp))
                                Text(t.label, fontSize = 11.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted)
                            }
                        }
                    }
                }
                Fab(Modifier.align(Alignment.TopEnd).offset(x = (-20).dp, y = (-30).dp)) { log = LogRequest(null, Dates.today(), false) }
            }
        }

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
