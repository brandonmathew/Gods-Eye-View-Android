package com.makoallc.godseyeview.layersample

import com.makoallc.godseyeview.layerapi.CardField
import com.makoallc.godseyeview.layerapi.Drawable
import com.makoallc.godseyeview.layerapi.Entity
import com.makoallc.godseyeview.layerapi.GeoPoint
import com.makoallc.godseyeview.layerapi.Layer
import com.makoallc.godseyeview.layerapi.LayerDataSource
import com.makoallc.godseyeview.layerapi.MapLabel
import com.makoallc.godseyeview.layerapi.MapPoint
import com.makoallc.godseyeview.layerapi.PolygonRegion
import com.makoallc.godseyeview.layerapi.SystemWorldClock
import com.makoallc.godseyeview.layerapi.Track
import com.makoallc.godseyeview.layerapi.WorldClock
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Stage 4 throwaway proof layer (issue #47). Fake data only, generated
 *  locally - no network. Proves the whole layer system end to end:
 *  registry, panel, fetch polling, all four v1 primitives, picking, the
 *  generic detail card, and follow (the plane point moves every fetch).
 *  DELETED after Brandon confirms the proof - nothing here ships. */
class SampleLayer(private val clock: WorldClock = SystemWorldClock()) : Layer {
    override val id: String = "sample"
    override val title: String = "Sample layer (proof)"
    override val refreshInterval: Duration = 5.seconds
    override val freshnessTtl: Duration = 30.seconds
    override val credit: String = "Built-in sample data - no external source"

    override val dataSource: LayerDataSource = LayerDataSource { fetchSample() }

    private fun fetchSample(): List<Drawable> {
        val honolulu = GeoPoint(21.3245, -157.9251)
        val tokyo = GeoPoint(35.5494, 139.7798)
        // The "flight" loops Honolulu -> Tokyo over 10 minutes of clock.
        val fraction = (clock.nowMillis() % LOOP_MILLIS) / LOOP_MILLIS.toDouble()
        val planePosition = interpolate(honolulu, tokyo, fraction)
        val plane = Entity(
            id = "plane",
            layerId = id,
            position = planePosition,
            title = "Sample flight GEV1",
            cardFields = listOf(
                CardField("Route", "Honolulu to Tokyo"),
                CardField("Progress", "${(fraction * 100).toInt()}%"),
                CardField("Altitude", "${planePosition.altitude.toInt()} m"),
            ),
        )
        val arc = (0..ARC_STEPS).map { step ->
            interpolate(honolulu, tokyo, step / ARC_STEPS.toDouble())
        }
        return listOf(
            MapPoint(
                id,
                Entity(
                    id = "honolulu",
                    layerId = id,
                    position = honolulu,
                    title = "Honolulu (sample point)",
                    cardFields = listOf(CardField("Kind", "Sample point")),
                ),
            ),
            MapPoint(
                id,
                Entity(
                    id = "tokyo",
                    layerId = id,
                    position = tokyo,
                    title = "Tokyo (sample point)",
                    cardFields = listOf(CardField("Kind", "Sample point")),
                ),
            ),
            MapPoint(id, plane),
            Track(id, entityId = "plane", points = arc),
            PolygonRegion(
                id,
                entityId = "oahu",
                vertices = listOf(
                    GeoPoint(21.20, -158.30),
                    GeoPoint(21.20, -157.60),
                    GeoPoint(21.75, -157.60),
                    GeoPoint(21.75, -158.30),
                ),
            ),
            MapLabel(id, GeoPoint(28.0, -175.0), "Sample label - mid-Pacific"),
        )
    }

    /** Straight lat/lon interpolation with a sine altitude arc. */
    private fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint {
        // Longitude takes the short way across the Pacific (dateline-safe
        // enough for a sample: lerp, then wrap into -180..180).
        var lonDelta = b.longitude - a.longitude
        if (lonDelta > 180.0) lonDelta -= 360.0
        if (lonDelta < -180.0) lonDelta += 360.0
        var lon = a.longitude + lonDelta * fraction
        if (lon > 180.0) lon -= 360.0
        if (lon < -180.0) lon += 360.0
        return GeoPoint(
            latitude = a.latitude + (b.latitude - a.latitude) * fraction,
            longitude = lon,
            altitude = sin(fraction * Math.PI) * CRUISE_ALTITUDE,
        )
    }

    private companion object {
        const val LOOP_MILLIS = 600_000L
        const val ARC_STEPS = 32
        const val CRUISE_ALTITUDE = 10_000.0
    }
}
