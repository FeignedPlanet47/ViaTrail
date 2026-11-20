package com.viatrail.app

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.viatrail.data.BuildConfig
import com.viatrail.domain.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point as GeoJsonPoint

@Composable
fun MapPanel(route:Route?,stops:List<Place>,modifier:Modifier=Modifier,current:Point?=null) {
    val key= BuildConfig.GEOAPIFY_KEY
    if(key.isBlank()) { Surface(modifier) { Text("Добавь GEOAPIFY_KEY в ~/.gradle/gradle.properties",Modifier.padding(20.dp)) };return }
    val latestRoute by rememberUpdatedState(route)
    val latestStops by rememberUpdatedState(stops)
    val latestLocation by rememberUpdatedState(current)
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val view=remember(context) { MapLibre.getInstance(context);MapView(context).apply { onCreate(null) } }
    var lastRouteSignature by remember { mutableStateOf("") }
    DisposableEffect(view,owner) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_START->view.onStart()
            Lifecycle.Event.ON_RESUME->view.onResume()
            Lifecycle.Event.ON_PAUSE->view.onPause()
            Lifecycle.Event.ON_STOP->view.onStop()
            else->Unit
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);view.onPause();view.onStop();view.onDestroy() }
    }
    AndroidView(modifier=modifier,factory={
        view.apply { getMapAsync { map -> map.setStyle(Style.Builder().fromUri("https://maps.geoapify.com/v1/styles/osm-carto/style.json?apiKey=$key")) { style ->
            style.addSource(GeoJsonSource("via-route"))
            style.addLayer(LineLayer("via-line","via-route").withProperties(lineColor(Color.rgb(28,104,205)),lineWidth(5f)))
            style.addSource(GeoJsonSource("via-stops"))
            style.addLayer(CircleLayer("via-dots","via-stops").withProperties(circleColor(Color.rgb(243,91,64)),circleRadius(7f),circleStrokeColor(Color.WHITE),circleStrokeWidth(2f)))
            style.addSource(GeoJsonSource("via-location"))
            style.addLayer(CircleLayer("via-current","via-location").withProperties(circleColor(Color.rgb(20,130,230)),circleRadius(8f),circleStrokeColor(Color.WHITE),circleStrokeWidth(2f)))
            updateMap(map,latestRoute,latestStops,latestLocation)
            latestRoute?.let { if(it.points.size>=2) { lastRouteSignature=signature(it);zoom(map,it) } }
        } } }
    },update={ v -> v.getMapAsync { map ->
        updateMap(map,route,stops,current)
        route?.let { if(it.points.size>=2 && lastRouteSignature!=signature(it)) { lastRouteSignature=signature(it);zoom(map,it) } }
    } })
}
private fun signature(r:Route):String="${r.points.size}:${r.distanceMeters}:${r.points.firstOrNull()}:${r.points.lastOrNull()}"
private fun zoom(map:MapLibreMap,route:Route) {
    val coords=route.points
    if(coords.size<2) return
    val bounds=LatLngBounds.Builder().includes(coords.map { LatLng(it.lat,it.lon) }).build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,70),600)
}
private fun updateMap(map:MapLibreMap,route:Route?,stops:List<Place>,current:Point?) {
    val style=map.style?:return
    val coords=route?.points.orEmpty().map { GeoJsonPoint.fromLngLat(it.lon,it.lat) }
    if(coords.size>=2) style.getSourceAs<GeoJsonSource>("via-route")?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(coords)))
    else style.getSourceAs<GeoJsonSource>("via-route")?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    style.getSourceAs<GeoJsonSource>("via-stops")?.setGeoJson(FeatureCollection.fromFeatures(stops.map { Feature.fromGeometry(GeoJsonPoint.fromLngLat(it.coordinate.lon,it.coordinate.lat)) }))
    style.getSourceAs<GeoJsonSource>("via-location")?.setGeoJson(FeatureCollection.fromFeatures(current?.let { listOf(Feature.fromGeometry(GeoJsonPoint.fromLngLat(it.lon,it.lat))) } ?: emptyList()))
}
