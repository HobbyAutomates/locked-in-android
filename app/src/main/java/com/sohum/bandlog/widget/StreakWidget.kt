package com.sohum.bandlog.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sohum.bandlog.MainActivity
import com.sohum.bandlog.util.Dates

/**
 * v2.18 E3 home-screen widget (Glance): calories left (or the words, with "Hide calorie numbers")
 * and the day streak, on the brand ink card. Home publishes both numbers; the v2.3 RemoteViews
 * widget stays as it was. Tap opens the app on Add food.
 */
class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cal = context.getSharedPreferences("bandlog_widget", Context.MODE_PRIVATE)
        val mine = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val today = Dates.today()
        val fresh = cal.getString("date", null) == today
        val left = cal.getInt("left", -1)
        val words = cal.getString("words", null)
        val streak = if (mine.getString("date", null) == today || mine.getString("date", null) == Dates.addDays(today, -1)) mine.getInt("streak", 0) else 0
        val freezes = mine.getInt("freezes", 0)
        val open = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_MEAL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        provideContent {
            val bone = ColorProvider(Color(0xFFF4F1EA))
            val muted = ColorProvider(Color(0xFF8F8A82))
            val ember = ColorProvider(Color(0xFFFF5B1F))
            Column(
                GlanceModifier.fillMaxSize().background(Color(0xFF0B0B0C)).cornerRadius(22.dp).padding(14.dp).clickable(actionStartActivity(open)),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text("LOCKED IN", style = TextStyle(color = muted, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(6.dp))
                if (fresh && words != null) Text(words, style = TextStyle(color = bone, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                else {
                    Text(if (fresh && left >= 0) String.format(java.util.Locale.US, "%,d", left) else "—", style = TextStyle(color = bone, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                    Text(if (fresh) "calories left" else "open the app to update", style = TextStyle(color = muted, fontSize = 11.sp))
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("●", style = TextStyle(color = ember, fontSize = 11.sp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(if (streak > 0) "$streak-day streak" else "Start a streak", style = TextStyle(color = bone, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    if (freezes > 0) { Spacer(GlanceModifier.width(6.dp)); Text("· $freezes ❄", style = TextStyle(color = muted, fontSize = 12.sp)) }
                }
            }
        }
    }

    companion object {
        private const val FILE = "streak_widget_v218"

        /** Called when the day streak, freezes or today's meals change (SocialOverlays); calories left come from Home's widget cache. */
        suspend fun publish(context: Context, streak: Int, freezes: Int) {
            val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val today = Dates.today()
            p.edit().putInt("streak", streak).putInt("freezes", freezes).putString("date", today).apply()
            runCatching { StreakWidget().updateAll(context) }
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
