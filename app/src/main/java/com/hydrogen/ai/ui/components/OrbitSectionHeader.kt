package com.hydrogen.ai.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.LocalOrbitPalette
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * Reusable section label, using Orbit's text roles and 24dp top / 8dp bottom rhythm.
 * The heading semantic lets accessibility services navigate sections by heading.
 */
@Composable
fun OrbitSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        color = OrbitTheme.colors.muted,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .semantics { heading() }
            .padding(top = 24.dp, bottom = 8.dp),
    )
}

@Preview(name = "Orbit section: Paper", showBackground = true)
@Composable
private fun OrbitSectionHeaderPaperPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        OrbitSectionHeader(label = "Recent conversations")
    }
}

@Preview(name = "Orbit section: Night", showBackground = true)
@Composable
private fun OrbitSectionHeaderNightPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Night) {
        OrbitSectionHeader(label = "Recent conversations")
    }
}

@Preview(name = "Orbit section: large text", showBackground = true, fontScale = 1.5f)
@Composable
private fun OrbitSectionHeaderLargeTextPreview() {
    CompositionLocalProvider(LocalOrbitPalette provides OrbitPalettes.Paper) {
        OrbitSectionHeader(label = "Recent conversations")
    }
}
