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

    fun onPageReady(initialView: CameraViewState) {
        ready = true
        eval("window.GEV && window.GEV.setView(${initialView.toJson()})")
        flush()
    }

    fun setView(view: CameraViewState) = eval("window.GEV && window.GEV.setView(${view.toJson()})")

    fun resetGlobe() = eval("window.GEV && window.GEV.resetGlobe()")

    fun setBasemap(basemap: Basemap) = eval("window.GEV && window.GEV.setBasemap('${basemap.id}')")

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
