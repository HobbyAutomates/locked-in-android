package com.sohum.bandlog.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sohum.bandlog.MainActivity
import com.sohum.bandlog.R

/**
 * v2.3 home-screen widget (plain RemoteViews): "Calories left" as last computed by Home, and a
 * "Log food" button that opens the Log page on Meal (same path as a meal reminder).
 */
class CaloriesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        private const val FILE = "bandlog_widget"

        /** Called by Home whenever today's calories-left number changes. */
        fun publish(context: Context, caloriesLeft: Int, words: String? = null) {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val today = com.sohum.bandlog.util.Dates.today()
            if (prefs.getInt("left", -1) == caloriesLeft && prefs.getString("date", null) == today && prefs.getString("words", null) == words) return
            prefs.edit().putInt("left", caloriesLeft).putString("date", today).putString("words", words).apply()
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, CaloriesWidget::class.java))
                ids.forEach { manager.updateAppWidget(it, views(context)) }
            }
        }

        private fun views(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val fresh = prefs.getString("date", null) == com.sohum.bandlog.util.Dates.today()
            val left = prefs.getInt("left", -1)
            val rv = RemoteViews(context.packageName, R.layout.widget_calories)
            // v2.11: with "Hide calorie numbers" on, the widget shows words instead of a kcal number.
            val words = prefs.getString("words", null)
            if (fresh && words != null) {
                rv.setViewVisibility(R.id.widget_value, android.view.View.GONE)
                rv.setTextViewText(R.id.widget_label, words)
            } else {
                rv.setViewVisibility(R.id.widget_value, android.view.View.VISIBLE)
                rv.setTextViewText(R.id.widget_value, if (fresh && left >= 0) String.format(java.util.Locale.US, "%,d", left) else "—")
                rv.setTextViewText(R.id.widget_label, if (fresh) "Calories left" else "Open the app to update")
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val log = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_MEAL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            rv.setOnClickPendingIntent(R.id.widget_log, log)
            rv.setOnClickPendingIntent(R.id.widget_water, com.sohum.bandlog.notify.Notifications.addGlassIntent(context, "widget", 9203))
            return rv
        }
    }
}
