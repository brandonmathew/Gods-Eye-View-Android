package com.makoallc.godseyeview.core.globe

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/** The Cesium globe. This composable renders ONLY the globe (plan Q5);
 *  every panel, card and control around it is native Compose. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CesiumGlobe(
    controller: GlobeController,
    initialView: CameraViewState,
    initialBasemap: Basemap = Basemap.ESRI,
    initialQualityId: String = "balanced",
    initialIonToken: String? = null,
    onCameraChanged: (CameraViewState) -> Unit,
    onBasemapChanged: (Basemap) -> Unit,
    onGlobeError: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDiagnostics: (String) -> Unit = {},
    onConsoleMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val bridge = remember {
        object {
            @JavascriptInterface
            fun onGlobeReady() {
                controller.onPageReady(initialView, initialBasemap, initialQualityId, initialIonToken)
            }

            @JavascriptInterface
            fun onCameraChanged(json: String) {
                CameraViewState.fromJson(json)?.let(onCameraChanged)
            }

            @JavascriptInterface
            fun onBasemapChanged(id: String) {
                onBasemapChanged(Basemap.fromId(id))
            }

            @JavascriptInterface
            fun onError(message: String) {
                onGlobeError(message)
            }

            @JavascriptInterface
            fun onDiagnostics(json: String) {
                onDiagnostics(json)
            }
        }
    }
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? =
                    TerrainTileProxy.fetch(request.url) ?: assetLoader.shouldInterceptRequest(request.url)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    onConsoleMessage(
                        "${consoleMessage.messageLevel()}: ${consoleMessage.message()}",
                    )
                    return true
                }
            }
            addJavascriptInterface(bridge, "AndroidBridge")
            controller.attach(this)
            loadUrl("https://appassets.androidplatform.net/assets/globe/index.html")
        }
    }
    DisposableEffect(webView) {
        onDispose {
            controller.detach(webView)
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}
