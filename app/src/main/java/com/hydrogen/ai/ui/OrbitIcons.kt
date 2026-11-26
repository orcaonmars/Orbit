package com.hydrogen.ai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small original outline icon family, 24dp viewport, 1.75dp strokes and rounded ends.
 * Keep the visual style consistent when adding further icons.
 */
object OrbitIcons {
    val StarOutline: ImageVector = starVector(false)
    val StarFilled: ImageVector = starVector(true)

    private fun starVector(filled: Boolean): ImageVector = ImageVector.Builder(
        name = if (filled) "OrbitStarFilled" else "OrbitStarOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 3f)
            lineTo(14.8f, 8.7f)
            lineTo(21.2f, 9.6f)
            lineTo(16.6f, 14f)
            lineTo(17.7f, 20.4f)
            lineTo(12f, 17.4f)
            lineTo(6.3f, 20.4f)
            lineTo(7.4f, 14f)
            lineTo(2.8f, 9.6f)
            lineTo(9.2f, 8.7f)
            close()
        }
    }.build()

    val ArrowUpward: ImageVector = ImageVector.Builder(
        name = "OrbitArrowUpward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 20f)
            lineTo(12f, 4f)
            moveTo(5.5f, 10.5f)
            lineTo(12f, 4f)
            lineTo(18.5f, 10.5f)
        }
    }.build()

    val Check: ImageVector = ImageVector.Builder(
        name = "OrbitCheck",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.5f, 12.5f)
            lineTo(9.5f, 17.5f)
            lineTo(19.5f, 6.5f)
        }
    }.build()

    /** Consistent original outline search glyph for model and chat filtering. */
    val Search: ImageVector = ImageVector.Builder(
        name = "OrbitSearch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11f, 4f)
            curveTo(7.1f, 4f, 4f, 7.1f, 4f, 11f)
            curveTo(4f, 14.9f, 7.1f, 18f, 11f, 18f)
            curveTo(14.9f, 18f, 18f, 14.9f, 18f, 11f)
            curveTo(18f, 7.1f, 14.9f, 4f, 11f, 4f)
            moveTo(16.5f, 16.5f)
            lineTo(21f, 21f)
        }
    }.build()

    /** Original close/clear glyph. */
    val Close: ImageVector = ImageVector.Builder(
        name = "OrbitClose",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 5f)
            lineTo(19f, 19f)
            moveTo(19f, 5f)
            lineTo(5f, 19f)
        }
    }.build()

    /** Original cloud glyph for servers reached over the network. */
    val Cloud: ImageVector = ImageVector.Builder(
        name = "OrbitCloud",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 18f)
            lineTo(17f, 18f)
            curveTo(19.3f, 18f, 21f, 16.3f, 21f, 14f)
            curveTo(21f, 11.7f, 19.3f, 10f, 17f, 10f)
            curveTo(16.7f, 7.2f, 14.4f, 5f, 11.5f, 5f)
            curveTo(8.5f, 5f, 6.1f, 7.2f, 5.8f, 10.1f)
            curveTo(3.7f, 10.4f, 2f, 12.1f, 2f, 14.1f)
            curveTo(2f, 16.3f, 3.7f, 18f, 5.9f, 18f)
            lineTo(7f, 18f)
        }
    }.build()

    /** Original display glyph for servers running on the local machine or LAN. */
    val Computer: ImageVector = ImageVector.Builder(
        name = "OrbitComputer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 5.5f)
            lineTo(21f, 5.5f)
            lineTo(21f, 16.5f)
            lineTo(3f, 16.5f)
            close()
            moveTo(12f, 16.5f)
            lineTo(12f, 20f)
            moveTo(9f, 20f)
            lineTo(15f, 20f)
        }
    }.build()

    /** Original key glyph for saved connections that still need a credential. */
    val Key: ImageVector = ImageVector.Builder(
        name = "OrbitKey",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 8f)
            curveTo(5.8f, 8f, 4f, 9.8f, 4f, 12f)
            curveTo(4f, 14.2f, 5.8f, 16f, 8f, 16f)
            curveTo(10.2f, 16f, 12f, 14.2f, 12f, 12f)
            curveTo(12f, 9.8f, 10.2f, 8f, 8f, 8f)
            moveTo(12f, 12f)
            lineTo(21f, 12f)
            moveTo(18f, 12f)
            lineTo(18f, 15f)
            moveTo(15f, 12f)
            lineTo(15f, 14f)
        }
    }.build()

    /** Original pencil glyph for renaming a saved connection. */
    val Edit: ImageVector = ImageVector.Builder(
        name = "OrbitEdit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 20f)
            lineTo(4.6f, 16.1f)
            lineTo(16.1f, 4.6f)
            curveTo(16.9f, 3.8f, 18.2f, 3.8f, 19f, 4.6f)
            lineTo(19.4f, 5f)
            curveTo(20.2f, 5.8f, 20.2f, 7.1f, 19.4f, 7.9f)
            lineTo(7.9f, 19.4f)
            close()
            moveTo(14.5f, 6.2f)
            lineTo(17.8f, 9.5f)
        }
    }.build()

    /** Original sliders glyph for connection management. */
    val Tune: ImageVector = ImageVector.Builder(
        name = "OrbitTune",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 7f)
            lineTo(8f, 7f)
            moveTo(12f, 7f)
            lineTo(20f, 7f)
            moveTo(10f, 5f)
            curveTo(8.9f, 5f, 8f, 5.9f, 8f, 7f)
            curveTo(8f, 8.1f, 8.9f, 9f, 10f, 9f)
            curveTo(11.1f, 9f, 12f, 8.1f, 12f, 7f)
            curveTo(12f, 5.9f, 11.1f, 5f, 10f, 5f)
            moveTo(4f, 12f)
            lineTo(13f, 12f)
            moveTo(17f, 12f)
            lineTo(20f, 12f)
            moveTo(15f, 10f)
            curveTo(13.9f, 10f, 13f, 10.9f, 13f, 12f)
            curveTo(13f, 13.1f, 13.9f, 14f, 15f, 14f)
            curveTo(16.1f, 14f, 17f, 13.1f, 17f, 12f)
            curveTo(17f, 10.9f, 16.1f, 10f, 15f, 10f)
            moveTo(4f, 17f)
            lineTo(5f, 17f)
            moveTo(9f, 17f)
            lineTo(20f, 17f)
            moveTo(7f, 15f)
            curveTo(5.9f, 15f, 5f, 15.9f, 5f, 17f)
            curveTo(5f, 18.1f, 5.9f, 19f, 7f, 19f)
            curveTo(8.1f, 19f, 9f, 18.1f, 9f, 17f)
            curveTo(9f, 15.9f, 8.1f, 15f, 7f, 15f)
        }
    }.build()

    val Add: ImageVector = ImageVector.Builder(
        name = "OrbitAdd",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }.build()

    val Menu: ImageVector = ImageVector.Builder(
        name = "OrbitMenu",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 18f)
            lineTo(20f, 18f)
        }
    }.build()

    val ChevronDown: ImageVector = ImageVector.Builder(
        name = "OrbitChevronDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    }.build()

    val ChevronUp: ImageVector = ImageVector.Builder(
        name = "OrbitChevronUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 15f)
            lineTo(12f, 9f)
            lineTo(18f, 15f)
        }
    }.build()

    val Mic: ImageVector = ImageVector.Builder(
        name = "OrbitMic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 5f)
            curveTo(9f, 1f, 15f, 1f, 15f, 5f)
            lineTo(15f, 12f)
            curveTo(15f, 16f, 9f, 16f, 9f, 12f)
            close()
            moveTo(5f, 10f)
            lineTo(5f, 12f)
            curveTo(5f, 21f, 19f, 21f, 19f, 12f)
            lineTo(19f, 10f)
            moveTo(12f, 19f)
            lineTo(12f, 22f)
        }
    }.build()

    val Delete: ImageVector = ImageVector.Builder(
        name = "OrbitDelete",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 7f)
            lineTo(19f, 7f)
            moveTo(9f, 7f)
            lineTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 7f)
            moveTo(7f, 7f)
            lineTo(8f, 20f)
            lineTo(16f, 20f)
            lineTo(17f, 7f)
            moveTo(10f, 10f)
            lineTo(10.5f, 17f)
            moveTo(14f, 10f)
            lineTo(13.5f, 17f)
        }
    }.build()

    private fun line(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()

    val ChevronRight: ImageVector by lazy {
        line("OrbitChevronRight") { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    }

    val ArrowBack: ImageVector by lazy {
        line("OrbitArrowBack") {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }

    /** Three dots drawn as short round-capped strokes so they stay in the 1.75 family. */
    val MoreHorizontal: ImageVector by lazy {
        line("OrbitMoreHorizontal") {
            moveTo(5.5f, 12f); lineTo(6.5f, 12f)
            moveTo(11.5f, 12f); lineTo(12.5f, 12f)
            moveTo(17.5f, 12f); lineTo(18.5f, 12f)
        }
    }

    /** Two offset rounded sheets. */
    val Copy: ImageVector by lazy {
        line("OrbitCopy") {
            moveTo(10f, 8f); lineTo(18f, 8f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); lineTo(20f, 18f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); lineTo(10f, 20f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); lineTo(8f, 10f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
            moveTo(16f, 8f); lineTo(16f, 6f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, -2f); lineTo(6f, 4f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, 2f); lineTo(4f, 14f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); lineTo(8f, 16f)
        }
    }

    /** Box with an arrow leaving upward. */
    val Share: ImageVector by lazy {
        line("OrbitShare") {
            moveTo(12f, 15f); lineTo(12f, 3f)
            moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
            moveTo(8f, 11f); lineTo(6f, 11f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, 2f); lineTo(4f, 19f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); lineTo(18f, 21f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); lineTo(20f, 13f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, -2f); lineTo(16f, 11f)
        }
    }

    /** Open circle with an arrowhead, for try again. */
    val Refresh: ImageVector by lazy {
        line("OrbitRefresh") {
            moveTo(20f, 12f)
            arcToRelative(8f, 8f, 0f, true, true, -2.34f, -5.66f)
            moveTo(20f, 4f); lineTo(20f, 8f); lineTo(16f, 8f)
        }
    }

    /** Speaker with two sound waves, for read aloud. */
    val Speaker: ImageVector by lazy {
        line("OrbitSpeaker") {
            moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f)
            lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
            moveTo(15.5f, 9.5f); arcToRelative(3.5f, 3.5f, 0f, false, true, 0f, 5f)
            moveTo(18f, 7f); arcToRelative(7f, 7f, 0f, false, true, 0f, 10f)
        }
    }

    /** Rounded square, for stop reading or stop generating. */
    val Stop: ImageVector by lazy {
        line("OrbitStop") {
            moveTo(9f, 7f); lineTo(15f, 7f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); lineTo(17f, 15f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); lineTo(9f, 17f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); lineTo(7f, 9f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
        }
    }

    /** Two columns side by side, for compare replies. */
    val Compare: ImageVector by lazy {
        line("OrbitCompare") {
            moveTo(6f, 4f); lineTo(9f, 4f); arcToRelative(1f, 1f, 0f, false, true, 1f, 1f); lineTo(10f, 19f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, 1f); lineTo(6f, 20f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); lineTo(4f, 6f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
            moveTo(15f, 4f); lineTo(18f, 4f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); lineTo(20f, 18f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); lineTo(15f, 20f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, -1f); lineTo(14f, 5f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, -1f); close()
        }
    }

    /** A line that splits in two, for branch from here. */
    val Branch: ImageVector by lazy {
        line("OrbitBranch") {
            moveTo(7f, 4f); lineTo(7f, 20f)
            moveTo(7f, 14f); curveTo(7f, 10f, 17f, 12f, 17f, 7f)
            moveTo(14f, 7f); lineTo(17f, 4f); lineTo(20f, 7f)
        }
    }

    /** Push pin outline. */
    val Pin: ImageVector by lazy {
        line("OrbitPin") {
            moveTo(9f, 4f); lineTo(15f, 4f)
            moveTo(10f, 4f); lineTo(10f, 10f); lineTo(7f, 14f); lineTo(17f, 14f); lineTo(14f, 10f); lineTo(14f, 4f)
            moveTo(12f, 14f); lineTo(12f, 20f)
        }
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.folderOutline() {
        moveTo(3f, 7f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); lineTo(9f, 5f); lineTo(11f, 7f)
        lineTo(19f, 7f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); lineTo(21f, 17f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); lineTo(5f, 19f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); close()
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t); lineTo(r - rad, t); arcToRelative(rad, rad, 0f, false, true, rad, rad); lineTo(r, b - rad)
        arcToRelative(rad, rad, 0f, false, true, -rad, rad); lineTo(l + rad, b)
        arcToRelative(rad, rad, 0f, false, true, -rad, -rad); lineTo(l, t + rad)
        arcToRelative(rad, rad, 0f, false, true, rad, -rad); close()
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r); arcToRelative(r, r, 0f, true, true, 0f, 2 * r); arcToRelative(r, r, 0f, true, true, 0f, -2 * r)
    }

    val Folder: ImageVector by lazy { line("OrbitFolder") { folderOutline() } }

    val FolderAdd: ImageVector by lazy {
        line("OrbitFolderAdd") { folderOutline(); moveTo(12f, 10f); lineTo(12f, 16f); moveTo(9f, 13f); lineTo(15f, 13f) }
    }

    val FolderOff: ImageVector by lazy {
        line("OrbitFolderOff") { folderOutline(); moveTo(4f, 4f); lineTo(20f, 20f) }
    }

    /** Square with a pencil, for new chat. */
    val NewChat: ImageVector by lazy {
        line("OrbitNewChat") {
            moveTo(12f, 4f); lineTo(6f, 4f); arcToRelative(2f, 2f, 0f, false, false, -2f, 2f); lineTo(4f, 18f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); lineTo(18f, 20f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); lineTo(20f, 12f)
            moveTo(17.5f, 3.5f); lineTo(20.5f, 6.5f); lineTo(12f, 15f); lineTo(9f, 15f); lineTo(9f, 12f); close()
        }
    }

    val Archive: ImageVector by lazy {
        line("OrbitArchive") {
            roundRect(3f, 4f, 21f, 8f, 1f)
            moveTo(5f, 8f); lineTo(5f, 18f); arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); lineTo(17f, 20f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); lineTo(19f, 8f)
            moveTo(10f, 12f); lineTo(14f, 12f)
        }
    }

    val Unarchive: ImageVector by lazy {
        line("OrbitUnarchive") {
            roundRect(3f, 4f, 21f, 8f, 1f)
            moveTo(5f, 8f); lineTo(5f, 18f); arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); lineTo(17f, 20f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); lineTo(19f, 8f)
            moveTo(12f, 17f); lineTo(12f, 11f); moveTo(9.5f, 13.5f); lineTo(12f, 11f); lineTo(14.5f, 13.5f)
        }
    }

    /** Page with a folded corner and text lines. */
    val File: ImageVector by lazy {
        line("OrbitFile") {
            moveTo(14f, 3f); lineTo(7f, 3f); arcToRelative(2f, 2f, 0f, false, false, -2f, 2f); lineTo(5f, 19f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); lineTo(17f, 21f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); lineTo(19f, 8f); close()
            moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
            moveTo(9f, 13f); lineTo(15f, 13f); moveTo(9f, 17f); lineTo(13f, 17f)
        }
    }

    val Terminal: ImageVector by lazy {
        line("OrbitTerminal") {
            roundRect(3f, 4f, 21f, 20f, 2f)
            moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f); moveTo(12f, 15f); lineTo(17f, 15f)
        }
    }

    /** Four-point spark. */
    val Sparkle: ImageVector by lazy {
        line("OrbitSparkle") {
            moveTo(12f, 3f); curveTo(12.6f, 8.5f, 15.5f, 11.4f, 21f, 12f)
            curveTo(15.5f, 12.6f, 12.6f, 15.5f, 12f, 21f)
            curveTo(11.4f, 15.5f, 8.5f, 12.6f, 3f, 12f)
            curveTo(8.5f, 11.4f, 11.4f, 8.5f, 12f, 3f); close()
        }
    }

    /** Head and shoulders. */
    val Person: ImageVector by lazy {
        line("OrbitPerson") {
            circle(12f, 8f, 4f)
            moveTo(4.5f, 20f); curveTo(5.5f, 16.5f, 8.5f, 14.5f, 12f, 14.5f); curveTo(15.5f, 14.5f, 18.5f, 16.5f, 19.5f, 20f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        line("OrbitChevronLeft") { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
    }

    /** Gear: a hub inside a ring with six short teeth. */
    val Settings: ImageVector by lazy {
        line("OrbitSettings") {
            circle(12f, 12f, 3f)
            circle(12f, 12f, 6.5f)
            for (i in 0 until 6) {
                val a = Math.toRadians(i * 60.0)
                val c = kotlin.math.cos(a).toFloat()
                val sn = kotlin.math.sin(a).toFloat()
                moveTo(12f + 6.5f * c, 12f + 6.5f * sn); lineTo(12f + 9f * c, 12f + 9f * sn)
            }
        }
    }

    val Image: ImageVector by lazy {
        line("OrbitImage") {
            roundRect(3f, 4f, 21f, 20f, 2f)
            circle(9f, 9.5f, 1.5f)
            moveTo(21f, 15f); lineTo(16f, 10f); lineTo(5f, 20f)
        }
    }

    val Camera: ImageVector by lazy {
        line("OrbitCamera") {
            moveTo(3f, 9f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); lineTo(7.5f, 7f); lineTo(9f, 4.5f)
            lineTo(15f, 4.5f); lineTo(16.5f, 7f); lineTo(19f, 7f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            lineTo(21f, 18f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); lineTo(5f, 20f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); close()
            circle(12f, 13f, 3.5f)
        }
    }

    val Bookmark: ImageVector by lazy {
        line("OrbitBookmark") {
            moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 16.5f); lineTo(7f, 20f); close()
        }
    }

    /** Five bars of a sound wave, for voice chat. */
    val Waveform: ImageVector by lazy {
        line("OrbitWaveform") {
            moveTo(4f, 10.5f); lineTo(4f, 13.5f)
            moveTo(8f, 7f); lineTo(8f, 17f)
            moveTo(12f, 4f); lineTo(12f, 20f)
            moveTo(16f, 7f); lineTo(16f, 17f)
            moveTo(20f, 10.5f); lineTo(20f, 13.5f)
        }
    }

    /** Circle split down the middle, for appearance and theme. */
    val Contrast: ImageVector by lazy {
        line("OrbitContrast") {
            circle(12f, 12f, 9f)
            moveTo(12f, 3f); lineTo(12f, 21f)
            moveTo(12f, 7f); lineTo(16f, 7f); moveTo(12f, 11f); lineTo(18f, 11f); moveTo(12f, 15f); lineTo(17f, 15f)
        }
    }

    /** Circle with a lowercase i, for about and information. */
    val Info: ImageVector by lazy {
        line("OrbitInfo") {
            circle(12f, 12f, 9f)
            moveTo(12f, 11f); lineTo(12f, 16.5f)
            moveTo(12f, 7.5f); lineTo(12f, 7.6f)
        }
    }

    /** Triangle pointing right, for play. */
    val Play: ImageVector by lazy {
        line("OrbitPlay") {
            moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close()
        }
    }

    /** Two bars, for pause. */
    val Pause: ImageVector by lazy {
        line("OrbitPause") {
            moveTo(9f, 6f); lineTo(9f, 18f)
            moveTo(15f, 6f); lineTo(15f, 18f)
        }
    }

    /** Microphone with a slash, for muted. */
    val MicOff: ImageVector by lazy {
        line("OrbitMicOff") {
            moveTo(9f, 5f); curveTo(9f, 1f, 15f, 1f, 15f, 5f); lineTo(15f, 12f)
            curveTo(15f, 16f, 9f, 16f, 9f, 12f); close()
            moveTo(5f, 10f); lineTo(5f, 12f); curveTo(5f, 21f, 19f, 21f, 19f, 12f); lineTo(19f, 10f)
            moveTo(12f, 19f); lineTo(12f, 22f)
            moveTo(3f, 3f); lineTo(21f, 21f)
        }
    }

    /** Padlock, for things that stay private. */
    val Lock: ImageVector by lazy {
        line("OrbitLock") {
            roundRect(5f, 11f, 19f, 21f, 2f)
            moveTo(8f, 11f); lineTo(8f, 7.5f); curveTo(8f, 2.5f, 16f, 2.5f, 16f, 7.5f); lineTo(16f, 11f)
        }
    }

    /** Phone outline. */
    val Phone: ImageVector by lazy {
        line("OrbitPhone") {
            roundRect(6.5f, 2.5f, 17.5f, 21.5f, 2.5f)
            moveTo(11f, 18.5f); lineTo(13f, 18.5f)
        }
    }

    /** Eye with a slash, for nothing tracked. */
    val EyeOff: ImageVector by lazy {
        line("OrbitEyeOff") {
            moveTo(2.5f, 12f); curveTo(5f, 7f, 8.5f, 5f, 12f, 5f); curveTo(15.5f, 5f, 19f, 7f, 21.5f, 12f)
            curveTo(19f, 17f, 15.5f, 19f, 12f, 19f); curveTo(8.5f, 19f, 5f, 17f, 2.5f, 12f); close()
            circle(12f, 12f, 3f)
            moveTo(4f, 4f); lineTo(20f, 20f)
        }
    }

    /** Shield, for privacy and security. */
    val Shield: ImageVector by lazy {
        line("OrbitShield") {
            moveTo(12f, 3f); lineTo(19.5f, 6f); lineTo(19.5f, 11.5f)
            curveTo(19.5f, 16f, 16.5f, 19.5f, 12f, 21f)
            curveTo(7.5f, 19.5f, 4.5f, 16f, 4.5f, 11.5f); lineTo(4.5f, 6f); close()
        }
    }
}
