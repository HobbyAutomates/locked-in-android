package com.sohum.bandlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.Serving
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Counting
import com.sohum.bandlog.util.QuantityFood
import com.sohum.bandlog.util.Recents

/**
 * v2.17 "Recent" row (Scan screen and Add food): past scans and logged foods, newest first. The
 * ember "+" re-adds one at its last-used quantity; tapping the card opens the amount first.
 */
@Composable
fun RecentFoodsRow(
    recents: List<Recents.Recent>,
    onAdd: (Recents.Recent) -> Unit,
    onAdjust: (Recents.Recent) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Recent",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Overrides for the dark scan stage (defaults: the theme's card / ink / muted). */
    cardColor: Color? = null, inkColor: Color? = null, mutedColor: Color? = null,
) {
    if (recents.isEmpty()) return
    val p = palette
    val ember = Color(0xFFFF5B1F)
    val card = cardColor ?: p.card
    val ink = inkColor ?: p.ink
    val muted = mutedColor ?: p.muted
    Column(modifier.fillMaxWidth()) {
        Text(
            title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight(700), letterSpacing = 1.4.sp, color = muted,
            modifier = Modifier.padding(contentPadding).padding(start = 4.dp, bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = contentPadding) {
            items(recents, key = { it.key }) { r ->
                Column(
                    Modifier.width(148.dp).background(card, RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Change the amount of ${r.name}") { onAdjust(r) }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        RemoteImage(
                            url = r.imageUrl, size = 40.dp, radius = 10.dp,
                            fallback = when (r.scanKind) { "barcode" -> BarcodeIcon; "label" -> LineIcons.Info; else -> LineIcons.Bowl },
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(44.dp).clickable(onClickLabel = "Add ${r.name} again") { onAdd(r) }.semantics { contentDescription = "Add ${r.name}, ${r.kcal} kcal" },
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            Box(Modifier.size(30.dp).background(ember, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(PlusIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text(
                        r.name, fontSize = 13.sp, fontWeight = FontWeight(700), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp).height(34.dp),
                    )
                    Text(
                        "${r.kcal} kcal · ${r.item.quantityLabel}", fontSize = 11.5.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** The recent item as a food the Quantity sheet can price (its last-used portion as "1 serving" when it was one). */
fun recentFood(item: MealItem): QuantityFood {
    val servings = when {
        item.servingUnit != null -> listOf(item.servingUnit)
        item.unit == "serving" && (item.servings ?: 0.0) > 0 -> listOf(Counting.savedUnitOf(item) ?: Serving("1 serving", item.grams / item.servings!!))
        else -> listOfNotNull(Counting.pieceServing(item.name, item.grams))
    }
    return QuantityFood.from(item, servings)
}
