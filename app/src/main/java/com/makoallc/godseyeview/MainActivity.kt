package com.makoallc.godseyeview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makoallc.godseyeview.core.globe.Basemap
import com.makoallc.godseyeview.core.globe.CameraViewState
import com.makoallc.godseyeview.core.globe.CesiumGlobe
import com.makoallc.godseyeview.core.globe.GlobeController
import com.makoallc.godseyeview.core.globe.LastViewStore
import com.makoallc.godseyeview.core.ui.GlobeChip
import com.makoallc.godseyeview.core.ui.GlobeControlBar
import com.makoallc.godseyeview.core.ui.GodsEyeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            GodsEyeTheme { GlobeScreen() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Issue #32: immersive full-screen - the navigation bar was staying
     *  visible and covering the bottom of the app. Bars hide on entry and
     *  on focus return; an edge swipe reveals them transiently and they
     *  hide again on their own. */
    private fun hideSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
private fun GlobeScreen() {
    val context = LocalContext.current
    val store = remember { LastViewStore(context) }
    val settingsStore = remember { com.makoallc.godseyeview.settings.SettingsStore(context) }
    val tokenStore = remember { com.makoallc.godseyeview.settings.TokenStore(context) }
    var hasToken by remember { mutableStateOf(tokenStore.hasToken()) }
    val initialToken = remember { tokenStore.loadToken() }
    var qualityMode by remember {
        mutableStateOf(settingsStore.loadQualityMode())
    }
    var cacheLimitMb by remember { mutableStateOf(settingsStore.loadCacheLimitMb()) }
    var cacheSizeMb by remember {
        mutableStateOf(com.makoallc.godseyeview.settings.CacheInfo.sizeMb(context))
    }
    val effectiveQuality = com.makoallc.godseyeview.settings.QualityResolver
        .effectiveMode(context, qualityMode)
    val controller = remember { GlobeController() }
    // Plan Q19 cap enforcement: over the limit at launch -> clear once.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (cacheSizeMb > cacheLimitMb) {
            controller.clearTileCache()
            com.makoallc.godseyeview.settings.CacheInfo.clear(context)
            cacheSizeMb = com.makoallc.godseyeview.settings.CacheInfo.sizeMb(context)
        }
    }
    val initialView = remember { store.load() ?: CameraViewState.FULL_EARTH }
    var basemap by remember { mutableStateOf(store.loadBasemap()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val globeErrorText = stringResource(R.string.globe_error)
    // Issue #12 diagnostics: latest globe state JSON from the page plus the
    // WebView console lines, shown by the Globe status button.
    var diagnostics by remember { mutableStateOf<String?>(null) }
    val consoleLines = remember { mutableListOf<String>() }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        CesiumGlobe(
            controller = controller,
            initialView = initialView,
            // A saved photorealistic basemap without a token cannot load;
            // boot on Esri instead of leaving chip and globe disagreeing.
            initialBasemap = if (basemap == Basemap.PHOTOREALISTIC && initialToken == null) {
                Basemap.ESRI
            } else {
                basemap
            },
            initialQualityId = effectiveQuality.id,
            initialIonToken = initialToken,
            onCameraChanged = { view -> store.save(view) },
            onBasemapChanged = { newBasemap ->
                basemap = newBasemap
                store.saveBasemap(newBasemap)
            },
            onGlobeError = {
                scope.launch { snackbar.showSnackbar(globeErrorText) }
            },
            onDiagnostics = { json -> diagnostics = json },
            onConsoleMessage = { line ->
                consoleLines.add(line)
                if (consoleLines.size > 50) consoleLines.removeAt(0)
            },
            modifier = Modifier.fillMaxSize(),
        )
        GlobeChip(
            text = stringResource(R.string.app_name),
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        GlobeChip(
            text = stringResource(
                when (basemap) {
                    Basemap.OSM -> R.string.basemap_osm
                    Basemap.PHOTOREALISTIC -> R.string.basemap_photorealistic
                    Basemap.ESRI -> R.string.basemap_esri
                },
            ),
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.makoallc.godseyeview.core.ui.GlobeActionBar(
                onSearch = { showSearch = true },
                searchLabel = stringResource(R.string.search_button),
            )
            GlobeControlBar(
                onResetGlobe = { controller.resetGlobe() },
                resetLabel = stringResource(R.string.reset_globe),
                onGlobeStatus = {
                    controller.reportDiagnostics()
                    showDiagnostics = true
                },
                statusLabel = stringResource(R.string.globe_status),
                onSettings = { showSettings = true },
                settingsLabel = stringResource(R.string.settings_button),
            )
        }
        if (showSearch) {
            com.makoallc.godseyeview.search.SearchScreen(
                onResultSelected = { result ->
                    controller.flyTo(result.longitude, result.latitude, result.heightMeters)
                    controller.showPin(result.longitude, result.latitude, result.name)
                    showSearch = false
                },
                onBack = { showSearch = false },
            )
        }
        if (showSettings) {
            com.makoallc.godseyeview.settings.SettingsScreen(
                basemapLabel = stringResource(
                    when (basemap) {
                        Basemap.OSM -> R.string.basemap_osm
                        Basemap.PHOTOREALISTIC -> R.string.basemap_photorealistic
                        Basemap.ESRI -> R.string.basemap_esri
                    },
                ),
                basemap = basemap,
                onBasemapSelected = { selected ->
                    basemap = selected
                    store.saveBasemap(selected)
                    controller.setBasemap(selected)
                },
                qualityMode = qualityMode,
                onQualitySelected = { selected ->
                    qualityMode = selected
                    settingsStore.saveQualityMode(selected)
                    controller.setQuality(
                        com.makoallc.godseyeview.settings.QualityResolver
                            .effectiveMode(context, selected).id,
                    )
                },
                cacheSummary = stringResource(
                    R.string.settings_cache_size,
                    String.format(java.util.Locale.US, "%.1f", cacheSizeMb),
                ),
                cacheLimitMb = cacheLimitMb,
                onCacheLimitSelected = { limit ->
                    cacheLimitMb = limit
                    settingsStore.saveCacheLimitMb(limit)
                },
                onClearCache = {
                    controller.clearTileCache()
                    com.makoallc.godseyeview.settings.CacheInfo.clear(context)
                    cacheSizeMb = com.makoallc.godseyeview.settings.CacheInfo.sizeMb(context)
                },
                hasToken = hasToken,
                onSaveToken = { token ->
                    tokenStore.saveToken(token)
                    hasToken = true
                    controller.setIonToken(token)
                },
                onClearToken = {
                    tokenStore.clearToken()
                    hasToken = false
                    controller.setIonToken(null)
                },
                onOpenCredits = { showCredits = true },
                effectiveQualityLabel = when (effectiveQuality) {
                    com.makoallc.godseyeview.settings.QualityMode.HIGH ->
                        stringResource(R.string.settings_quality_high)
                    com.makoallc.godseyeview.settings.QualityMode.BALANCED ->
                        stringResource(R.string.settings_quality_balanced)
                    com.makoallc.godseyeview.settings.QualityMode.BATTERY_SAVER ->
                        stringResource(R.string.settings_quality_battery)
                    com.makoallc.godseyeview.settings.QualityMode.AUTO -> ""
                },
                onBack = { showSettings = false },
            )
        }
        if (showCredits) {
            com.makoallc.godseyeview.settings.CreditsScreen(
                onBack = { showCredits = false },
            )
        }
        if (showDiagnostics) {
            val reportText = buildString {
                append(diagnostics ?: stringResource(R.string.diagnostics_none))
                if (consoleLines.isNotEmpty()) {
                    append("\n\nWebView console:\n")
                    append(consoleLines.joinToString("\n"))
                }
            }
            AlertDialog(
                onDismissRequest = { showDiagnostics = false },
                title = { Text(stringResource(R.string.diagnostics_title)) },
                text = {
                    Text(
                        text = reportText,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("globe status", reportText))
                    }) { Text(stringResource(R.string.diagnostics_copy)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDiagnostics = false }) {
                        Text(stringResource(R.string.diagnostics_close))
                    }
                },
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
        )
    }
}
