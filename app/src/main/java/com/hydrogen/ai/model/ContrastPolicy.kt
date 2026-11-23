package com.hydrogen.ai.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure Kotlin WCAG 2.x color contrast and hex parsing utilities.
 * Zero Android or Compose dependencies; never throws.
 */
object ContrastPolicy {

    const val AA_NORMAL = 4.5
    const val AA_LARGE = 3.0

    fun relativeLuminance(argb: Int): Double {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return luminanceFromSrgb(r, g, b)
    }

    fun contrastRatio(foregroundArgb: Int, backgroundArgb: Int): Double {
        val fgAlphaInt = (foregroundArgb ushr 24) and 0xFF
        val bgR = ((backgroundArgb ushr 16) and 0xFF) / 255.0
        val bgG = ((backgroundArgb ushr 8) and 0xFF) / 255.0
        val bgB = (backgroundArgb and 0xFF) / 255.0

        val fgR = ((foregroundArgb ushr 16) and 0xFF) / 255.0
        val fgG = ((foregroundArgb ushr 8) and 0xFF) / 255.0
        val fgB = (foregroundArgb and 0xFF) / 255.0

        val lFg = if (fgAlphaInt >= 255) {
            luminanceFromSrgb(fgR, fgG, fgB)
        } else {
            val alpha = fgAlphaInt / 255.0
            val invAlpha = 1.0 - alpha
            luminanceFromSrgb(
                r = (fgR * alpha + bgR * invAlpha).coerceIn(0.0, 1.0),
                g = (fgG * alpha + bgG * invAlpha).coerceIn(0.0, 1.0),
                b = (fgB * alpha + bgB * invAlpha).coerceIn(0.0, 1.0)
            )
        }
        val lBg = luminanceFromSrgb(bgR, bgG, bgB)

        val lMax = max(lFg, lBg)
        val lMin = min(lFg, lBg)
        return ((lMax + 0.05) / (lMin + 0.05)).coerceIn(1.0, 21.0)
    }

    fun meetsAa(
        foregroundArgb: Int,
        backgroundArgb: Int,
        largeText: Boolean = false
    ): Boolean {
        val threshold = if (largeText) AA_LARGE else AA_NORMAL
        return contrastRatio(foregroundArgb, backgroundArgb) >= threshold
    }

    fun parseHex(hex: String): Int? {
        val digits: String
        val hasAlpha: Boolean
        when (hex.length) {
            6 -> {
                digits = hex
                hasAlpha = false
            }
            7 -> {
                if (hex[0] != '#') return null
                digits = hex.substring(1)
                hasAlpha = false
            }
            9 -> {
                if (hex[0] != '#') return null
                digits = hex.substring(1)
                hasAlpha = true
            }
            else -> return null
        }

        var accum = 0L
        for (i in 0 until digits.length) {
            val nibble = hexDigitValue(digits[i])
            if (nibble < 0) return null
            accum = (accum shl 4) or nibble.toLong()
        }

        return if (hasAlpha) {
            accum.toInt()
        } else {
            (0xFF000000L or accum).toInt()
        }
    }

    private fun luminanceFromSrgb(r: Double, g: Double, b: Double): Double =
        0.2126 * linearizeChannel(r) +
            0.7152 * linearizeChannel(g) +
            0.0722 * linearizeChannel(b)

    private fun linearizeChannel(c: Double): Double =
        if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }

    private fun hexDigitValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> 10 + (c - 'a')
        in 'A'..'F' -> 10 + (c - 'A')
        else -> -1
    }
}
