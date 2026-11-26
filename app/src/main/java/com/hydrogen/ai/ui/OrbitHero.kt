package com.hydrogen.ai.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.OrbitPalette
import com.hydrogen.ai.ui.theme.OrbitTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One lap of the moon, in milliseconds. Slow enough to read as calm, not as a loading spinner. */
private const val LAP_MS = 11_000

/** The ring's height as a share of its width: how far it is tilted toward the viewer. */
private const val TILT = 0.27f

/** The whole system leans a little, so it reads as an object in space rather than a diagram. */
private const val LEAN_DEG = -13f

/** Where the moon waits when animations are off: front left, in front of the planet. */
private const val REST_TURNS = 0.62f

/** Ring lanes: radius as a share of the outer radius, stroke width as a share of the size, and opacity. */
private val LANES = listOf(
    Triple(0.74f, 0.010f, 0.16f),
    Triple(0.79f, 0.016f, 0.30f),
    Triple(0.84f, 0.010f, 0.22f),
    Triple(0.875f, 0.004f, 0.12f),
    Triple(0.935f, 0.012f, 0.40f),
    Triple(1.00f, 0.0035f, 0.70f)
)

/**
 * The start screen's mark: a shaded planet with a banded, tilted ring and a moon in the accent color circling it.
 * The moon passes behind the planet and in front of it, and lights the side of the planet it faces.
 * Everything that does not move is drawn once per size into dithered bitmaps at the exact pixel size, so it stays
 * sharp and free of gradient banding; each frame only places the moon. The lap runs in the draw phase (no
 * recomposition per frame) and stops under reduced motion. Purely decorative, so screen readers skip it.
 */
