package com.sohum.bandlog.ui.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.ui.components.BowlIcon
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chevron
import com.sohum.bandlog.ui.components.FoodImage
import com.sohum.bandlog.ui.components.FoodImages
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.RemoteImage
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.MealTypes
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v2.8: one of a day's four meal sections — Breakfast · Lunch · Dinner · Snacks — with its kcal and
 * protein, its meals (tap one to edit it) and a small "+ Add" that opens Add food with that type
 * picked. An empty section is one slim row: "Breakfast · + Add". Home and Calendar both use it
 * (mirrors the web's MealSections.tsx).
 */
@Composable
fun MealSection(section: MealTypes.Section, onAdd: (String) -> Unit, onOpen: (Meal) -> Unit) {
    val p = palette
    val t = section.type
    if (section.meals.isEmpty()) {
        Card(padding = 0.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(com.sohum.bandlog.ui.components.mealTypeIcon(t.key), null, tint = p.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.weight(1f), maxLines = 1)
                AddPill(t.label) { onAdd(t.key) }
            }
        }
        return
    }
    Card(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(com.sohum.bandlog.ui.components.mealTypeIcon(t.key), null, tint = p.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t.label, fontSize = 16.sp, fontWeight = FontWeight(800), letterSpacing = (-0.3).sp, color = p.ink, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(
                "${section.kcal} kcal · ${fmt(section.protein)} g protein", fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AddPill(t.label) { onAdd(t.key) }
        }
        section.meals.forEach { m ->
            Box(Modifier.padding(horizontal = 12.dp)) { Hair() }
            MealLine(m) { onOpen(m) }
        }
    }
}

/** The small "+ Add" on a section. */
@Composable
private fun AddPill(label: String, onClick: () -> Unit) {
    val p = palette
    Box(Modifier.heightIn(min = 44.dp).pressable().clickable(onClick = onClick).semantics { contentDescription = "Add to $label" }, contentAlignment = Alignment.Center) {
        Box(Modifier.background(p.card2, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text("+ Add", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
        }
    }
}

/** One logged meal: picture, what was in it, amount / protein / time, kcal. The whole row opens the editor. */
@Composable
private fun MealLine(m: Meal, onClick: () -> Unit) {
    val p = palette
    val title = m.items.joinToString(", ") { it.name }.ifBlank { m.rawText.ifBlank { "Meal" } }
    val detail = if (m.items.size == 1) m.items[0].quantityLabel else "${m.items.size} items"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "$title, ${m.calories.roundToInt()} kcal. Edit" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Plate photo when the meal has one; else a picture of its biggest item (v2.4).
        if (m.photoPath != null) RemoteImage(storagePath = m.photoPath, size = 40.dp, radius = 12.dp, fallback = BowlIcon, fallbackTint = p.orange, fallbackBg = p.orangeBg)
        else {
            val big = m.items.maxByOrNull { it.calories }
            if (big == null) Box(Modifier.size(40.dp).background(p.orangeBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(BowlIcon, null, tint = p.orange, modifier = Modifier.size(20.dp)) }
            else FoodImage(big.name, big.imageUrl, kind = FoodImages.kindFor(big.source), size = 40.dp, foodId = big.foodId, fallbackBg = p.orangeBg)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$detail · ${fmt((m.protein * 10).roundToInt() / 10.0)} g protein" + mealTime(m.createdAt).let { if (it.isBlank()) "" else " · $it" },
                fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("${m.calories.roundToInt()} kcal", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Chevron()
    }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "1:15 PM" in India time for a meal's created_at, "" when unreadable. */
private fun mealTime(iso: String): String = runCatching {
    OffsetDateTime.parse(iso.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" })
        .atZoneSameInstant(com.sohum.bandlog.util.Dates.ZONE).format(TIME)
}.getOrDefault("")
