package com.makoallc.godseyeview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
        )
    }
}
