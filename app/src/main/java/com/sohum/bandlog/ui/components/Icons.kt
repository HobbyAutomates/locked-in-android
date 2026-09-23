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

/** Footprints for the steps card. */
val StepsIcon: ImageVector by lazy {
    stroke("Steps") {
        moveTo(6f, 3f); curveTo(8f, 3f, 9f, 5f, 9f, 8f); curveTo(9f, 11f, 8f, 13f, 6.5f, 13f); curveTo(5f, 13f, 4f, 11f, 4f, 8f); curveTo(4f, 5f, 4.5f, 3f, 6f, 3f); close()
        moveTo(5f, 15f); horizontalLineTo(8f); verticalLineTo(17f); horizontalLineTo(5f); close()
        moveTo(18f, 7f); curveTo(20f, 7f, 20f, 9f, 20f, 12f); curveTo(20f, 15f, 19f, 17f, 17.5f, 17f); curveTo(16f, 17f, 15f, 15f, 15f, 12f); curveTo(15f, 9f, 16f, 7f, 18f, 7f); close()
        moveTo(16f, 19f); horizontalLineTo(19f); verticalLineTo(21f); horizontalLineTo(16f); close()
    }
}

/**
 * Goal-speed slider animals, drawn as simple silhouettes so the three read at a glance:
 * a hunched sloth, an upright rabbit, a stretched-out cheetah.
 */
val SlothIcon: ImageVector by lazy {
    stroke("Sloth") {
        // Round body hugging a branch, with a low, heavy head.
        moveTo(4f, 6f); horizontalLineTo(20f)
        moveTo(9f, 6f); verticalLineTo(9f); moveTo(16f, 6f); verticalLineTo(9f)
        moveTo(12.5f, 9f); curveTo(16f, 9f, 18f, 11.5f, 18f, 14.5f); curveTo(18f, 17.5f, 16f, 20f, 12.5f, 20f)
        curveTo(9f, 20f, 7f, 17.5f, 7f, 14.5f); curveTo(7f, 11.5f, 9f, 9f, 12.5f, 9f); close()
        moveTo(10.5f, 14f); horizontalLineTo(10.6f); moveTo(14.5f, 14f); horizontalLineTo(14.6f)
    }
}

val RabbitIcon: ImageVector by lazy {
    stroke("Rabbit") {
        // Two tall ears over a compact sitting body.
        moveTo(9f, 10f); curveTo(8f, 7f, 8f, 4f, 9.5f, 3f); curveTo(11f, 4f, 11f, 7f, 10.5f, 10f)
        moveTo(14.5f, 10f); curveTo(15.5f, 7f, 15.5f, 4f, 14f, 3f); curveTo(12.5f, 4f, 12.5f, 7f, 13f, 10f)
        moveTo(12f, 10f); curveTo(15.5f, 10f, 18f, 12.5f, 18f, 16f); curveTo(18f, 19f, 15.5f, 21f, 12f, 21f)
        curveTo(8.5f, 21f, 6f, 19f, 6f, 16f); curveTo(6f, 12.5f, 8.5f, 10f, 12f, 10f); close()
        moveTo(10f, 15f); horizontalLineTo(10.1f); moveTo(14f, 15f); horizontalLineTo(14.1f)
    }
}

val CheetahIcon: ImageVector by lazy {
    stroke("Cheetah") {
        // Long low back, small head forward, legs mid-stride, tail streaming behind.
        moveTo(3f, 15f); curveTo(5f, 11f, 9f, 10f, 13f, 10f); curveTo(16f, 10f, 18f, 9f, 19f, 7f)
        moveTo(19f, 7f); lineTo(21.5f, 7.5f)
        moveTo(3f, 15f); curveTo(2f, 16f, 1.5f, 17f, 2f, 18f)
        moveTo(6f, 13.5f); lineTo(5f, 19f); moveTo(9.5f, 12.5f); lineTo(9f, 19f)
        moveTo(13.5f, 11.5f); lineTo(14.5f, 18f); moveTo(16.5f, 9.5f); lineTo(18f, 15f)
    }
}

/** Share arrow for earned badges. */
val ShareIcon: ImageVector by lazy {
    stroke("Share") {
        moveTo(12f, 3f); verticalLineTo(15f); moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
        moveTo(5f, 13f); verticalLineTo(19f); arcTo(2f, 2f, 0f, false, false, 7f, 21f); horizontalLineTo(17f)
        arcTo(2f, 2f, 0f, false, false, 19f, 19f); verticalLineTo(13f)
    }
}

/** Bathroom scale for the weight rows. */
val ScaleIcon: ImageVector by lazy {
    stroke("Scale") {
        moveTo(5f, 4f); horizontalLineTo(19f); arcTo(2f, 2f, 0f, false, true, 21f, 6f); verticalLineTo(18f)
        arcTo(2f, 2f, 0f, false, true, 19f, 20f); horizontalLineTo(5f); arcTo(2f, 2f, 0f, false, true, 3f, 18f)
        verticalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 5f, 4f); close()
        moveTo(8f, 9f); curveTo(8f, 7f, 10f, 6.5f, 12f, 6.5f); curveTo(14f, 6.5f, 16f, 7f, 16f, 9f)
        moveTo(12f, 9f); verticalLineTo(13f)
    }
}

