package com.makoallc.godseyeview.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makoallc.godseyeview.R

/** Stage 2 Settings (plan Q14/Q19/Q20/Q32), a full-screen overlay above
 *  the globe so the WebView stays alive underneath. This scaffold carries
 *  the section structure; each section's controls land with its own
 *  Stage 2 issue (basemap #18, ion token #19, cache #20, quality #21,
 *  credits #22, terrain #15). */
@Composable
fun SettingsScreen(
    basemapLabel: String,
    basemap: com.makoallc.godseyeview.core.globe.Basemap =
        com.makoallc.godseyeview.core.globe.Basemap.ESRI,
    onBasemapSelected: (com.makoallc.godseyeview.core.globe.Basemap) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
        }
        Spacer(Modifier.height(12.dp))
        SettingsSection(title = stringResource(R.string.settings_basemap_title)) {
            BasemapOption(
                label = stringResource(R.string.basemap_esri),
                selected = basemap == com.makoallc.godseyeview.core.globe.Basemap.ESRI,
                onSelect = { onBasemapSelected(com.makoallc.godseyeview.core.globe.Basemap.ESRI) },
            )
            BasemapOption(
                label = stringResource(R.string.basemap_osm),
                selected = basemap == com.makoallc.godseyeview.core.globe.Basemap.OSM,
                onSelect = { onBasemapSelected(com.makoallc.godseyeview.core.globe.Basemap.OSM) },
            )
            Text(
                text = stringResource(R.string.settings_basemap_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_terrain_title)) {
            Text(
                text = stringResource(R.string.settings_terrain_terrarium),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_token_title)) {
            Text(
                text = stringResource(R.string.settings_token_none),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_quality_title)) {
            Text(
                text = stringResource(R.string.settings_quality_auto),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_cache_title)) {
            Text(
                text = stringResource(R.string.settings_cache_summary),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_credits_title)) {
            Text(
                text = stringResource(R.string.settings_credits_summary),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_about_title)) {
            Text(
                text = stringResource(R.string.settings_about_version, versionName),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun BasemapOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
