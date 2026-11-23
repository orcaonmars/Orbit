package com.hydrogen.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.ui.theme.OrbitTheme

private val MenuShape = RoundedCornerShape(18.dp)
private val ItemShape = RoundedCornerShape(12.dp)

/**
 * Orbit's pop-up menu: a rounded floating card with a soft shadow and a hairline edge, roomier than the stock
 * Material menu. Every menu in the app goes through this so they all look the same.
 */
@Composable
fun OrbitMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = OrbitTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 220.dp).padding(horizontal = 6.dp),
        offset = offset,
        shape = MenuShape,
        containerColor = colors.surface2,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, colors.line),
        content = content
    )
}

/** One row in an [OrbitMenu]: icon, label, a little rounding on press, and comfortable height. */
@Composable
fun OrbitMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val colors = OrbitTheme.colors
    CompositionLocalProvider(LocalContentColor provides colors.text) {
        DropdownMenuItem(
            text = {
                CompositionLocalProvider(LocalContentColor provides colors.text) {
                    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                }
            },
            onClick = onClick,
            modifier = modifier.clip(ItemShape),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            enabled = enabled,
            colors = MenuDefaults.itemColors(
                textColor = colors.text,
                leadingIconColor = colors.muted,
                trailingIconColor = colors.muted
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}
