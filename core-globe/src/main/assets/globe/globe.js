'use strict';
/* God's Eye View Android - core globe (Stage 1).
   Keyless basemap ladder (plan Q4): Esri World Imagery first, OSM if Esri
   tiles keep failing. Keyless terrain from AWS Terrain Tiles
   (quantized-mesh, Mapzen/Tilezen data, ODbL/attribution in Credits);
   if the terrain service is unreachable the globe continues on the
   ellipsoid - imagery is never blocked by terrain. */
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
  var TERRAIN_URL = 'https://s3.amazonaws.com/elevation-tiles-prod/tiles/terrain/';

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
  addCredit('Terrain: AWS Terrain Tiles (Mapzen/Tilezen, ODbL)', 'https://registry.opendata.aws/terrain-tiles/');
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

  /* Terrain self-heal (issue #12): if the AWS terrain provider loads but
     its tiles keep erroring in this WebView, surface tiles never become
     renderable and the globe disappears entirely (only the skybox shows).
     Same pattern as the imagery fallback: 8 tile errors and we revert to
     the ellipsoid, which always renders. */
  var terrainActive = 'ellipsoid';
  var terrainErrorCount = 0;
  var terrainReverted = false;
  Cesium.CesiumTerrainProvider.fromUrl(TERRAIN_URL, { requestVertexNormals: false })
    .then(function (provider) {
      provider.errorEvent.addEventListener(function () {
        terrainErrorCount++;
        if (!terrainReverted && terrainErrorCount >= 8) {
          terrainReverted = true;
          terrainActive = 'ellipsoid (reverted after ' + terrainErrorCount + ' terrain tile errors)';
          viewer.terrainProvider = new Cesium.EllipsoidTerrainProvider();
          sendDiagnostics();
        }
      });
      viewer.terrainProvider = provider;
      terrainActive = 'aws-terrain-tiles';
    })
    .catch(function (err) {
      terrainActive = 'ellipsoid (terrain provider failed to load: ' +
        (err && err.message || err || 'unknown') + ')';
    });

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

  /* Issue #12 diagnostics: the actual globe state, so a phone report
     carries values instead of guesses. Sent once 6s after ready (after
     the terrain swap has had its chance) and on demand from the app. */
  function collectDiagnostics() {
    var cam = viewer.camera;
    var carto = Cesium.Cartographic.fromCartesian(cam.positionWC);
    var surface = viewer.scene.globe._surface;
    return {
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
      jsErrors: jsErrors
    };
  }
  function sendDiagnostics() {
    bridge.onDiagnostics(JSON.stringify(collectDiagnostics(), null, 1));
  }

  window.GEV = {
    setView: setView,
    resetGlobe: function () {
      setView({ longitude: 0, latitude: 18, height: 22000000, heading: 0, pitch: -90, roll: 0 });
    },
    setBasemap: setBasemap,
    reportDiagnostics: sendDiagnostics
  };

  bridge.onGlobeReady();
  setTimeout(sendDiagnostics, 6000);
})();
