package com.sohum.bandlog.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Filled flame (streak / calories), same path as the design canvas. */
val FlameIcon: ImageVector by lazy {
    ImageVector.Builder("Flame", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 2f); curveTo(13f, 6f, 16f, 7f, 16f, 11f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 11f)
            curveTo(8f, 10f, 8.3f, 9f, 9f, 8f); curveTo(9f, 10f, 10f, 11f, 11f, 11f)
            curveTo(11f, 8f, 10f, 5f, 12f, 2f); close()
        }
    }.build()
}

private fun stroke(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = build)
    }.build()

/** Dumbbell for workout rows. */
val DumbbellIcon: ImageVector by lazy {
    stroke("Dumbbell") {
        moveTo(6f, 8f); verticalLineTo(16f); moveTo(3f, 10f); verticalLineTo(14f)
        moveTo(18f, 8f); verticalLineTo(16f); moveTo(21f, 10f); verticalLineTo(14f); moveTo(6f, 12f); horizontalLineTo(18f)
    }
}

/** Bowl for meal rows. */
val BowlIcon: ImageVector by lazy {
    stroke("Bowl") {
        moveTo(4f, 11f); horizontalLineTo(20f); lineTo(18.5f, 19f); horizontalLineTo(5.5f); close()
        moveTo(6f, 11f); curveTo(6f, 7f, 9f, 5f, 12f, 5f); curveTo(15f, 5f, 18f, 7f, 18f, 11f)
    }
}

/** Microphone for the dictation card. */
val MicIcon: ImageVector by lazy {
    stroke("Mic") {
        moveTo(12f, 2f); arcTo(3f, 3f, 0f, false, true, 15f, 5f); verticalLineTo(11f); arcTo(3f, 3f, 0f, false, true, 9f, 11f); verticalLineTo(5f); arcTo(3f, 3f, 0f, false, true, 12f, 2f); close()
        moveTo(5f, 10f); arcTo(7f, 7f, 0f, false, false, 19f, 10f); moveTo(12f, 17f); verticalLineTo(22f)
    }
}
