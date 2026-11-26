package com.hydrogen.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.BuildConfig
import com.hydrogen.ai.R
import com.hydrogen.ai.model.AboutOrbit
import com.hydrogen.ai.ui.theme.OrbitAccents
import com.hydrogen.ai.ui.theme.OrbitPalettes
import com.hydrogen.ai.ui.theme.OrbitTheme

/**
 * About Orbit: the app mark as it looks on the home screen, the version, what Orbit promises and the way to What's
 * new, Privacy & data and the open source licenses. Every line of copy comes from [AboutOrbit].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    onDismiss: () -> Unit,
    onWhatsNew: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit
) {
    val orbit = OrbitTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppMarkTile()
            Spacer(Modifier.height(14.dp))
            Text(
                AboutOrbit.NAME,
                style = MaterialTheme.typography.headlineSmall,
                color = orbit.text,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                AboutOrbit.versionLine(BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = orbit.muted
            )
            Spacer(Modifier.height(10.dp))
            Text(
                AboutOrbit.TAGLINE,
                style = MaterialTheme.typography.bodyLarge,
                color = orbit.text,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))

            AboutCard {
                val icons = listOf(com.hydrogen.ai.ui.OrbitIcons.Phone, com.hydrogen.ai.ui.OrbitIcons.EyeOff, com.hydrogen.ai.ui.OrbitIcons.Lock)
                AboutOrbit.PROMISES.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icons.getOrElse(index) { com.hydrogen.ai.ui.OrbitIcons.Lock },
                            contentDescription = null,
                            tint = orbit.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = orbit.text)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            AboutCard {
                AboutLinkRow(com.hydrogen.ai.ui.OrbitIcons.Sparkle, AboutOrbit.WHATS_NEW, onWhatsNew)
                HorizontalDivider(color = orbit.line, modifier = Modifier.padding(start = 50.dp))
                AboutLinkRow(com.hydrogen.ai.ui.OrbitIcons.Shield, AboutOrbit.PRIVACY, onPrivacy)
                HorizontalDivider(color = orbit.line, modifier = Modifier.padding(start = 50.dp))
                AboutLinkRow(com.hydrogen.ai.ui.OrbitIcons.File, AboutOrbit.LICENSES, onLicenses)
            }
            Spacer(Modifier.height(20.dp))

            Text(
                AboutOrbit.EARLY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = orbit.muted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                AboutOrbit.ORBIT_LICENSE_LINE,
                style = MaterialTheme.typography.bodySmall,
                color = orbit.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Open source licenses: who made each library Orbit ships, and the full Apache License 2.0 text, reflowed so it
 * reads like a page instead of a fixed-width file. Back returns to About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesSheet(onDismiss: () -> Unit, onBack: () -> Unit) {
    val orbit = OrbitTheme.colors
    val context = LocalContext.current
    // Read off the main thread: the full license text is a raw resource.
    val licenseText by androidx.compose.runtime.produceState(initialValue = emptyList<String>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
            }.map(AboutOrbit::reflow).getOrDefault(emptyList())
        }
    }
    var showLicense by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(com.hydrogen.ai.ui.OrbitIcons.ArrowBack, contentDescription = "${AboutOrbit.BACK} to About Orbit")
                }
                Text(
                    AboutOrbit.LICENSES,
                    style = MaterialTheme.typography.titleLarge,
                    color = orbit.text,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(AboutOrbit.LICENSES_INTRO, style = MaterialTheme.typography.bodyMedium, color = orbit.muted)
            Spacer(Modifier.height(16.dp))

            AboutCard {
                AboutOrbit.LIBRARIES.forEachIndexed { index, library ->
                    if (index > 0) HorizontalDivider(color = orbit.line, modifier = Modifier.padding(start = 16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription = "${library.name}. ${library.by}. ${library.license}."
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(library.name, style = MaterialTheme.typography.titleSmall, color = orbit.text)
                        Text(
                            "${library.by}, ${library.license}",
                            style = MaterialTheme.typography.bodySmall,
                            color = orbit.muted
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (licenseText.isNotEmpty()) {
                AboutCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button) { showLicense = !showLicense }
                            .semantics { stateDescription = if (showLicense) "Shown" else "Hidden" }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            AboutOrbit.READ_LICENSE,
                            style = MaterialTheme.typography.bodyLarge,
                            color = orbit.text,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (showLicense) com.hydrogen.ai.ui.OrbitIcons.ChevronUp else com.hydrogen.ai.ui.OrbitIcons.ChevronDown,
                            contentDescription = null,
                            tint = orbit.muted
                        )
                    }
                    if (showLicense) {
                        HorizontalDivider(color = orbit.line)
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            licenseText.forEach { paragraph ->
                                Text(paragraph, style = MaterialTheme.typography.bodySmall, color = orbit.muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(AboutOrbit.ORBIT_LICENSE_LINE, style = MaterialTheme.typography.bodySmall, color = orbit.muted)
        }
    }
}

/** The launcher icon, drawn with the same colors as the adaptive icon (res/drawable/ic_launcher_*.xml). */
@Composable
private fun AppMarkTile() {
    // Always the dark launcher tile. The hairline keeps its edge visible on the Night sheet, which is nearly the
    // same colour.
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(shape)
            .background(OrbitPalettes.Night.surface)
            .border(1.dp, OrbitTheme.colors.line, shape),
        contentAlignment = Alignment.Center
    ) {
        OrbitIndicator(
            spinning = false,
            size = 40.dp,
            ringColor = OrbitPalettes.Night.text,
            dotColor = OrbitAccents.Sun.nightAccent
        )
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OrbitTheme.colors.surface)
    ) {
        content()
    }
}

@Composable
private fun AboutLinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val orbit = OrbitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = orbit.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = orbit.text, modifier = Modifier.weight(1f))
        Icon(com.hydrogen.ai.ui.OrbitIcons.ChevronRight, contentDescription = null, tint = orbit.faint)
    }
}
