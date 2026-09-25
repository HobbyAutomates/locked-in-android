package com.sohum.bandlog.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.theme.palette

/** v2.8: what the meal page was opened with — a section's "+ Add" ([mealType]) or a logged meal ([meal], the editor). */
data class MealRequest(val date: String, val mealType: String? = null, val meal: Meal? = null)

/**
 * v2.8: the Add-food screen on its own page (the same MealForm as Log → Meal): a meal type's
 * "+ Add" with that type picked, or a logged meal opened for editing.
 */
@Composable
fun MealScreen(vm: AppViewModel, req: MealRequest, onClose: () -> Unit) {
    val p = palette
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(18.dp)) }
            Text(if (req.meal != null) "Edit meal" else "Add food", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.width(40.dp))
        }
        MealForm(vm, req.meal?.date ?: req.date, onClose, mealType = req.mealType, existing = req.meal)
    }
}