/** Ruler for the height row. */
val RulerIcon: ImageVector by lazy {
    stroke("Ruler") {
        moveTo(4f, 8f); horizontalLineTo(20f); arcTo(1f, 1f, 0f, false, true, 21f, 9f); verticalLineTo(15f)
        arcTo(1f, 1f, 0f, false, true, 20f, 16f); horizontalLineTo(4f); arcTo(1f, 1f, 0f, false, true, 3f, 15f)
        verticalLineTo(9f); arcTo(1f, 1f, 0f, false, true, 4f, 8f); close()
        moveTo(7f, 8f); verticalLineTo(12f); moveTo(11f, 8f); verticalLineTo(12f); moveTo(15f, 8f); verticalLineTo(12f); moveTo(19f, 8f); verticalLineTo(12f)
    }
}

/** Pencil used on the "tap to edit" rows. */
val PencilIcon: ImageVector by lazy {
    stroke("Pencil") {
        moveTo(4f, 20f); lineTo(4.8f, 16.2f); lineTo(16.5f, 4.5f); arcTo(2.1f, 2.1f, 0f, false, true, 19.5f, 7.5f)
        lineTo(7.8f, 19.2f); close()
        moveTo(14.5f, 6.5f); lineTo(17.5f, 9.5f)
    }
}

/** Target rings for the goal / nutrition rows. */
val TargetIcon: ImageVector by lazy {
    stroke("Target") {
        moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.9f, 3f); close()
        moveTo(12f, 7.5f); arcTo(4.5f, 4.5f, 0f, true, true, 11.9f, 7.5f); close()
        moveTo(12f, 11f); horizontalLineTo(12.1f)
    }
}

/** A filled teaspoon — one of these per spoon of sugar in the scan report. */
val SpoonIcon: ImageVector by lazy {
    ImageVector.Builder("Spoon", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            // Oval bowl on top of a short tapered handle.
            moveTo(12f, 2f)
            curveTo(15f, 2f, 16.8f, 4.6f, 16.8f, 7.4f)
            curveTo(16.8f, 10.2f, 15f, 12.2f, 12f, 12.2f)
            curveTo(9f, 12.2f, 7.2f, 10.2f, 7.2f, 7.4f)
            curveTo(7.2f, 4.6f, 9f, 2f, 12f, 2f)
            close()
            moveTo(10.7f, 12.4f)
            horizontalLineTo(13.3f)
            lineTo(12.9f, 21.2f)
            curveTo(12.9f, 22f, 12.5f, 22.4f, 12f, 22.4f)
            curveTo(11.5f, 22.4f, 11.1f, 22f, 11.1f, 21.2f)
            close()
        }
    }.build()
}

/** Tick used by the plan checklist and the claims rows. */
val CheckIcon: ImageVector by lazy {
    stroke("Check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f) }
}

/** Cross for a claim the label doesn't support. */
val CrossIcon: ImageVector by lazy {
    stroke("Cross") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
}

/** Question mark for an unclear claim. */
val QuestionIcon: ImageVector by lazy {
    stroke("Question") {
        moveTo(9f, 9f); curveTo(9f, 6.8f, 10.5f, 5.5f, 12f, 5.5f); curveTo(13.8f, 5.5f, 15f, 6.8f, 15f, 8.5f)
        curveTo(15f, 10.5f, 12f, 11f, 12f, 14f)
        moveTo(12f, 18f); horizontalLineTo(12.1f)
    }
}

/** Chevron used by the collapsible cards on the scan report. */
val ChevronDownIcon: ImageVector by lazy {
    stroke("ChevronDown") { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) }
}

/** Microphone for the dictation card. */
val MicIcon: ImageVector by lazy {
    stroke("Mic") {
        moveTo(12f, 2f); arcTo(3f, 3f, 0f, false, true, 15f, 5f); verticalLineTo(11f); arcTo(3f, 3f, 0f, false, true, 9f, 11f); verticalLineTo(5f); arcTo(3f, 3f, 0f, false, true, 12f, 2f); close()
        moveTo(5f, 10f); arcTo(7f, 7f, 0f, false, false, 19f, 10f); moveTo(12f, 17f); verticalLineTo(22f)
    }
}

/** Barcode bars for the Barcode scan mode and history rows. */
val BarcodeIcon: ImageVector by lazy {
    stroke("Barcode") {
        moveTo(4f, 6f); verticalLineTo(18f)
        moveTo(8f, 6f); verticalLineTo(18f)
        moveTo(11f, 6f); verticalLineTo(18f)
        moveTo(15f, 6f); verticalLineTo(18f)
        moveTo(17f, 6f); verticalLineTo(18f)
        moveTo(20f, 6f); verticalLineTo(18f)
    }
}

/** Camera for the Food photo scan mode. */
val CameraIcon: ImageVector by lazy {
    stroke("Camera") {
        moveTo(4f, 8f); horizontalLineTo(7f); lineTo(9f, 5f); horizontalLineTo(15f); lineTo(17f, 8f); horizontalLineTo(20f)
        arcTo(1f, 1f, 0f, false, true, 21f, 9f); verticalLineTo(18f); arcTo(1f, 1f, 0f, false, true, 20f, 19f)
        horizontalLineTo(4f); arcTo(1f, 1f, 0f, false, true, 3f, 18f); verticalLineTo(9f); arcTo(1f, 1f, 0f, false, true, 4f, 8f); close()
        moveTo(15.5f, 13f); arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 13f); arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 13f)
    }
}

/** Clock-arrow for the History heading. */
val HistoryIcon: ImageVector by lazy {
    stroke("History") {
        moveTo(3f, 12f); arcTo(9f, 9f, 0f, true, true, 6f, 18.7f)
        moveTo(3f, 12f); horizontalLineTo(7f); moveTo(3f, 12f); verticalLineTo(8f)
        moveTo(12f, 7f); verticalLineTo(12f); lineTo(15f, 14f)
    }
}
