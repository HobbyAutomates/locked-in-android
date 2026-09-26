package com.sohum.bandlog.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.MenuDish
import com.sohum.bandlog.data.MenuScan
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.nutrition.NutritionIcons
import com.sohum.bandlog.ui.nutrition.Tag
import com.sohum.bandlog.ui.nutrition.confidenceColors
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.DietModes
import com.sohum.bandlog.util.WhatToEat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun range(lo: Double, hi: Double): String {
    val a = lo.roundToInt(); val b = hi.roundToInt()
    return if (a == b) "$a" else "$a–$b"
}

/** §10 the menu result: each dish with its kcal / protein range per portion, confidence, the best pick, one-tap log. */
@Composable
fun MenuResultView(scan: MenuScan, remaining: WhatToEat.Remaining?, onLog: suspend (MenuDish) -> Boolean) {
    val p = palette
    val scope = rememberCoroutineScope()
    var logged by remember(scan) { mutableStateOf(setOf<String>()) }
    var busy by remember(scan) { mutableStateOf<String?>(null) }
    // The server flags the best pick (diet-mode fit, protein per kcal, fits what's left, confidence); it goes first.
    val ordered = scan.dishes.sortedByDescending { it.bestPick }
    val mode = scan.dietMode?.takeIf { DietModes.isMode(it) } ?: DietModes.BALANCED
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(p.orangeBg, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(NutritionIcons.Menu, null, tint = p.orange, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(scan.restaurant ?: "From the menu", fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink)
                    Text("${scan.dishes.size} dish${if (scan.dishes.size == 1) "" else "es"}" + (remaining?.let { " · ${it.kcal.roundToInt()} kcal and ${it.protein.roundToInt()} g protein left today" } ?: ""), fontSize = 12.sp, color = p.muted)
                }
            }
            if (scan.note.isNotBlank()) Text(scan.note, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Text("Restaurant portions vary a lot, so these are ranges per portion. Logging uses the middle.", fontSize = 11.sp, color = p.muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (scan.dishes.isNotEmpty() && scan.dishes.none { it.bestPick } && mode != DietModes.BALANCED) Card {
            Text("Nothing here fits your ${DietModes.byKey(mode).label.lowercase()} mode.", fontSize = 13.sp, color = p.ink)
        }
        if (scan.dishes.isEmpty()) Card { Text("Couldn't read any dishes. Try a sharper, closer photo of one menu page.", fontSize = 14.sp, color = p.ink) }
        else Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ordered.forEachIndexed { i, d ->
                    if (i > 0) Hair()
                    val best = d.bestPick
                    val (cfg, cbg) = confidenceColors(d.confidence)
                    val done = d.name in logged
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 10.dp).semantics(mergeDescendants = true) {
                            contentDescription = "${d.name}${if (best) ", best pick" else ""}, ${range(d.kcalLow, d.kcalHigh)} kcal, ${range(d.proteinLow, d.proteinHigh)} grams protein, ${d.confidence} confidence"
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (best) Tag("Best pick", p.green, p.greenBg)
                                Tag(d.confidence.replaceFirstChar { it.uppercase() }, cfg, cbg)
                            }
                            Text(d.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(top = 4.dp))
                            Text("${d.portion} · ${range(d.kcalLow, d.kcalHigh)} kcal · ${range(d.proteinLow, d.proteinHigh)} g protein", fontSize = 12.sp, color = p.muted)
                            if (d.why.isNotBlank()) Text(d.why, fontSize = 12.sp, color = if (best) p.green else p.muted, lineHeight = 16.sp)
                            if (!d.fitsDiet) Text(
                                "Has " + d.dietConflicts.joinToString(", ") { c -> if (c == "roots") "roots / onion / garlic" else c }.ifBlank { "something" } + " · outside your ${DietModes.byKey(mode).label.lowercase()} mode",
                                fontSize = 11.sp, color = p.orange,
                            )
                            d.price?.let { Text(it, fontSize = 11.sp, color = p.muted) }
                        }
                        Box(
                            Modifier.size(48.dp).clickable(enabled = busy == null && !done, onClickLabel = "Log ${d.name}") {
                                scope.launch { busy = d.name; if (onLog(d)) logged = logged + d.name; busy = null }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(34.dp).then(if (done) Modifier.border(1.5.dp, p.green, CircleShape) else Modifier.background(p.btn, CircleShape)),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    busy == d.name -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = p.btnInk)
                                    done -> Icon(CheckIcon, null, tint = p.green, modifier = Modifier.size(14.dp))
                                    else -> Text("+", fontSize = 18.sp, fontWeight = FontWeight(800), color = p.btnInk)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
