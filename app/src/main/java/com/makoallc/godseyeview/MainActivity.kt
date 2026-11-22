package com.makoallc.godseyeview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    /* Issue #34: My Location - tap-only, single fix, never on launch (Q16). */
    var locationStatus by remember { mutableStateOf("") }
    fun flyToDeviceLocation() {
        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
            as android.location.LocationManager
        val providers = listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER,
        )
        val lastKnown = providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        fun useFix(loc: android.location.Location) {
            locationStatus = ""
            controller.flyTo(loc.longitude, loc.latitude, 3_000.0)
            controller.showLocationDot(loc.longitude, loc.latitude)
        }
        val active = providers.firstOrNull { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        if (active == null) {
            if (lastKnown != null) useFix(lastKnown)
            else locationStatus = context.getString(R.string.location_unavailable)
            return
        }
        runCatching {
            manager.getCurrentLocation(
                active, null,
                androidx.core.content.ContextCompat.getMainExecutor(context),
            ) { loc ->
                when {
                    loc != null -> useFix(loc)
                    lastKnown != null -> useFix(lastKnown)
                    else -> locationStatus = context.getString(R.string.location_unavailable)
                }
            }
        }.onFailure {
            if (lastKnown != null) useFix(lastKnown)
            else locationStatus = context.getString(R.string.location_unavailable)
        }
    }
    val locationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) flyToDeviceLocation()
            else locationStatus = context.getString(R.string.location_denied)
        }
    // Plan Q19 cap enforcement: over the limit at launch -> clear once.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (cacheSizeMb > cacheLimitMb) {
            controller.clearTileCache()
            com.makoallc.godseyeview.settings.CacheInfo.clear(context)
            cacheSizeMb = com.makoallc.godseyeview.settings.CacheInfo.sizeMb(context)
        }
    }
    val initialView = remember { store.load() ?: CameraViewState.FULL_EARTH }
    /* Issue #35: the live camera state, for Share - the same reports
       that persist the last view. */
    var currentView by remember { mutableStateOf(initialView) }
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
    var showLayers by remember { mutableStateOf(false) }

    /* Stage 4 (issues #45/#47): the layer registry + fetch manager. Layer
       modules register here at startup; with none registered the manager
       simply idles and the Layers panel shows its empty state. */
    val layerManager = remember {
        com.makoallc.godseyeview.layers.LayerManager(
            registry = com.makoallc.godseyeview.layerapi.LayerRegistry(),
            controller = controller,
        )
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, layerManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> layerManager.setPaused(true)
                androidx.lifecycle.Lifecycle.Event.ON_START -> layerManager.setPaused(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            layerManager.shutdown()
        }
    }

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
            onCameraChanged = { view ->
                currentView = view
                store.save(view)
            },
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
            onEntityPicked = { layerId, entityId ->
                layerManager.onPicked(layerId, entityId)
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
            com.makoallc.godseyeview.layers.EntityCard(manager = layerManager)
            if (locationStatus.isNotEmpty()) {
                GlobeChip(text = locationStatus)
            }
            com.makoallc.godseyeview.core.ui.GlobeActionBar(
                onSearch = { showSearch = true },
                searchLabel = stringResource(R.string.search_button),
                onMyLocation = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        flyToDeviceLocation()
                    } else {
                        locationPermissionLauncher.launch(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    }
                },
                myLocationLabel = stringResource(R.string.my_location_button),
                onShare = {
                    val lat = String.format(java.util.Locale.US, "%.5f", currentView.latitude)
                    val lon = String.format(java.util.Locale.US, "%.5f", currentView.longitude)
                    val height = String.format(
                        java.util.Locale.US, "%,.0f", currentView.heightMeters,
                    )
                    val text = listOf(
                        context.getString(R.string.share_header),
                        "${context.getString(R.string.share_latitude)}: $lat",
                        "${context.getString(R.string.share_longitude)}: $lon",
                        "${context.getString(R.string.share_height)}: $height m",
                        "${context.getString(R.string.share_basemap)}: " +
                            context.getString(
                                when (basemap) {
                                    Basemap.OSM -> R.string.basemap_osm
                                    Basemap.PHOTOREALISTIC -> R.string.basemap_photorealistic
                                    Basemap.ESRI -> R.string.basemap_esri
                                },
                            ),
                        "https://www.google.com/maps/search/?api=1&query=$lat,$lon",
                    ).joinToString("\n")
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                        putExtra(
                            android.content.Intent.EXTRA_SUBJECT,
                            context.getString(R.string.share_subject),
                        )
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(sendIntent, null),
                    )
                },
                shareLabel = stringResource(R.string.share_button),
                onLayers = { showLayers = true },
                layersLabel = stringResource(R.string.layers_button),
            )
            GlobeControlBar(
                onResetGlobe = {
                    layerManager.select(null)
                    controller.resetGlobe()
                },
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
        if (showLayers) {
            com.makoallc.godseyeview.layers.LayerPanel(
                manager = layerManager,
                onBack = { showLayers = false },
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
