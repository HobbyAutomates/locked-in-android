package com.sohum.bandlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.FoodVariant
import com.sohum.bandlog.data.SourceInfo
import com.sohum.bandlog.ui.theme.palette
import kotlin.math.roundToInt

/**
 * v2.9 "Which one?" — the variant chips under an ambiguous row (same as web's VariantChips). The
 * current food reads as picked; tapping another swaps the row to it at the same grams.
 */
@Composable
fun VariantChips(variants: List<FoodVariant>, currentId: String?, onPick: (FoodVariant) -> Unit, modifier: Modifier = Modifier) {
    if (variants.size < 2) return
    val p = palette
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Which one?", fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted)
        variants.forEach { v ->
            val sel = v.foodId == currentId
            Box(
                Modifier.heightIn(min = 44.dp).clickable(enabled = !sel) { onPick(v) }
                    .semantics { role = Role.RadioButton; selected = sel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.heightIn(min = 30.dp).background(if (sel) p.ink else p.card2, CircleShape).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("${v.chip}  ${v.kcalPer100g.roundToInt()}", fontSize = 12.sp, fontWeight = FontWeight(600), color = if (sel) p.card else p.ink, maxLines = 1)
                }
            }
        }
    }
}

/** The small ⓘ on every row; highlighted as "Check" when the row is low confidence or has variants. */
@Composable
fun InfoButton(name: String, check: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.heightIn(min = 44.dp).widthIn(min = 32.dp).clickable(onClick = onClick)
            .semantics { contentDescription = "Where's $name from?" + if (check) " Worth a check." else "" },
        contentAlignment = Alignment.Center,
    ) {
        if (check) Box(Modifier.background(p.orangeBg, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text("ⓘ Check", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.orange)
        } else Text("ⓘ", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.muted)
    }
}

/**
 * "Where's this from?" — source (with links where one can be built), confidence and the per-100 g
 * numbers, then "Not right? Pick another" (the chips, or search) and "Report" (a meal_feedback row).
 * [info] null means the lookup is still running.
 */
@Composable
fun SourceSheet(
    name: String,
    info: SourceInfo?,
    confidence: String,
    per100: String,
    variants: List<FoodVariant>,
    currentId: String?,
    reported: Boolean,
    onDismiss: () -> Unit,
    onPickVariant: ((FoodVariant) -> Unit)?,
    onPickAnother: (() -> Unit)?,
    onReport: () -> Unit,
) {
    val p = palette
    val uri = LocalUriHandler.current
    BottomSheet(title = "Where's this from?", subtitle = name, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(14.dp, 12.dp)) {
            if (info == null) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = p.muted)
                Text("  Checking the source…", fontSize = 13.sp, color = p.muted)
            } else {
                Text(info.label, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(info.detail, fontSize = 13.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
                info.links.forEach { l ->
                    Box(Modifier.heightIn(min = 44.dp).clickable { runCatching { uri.openUri(l.url) } }, contentAlignment = Alignment.CenterStart) {
                        Text("${l.label} ↗", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.blue, textDecoration = TextDecoration.Underline)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Confidence: $confidence", fontSize = 13.sp, color = p.ink, modifier = Modifier.padding(horizontal = 4.dp))
        Text(per100, fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        Spacer(Modifier.height(6.dp))
        if (variants.size > 1 && onPickVariant != null) {
            Text("Not right? Pick another", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(horizontal = 4.dp))
            VariantChips(variants, currentId, onPickVariant, Modifier.fillMaxWidth())
        } else if (onPickAnother != null) {
            Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onPickAnother).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("Not right? Pick another", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, textDecoration = TextDecoration.Underline)
            }
        }
        Box(Modifier.heightIn(min = 44.dp).clickable(enabled = !reported, onClick = onReport).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            Text(if (reported) "Reported — thanks, we'll check it" else "Report a wrong number", fontSize = 13.sp, fontWeight = FontWeight(600), color = if (reported) p.muted else p.red)
        }
    }
}
