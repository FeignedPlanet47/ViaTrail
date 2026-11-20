package com.viatrail.app

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.viatrail.domain.*
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point as GeoJsonPoint
import com.viatrail.data.BuildConfig

@Composable
fun MapPanel(route: Route?, stops: List<Place>, modifier: Modifier = Modifier) {
    val apiKey = BuildConfig.GEOAPIFY_KEY
    if (apiKey.isBlank()) {
        Surface(modifier) { Box(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Для загрузки карты добавь ключ Geoapify в ~/.gradle/gradle.properties и синхронизируй проект.")
        } }
        return
    }
    val latestRoute by rememberUpdatedState(route)
    val latestStops by rememberUpdatedState(stops)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                Lifecycle.Event.ON_DESTROY -> view.onDestroy()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); view.onDestroy() }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            view.apply {
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromUri("https://maps.geoapify.com/v1/styles/osm-carto/style.json?apiKey=$apiKey")) { style ->
                        style.addSource(GeoJsonSource("via-route"))
                        style.addLayer(LineLayer("via-line", "via-route").withProperties(lineColor(Color.rgb(35, 108, 205)), lineWidth(5f)))
                        style.addSource(GeoJsonSource("via-stops"))
                        style.addLayer(CircleLayer("via-dots", "via-stops").withProperties(circleColor(Color.rgb(245, 90, 65)), circleRadius(7f), circleStrokeColor(Color.WHITE), circleStrokeWidth(2f)))
                        updateMap(map,latestRoute,latestStops)
                    }
                }
            }
        },
        update = { view -> view.getMapAsync { map -> updateMap(map,route,stops) } }
    )
}
private fun updateMap(map: org.maplibre.android.maps.MapLibreMap, route: Route?, stops: List<Place>) {
    val style = map.style ?: return
    val coords = route?.points.orEmpty().map { GeoJsonPoint.fromLngLat(it.lon, it.lat) }
    if (coords.size >= 2) style.getSourceAs<GeoJsonSource>("via-route")?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(coords)))
    val points = stops.map { Feature.fromGeometry(GeoJsonPoint.fromLngLat(it.coordinate.lon,it.coordinate.lat)) }
    style.getSourceAs<GeoJsonSource>("via-stops")?.setGeoJson(FeatureCollection.fromFeatures(points))
    if (coords.size >= 2) {
        val bounds = LatLngBounds.Builder().includes(route!!.points.map { LatLng(it.lat,it.lon) }).build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 700)
    }
}
