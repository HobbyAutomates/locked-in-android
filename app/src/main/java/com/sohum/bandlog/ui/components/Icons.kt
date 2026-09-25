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

/**
 * v2.4: the Scan tab's own filled mark (scanning is separate from logging meals): four solid
 * viewfinder corners around a solid scan line.
 */
val ScanFilledIcon: ImageVector by lazy {
    ImageVector.Builder("ScanFilled", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            // top-left corner
            moveTo(3f, 9f); verticalLineTo(6f); arcTo(3f, 3f, 0f, false, true, 6f, 3f); horizontalLineTo(9f); verticalLineTo(5.6f); horizontalLineTo(6.4f)
            arcTo(0.8f, 0.8f, 0f, false, false, 5.6f, 6.4f); verticalLineTo(9f); close()
            // top-right
            moveTo(15f, 3f); horizontalLineTo(18f); arcTo(3f, 3f, 0f, false, true, 21f, 6f); verticalLineTo(9f); horizontalLineTo(18.4f); verticalLineTo(6.4f)
            arcTo(0.8f, 0.8f, 0f, false, false, 17.6f, 5.6f); horizontalLineTo(15f); close()
            // bottom-right
            moveTo(21f, 15f); verticalLineTo(18f); arcTo(3f, 3f, 0f, false, true, 18f, 21f); horizontalLineTo(15f); verticalLineTo(18.4f); horizontalLineTo(17.6f)
            arcTo(0.8f, 0.8f, 0f, false, false, 18.4f, 17.6f); verticalLineTo(15f); close()
            // bottom-left
            moveTo(9f, 21f); horizontalLineTo(6f); arcTo(3f, 3f, 0f, false, true, 3f, 18f); verticalLineTo(15f); horizontalLineTo(5.6f); verticalLineTo(17.6f)
            arcTo(0.8f, 0.8f, 0f, false, false, 6.4f, 18.4f); horizontalLineTo(9f); close()
            // the scan line, as a solid rounded bar
            moveTo(7.5f, 10.6f); horizontalLineTo(16.5f); arcTo(1.4f, 1.4f, 0f, false, true, 16.5f, 13.4f); horizontalLineTo(7.5f); arcTo(1.4f, 1.4f, 0f, false, true, 7.5f, 10.6f); close()
        }
    }.build()
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

/** Running figure for the Run option and exercise rows. */
val RunIcon: ImageVector by lazy {
    stroke("Run") {
        // Head
        moveTo(15f, 4f); arcTo(1.5f, 1.5f, 0f, true, true, 14.9f, 4f); close()
        // Torso + leading leg
        moveTo(6f, 21f); lineTo(9.5f, 14.5f); lineTo(12.5f, 16.5f); lineTo(14f, 21f)
        // Back leg + body line
        moveTo(9.5f, 14.5f); lineTo(10.5f, 10f); lineTo(14f, 8.5f); lineTo(16.5f, 12f); lineTo(20f, 12.5f)
        // Trailing arm
        moveTo(10.5f, 10f); lineTo(7f, 11.5f); lineTo(5f, 9f)
    }
}

/** Magnifier for the food Search tab. */
val SearchIcon: ImageVector by lazy {
    stroke("Search") {
        moveTo(17.5f, 11f); arcTo(6.5f, 6.5f, 0f, true, true, 4.5f, 11f); arcTo(6.5f, 6.5f, 0f, true, true, 17.5f, 11f)
        moveTo(16f, 16f); lineTo(21f, 21f)
    }
}

/** Oil drop for the "Cooked in…" fat chips. */
val DropIcon: ImageVector by lazy {
    stroke("Drop") {
        moveTo(12f, 3f); curveTo(12f, 3f, 18f, 9.5f, 18f, 14f); arcTo(6f, 6f, 0f, true, true, 6f, 14f); curveTo(6f, 9.5f, 12f, 3f, 12f, 3f); close()
    }
}

/** Two people: the Squad tab. */
val PeopleIcon: ImageVector by lazy {
    stroke("People") {
        moveTo(12.2f, 8f); arcTo(3.2f, 3.2f, 0f, true, true, 5.8f, 8f); arcTo(3.2f, 3.2f, 0f, true, true, 12.2f, 8f); close()
        moveTo(3.5f, 19.5f); arcTo(5.5f, 5.5f, 0f, false, true, 14.5f, 19.5f)
        moveTo(15.5f, 5.2f); arcTo(3f, 3f, 0f, false, true, 15.5f, 10.8f)
        moveTo(17.5f, 13.8f); arcTo(5.5f, 5.5f, 0f, false, true, 20.5f, 19.5f)
    }
}

