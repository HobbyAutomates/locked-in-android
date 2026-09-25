package com.sohum.bandlog.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
 * v2.12 thin line icons (1.5 stroke on a 24 grid, round caps / joins) for the redesigned Profile
 * and Progress screens. Shapes follow Lucide / Feather (ISC / MIT, see the note in Icons.kt), the
 * same strings as the v2.12 design canvas. Tint with the current text colour.
 */
private fun thin(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object LineIcons {
    val Settings: ImageVector by lazy {
        thin(
            "Settings",
            "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
        )
    }
    val Share: ImageVector by lazy { thin("Share", "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "M16 6l-4-4-4 4", "M12 2v13") }
    val Star: ImageVector by lazy { thin("Star", "M12 3l1.9 5.8H20l-4.9 3.6 1.9 5.8L12 14.6 7 18.2l1.9-5.8L4 8.8h6.1z") }
    val Flame: ImageVector by lazy { thin("Flame", "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z") }
    val Drop: ImageVector by lazy { thin("Drop", "M12 2.7s6 6.3 6 11.3a6 6 0 0 1-12 0c0-5 6-11.3 6-11.3z") }
    val Balance: ImageVector by lazy { thin("Balance", "M3 7h18", "M6 7l-3 7a3 3 0 0 0 6 0z", "M18 7l-3 7a3 3 0 0 0 6 0z", "M12 3v18", "M8 21h8") }
    val User: ImageVector by lazy { thin("User", "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z") }
    val Chart: ImageVector by lazy { thin("Chart", "M3 3v18h18", "M7 15l4-4 3 3 6-6") }
    val Bell: ImageVector by lazy { thin("Bell", "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9", "M13.7 21a2 2 0 0 1-3.4 0") }
    val Sliders: ImageVector by lazy { thin("Sliders", "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6") }
    val Message: ImageVector by lazy { thin("Message", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    val ChevronRight: ImageVector by lazy { thin("ChevronRight", "M9 18l6-6-6-6") }
    val Plus: ImageVector by lazy { thin("Plus", "M12 5v14", "M5 12h14") }
    val Target: ImageVector by lazy { thin("Target", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12z", "M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z") }
    val Flag: ImageVector by lazy { thin("Flag", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7") }
    val Refresh: ImageVector by lazy { thin("Refresh", "M23 4v6h-6", "M1 20v-6h6", "M3.5 9a9 9 0 0 1 14.9-3.4L23 10", "M1 14l4.6 4.4A9 9 0 0 0 20.5 15") }
    val Shield: ImageVector by lazy { thin("Shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z") }
    val LogOut: ImageVector by lazy { thin("LogOut", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9") }
    val Award: ImageVector by lazy { thin("Award", "M12 15a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "M8.2 13.9L7 23l5-3 5 3-1.2-9.1") }
    val Camera: ImageVector by lazy { thin("Camera", "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z", "M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z") }
    val AtSign: ImageVector by lazy { thin("AtSign", "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.9 7.9") }
    val Info: ImageVector by lazy { thin("Info", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 16v-4", "M12 8h.01") }
    val Crown: ImageVector by lazy { thin("Crown", "M2 4l3 12h14l3-12-6 7-4-7-4 7-6-7z", "M5 20h14") }

    // Medal engravings (drawn inside Medals v2).
    val Bowl: ImageVector by lazy { thin("Bowl", "M4 11h16l-1.5 8h-13z", "M6 11c0-4 3-6 6-6s6 2 6 6") }
    val Leaf: ImageVector by lazy { thin("Leaf", "M11 20A7 7 0 0 1 4 13c0-6 6-9 16-9 0 10-3 16-9 16z", "M4 21c3-6 6-8 10-10") }
    val Trophy: ImageVector by lazy { thin("Trophy", "M8 21h8", "M12 17v4", "M7 4h10v5a5 5 0 0 1-10 0z", "M17 5h3v2a3 3 0 0 1-3 3", "M7 5H4v2a3 3 0 0 0 3 3") }
}
