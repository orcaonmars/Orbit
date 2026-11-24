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
}