/** A raised fist: the nudge. */
val FistIcon: ImageVector by lazy {
    stroke("Fist") {
        moveTo(7f, 11f); verticalLineTo(8.5f); arcTo(1.5f, 1.5f, 0f, false, true, 10f, 8.5f); verticalLineTo(11f)
        moveTo(10f, 10f); verticalLineTo(7.5f); arcTo(1.5f, 1.5f, 0f, false, true, 13f, 7.5f); verticalLineTo(10f)
        moveTo(13f, 10f); verticalLineTo(8f); arcTo(1.5f, 1.5f, 0f, false, true, 16f, 8f); verticalLineTo(11f)
        moveTo(16f, 10.5f); arcTo(1.5f, 1.5f, 0f, false, true, 19f, 10.5f); verticalLineTo(14f)
        arcTo(6f, 6f, 0f, false, true, 13f, 20f); horizontalLineTo(11.5f)
        arcTo(5.5f, 5.5f, 0f, false, true, 6f, 14.5f); verticalLineTo(12f); arcTo(1.5f, 1.5f, 0f, false, true, 9f, 12f)
    }
}

/** Two sheets: copy the squad code. */
val CopyIcon: ImageVector by lazy {
    stroke("Copy") {
        moveTo(11f, 9f); horizontalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 20f, 11f); verticalLineTo(18f)
        arcTo(2f, 2f, 0f, false, true, 18f, 20f); horizontalLineTo(11f); arcTo(2f, 2f, 0f, false, true, 9f, 18f)
        verticalLineTo(11f); arcTo(2f, 2f, 0f, false, true, 11f, 9f); close()
        moveTo(5f, 15f); verticalLineTo(5f); arcTo(2f, 2f, 0f, false, true, 7f, 3f); horizontalLineTo(15f)
    }
}

/** Moon and a star: the 9 pm wrap. */
val MoonStarIcon: ImageVector by lazy {
    stroke("MoonStar") {
        moveTo(20f, 14.5f); arcTo(8f, 8f, 0f, true, true, 9.5f, 4f); arcTo(6.5f, 6.5f, 0f, false, false, 20f, 14.5f); close()
        moveTo(17f, 3f); verticalLineTo(7f); moveTo(15f, 5f); horizontalLineTo(19f)
    }
}

/** Price tag for label scans (a packaged food's ingredients / nutrition label). */
val TagIcon: ImageVector by lazy {
    stroke("Tag") {
        moveTo(3f, 12f); verticalLineTo(5f); arcTo(2f, 2f, 0f, false, true, 5f, 3f); horizontalLineTo(12f)
        lineTo(20.6f, 11.6f); arcTo(2f, 2f, 0f, false, true, 20.6f, 14.4f); lineTo(14.4f, 20.6f)
        arcTo(2f, 2f, 0f, false, true, 11.6f, 20.6f); close()
        moveTo(8f, 7.5f); arcTo(0.5f, 0.5f, 0f, true, true, 8f, 8.5f); arcTo(0.5f, 0.5f, 0f, true, true, 8f, 7.5f)
    }
}

// ---- v2.3: activity icons for the Exercise picker ----

/** Walking figure. */
val WalkIcon: ImageVector by lazy {
    stroke("Walk") {
        moveTo(13f, 4f); arcTo(1.5f, 1.5f, 0f, true, true, 12.9f, 4f); close()
        moveTo(12f, 8f); lineTo(11f, 14f); lineTo(13f, 17f); lineTo(14f, 21f)
        moveTo(11f, 14f); lineTo(9f, 21f)
        moveTo(12f, 8f); lineTo(9f, 10f); lineTo(8f, 13f)
        moveTo(12f, 8f); lineTo(14f, 11f); lineTo(16f, 12f)
    }
}

