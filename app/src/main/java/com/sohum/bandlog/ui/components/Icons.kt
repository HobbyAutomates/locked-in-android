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

/** Viewfinder brackets for the label scanner. */
val ScanIcon: ImageVector by lazy {
    stroke("Scan") {
        moveTo(4f, 9f); verticalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 6f, 4f); horizontalLineTo(9f)
        moveTo(15f, 4f); horizontalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 20f, 6f); verticalLineTo(9f)
        moveTo(20f, 15f); verticalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 18f, 20f); horizontalLineTo(15f)
        moveTo(9f, 20f); horizontalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 4f, 18f); verticalLineTo(15f)
        moveTo(7f, 12f); horizontalLineTo(17f)
    }
}

/** Thumbs for the AI feedback row. */
val ThumbUpIcon: ImageVector by lazy {
    stroke("ThumbUp") { moveTo(7f, 10f); verticalLineTo(21f); moveTo(3f, 12f); verticalLineTo(19f); arcTo(2f, 2f, 0f, false, false, 5f, 21f); horizontalLineTo(7f); moveTo(7f, 10f); lineTo(11f, 3f); arcTo(2f, 2f, 0f, false, true, 13f, 5f); verticalLineTo(9f); horizontalLineTo(19f); arcTo(2f, 2f, 0f, false, true, 21f, 11.3f); lineTo(19.5f, 19.3f); arcTo(2f, 2f, 0f, false, true, 17.5f, 21f); horizontalLineTo(7f) }
}
val ThumbDownIcon: ImageVector by lazy {
    stroke("ThumbDown") { moveTo(17f, 14f); verticalLineTo(3f); moveTo(21f, 12f); verticalLineTo(5f); arcTo(2f, 2f, 0f, false, false, 19f, 3f); horizontalLineTo(17f); moveTo(17f, 14f); lineTo(13f, 21f); arcTo(2f, 2f, 0f, false, true, 11f, 19f); verticalLineTo(15f); horizontalLineTo(5f); arcTo(2f, 2f, 0f, false, true, 3f, 12.7f); lineTo(4.5f, 4.7f); arcTo(2f, 2f, 0f, false, true, 6.5f, 3f); horizontalLineTo(17f) }
}

/** The Locked In mark: padlock with a band-shaped shackle. */
val LockIcon: ImageVector by lazy {
    ImageVector.Builder("Lock", 24.dp, 24.dp, 108f, 108f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 9f, strokeLineCap = StrokeCap.Round) { moveTo(40f, 52f); verticalLineTo(40f); arcTo(14f, 14f, 0f, false, true, 68f, 40f); verticalLineTo(52f) }
        path(fill = SolidColor(Color.Black)) { moveTo(34f, 50f); horizontalLineTo(74f); arcTo(5f, 5f, 0f, false, true, 79f, 55f); verticalLineTo(79f); arcTo(5f, 5f, 0f, false, true, 74f, 84f); horizontalLineTo(34f); arcTo(5f, 5f, 0f, false, true, 29f, 79f); verticalLineTo(55f); arcTo(5f, 5f, 0f, false, true, 34f, 50f); close() }
    }.build()
}

/** Microphone for the dictation card. */
val MicIcon: ImageVector by lazy {
    stroke("Mic") {
        moveTo(12f, 2f); arcTo(3f, 3f, 0f, false, true, 15f, 5f); verticalLineTo(11f); arcTo(3f, 3f, 0f, false, true, 9f, 11f); verticalLineTo(5f); arcTo(3f, 3f, 0f, false, true, 12f, 2f); close()
        moveTo(5f, 10f); arcTo(7f, 7f, 0f, false, false, 19f, 10f); moveTo(12f, 17f); verticalLineTo(22f)
    }
}
