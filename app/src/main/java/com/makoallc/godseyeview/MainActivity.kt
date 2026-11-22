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
        setContent {
            GodsEyeTheme { GlobeScreen() }
        }
    }
}

@Composable
private fun GlobeScreen() {
    val context = LocalContext.current
    val store = remember { LastViewStore(context) }
    val controller = remember { GlobeController() }
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

    Box(modifier = Modifier.fillMaxSize()) {
        CesiumGlobe(
            controller = controller,
            initialView = initialView,
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
                if (basemap == Basemap.OSM) R.string.basemap_osm else R.string.basemap_esri,
            ),
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )
        GlobeControlBar(
            onResetGlobe = { controller.resetGlobe() },
            resetLabel = stringResource(R.string.reset_globe),
            onGlobeStatus = {
                controller.reportDiagnostics()
                showDiagnostics = true
            },
            statusLabel = stringResource(R.string.globe_status),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
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