/** Bicycle. */
val CycleIcon: ImageVector by lazy {
    stroke("Cycle") {
        moveTo(9f, 16f); arcTo(3.5f, 3.5f, 0f, true, true, 2f, 16f); arcTo(3.5f, 3.5f, 0f, true, true, 9f, 16f)
        moveTo(22f, 16f); arcTo(3.5f, 3.5f, 0f, true, true, 15f, 16f); arcTo(3.5f, 3.5f, 0f, true, true, 22f, 16f)
        moveTo(5.5f, 16f); lineTo(9f, 9f); horizontalLineTo(15f); lineTo(18.5f, 16f)
        moveTo(9f, 9f); lineTo(12f, 16f); lineTo(15f, 9f); moveTo(8f, 6f); horizontalLineTo(11f)
    }
}

/** Cricket bat and ball. */
val CricketIcon: ImageVector by lazy {
    stroke("Cricket") {
        moveTo(18f, 3f); lineTo(21f, 6f); lineTo(10f, 17f); lineTo(7f, 14f); close()
        moveTo(8.5f, 15.5f); lineTo(4f, 20f)
        moveTo(7f, 6.5f); arcTo(2f, 2f, 0f, true, true, 3f, 6.5f); arcTo(2f, 2f, 0f, true, true, 7f, 6.5f)
    }
}

/** Shuttlecock. */
val BadmintonIcon: ImageVector by lazy {
    stroke("Badminton") {
        moveTo(12f, 21f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 16f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 21f)
        moveTo(10f, 16.5f); lineTo(6f, 4f); horizontalLineTo(18f); lineTo(14f, 16.5f)
        moveTo(10f, 4f); lineTo(11f, 16f); moveTo(14f, 4f); lineTo(13f, 16f)
    }
}

/** Football. */
val FootballIcon: ImageVector by lazy {
    stroke("Football") {
        moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f)
        moveTo(12f, 8f); lineTo(15.5f, 10.5f); lineTo(14f, 14.5f); horizontalLineTo(10f); lineTo(8.5f, 10.5f); close()
        moveTo(12f, 8f); verticalLineTo(3f); moveTo(15.5f, 10.5f); lineTo(20.5f, 9f); moveTo(14f, 14.5f); lineTo(17f, 19f)
        moveTo(10f, 14.5f); lineTo(7f, 19f); moveTo(8.5f, 10.5f); lineTo(3.5f, 9f)
    }
}

/** Swimmer over waves. */
val SwimIcon: ImageVector by lazy {
    stroke("Swim") {
        moveTo(17f, 7f); arcTo(1.5f, 1.5f, 0f, true, true, 16.9f, 7f); close()
        moveTo(6f, 12f); lineTo(10f, 8f); lineTo(13f, 11f); lineTo(10f, 13f)
        moveTo(3f, 17f); curveTo(5f, 15.5f, 7f, 15.5f, 9f, 17f); curveTo(11f, 18.5f, 13f, 18.5f, 15f, 17f); curveTo(17f, 15.5f, 19f, 15.5f, 21f, 17f)
        moveTo(3f, 21f); curveTo(5f, 19.5f, 7f, 19.5f, 9f, 21f); curveTo(11f, 22.5f, 13f, 22.5f, 15f, 21f); curveTo(17f, 19.5f, 19f, 19.5f, 21f, 21f)
    }
}

/** Seated yoga figure. */
val YogaIcon: ImageVector by lazy {
    stroke("Yoga") {
        moveTo(13.5f, 4.5f); arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 4.5f); arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 4.5f)
        moveTo(12f, 8f); verticalLineTo(14f)
        moveTo(4f, 10f); lineTo(12f, 9f); lineTo(20f, 10f)
        moveTo(12f, 14f); lineTo(6f, 18f); horizontalLineTo(18f); lineTo(12f, 14f)
    }
}

/** A resistance band loop between two handles. */
val BandIcon: ImageVector by lazy {
    stroke("Band") {
        moveTo(4f, 6f); verticalLineTo(10f); moveTo(20f, 6f); verticalLineTo(10f)
        moveTo(4f, 8f); curveTo(8f, 20f, 16f, 20f, 20f, 8f)
        moveTo(4f, 8f); curveTo(8f, 14f, 16f, 14f, 20f, 8f)
    }
}

/** A glass of water. */
val GlassIcon: ImageVector by lazy {
    stroke("Glass") {
        moveTo(6f, 3f); horizontalLineTo(18f); lineTo(16.5f, 20f); arcTo(1.5f, 1.5f, 0f, false, true, 15f, 21f)
        horizontalLineTo(9f); arcTo(1.5f, 1.5f, 0f, false, true, 7.5f, 20f); close()
        moveTo(6.8f, 11f); horizontalLineTo(17.2f)
    }
}

