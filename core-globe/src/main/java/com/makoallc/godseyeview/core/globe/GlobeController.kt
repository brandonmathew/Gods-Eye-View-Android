package com.makoallc.godseyeview.core.globe

import android.webkit.WebView

/** Kotlin side of the bridge. Kotlin owns state; the WebView only renders
 *  (plan, Architecture). Calls made before the page signals ready are queued
 *  and flushed in order once it does. */
class GlobeController {
    private var webView: WebView? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    fun attach(view: WebView) {
        webView = view
    }

    fun detach(view: WebView) {
        if (webView === view) {
            webView = null
            ready = false
            pending.clear()
        }
    }

    fun onPageReady(
        initialView: CameraViewState,
        initialBasemap: Basemap = Basemap.ESRI,
        initialQualityId: String = "balanced",
        initialIonToken: String? = null,
    ) {
        ready = true
        eval("window.GEV && window.GEV.setView(${initialView.toJson()})")
        if (!initialIonToken.isNullOrEmpty()) {
            // Token first: a saved photorealistic basemap needs it in place.
            eval("window.GEV && window.GEV.setIonToken(${org.json.JSONObject.quote(initialIonToken)})")
        }
        if (initialBasemap != Basemap.ESRI) {
            // The page boots on Esri; re-apply the saved choice so the globe
            // and the basemap chip agree from the first frame (issue #18).
            eval("window.GEV && window.GEV.setBasemap('${initialBasemap.id}')")
        }
        eval("window.GEV && window.GEV.setQuality('$initialQualityId')")
        flush()
    }

    fun setView(view: CameraViewState) = eval("window.GEV && window.GEV.setView(${view.toJson()})")

    fun resetGlobe() = eval("window.GEV && window.GEV.resetGlobe()")

    fun setBasemap(basemap: Basemap) = eval("window.GEV && window.GEV.setBasemap('${basemap.id}')")

    fun setQuality(effectiveModeId: String) =
        eval("window.GEV && window.GEV.setQuality('$effectiveModeId')")

    /** Issue #19: hand the page the user's Cesium ion token (or clear it). */
    fun setIonToken(token: String?) =
        eval("window.GEV && window.GEV.setIonToken(${org.json.JSONObject.quote(token ?: "")})")

    /** Issue #33: search fly-to + temporary pin. */
    fun flyTo(longitude: Double, latitude: Double, heightMeters: Double) =
        eval(
            "window.GEV && window.GEV.flyTo(" +
                "${fmt(longitude)}, ${fmt(latitude)}, ${fmt(heightMeters)})",
        )

    fun showPin(longitude: Double, latitude: Double, label: String) =
        eval(
            "window.GEV && window.GEV.showPin(" +
                "${fmt(longitude)}, ${fmt(latitude)}, ${org.json.JSONObject.quote(label)})",
        )

    private fun fmt(value: Double): String =
        String.format(java.util.Locale.US, "%.6f", value)

    /** Issue #12 diagnostics: ask the page to send its current globe state. */
    fun reportDiagnostics() = eval("window.GEV && window.GEV.reportDiagnostics()")

    /** Issue #20: drop the WebView HTTP cache (imagery tiles). */
    fun clearTileCache() {
        val view = webView ?: return
        view.post { view.clearCache(true) }
    }

    private fun eval(js: String) {
        val view = webView
        if (view == null || !ready) {
            pending.addLast(js)
            return
        }
        view.post { view.evaluateJavascript(js, null) }
    }

    private fun flush() {
        val view = webView ?: return
        while (pending.isNotEmpty()) {
            val js = pending.removeFirst()
            view.post { view.evaluateJavascript(js, null) }
        }
    }
}
