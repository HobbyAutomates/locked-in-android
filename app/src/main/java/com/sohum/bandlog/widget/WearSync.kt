package com.sohum.bandlog.widget

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * v2.18 E3: today's numbers for the Wear OS tile (the :wear module), over the Data Layer at
 * "/li/today". Calories left (or the words, with "Hide calorie numbers") come from Home's widget
 * cache. With no watch paired the put simply goes nowhere; every failure is ignored.
 */
object WearSync {
    const val PATH = "/li/today"

    fun publish(context: Context, streak: Int, freezes: Int) {
        runCatching {
            val cal = context.getSharedPreferences("bandlog_widget", Context.MODE_PRIVATE)
            val today = com.sohum.bandlog.util.Dates.today()
            val fresh = cal.getString("date", null) == today
            val req = PutDataMapRequest.create(PATH).apply {
                dataMap.putInt("left", if (fresh) cal.getInt("left", -1) else -1)
                cal.getString("words", null)?.takeIf { fresh }?.let { dataMap.putString("words", it) }
                dataMap.putInt("streak", streak)
                dataMap.putInt("freezes", freezes)
                dataMap.putString("date", today)
                dataMap.putLong("at", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }
}
