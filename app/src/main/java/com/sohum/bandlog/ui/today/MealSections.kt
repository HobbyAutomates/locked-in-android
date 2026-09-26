package com.sohum.bandlog.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
/**
 * v2.13: long-press a logged meal and drag it onto another section to move it (Home). [bounds] is
 * where each section sits on screen (root coordinates); [hover] the section under the finger.
 */
class MealDrag {
    var meal by mutableStateOf<Meal?>(null); private set
    /** Finger position in root coordinates while dragging. */
    var pointer by mutableStateOf(Offset.Zero); private set
    val bounds = mutableMapOf<String, Rect>()
    val hover: String? get() = if (meal == null) null else bounds.entries.firstOrNull { it.value.contains(pointer) }?.key

    fun start(m: Meal, at: Offset) { meal = m; pointer = at }
    fun move(by: Offset) { pointer += by }
    /** The section it was dropped on (null = same section / nowhere); ends the drag. */
    fun drop(): Pair<Meal, String>? {
        val m = meal ?: return null
        val to = hover
        meal = null
        return if (to != null && to != MealTypes.of(m)) m to to else null
    }
    fun cancel() { meal = null }
}

@Composable
fun MealSection(
    section: MealTypes.Section, onAdd: (String) -> Unit, onOpen: (Meal) -> Unit,
    /** v2.13 (Home): drag state shared by the four sections, and the move itself; null = no moving (Calendar). */
    drag: MealDrag? = null, onMove: ((Meal, String) -> Unit)? = null,
) {
    val p = palette
    val t = section.type
    val target = drag != null && drag.meal != null && drag.hover == t.key && drag.meal?.let { MealTypes.of(it) } != t.key
    val track = if (drag != null) Modifier.onGloballyPositioned { drag.bounds[t.key] = it.boundsInRoot() } else Modifier
    val ring = if (target) Modifier.border(2.dp, p.ink, RoundedCornerShape(20.dp)) else Modifier
    // A section scrolled out of the list must not keep catching drops at its old spot.
    if (drag != null) androidx.compose.runtime.DisposableEffect(drag, t.key) { onDispose { drag.bounds.remove(t.key) } }
    if (section.meals.isEmpty()) {
        Card(track.then(ring), padding = 0.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(com.sohum.bandlog.ui.components.mealTypeIcon(t.key), null, tint = p.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.weight(1f), maxLines = 1)
                if (target) Text("Drop to move here", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(end = 8.dp))
                else AddPill(t.label) { onAdd(t.key) }
            }
        }
        return
    }
    Card(track.then(ring), padding = 0.dp) {
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
            MealLine(m, drag, onMove) { onOpen(m) }
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

/**
 * One logged meal: picture, what was in it, amount / protein / time, kcal. The whole row opens the
 * editor. v2.13: long-press + drag moves it to another section; the more button (and the
 * accessibility actions) offer "Move to…" as the non-drag path.
 */
@Composable
private fun MealLine(m: Meal, drag: MealDrag? = null, onMove: ((Meal, String) -> Unit)? = null, onClick: () -> Unit) {
    val p = palette
    val title = m.items.joinToString(", ") { it.name }.ifBlank { m.rawText.ifBlank { "Meal" } }
    val detail = if (m.items.size == 1) m.items[0].quantityLabel else "${m.items.size} items"
    val haptic = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    // A long-press that turned into a drag must not also open the editor on release.
    var dragEndAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val here = MealTypes.of(m)
    val others = MealTypes.ALL.filter { it.key != here }
    val dragMod = if (drag != null && onMove != null) Modifier.onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(m.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { at -> dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); drag.start(m, origin + at) },
            onDrag = { change, amount -> change.consume(); drag.move(amount) },
            onDragEnd = { dragging = false; dragEndAt = System.currentTimeMillis(); drag.drop()?.let { (meal, to) -> onMove(meal, to) } },
            onDragCancel = { dragging = false; dragEndAt = System.currentTimeMillis(); drag.cancel() },
        )
    } else Modifier
    val lifted = drag?.meal?.id == m.id
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).then(dragMod)
            .background(if (lifted) p.card2 else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { if (!dragging && System.currentTimeMillis() - dragEndAt > 400) onClick() }
            .padding(start = 12.dp, end = if (onMove != null) 0.dp else 12.dp, top = 8.dp, bottom = 8.dp)
            .semantics {
                contentDescription = "$title, ${m.calories.roundToInt()} kcal. Edit" + if (onMove != null) ". Long-press and drag to move it to another meal." else ""
                if (onMove != null) customActions = others.map { o -> CustomAccessibilityAction("Move to ${o.label}") { onMove(m, o.key); true } }
            },
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
        if (onMove == null) {
            Spacer(Modifier.width(6.dp))
            Chevron()
        } else Box {
            Box(Modifier.size(44.dp).clickable(onClickLabel = "Edit or move this meal") { menu = true }, contentAlignment = Alignment.Center) {
                Icon(com.sohum.bandlog.ui.nutrition.NutritionIcons.More, "More for $title", tint = p.muted, modifier = Modifier.size(18.dp))
            }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(p.card)) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Edit", color = p.ink) }, onClick = { menu = false; onClick() })
                others.forEach { o ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Move to ${o.label}", color = p.ink) },
                        leadingIcon = { Icon(com.sohum.bandlog.ui.components.mealTypeIcon(o.key), null, tint = p.muted, modifier = Modifier.size(16.dp)) },
                        onClick = { menu = false; onMove(m, o.key) },
                    )
                }
            }
        }
    }
}

/** The meal riding under the finger while it's dragged (drawn by Home over the list). */
@Composable
fun MealDragGhost(drag: MealDrag, originInRoot: Offset) {
    val m = drag.meal ?: return
    val p = palette
    val title = m.items.joinToString(", ") { it.name }.ifBlank { m.rawText.ifBlank { "Meal" } }
    val at = drag.pointer - originInRoot
    Box(
        Modifier.offset { androidx.compose.ui.unit.IntOffset((at.x - 120.dp.toPx()).roundToInt(), (at.y - 70.dp.toPx()).roundToInt()) }
            .width(240.dp).shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = p.shadow, spotColor = p.shadow)
            .background(p.card, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val h = drag.hover
            Text(if (h == null || h == MealTypes.of(m)) "Drag onto another meal" else "Move to ${MealTypes.label(h)}", fontSize = 12.sp, color = p.muted)
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "1:15 PM" in India time for a meal's created_at, "" when unreadable. */
private fun mealTime(iso: String): String = runCatching {
    OffsetDateTime.parse(iso.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" })
        .atZoneSameInstant(com.sohum.bandlog.util.Dates.ZONE).format(TIME)
}.getOrDefault("")
