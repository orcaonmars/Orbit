package com.hydrogen.ai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Orbit design system icon library.
 * Modern, thin stroke (1.75dp), rounded caps and joins, built using standard Compose ImageVector API.
 */
object OrbitIcons {
    private val strokeColor = SolidColor(Color.Black)

    val Copy: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.Copy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Front box
        path(
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5.5f, 9f)
            lineTo(13.5f, 9f)
            quadTo(15f, 9f, 15f, 10.5f)
            lineTo(15f, 19f)
            quadTo(15f, 20.5f, 13.5f, 20.5f)
            lineTo(5.5f, 20.5f)
            quadTo(4f, 20.5f, 4f, 19f)
            lineTo(4f, 10.5f)
            quadTo(4f, 9f, 5.5f, 9f)
            close()
        }
        // Back card accent
        path(
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 6.5f)
            lineTo(9f, 5.5f)
            quadTo(9f, 4f, 10.5f, 4f)
            lineTo(18.5f, 4f)
            quadTo(20f, 4f, 20f, 5.5f)
            lineTo(20f, 13.5f)
            quadTo(20f, 15f, 18.5f, 15f)
            lineTo(17.5f, 15f)
        }
    }.build()

    val Check: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.Check",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = strokeColor,
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5f, 12f)
        lineTo(10f, 17f)
        lineTo(19f, 7f)
    }.build()

    val Back: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.Back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = strokeColor,
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19f, 12f)
        lineTo(5f, 12f)
        moveTo(11f, 6f)
        lineTo(5f, 12f)
        lineTo(11f, 18f)
    }.build()

    val Close: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.Close",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = strokeColor,
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18f, 6f)
        lineTo(6f, 18f)
        moveTo(6f, 6f)
        lineTo(18f, 18f)
    }.build()

    val Search: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.Search", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(stroke = strokeColor, strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(10.5f, 3.5f)
            curveTo(14.37f, 3.5f, 17.5f, 6.63f, 17.5f, 10.5f)
            curveTo(17.5f, 14.37f, 14.37f, 17.5f, 10.5f, 17.5f)
            curveTo(6.63f, 17.5f, 3.5f, 14.37f, 3.5f, 10.5f)
            curveTo(3.5f, 6.63f, 6.63f, 3.5f, 10.5f, 3.5f)
            close()
            moveTo(16f, 16f)
            lineTo(21f, 21f)
        }
    }.build()

    val Settings: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.Settings", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(stroke = strokeColor, strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 3f)
            lineTo(13.5f, 3f)
            lineTo(14.2f, 5.25f)
            lineTo(16f, 6.1f)
            lineTo(18.1f, 5.1f)
            lineTo(20f, 7f)
            lineTo(18.95f, 9.1f)
            lineTo(19.6f, 11f)
            lineTo(22f, 11.7f)
            lineTo(22f, 13.3f)
            lineTo(19.6f, 14f)
            lineTo(18.95f, 15.9f)
            lineTo(20f, 18f)
            lineTo(18.1f, 19.9f)
            lineTo(16f, 18.9f)
            lineTo(14.2f, 19.75f)
            lineTo(13.5f, 22f)
            lineTo(10.5f, 22f)
            lineTo(9.8f, 19.75f)
            lineTo(8f, 18.9f)
            lineTo(5.9f, 19.9f)
            lineTo(4f, 18f)
            lineTo(5.05f, 15.9f)
            lineTo(4.4f, 14f)
            lineTo(2f, 13.3f)
            lineTo(2f, 11.7f)
            lineTo(4.4f, 11f)
            lineTo(5.05f, 9.1f)
            lineTo(4f, 7f)
            lineTo(5.9f, 5.1f)
            lineTo(8f, 6.1f)
            lineTo(9.8f, 5.25f)
            lineTo(10.5f, 3f)
            close()
            moveTo(12f, 8.8f)
            curveTo(10.23f, 8.8f, 8.8f, 10.23f, 8.8f, 12f)
            curveTo(8.8f, 13.77f, 10.23f, 15.2f, 12f, 15.2f)
            curveTo(13.77f, 15.2f, 15.2f, 13.77f, 15.2f, 12f)
            curveTo(15.2f, 10.23f, 13.77f, 8.8f, 12f, 8.8f)
            close()
        }
    }.build()

    val NewChat: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.NewChat", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(stroke = strokeColor, strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 4.5f)
            lineTo(6f, 4.5f)
            quadTo(4.5f, 4.5f, 4.5f, 6f)
            lineTo(4.5f, 18f)
            quadTo(4.5f, 19.5f, 6f, 19.5f)
            lineTo(18f, 19.5f)
            quadTo(19.5f, 19.5f, 19.5f, 18f)
            lineTo(19.5f, 12f)
        }
        path(stroke = strokeColor, strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(11f, 13f)
            lineTo(18.7f, 5.3f)
            quadTo(19.5f, 4.5f, 20.3f, 5.3f)
            quadTo(21.1f, 6.1f, 20.3f, 6.9f)
            lineTo(12.6f, 14.6f)
            lineTo(10.5f, 15.2f)
            close()
        }
    }.build()

    val Send: ImageVector = ImageVector.Builder(
        name = "OrbitIcons.Send", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = strokeColor, strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(12f, 20f)
        lineTo(12f, 4f)
        moveTo(5.5f, 10.5f)
        lineTo(12f, 4f)
        lineTo(18.5f, 10.5f)
    }.build()
}