/** A water bottle. */
val BottleIcon: ImageVector by lazy {
    stroke("Bottle") {
        moveTo(10f, 2f); horizontalLineTo(14f); verticalLineTo(5f); moveTo(10f, 2f); verticalLineTo(5f)
        moveTo(10f, 5f); lineTo(8f, 8f); verticalLineTo(20f); arcTo(2f, 2f, 0f, false, false, 10f, 22f)
        horizontalLineTo(14f); arcTo(2f, 2f, 0f, false, false, 16f, 20f); verticalLineTo(8f); lineTo(14f, 5f); close()
        moveTo(8f, 12f); horizontalLineTo(16f)
    }
}

/** Arrows for the Progress change tables. */
val ArrowUpIcon: ImageVector by lazy { stroke("ArrowUp") { moveTo(12f, 19f); verticalLineTo(5f); moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f) } }
val ArrowDownIcon: ImageVector by lazy { stroke("ArrowDown") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f) } }
val ArrowFlatIcon: ImageVector by lazy { stroke("ArrowFlat") { moveTo(5f, 12f); horizontalLineTo(19f); moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f) } }

/** Picks an icon for an activity by name; flame when nothing matches. */
fun activityIcon(name: String, code: String? = null): ImageVector {
    val n = name.lowercase()
    return when {
        code?.startsWith("LI-BAND") == true || "band" in n -> BandIcon
        "run" in n || "jog" in n || "sprint" in n -> RunIcon
        "walk" in n || "hike" in n || "stair" in n -> WalkIcon
        "cycl" in n || "bicycl" in n || "bike" in n || "spin" in n -> CycleIcon
        "cricket" in n -> CricketIcon
        "badminton" in n || "tennis" in n || "squash" in n -> BadmintonIcon
        "football" in n || "soccer" in n || "futsal" in n || "basketball" in n -> FootballIcon
        "swim" in n -> SwimIcon
        "yoga" in n || "stretch" in n || "pilates" in n -> YogaIcon
        "gym" in n || "weight" in n || "lift" in n || "strength" in n -> DumbbellIcon
        else -> FlameIcon
    }
}

/** v2.5: a push-up figure, for bodyweight sessions. */
val BodyweightIcon: ImageVector by lazy {
    stroke("Bodyweight") {
        moveTo(20.5f, 9f); arcTo(1.5f, 1.5f, 0f, true, true, 17.5f, 9f); arcTo(1.5f, 1.5f, 0f, true, true, 20.5f, 9f)
        moveTo(17f, 11f); lineTo(4f, 15f)
        moveTo(15f, 11.6f); verticalLineTo(18f)
        moveTo(3f, 18f); horizontalLineTo(21f)
    }
}

/** v2.5: the Workout type picker's icon for each kind. */
fun workoutKindIcon(kind: String, name: String = ""): ImageVector = when (kind) {
    "gym" -> DumbbellIcon
    "bodyweight" -> BodyweightIcon
    "cardio" -> if (name.isNotBlank()) activityIcon(name).takeIf { it != FlameIcon } ?: RunIcon else RunIcon
    "sport" -> if (name.isNotBlank()) activityIcon(name).takeIf { it != FlameIcon } ?: FootballIcon else FootballIcon
    "yoga" -> YogaIcon
    else -> BandIcon
}

// ---- v2.6: squad icons (Cal AI-style pre-made group photos: a white mark on a gradient disc) ----

private fun circle(b: androidx.compose.ui.graphics.vector.PathBuilder, cx: Float, cy: Float, r: Float) {
    b.moveTo(cx - r, cy); b.arcTo(r, r, 0f, true, true, cx + r, cy); b.arcTo(r, r, 0f, true, true, cx - r, cy); b.close()
}

/** A filled mark with an optional stroked overlay (stems, handles, veins). */
private fun mark(name: String, fillPath: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit, strokePath: (androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit)? = null) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero, pathBuilder = fillPath)
        if (strokePath != null) path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = strokePath)
    }.build()

