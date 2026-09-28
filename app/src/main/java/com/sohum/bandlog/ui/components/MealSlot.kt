package com.sohum.bandlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.MealTypes

/**
 * v2.17 meal-slot picker (Breakfast · Lunch · Dinner · Snacks) for the scan "Log it" flows. The
 * caller starts it on [MealTypes.default] (the same hour rule as Home).
 */
@Composable
fun MealSlotPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    val ember = Color(0xFFFF5B1F)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MealTypes.ALL.forEach { t ->
            val on = t.key == selected
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp)
                    .background(if (on) ember.copy(alpha = 0.14f) else p.card2, RoundedCornerShape(14.dp))
                    .border(1.dp, if (on) ember else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton, onClickLabel = "Log to ${t.label}") { onSelect(t.key) }
                    .semantics { this.selected = on; contentDescription = t.label }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(mealTypeIcon(t.key), null, tint = if (on) ember else p.muted, modifier = Modifier.size(18.dp))
                Text(t.label, fontSize = 12.sp, fontWeight = FontWeight(if (on) 700 else 500), color = if (on) p.ink else p.muted, maxLines = 1)
            }
        }
    }
}
