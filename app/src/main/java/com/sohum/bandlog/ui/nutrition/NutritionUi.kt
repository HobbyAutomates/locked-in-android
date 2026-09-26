package com.sohum.bandlog.ui.nutrition

import com.sohum.bandlog.ui.components.hatchTrack

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.lucide
import com.sohum.bandlog.ui.motion.growFromLeft
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.theme.palette

/** v2.13 nutrition line icons (Lucide shapes, 24 grid, same helper as the rest of the app). */
object NutritionIcons {
    val Timer: ImageVector by lazy { lucide("Timer", listOf("M10 2h4", "M12 14l3-3", "M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16z")) }
    val ChefHat: ImageVector by lazy { lucide("ChefHat", listOf("M6 13.87A4 4 0 0 1 7.41 6a5.11 5.11 0 0 1 1.05-1.54 5 5 0 0 1 7.08 0A5.11 5.11 0 0 1 16.59 6 4 4 0 0 1 18 13.87V21H6z", "M6 17h12")) }
    val Leaf: ImageVector by lazy { lucide("Leaf", listOf("M11 20A7 7 0 0 1 4 13c0-6 6-9 16-9 0 10-3 16-9 16z", "M4 21c3-6 6-8 10-10")) }
    val Sparkles: ImageVector by lazy { lucide("Sparkles", listOf("M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z", "M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9z")) }
    val Menu: ImageVector by lazy { lucide("MenuBook", listOf("M2 4h7a3 3 0 0 1 3 3v14a2 2 0 0 0-2-2H2z", "M22 4h-7a3 3 0 0 0-3 3v14a2 2 0 0 1 2-2h8z")) }
    val Move: ImageVector by lazy { lucide("Move", listOf("M5 9l-3 3 3 3", "M9 5l3-3 3 3", "M15 19l-3 3-3-3", "M19 9l3 3-3 3", "M2 12h20", "M12 2v20")) }
    val More: ImageVector by lazy { lucide("More", listOf("M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z", "M12 6a1 1 0 1 0 0-2 1 1 0 0 0 0 2z", "M12 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z")) }
    val Flash: ImageVector by lazy { lucide("Flash", listOf("M13 2L3 14h9l-1 8 10-12h-9z")) }
    val FlashOff: ImageVector by lazy { lucide("FlashOff", listOf("M12.4 6.6L13 2l-3.2 3.9", "M18.3 12H21l-2.4 2.9", "M16 16l-5 6 1-8H3l3.8-4.6", "M2 2l20 20")) }
    val Trend: ImageVector by lazy { lucide("Trend", listOf("M22 7l-8.5 8.5-5-5L2 17", "M16 7h6v6")) }
    val Pill: ImageVector by lazy { lucide("Pill", listOf("M10.5 20.5a7.07 7.07 0 0 1-10-10l10-10a7.07 7.07 0 0 1 10 10z", "M8.5 8.5l7 7")) }
}

/** "Coming with the next update": what a v2.13 feature shows while schema_v36 / the web route isn't live. */
@Composable
fun ComingSoonCard(title: String, body: String = "This needs a server update that's on its way. Everything else works as usual.") {
    val p = palette
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NutritionIcons.Sparkles, null, tint = p.muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(NotYetAvailable.COMING, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.orange)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
    }
}

/** A thin progress bar that grows in from the left (m-growx), with an optional marker at [marker] (0..1). */
@Composable
fun GrowBar(fraction: Float, color: Color, key: String, modifier: Modifier = Modifier, height: Int = 8, marker: Float? = null) {
    val p = palette
    val t = rememberMotion("bar-$key", 200, 1200)
    Box(modifier.fillMaxWidth().height(height.dp).hatchTrack(CircleShape)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height.dp).growFromLeft(t).background(color, CircleShape))
        if (marker != null) Box(Modifier.fillMaxWidth(marker.coerceIn(0f, 1f)).height(height.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.width(2.dp).height(height.dp + 6.dp).background(p.ink.copy(alpha = 0.55f)))
        }
    }
}

/** A tiny rounded label: "PICK", "High", "Low this week". */
@Composable
fun Tag(text: String, fg: Color, bg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight(700), color = fg, maxLines = 1)
    }
}

@Composable
fun confidenceColors(level: String): Pair<Color, Color> {
    val p = palette
    return when (level) { "high" -> p.green to p.greenBg; "medium" -> p.orange to p.orangeBg; else -> p.red to p.redBg }
}

/** Short-lived message line under a page title. */
@Composable
fun NoticeLine(nvm: NutritionViewModel) {
    val m = nvm.message ?: return
    val p = palette
    LaunchedEffect(m) { kotlinx.coroutines.delay(4000); if (nvm.message == m) nvm.message = null }
    Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(m, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink)
    }
}

/**
 * The nutrition pages over the tab shell (one line in MainActivity, like SquadOverlays): Fasting,
 * Recipes, a recipe's editor, the Micronutrient dashboard and the "What should I eat?" sheet.
 */
@Composable
fun NutritionOverlays(vm: AppViewModel) {
    val nvm: NutritionViewModel = viewModel()
    LaunchedEffect(vm.loadedOnce, vm.signedIn) { if (vm.signedIn && vm.loadedOnce) nvm.loadSettings() }
    // A tapped "fast goal reached" notification (MainActivity passes EXTRA_OPEN = fasting).
    LaunchedEffect(NutritionNav.openFastingTick) { if (NutritionNav.openFastingTick > 0) nvm.page = NutritionPage.Fasting }
    when (val pg = nvm.page) {
        null -> {}
        NutritionPage.Fasting -> { BackHandler { nvm.page = null }; FastingScreen(vm, nvm) { nvm.page = null } }
        NutritionPage.Recipes -> { BackHandler { nvm.page = null }; RecipesScreen(vm, nvm) { nvm.page = null } }
        is NutritionPage.RecipeEdit -> { BackHandler { nvm.page = NutritionPage.Recipes }; RecipeEditor(vm, nvm, pg.recipe) { nvm.page = NutritionPage.Recipes } }
        NutritionPage.Micros -> { BackHandler { nvm.page = null }; MicrosScreen(vm, nvm) { nvm.page = null } }
        NutritionPage.WhatToEat -> WhatToEatSheet(vm, nvm) { nvm.page = null }
    }
}

/** Process-wide ticks from notifications into the Compose tree. */
object NutritionNav {
    var openFastingTick by androidx.compose.runtime.mutableIntStateOf(0)
}