val BicepsMark: ImageVector by lazy {
    mark("Biceps", {
        // shoulder → bicep bulge → forearm up to the fist → elbow → back along the underside
        moveTo(2f, 20.5f); verticalLineTo(14.5f); curveTo(2f, 12.5f, 3.5f, 11.5f, 5.5f, 11.5f)
        curveTo(6f, 7f, 10.5f, 6.5f, 12.8f, 9.8f); lineTo(13f, 7f); lineTo(12f, 4.8f)
        curveTo(11.8f, 3.6f, 12.8f, 2.7f, 14f, 3f); lineTo(16.8f, 3.8f); curveTo(18f, 4.2f, 18.2f, 5.4f, 17.4f, 6.2f)
        lineTo(16.2f, 7.2f); lineTo(17.5f, 11.2f); curveTo(20.5f, 12.5f, 21.5f, 16f, 19.5f, 18.3f)
        curveTo(18f, 20f, 15f, 20.5f, 12f, 20.5f); close()
    })
}

val DumbbellMark: ImageVector by lazy {
    mark("DumbbellFill", {
        moveTo(6f, 11f); horizontalLineTo(18f); verticalLineTo(13f); horizontalLineTo(6f); close()
        moveTo(3.5f, 7.5f); horizontalLineTo(6.5f); verticalLineTo(16.5f); horizontalLineTo(3.5f); close()
        moveTo(17.5f, 7.5f); horizontalLineTo(20.5f); verticalLineTo(16.5f); horizontalLineTo(17.5f); close()
        moveTo(1.5f, 9.5f); horizontalLineTo(3.5f); verticalLineTo(14.5f); horizontalLineTo(1.5f); close()
        moveTo(20.5f, 9.5f); horizontalLineTo(22.5f); verticalLineTo(14.5f); horizontalLineTo(20.5f); close()
    })
}

val SaladMark: ImageVector by lazy {
    mark("Salad", {
        moveTo(3f, 11.5f); horizontalLineTo(21f); curveTo(21f, 16.5f, 17f, 20.5f, 12f, 20.5f); curveTo(7f, 20.5f, 3f, 16.5f, 3f, 11.5f); close()
        moveTo(7.5f, 10.5f); curveTo(6.5f, 7.5f, 8.5f, 5f, 11f, 5.8f); curveTo(11.2f, 8f, 10f, 10f, 7.5f, 10.5f); close()
        moveTo(12.5f, 10.5f); curveTo(12.8f, 7.2f, 15.5f, 5.5f, 18f, 6.6f); curveTo(17.5f, 8.8f, 15.2f, 10.5f, 12.5f, 10.5f); close()
    })
}

val CherryMark: ImageVector by lazy {
    mark("Cherry", { circle(this, 7.5f, 16.5f, 4f); circle(this, 16.5f, 15.5f, 4f) }) {
        moveTo(7.5f, 12.5f); curveTo(8.5f, 8f, 11f, 5f, 14f, 3.5f); moveTo(16.5f, 11.5f); curveTo(16f, 8f, 15f, 5.5f, 14f, 3.5f)
        moveTo(14f, 3.5f); curveTo(16f, 2.5f, 18.5f, 3f, 19.5f, 4.5f)
    }
}

val AppleMark: ImageVector by lazy {
    mark("Apple", {
        moveTo(12f, 7.5f); curveTo(9f, 6f, 4.5f, 7f, 4.5f, 12.5f); curveTo(4.5f, 17f, 8f, 21f, 10f, 21f)
        curveTo(11f, 21f, 11.5f, 20.5f, 12f, 20.5f); curveTo(12.5f, 20.5f, 13f, 21f, 14f, 21f)
        curveTo(16f, 21f, 19.5f, 17f, 19.5f, 12.5f); curveTo(19.5f, 7f, 15f, 6f, 12f, 7.5f); close()
        moveTo(12.3f, 6.2f); curveTo(12.3f, 4.2f, 13.8f, 2.6f, 15.8f, 2.6f); curveTo(15.8f, 4.6f, 14.3f, 6.2f, 12.3f, 6.2f); close()
    })
}

val BoltMark: ImageVector by lazy {
    mark("Bolt", { moveTo(13.5f, 2f); lineTo(4f, 14f); horizontalLineTo(11f); lineTo(10f, 22f); lineTo(20f, 9.5f); horizontalLineTo(13f); close() })
}