@Composable
internal fun OrbitHero(modifier: Modifier = Modifier, size: Dp = 208.dp) {
    val orbit = OrbitTheme.colors
    val reducedMotion = rememberReducedMotion()
    val turns: State<Float> = if (reducedMotion) {
        remember { mutableFloatStateOf(REST_TURNS) }
    } else {
        rememberInfiniteTransition(label = "orbit-hero").animateFloat(
            initialValue = REST_TURNS,
            targetValue = REST_TURNS + 1f,
            animationSpec = infiniteRepeatable(tween(LAP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "orbit-hero-lap"
        )
    }
    Box(
        modifier
            .size(width = size, height = size * 0.66f)
            .clearAndSetSemantics { }
            .drawWithCache {
                val scene = OrbitScene(orbit, this.size.width.toInt().coerceAtLeast(1), this.size.height.toInt().coerceAtLeast(1))
                onDrawBehind { drawIntoCanvas { scene.draw(it.nativeCanvas, turns.value) } }
            }
    )
}

/** The pre-rendered layers and the per-frame moon. */
private class OrbitScene(orbit: OrbitPalette, private val pw: Int, private val ph: Int) {
    private val w = pw.toFloat()
    private val cx = pw / 2f
    private val cy = ph / 2f
    private val rx = w * 0.455f
    private val ry = rx * TILT
    private val planetR = w * 0.195f
    private val moonR = w * 0.036f
    private val lean = LEAN_DEG * PI.toFloat() / 180f
    private val accent = orbit.accent
    private val dark = orbit.dark

    private val back: Bitmap
    private val planet: Bitmap
    private val front: Bitmap
    private val glow: Bitmap
    private val core: Bitmap
    private val bounce: Bitmap
    private val planetClip = Path().apply { addCircle(cx, cy, planetR, Path.Direction.CW) }
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val dst = RectF()

    init {
        val ring = if (dark) lerp(orbit.line, orbit.text, 0.62f) else lerp(orbit.line, orbit.text, 0.45f)
        back = layer { c ->
            // A warm halo, so the planet sits in light rather than on a flat background.
            val haloR = minOf(w * 0.5f, ph * 0.5f)
            c.drawCircle(cx, cy, haloR, fill().apply {
                shader = RadialGradient(cx, cy, haloR,
                    intArrayOf(accent.copy(alpha = if (dark) 0.15f else 0.10f).toArgb(), accent.copy(alpha = 0.04f).toArgb(), 0),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            })
            leaned(c) { lanes(c, ring, 180f, 0.55f, null) }
        }
        planet = layer { c ->
            val lx = cx - planetR * 0.40f
            val ly = cy - planetR * 0.48f
            val lit = if (dark) lerp(orbit.surface2, orbit.text, 0.44f) else Color.White
            val mid = if (dark) lerp(orbit.surface2, orbit.text, 0.14f) else lerp(orbit.surface2, orbit.text, 0.06f)
            val low = if (dark) lerp(orbit.bg, Color.Black, 0.30f) else lerp(orbit.surface2, orbit.text, 0.22f)
            val edge = if (dark) lerp(orbit.bg, Color.Black, 0.62f) else lerp(orbit.surface2, orbit.text, 0.32f)
            c.drawCircle(cx, cy, planetR, fill().apply {
                shader = RadialGradient(lx, ly, planetR * 1.95f, intArrayOf(lit.toArgb(), mid.toArgb(), low.toArgb(), edge.toArgb()),
                    floatArrayOf(0f, 0.32f, 0.70f, 1f), Shader.TileMode.CLAMP)
            })
            c.save()
            c.clipPath(planetClip)
            // Soft cloud bands along the ring plane.
            leaned(c) {
                val band = fill().apply { maskFilter = BlurMaskFilter(planetR * 0.06f, BlurMaskFilter.Blur.NORMAL) }
                val rows = floatArrayOf(-0.72f, -0.46f, -0.24f, -0.05f, 0.18f, 0.40f, 0.64f)
                rows.forEachIndexed { i, y ->
                    band.color = if (i % 2 == 0) Color.White.copy(alpha = if (dark) 0.035f else 0.10f).toArgb()
                    else Color.Black.copy(alpha = if (dark) 0.10f else 0.04f).toArgb()
                    val h = planetR * (0.05f + 0.03f * (i % 3))
                    c.drawOval(cx - planetR * 1.2f, cy + y * planetR - h, cx + planetR * 1.2f, cy + y * planetR + h, band)
                }
                // The ring's shadow, a little below the ring where the light from above throws it.
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = w * 0.05f
                    color = Color.Black.copy(alpha = if (dark) 0.42f else 0.16f).toArgb()
                    maskFilter = BlurMaskFilter(w * 0.008f, BlurMaskFilter.Blur.NORMAL)
                }
                val o = planetR * 0.16f
                c.drawArc(cx - rx * 0.86f, cy - ry * 0.86f + o, cx + rx * 0.86f, cy + ry * 0.86f + o, 0f, 180f, false, shadow)
            }
            // Night side: the far half falls off into shadow.
            c.drawCircle(cx, cy, planetR, fill().apply {
                shader = RadialGradient(lx, ly, planetR * 2.1f,
                    intArrayOf(0, 0, Color.Black.copy(alpha = if (dark) 0.55f else 0.18f).toArgb()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
            // A small soft specular on the lit side.
            c.drawCircle(lx, ly, planetR * 0.42f, fill().apply {
                shader = RadialGradient(lx, ly, planetR * 0.42f, intArrayOf(Color.White.copy(alpha = if (dark) 0.10f else 0.35f).toArgb(), 0),
                    null, Shader.TileMode.CLAMP)
            })
            c.restore()
            // Atmosphere: a thin rim on the lit limb, and a faint one all round.
            c.drawCircle(cx, cy, planetR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = planetR * 0.025f
                color = lerp(orbit.text, accent, 0.25f).copy(alpha = if (dark) 0.10f else 0.18f).toArgb()
            })
            c.drawArc(cx - planetR, cy - planetR, cx + planetR, cy + planetR, 150f, 135f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = planetR * 0.035f; strokeCap = Paint.Cap.ROUND
                color = Color.White.copy(alpha = if (dark) 0.34f else 0.70f).toArgb()
                maskFilter = BlurMaskFilter(planetR * 0.03f, BlurMaskFilter.Blur.NORMAL)
            })
        }
        front = layer { c -> leaned(c) { lanes(c, ring, 0f, 1f, lerp(ring, orbit.text, 0.75f)) } }
        val gs = (moonR * 9f).toInt().coerceAtLeast(2)
        glow = sprite(gs) { c, r ->
            c.drawCircle(r, r, r, fill().apply {
                shader = RadialGradient(r, r, r, intArrayOf(accent.copy(alpha = 0.55f).toArgb(), accent.copy(alpha = 0.14f).toArgb(), 0),
                    floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
            })
        }
        core = sprite((moonR * 2f).toInt().coerceAtLeast(2) + 2) { c, r ->
            val rr = r - 1f
            c.drawCircle(r, r, rr, fill().apply {
                shader = RadialGradient(r - rr * 0.38f, r - rr * 0.42f, rr * 1.55f,
                    intArrayOf(lerp(accent, Color.White, 0.70f).toArgb(), lerp(accent, Color.White, 0.15f).toArgb(),
                        accent.toArgb(), lerp(accent, Color.Black, 0.45f).toArgb()),
                    floatArrayOf(0f, 0.22f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
        }
        bounce = sprite((planetR * 2.4f).toInt().coerceAtLeast(2)) { c, r ->
            c.drawCircle(r, r, r, fill().apply {
                shader = RadialGradient(r, r, r, intArrayOf(accent.copy(alpha = 0.40f).toArgb(), 0), null, Shader.TileMode.CLAMP)
            })
        }
    }

    private fun fill() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    private fun layer(block: (android.graphics.Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888).also { block(android.graphics.Canvas(it)) }

    private fun sprite(px: Int, block: (android.graphics.Canvas, Float) -> Unit): Bitmap =
        Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { block(android.graphics.Canvas(it), px / 2f) }

    private inline fun leaned(c: android.graphics.Canvas, block: () -> Unit) {
        c.save(); c.rotate(LEAN_DEG, cx, cy); block(); c.restore()
    }

    /** Half of the ring: [start] 180 is the far half (top), 0 the near half. [peak] brightens the middle of the near half. */
    private fun lanes(c: android.graphics.Canvas, color: Color, start: Float, alpha: Float, peak: Color?) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
        for ((k, width, a) in LANES) {
            p.strokeWidth = w * width
            val x = rx * k
            val y = ry * k
            p.shader = if (peak == null) null else LinearGradient(cx - x, 0f, cx + x, 0f,
                intArrayOf(color.copy(alpha = a * 0.55f).toArgb(), peak.copy(alpha = minOf(1f, a * 1.25f)).toArgb(), color.copy(alpha = a * 0.55f).toArgb()),
                null, Shader.TileMode.CLAMP)
            p.color = color.copy(alpha = a * alpha).toArgb()
            if (peak != null) p.alpha = 255
            c.drawArc(cx - x, cy - y, cx + x, cy + y, start, 180f, false, p)
        }
    }

    /** A point on the outer ring at angle [a] (0 is the far right, positive toward the viewer), leaned like the scene. */
    private fun at(a: Float, out: FloatArray) {
        val lx = rx * cos(a)
        val ly = ry * sin(a)
        out[0] = cx + lx * cos(lean) - ly * sin(lean)
        out[1] = cy + lx * sin(lean) + ly * cos(lean)
    }

    private val p0 = FloatArray(2)
    private val p1 = FloatArray(2)

    fun draw(c: android.graphics.Canvas, turns: Float) {
        val a = (turns % 1f) * 2f * PI.toFloat()
        val depth = (sin(a) + 1f) / 2f
        val inFront = sin(a) >= 0f
        c.drawBitmap(back, 0f, 0f, bmp)
        drawTrail(c, a, depth, front = false)
        if (!inFront) drawMoon(c, a, depth)
        c.drawBitmap(planet, 0f, 0f, bmp)
        // Warm light from the moon on the side of the planet that faces it, stronger when the moon is in front.
        at(a, p0)
        val dx = p0[0] - cx
        val dy = p0[1] - cy
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val bx = cx + dx / len * planetR * 0.92f
        val by = cy + dy / len * planetR * 0.92f
        c.save()
        c.clipPath(planetClip)
        bmp.alpha = (255 * (0.25f + 0.75f * depth)).toInt()
        val hb = bounce.width / 2f
        dst.set(bx - hb, by - hb, bx + hb, by + hb)
        c.drawBitmap(bounce, null, dst, bmp)
        bmp.alpha = 255
        c.restore()
        c.drawBitmap(front, 0f, 0f, bmp)
        drawTrail(c, a, depth, front = true)
        if (inFront) drawMoon(c, a, depth)
    }

    /** A tapered trail along the ring behind the moon; each piece is drawn with the half of the ring it lies on. */
    private fun drawTrail(c: android.graphics.Canvas, a: Float, depth: Float, front: Boolean) {
        val steps = 44
        val span = 1.15f
        for (i in 0 until steps) {
            val f0 = i / steps.toFloat()
            val a0 = a - span * (1f - f0)
            val a1 = a - span * (1f - (i + 1) / steps.toFloat())
            if ((sin((a0 + a1) / 2f) >= 0f) != front) continue
            at(a0, p0); at(a1, p1)
            val f = (i + 1) / steps.toFloat()
            trail.strokeWidth = moonR * (0.12f + 1.05f * f * f)
            trail.color = accent.copy(alpha = (0.42f * f * f * (0.55f + 0.45f * depth)).coerceIn(0f, 1f)).toArgb()
            c.drawLine(p0[0], p0[1], p1[0], p1[1], trail)
        }
    }

    private fun drawMoon(c: android.graphics.Canvas, a: Float, depth: Float) {
        at(a, p0)
        val s = 0.82f + 0.30f * depth
        val hg = glow.width / 2f * s
        bmp.alpha = (255 * (0.55f + 0.45f * depth)).toInt()
        dst.set(p0[0] - hg, p0[1] - hg, p0[0] + hg, p0[1] + hg)
        c.drawBitmap(glow, null, dst, bmp)
        bmp.alpha = 255
        val hc = core.width / 2f * s
        dst.set(p0[0] - hc, p0[1] - hc, p0[0] + hc, p0[1] + hc)
        c.drawBitmap(core, null, dst, bmp)
    }
}
