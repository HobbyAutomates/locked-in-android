package com.sohum.bandlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.UpdateDialog
import com.sohum.bandlog.ui.UpdateViewModel
import com.sohum.bandlog.ui.calendar.CalendarScreen
import com.sohum.bandlog.ui.login.LoginScreen
import com.sohum.bandlog.ui.progress.ProgressScreen
import com.sohum.bandlog.ui.settings.SettingsScreen
import com.sohum.bandlog.ui.theme.BandLogTheme
import com.sohum.bandlog.ui.today.TodayScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        enableEdgeToEdge()
        setContent {
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            BandLogTheme {
                val vm: AppViewModel = viewModel()
                val updateVm: UpdateViewModel = viewModel()
                LaunchedEffect(Unit) { updateVm.checkOnce(); if (vm.signedIn) vm.refresh() }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!vm.signedIn) LoginScreen(onSignedIn = { vm.onSignedIn() })
                    else MainScaffold(vm, updateVm)
                }
                UpdateDialog(updateVm)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(vm: AppViewModel, updateVm: UpdateViewModel) {
    val cs = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Tab("Today", Icons.Outlined.Today),
        Tab("Calendar", Icons.Outlined.CalendarMonth),
        Tab("Progress", Icons.Outlined.ShowChart),
        Tab("Settings", Icons.Outlined.Settings),
    )
    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            NavigationBar(containerColor = cs.surface) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = cs.primary, selectedTextColor = cs.primary,
                    indicatorColor = cs.primary.copy(alpha = 0.14f),
                    unselectedIconColor = cs.onSurfaceVariant, unselectedTextColor = cs.onSurfaceVariant,
                )
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, colors = colors, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            when (tab) {
                0 -> TodayScreen(vm)
                1 -> CalendarScreen(vm)
                2 -> ProgressScreen(vm)
                else -> SettingsScreen(vm, updateVm)
            }
        }
    }
}