val TrophyMark: ImageVector by lazy {
    mark("Trophy", {
        moveTo(7f, 3f); horizontalLineTo(17f); verticalLineTo(9f); curveTo(17f, 12f, 15f, 14f, 12f, 14f); curveTo(9f, 14f, 7f, 12f, 7f, 9f); close()
        moveTo(11f, 13.5f); horizontalLineTo(13f); verticalLineTo(18f); horizontalLineTo(11f); close()
        moveTo(7.5f, 18f); horizontalLineTo(16.5f); verticalLineTo(21f); horizontalLineTo(7.5f); close()
    }) {
        moveTo(7f, 5f); horizontalLineTo(4f); verticalLineTo(7f); curveTo(4f, 9f, 5.5f, 10.5f, 7.5f, 10.5f)
        moveTo(17f, 5f); horizontalLineTo(20f); verticalLineTo(7f); curveTo(20f, 9f, 18.5f, 10.5f, 16.5f, 10.5f)
    }
}

val HeartMark: ImageVector by lazy {
    mark("Heart", {
        moveTo(12f, 21f); curveTo(12f, 21f, 3f, 15f, 3f, 9f); curveTo(3f, 6f, 5.2f, 4f, 7.8f, 4f); curveTo(9.6f, 4f, 11.1f, 5f, 12f, 6.4f)
        curveTo(12.9f, 5f, 14.4f, 4f, 16.2f, 4f); curveTo(18.8f, 4f, 21f, 6f, 21f, 9f); curveTo(21f, 15f, 12f, 21f, 12f, 21f); close()
    })
}

val MountainMark: ImageVector by lazy {
    mark("Mountain", { moveTo(1.5f, 20f); lineTo(8.5f, 7.5f); lineTo(13f, 14f); lineTo(16f, 10f); lineTo(22.5f, 20f); close() })
}

val LeafMark: ImageVector by lazy {
    mark("Leaf", { moveTo(4.5f, 19.5f); curveTo(4.5f, 10f, 10.5f, 4f, 20f, 4f); curveTo(20f, 13.5f, 14f, 19.5f, 4.5f, 19.5f); close() }) {
        moveTo(3f, 21f); lineTo(14f, 10f)
    }
}

/** One pre-made squad icon: a key saved in groups.icon, its name, the mark, and the disc gradient. */
data class SquadIconPreset(val key: String, val label: String, val icon: ImageVector, val from: Color, val to: Color)

/** The 12 squad icons of the create flow (keys shared with the web app). */
val SQUAD_ICONS: List<SquadIconPreset> by lazy {
    listOf(
        SquadIconPreset("biceps", "Biceps", BicepsMark, Color(0xFFD7261E), Color(0xFFF7797D)),
        SquadIconPreset("salad", "Salad", SaladMark, Color(0xFF2E8B1E), Color(0xFF9ACD6B)),
        SquadIconPreset("cherry", "Cherry", CherryMark, Color(0xFF8E1B3A), Color(0xFFC2566E)),
        SquadIconPreset("dumbbell", "Dumbbell", DumbbellMark, Color(0xFF34457A), Color(0xFF6F86C9)),
        SquadIconPreset("flame", "Flame", FlameIcon, Color(0xFFF26B1D), Color(0xFFFFB24D)),
        SquadIconPreset("run", "Run", RunIcon, Color(0xFF0F8C8C), Color(0xFF4FD1C5)),
        SquadIconPreset("apple", "Apple", AppleMark, Color(0xFF3FA34D), Color(0xFFA8E063)),
        SquadIconPreset("bolt", "Bolt", BoltMark, Color(0xFFE08E0B), Color(0xFFF8D71C)),
        SquadIconPreset("trophy", "Trophy", TrophyMark, Color(0xFFB8860B), Color(0xFFF3C74E)),
        SquadIconPreset("heart", "Heart", HeartMark, Color(0xFFE0357B), Color(0xFFFF8FB1)),
        SquadIconPreset("mountain", "Mountain", MountainMark, Color(0xFF5B3CC4), Color(0xFF9F7AEA)),
        SquadIconPreset("leaf", "Leaf", LeafMark, Color(0xFF1F7A4C), Color(0xFF6EE7A8)),
    )
}

/* ---- v2.10: Lucide line icons ----
 * Path data copied from Lucide (https://lucide.dev), ISC License,
 * Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of Feather (MIT);
 * all other copyright (c) for Lucide are held by Lucide Contributors 2022.
 * The strings are the same ones as the web's LUCIDE map in src/components/icons.tsx (circles and
 * rects written as paths), parsed with Compose's PathParser, so both platforms draw identical icons.
 * Stroke 2, round caps and joins, as Lucide ships them; tint with the current text colour.
 */
