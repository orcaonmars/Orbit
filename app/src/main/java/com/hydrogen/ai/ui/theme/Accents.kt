package com.hydrogen.ai.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Orbit has one accent at a time and no gradients (docs/ORBIT_DESIGN.md, section 1). The choices live in
 * [OrbitAccents]; these helpers give an accent's colors for the theme in effect, e.g. for the picker swatches,
 * which show every accent at once.
 */

/** The accent's fill (the sun button color) in a dark or light theme. */
fun OrbitAccent.fillFor(dark: Boolean): Color = if (dark) nightFill else paperFill

/** The color drawn on [fillFor]. */
fun OrbitAccent.onFillFor(dark: Boolean): Color = if (dark) nightOn else paperOn

/** The text-safe accent (links, the orbit dot) in a dark or light theme. */
fun OrbitAccent.textFor(dark: Boolean): Color = if (dark) nightAccent else paperAccent
