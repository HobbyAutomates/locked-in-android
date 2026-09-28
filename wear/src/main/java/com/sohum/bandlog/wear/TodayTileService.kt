package com.sohum.bandlog.wear

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** "/li/today" as the phone last published it: calories left (or words), the day streak, the date. */
data class Today(val left: Int, val words: String?, val streak: Int, val freezes: Int, val date: String?)

internal const val PATH = "/li/today"
private const val PREFS = "li_tile"

internal fun saveToday(ctx: Context, t: Today) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putInt("left", t.left).putString("words", t.words).putInt("streak", t.streak).putInt("freezes", t.freezes).putString("date", t.date).apply()
}

internal fun loadToday(ctx: Context): Today {
    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return Today(p.getInt("left", -1), p.getString("words", null), p.getInt("streak", 0), p.getInt("freezes", 0), p.getString("date", null))
}

internal fun fromMap(item: DataMapItem): Today {
    val m = item.dataMap
    return Today(m.getInt("left", -1), m.getString("words"), m.getInt("streak", 0), m.getInt("freezes", 0), m.getString("date"))
}

/**
 * v2.18 E3 Wear OS tile: calories left and the day streak, on the brand ink with ember. The phone
 * publishes the numbers through the Data Layer; the tile reads the latest item (or its cache).
 */
class TodayTileService : TileService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        Futures.submit(Callable { tile(read()) }, executor)

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES).build())

    override fun onDestroy() { executor.shutdown(); super.onDestroy() }

    /** The newest Data Layer item, falling back to the last one saved on the watch. */
    private fun read(): Today = runCatching {
        val items = Tasks.await(Wearable.getDataClient(this).dataItems, 5, TimeUnit.SECONDS)
        try {
            items.filter { it.uri.path == PATH }.map { fromMap(DataMapItem.fromDataItem(it)) }.maxByOrNull { it.date ?: "" }?.also { saveToday(this, it) }
        } finally { items.release() }
    }.getOrNull() ?: loadToday(this)

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = LayoutElementBuilders.Text.Builder()
        .setText(s).setMaxLines(2)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL).build(),
        ).build()

    private fun tile(t: Today): TileBuilders.Tile {
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString()
        val fresh = t.date == today
        val bone = 0xFFF4F1EA.toInt(); val muted = 0xFF8F8A82.toInt(); val ember = 0xFFFF5B1F.toInt()
        val col = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("LOCKED IN", 11f, muted))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
        if (fresh && t.words != null) col.addContent(text(t.words, 16f, bone, bold = true))
        else {
            col.addContent(text(if (fresh && t.left >= 0) String.format(java.util.Locale.US, "%,d", t.left) else "—", 30f, bone, bold = true))
            col.addContent(text(if (fresh) "calories left" else "open Locked In on your phone", 12f, muted))
        }
        col.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
        col.addContent(text(if (t.streak > 0) "${t.streak}-day streak" else "Start a streak", 14f, ember, bold = true))
        if (t.freezes > 0) col.addContent(text("${t.freezes} ${if (t.freezes == 1) "freeze" else "freezes"} banked", 11f, muted))
        val root = LayoutElementBuilders.Box.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(col.build()).build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES)
            .setFreshnessIntervalMillis(30 * 60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
    }

    companion object { private const val RES = "1" }
}

/** Refreshes the tile when the phone publishes new numbers. */
class TodayListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.dataItem.uri.path == PATH }.lastOrNull()?.let { saveToday(this, fromMap(DataMapItem.fromDataItem(it.dataItem))) }
        TileService.getUpdater(this).requestUpdate(TodayTileService::class.java)
    }
}