object Lucide {
    val sunrise = listOf("M12 2v8", "M4.93 10.93l1.41 1.41", "M2 18h2", "M20 18h2", "M19.07 10.93l-1.41 1.41", "M22 22H2", "M8 6l4-4 4 4", "M16 18a4 4 0 0 0-8 0")
    val sun = listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M12 2v2", "M12 20v2", "M4.93 4.93l1.41 1.41", "M17.66 17.66l1.41 1.41", "M2 12h2", "M20 12h2", "M6.34 17.66l-1.41 1.41", "M19.07 4.93l-1.41 1.41")
    val moon = listOf("M12 3a6 6 0 0 0 9 9a9 9 0 1 1-9-9z")
    val cookie = listOf("M12 2a10 10 0 1 0 10 10a4 4 0 0 1-5-5a4 4 0 0 1-5-5", "M8.5 8.5v.01", "M16 15.5v.01", "M12 12v.01", "M11 17v.01", "M7 14v.01")
    val crown = listOf("M2 4l3 12h14l3-12-6 7-4-7-4 7-6-7z", "M5 20h14")
    val trophy = listOf(
        "M6 9H4.5a2.5 2.5 0 0 1 0-5H6",
        "M18 9h1.5a2.5 2.5 0 0 0 0-5H18",
        "M4 22h16",
        "M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22",
        "M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22",
        "M18 2H6v7a6 6 0 0 0 12 0V2z",
    )
    val flag = listOf("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7")
    val egg = listOf("M12 22c6.23-.05 7.87-5.57 7.5-10-.36-4.34-3.95-9.96-7.5-10-3.55.04-7.14 5.66-7.5 10-.37 4.43 1.27 9.95 7.5 10z")
    val clipboardList = listOf(
        "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z",
        "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
        "M12 11h4", "M12 16h4", "M8 11h.01", "M8 16h.01",
    )
    val droplet = listOf("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z")
}

/** An ImageVector from Lucide path strings (24 viewBox, stroke 2, round caps / joins). */
fun lucide(name: String, paths: List<String>): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

val SunriseIcon: ImageVector by lazy { lucide("Sunrise", Lucide.sunrise) }
val SunIcon: ImageVector by lazy { lucide("Sun", Lucide.sun) }
val MoonLineIcon: ImageVector by lazy { lucide("Moon", Lucide.moon) }
val CookieIcon: ImageVector by lazy { lucide("Cookie", Lucide.cookie) }
val CrownIcon: ImageVector by lazy { lucide("Crown", Lucide.crown) }
val TrophyIcon: ImageVector by lazy { lucide("Trophy", Lucide.trophy) }
val FlagIcon: ImageVector by lazy { lucide("Flag", Lucide.flag) }
val EggIcon: ImageVector by lazy { lucide("Egg", Lucide.egg) }
val ClipboardListIcon: ImageVector by lazy { lucide("ClipboardList", Lucide.clipboardList) }
val DropletIcon: ImageVector by lazy { lucide("Droplet", Lucide.droplet) }

/** Speech bubble, the same path as the web's Chat icon (WhatsApp invite action). */
val ChatIcon: ImageVector by lazy { lucide("Chat", listOf("M4 18.5l1.3-3.6A8 8 0 1 1 8.6 19z")) }

/** A meal section's icon: Breakfast sunrise, Lunch sun, Dinner moon, Snacks cookie (the web's MealTypeIcon). */
fun mealTypeIcon(key: String): ImageVector = when (key) {
    "breakfast" -> SunriseIcon
    "lunch" -> SunIcon
    "dinner" -> MoonLineIcon
    else -> CookieIcon
}

/** A challenge kind's tag icon: train days dumbbell, protein days egg, log every day clipboard (the web's ChallengeKindIcon). */
fun challengeKindIcon(kind: String): ImageVector = when (kind) {
    "train_days" -> DumbbellIcon
    "protein_days" -> EggIcon
    "log_days" -> ClipboardListIcon
    else -> FlagIcon
}

/** Plus, the same path as the web's Plus icon ("Start a challenge"). */
val PlusIcon: ImageVector by lazy { stroke("Plus") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } }
