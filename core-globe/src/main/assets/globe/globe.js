'use strict';
/* God's Eye View Android - core globe (Stage 1).
   Keyless basemap ladder (plan Q4): Esri World Imagery first, OSM if Esri
   tiles keep failing. Terrain is the ellipsoid for Stage 1: the AWS
   Terrain Tiles quantized-mesh dataset this page originally loaded no
   longer exists (issue #12 - its layer.json 404s and the never-ready
   provider silently blocked ALL globe rendering). A working terrain
   source is Stage 2 work (issue #15). */
(function () {
  var bridge = window.AndroidBridge || {
    onGlobeReady: function () {}, onCameraChanged: function () {},
    onBasemapChanged: function () {}, onError: function () {},
    onDiagnostics: function () {}
  };

  /* Issue #12 diagnostics: every JS error is forwarded to the app instead
     of dying silently in the WebView (onError was never called before). */
  var jsErrors = [];
  function noteError(text) {
    jsErrors.push(String(text).slice(0, 300));
    if (jsErrors.length > 20) jsErrors.shift();
    bridge.onError(String(text).slice(0, 300));
  }
  window.addEventListener('error', function (e) {
    noteError('window.onerror: ' + (e.message || 'unknown') +
      (e.filename ? ' @ ' + e.filename + ':' + e.lineno : ''));
  });
  window.addEventListener('unhandledrejection', function (e) {
    noteError('unhandledrejection: ' + (e.reason && e.reason.message || e.reason || 'unknown'));
  });

  var ESRI_URL = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';

  var viewer = new Cesium.Viewer('cesiumContainer', {
    animation: false, timeline: false, baseLayerPicker: false,
    geocoder: false, homeButton: false, sceneModePicker: false,
    navigationHelpButton: false, fullscreenButton: false,
    infoBox: false, selectionIndicator: false,
    baseLayer: false, terrainProvider: new Cesium.EllipsoidTerrainProvider()
  });
  viewer.scene.globe.enableLighting = false;
  viewer.scene.fog.enabled = true;

  var credits = viewer.scene.frameState.creditDisplay;
  function addCredit(text, href) {
    credits.addStaticCredit(new Cesium.Credit(
      href ? '<a href="' + href + '" target="_blank" rel="noopener">' + text + '</a>' : text, true));
  }
  addCredit('Imagery © Esri &mdash; Source: Esri, Maxar, Earthstar Geographics', 'https://www.esri.com');
  addCredit('© OpenStreetMap contributors', 'https://www.openstreetmap.org/copyright');

  var currentBasemap = 'esri';
  var esriErrorCount = 0;
  var fellBack = false;

  function makeOsmLayer() {
    return new Cesium.ImageryLayer(new Cesium.OpenStreetMapImageryProvider({
      url: 'https://tile.openstreetmap.org/'
    }));
  }
  function makeEsriLayer() {
    var provider = new Cesium.UrlTemplateImageryProvider({
      url: ESRI_URL, maximumLevel: 18,
      credit: 'Imagery © Esri'
    });
    provider.errorEvent.addEventListener(function () {
      esriErrorCount++;
      if (!fellBack && currentBasemap === 'esri' && esriErrorCount >= 8) {
        fellBack = true;
        setBasemap('osm');
        bridge.onBasemapChanged('osm');
      }
    });
    return new Cesium.ImageryLayer(provider);
  }

  var baseLayer = makeEsriLayer();
  viewer.imageryLayers.add(baseLayer);

  function setBasemap(id) {
    if (id === currentBasemap) return;
    var replacement = id === 'osm' ? makeOsmLayer() : makeEsriLayer();
    viewer.imageryLayers.add(replacement, 0);
    viewer.imageryLayers.remove(baseLayer, true);
    baseLayer = replacement;
    currentBasemap = id;
  }

  /* Terrain (issue #12 root cause, verified by bisect): the AWS Terrain
     Tiles quantized-mesh dataset is gone from its bucket (layer.json
     404s, no tiles/ prefix exists). CesiumTerrainProvider.fromUrl on it
     resolved into a provider that never became ready, and the globe
     surface waits for terrain readiness forever - zero tiles, no error.
     Stage 1 therefore stays on the ellipsoid, which renders. Working
     terrain is Stage 2 (issue #15). terrainErrorCount stays in the
     diagnostics as a canary: it must remain 0 now that nothing swaps. */
  var terrainActive = 'ellipsoid';
  var terrainErrorCount = 0;

  function reportCamera() {
    var cam = viewer.camera;
    var carto = Cesium.Cartographic.fromCartesian(cam.positionWC);
    if (!carto) return;
    bridge.onCameraChanged(JSON.stringify({
      longitude: Cesium.Math.toDegrees(carto.longitude),
      latitude: Cesium.Math.toDegrees(carto.latitude),
      height: carto.height,
      heading: Cesium.Math.toDegrees(cam.heading),
      pitch: Cesium.Math.toDegrees(cam.pitch),
      roll: Cesium.Math.toDegrees(cam.roll)
    }));
  }
  var reportTimer = null;
  viewer.camera.changed.addEventListener(function () {
    if (reportTimer) clearTimeout(reportTimer);
    reportTimer = setTimeout(reportCamera, 250);
  });
  viewer.camera.moveEnd.addEventListener(reportCamera);

  function setView(v) {
    viewer.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(v.longitude, v.latitude, Math.max(v.height, 10)),
      orientation: {
        heading: Cesium.Math.toRadians(v.heading || 0),
        pitch: Cesium.Math.toRadians(v.pitch == null ? -90 : v.pitch),
        roll: Cesium.Math.toRadians(v.roll || 0)
      }
    });
  }

  var tilesQueued = -1;
  viewer.scene.globe.tileLoadProgressEvent.addEventListener(function (queued) {
    tilesQueued = queued;
  });

  /* Issue #12 round-2 probes. The beta.2 report showed: imagery errors 0,
     terrain active with only 2 tile errors, no JS errors, globe visible as
     a black disk with an atmosphere rim. So the surface occludes but draws
     black. These probes split the remaining causes: worker processing,
     image loading, WebGL capabilities, and what a real rendered frame
     actually contains (sampled inside postRender, not between frames). */
  var probe = {
    frame: 'not sampled yet',
    workerBlob: 'pending',
    workerModule: 'pending',
    imageLoad: 'pending',
    webgl: 'pending',
    providersReady: 'pending'
  };

  (function probeWebgl() {
    try {
      var canvas = document.createElement('canvas');
      var gl2 = canvas.getContext('webgl2');
      var gl = gl2 || canvas.getContext('webgl');
      if (!gl) { probe.webgl = 'NO WebGL context at all'; return; }
      var dbg = gl.getExtension('WEBGL_debug_renderer_info');
      probe.webgl = {
        version: gl2 ? 'WebGL2' : 'WebGL1 only',
        renderer: dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER),
        maxTextureSize: gl.getParameter(gl.MAX_TEXTURE_SIZE)
      };
    } catch (e) { probe.webgl = 'threw: ' + (e && e.message || e); }
  })();

  (function probeWorkers() {
    try {
      var blob = new Blob(['onmessage=function(e){postMessage("ok")}'], { type: 'text/javascript' });
      var bw = new Worker(URL.createObjectURL(blob));
      var done = false;
      bw.onmessage = function () { done = true; probe.workerBlob = 'ok'; bw.terminate(); };
      bw.onerror = function (e) { probe.workerBlob = 'error: ' + (e.message || 'unknown'); };
      bw.postMessage('go');
      setTimeout(function () { if (!done && probe.workerBlob === 'pending') probe.workerBlob = 'timeout (no reply in 3s)'; }, 3000);
    } catch (e) { probe.workerBlob = 'threw: ' + (e && e.message || e); }
    try {
      var mw = new Worker(window.CESIUM_BASE_URL + 'Workers/createVerticesFromHeightmap.js', { type: 'module' });
      var settled = false;
      mw.onerror = function (e) { settled = true; probe.workerModule = 'error: ' + (e.message || 'unknown'); mw.terminate(); };
      setTimeout(function () {
        if (!settled) { probe.workerModule = 'loaded, no error in 3s (treated as ok)'; mw.terminate(); }
      }, 3000);
    } catch (e) { probe.workerModule = 'threw: ' + (e && e.message || e); }
  })();

  (function probeImage() {
    var img = new Image();
    img.crossOrigin = 'anonymous';
    var done = false;
    img.onload = function () { done = true; probe.imageLoad = 'ok (' + img.naturalWidth + 'x' + img.naturalHeight + ')'; };
    img.onerror = function () { probe.imageLoad = 'ERROR loading Esri tile z2'; };
    img.src = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/2/1/1';
    setTimeout(function () { if (!done && probe.imageLoad === 'pending') probe.imageLoad = 'timeout (no load in 5s)'; }, 5000);
  })();

  var frameCount = 0;
  viewer.scene.postRender.addEventListener(function () {
    frameCount++;
    if (frameCount % 30 !== 0) return;
    try {
      var surface = viewer.scene.globe._surface;
      var gl = viewer.scene.context._gl;
      var w = viewer.canvas.width, h = viewer.canvas.height;
      var pts = [];
      for (var gy = 1; gy <= 5; gy++) for (var gx = 1; gx <= 5; gx++) {
        pts.push([Math.floor(w * gx / 6), Math.floor(h * gy / 6)]);
      }
      var dark = 0, total = 0;
      var one = new Uint8Array(4);
      pts.forEach(function (p) {
        gl.readPixels(p[0], p[1], 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, one);
        total++;
        if (one[0] < 12 && one[1] < 12 && one[2] < 12) dark++;
      });
      probe.frame = {
        tilesToRenderInFrame: surface && surface._tilesToRender ? surface._tilesToRender.length : -1,
        darkPixelsOf25: dark,
        frame: frameCount
      };
      probe.providersReady = {
        imagery: baseLayer.imageryProvider ? !!baseLayer.imageryProvider.ready : 'no provider',
        terrain: viewer.terrainProvider ? !!viewer.terrainProvider.ready : 'no provider'
      };
    } catch (e) {
      probe.frame = 'sampling threw: ' + (e && e.message || e);
    }
  });

  /* Issue #12 diagnostics: the actual globe state, so a phone report
     carries values instead of guesses. Sent once 6s after ready (after
     the terrain swap has had its chance) and on demand from the app. */
  function collectDiagnostics() {
    var cam = viewer.camera;
    var carto = Cesium.Cartographic.fromCartesian(cam.positionWC);
    var surface = viewer.scene.globe._surface;
    return {
      quality: activeQuality,
      basemap: currentBasemap,
      esriImageryErrors: esriErrorCount,
      terrainActive: terrainActive,
      terrainTileErrors: terrainErrorCount,
      tilesQueued: tilesQueued,
      tilesToRender: surface && surface._tilesToRender ? surface._tilesToRender.length : -1,
      cameraHeightMeters: carto ? Math.round(carto.height) : -1,
      cameraLon: carto ? +Cesium.Math.toDegrees(carto.longitude).toFixed(4) : null,
      cameraLat: carto ? +Cesium.Math.toDegrees(carto.latitude).toFixed(4) : null,
      globeShow: viewer.scene.globe.show,
      canvasPixels: viewer.canvas.width + 'x' + viewer.canvas.height,
      probeFrame: probe.frame,
      probeWorkerBlob: probe.workerBlob,
      probeWorkerModuleCesium: probe.workerModule,
      probeImageLoad: probe.imageLoad,
      probeWebgl: probe.webgl,
      probeProvidersReady: probe.providersReady,
      jsErrors: jsErrors
    };
  }
  function sendDiagnostics() {
    bridge.onDiagnostics(JSON.stringify(collectDiagnostics(), null, 1));
  }

  /* Quality modes (plan Q20): Kotlin resolves Auto from device specs and
     sends the effective mode; the page only applies render knobs.
     resolutionScale is relative to the WebView's own pixel ratio. */
  var activeQuality = 'balanced';
  function setQuality(mode) {
    var presets = {
      high: { resolutionScale: 1.0, maximumScreenSpaceError: 1.5, tileCacheSize: 400 },
      balanced: { resolutionScale: 0.75, maximumScreenSpaceError: 2.0, tileCacheSize: 200 },
      battery_saver: { resolutionScale: 0.5, maximumScreenSpaceError: 3.0, tileCacheSize: 100 }
    };
    var preset = presets[mode] || presets.balanced;
    activeQuality = presets[mode] ? mode : 'balanced';
    viewer.resolutionScale = preset.resolutionScale;
    viewer.scene.globe.maximumScreenSpaceError = preset.maximumScreenSpaceError;
    viewer.scene.globe.tileCacheSize = preset.tileCacheSize;
  }

  window.GEV = {
    setView: setView,
    resetGlobe: function () {
      setView({ longitude: 0, latitude: 18, height: 22000000, heading: 0, pitch: -90, roll: 0 });
    },
    setBasemap: setBasemap,
    setQuality: setQuality,
    reportDiagnostics: sendDiagnostics
  };

  bridge.onGlobeReady();
  setTimeout(sendDiagnostics, 9000);
})();
