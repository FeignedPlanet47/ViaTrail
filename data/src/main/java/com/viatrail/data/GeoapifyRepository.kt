package com.viatrail.data

import com.viatrail.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class GeoapifyRepository(private val dao: TripsDao, private val client: OkHttpClient) : TravelRepository {
    private val key get() = BuildConfig.GEOAPIFY_KEY
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
    private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
        check(key.isNotBlank()) { "Добавь GEOAPIFY_KEY в ~/.gradle/gradle.properties" }
        client.newCall(Request.Builder().url(url).header("User-Agent", "ViaTrail/0.2 Android").build()).execute().use { response ->
            if (!response.isSuccessful) error("Geoapify: HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Пустой ответ сервера"))
        }
    }
    private fun features(json: JSONObject) = json.optJSONArray("features") ?: JSONArray()
    override suspend fun geocode(address: String): Point {
        val json = fetch("https://api.geoapify.com/v1/geocode/search?text=${encode(address)}&limit=1&apiKey=${encode(key)}")
        val props = features(json).optJSONObject(0)?.optJSONObject("properties") ?: error("Адрес не найден: $address")
        return Point(props.getDouble("lat"),props.getDouble("lon"))
    }
    override suspend fun route(points: List<Point>): Route {
        require(points.size >= 2) { "Укажи минимум две точки" }
        val waypoints = points.joinToString("|") { "${it.lat},${it.lon}" }
        val obj = fetch("https://api.geoapify.com/v1/routing?waypoints=${encode(waypoints)}&mode=drive&apiKey=${encode(key)}")
        val f = features(obj).optJSONObject(0) ?: error("Маршрут не найден")
        val p = f.getJSONObject("properties")
        val geometry = f.getJSONObject("geometry")
        val all = mutableListOf<Point>()
        fun append(line: JSONArray) { for (i in 0 until line.length()) {
            val point=line.getJSONArray(i); all.add(Point(point.getDouble(1),point.getDouble(0)))
        } }
        val coords=geometry.getJSONArray("coordinates")
        when (geometry.getString("type")) {
            "LineString" -> append(coords)
            "MultiLineString" -> for (i in 0 until coords.length()) append(coords.getJSONArray(i))
            else -> error("Неподдерживаемый тип маршрута")
        }
        check(all.size >= 2) { "Маршрут без геометрии" }
        return Route(all,p.optDouble("distance"),p.optDouble("time"))
    }
    override suspend fun discover(route: Route, category: String): List<Place> {
        val codes = mapOf("Интересные места" to "tourism.attraction", "Кафе" to "catering.cafe", "Отели" to "accommodation.hotel")
        val code=codes[category] ?: return emptyList()
        val path=route.points
        if(path.size<2) return emptyList()
        // Sample by distance, rather than raw vertex indexes (dense bends should not dominate).
        val samples=mutableListOf(path.first())
        val step=(route.distanceMeters/7).coerceAtLeast(1500.0)
        var passed=0.0; var next=step
        for(i in 1 until path.size) {
            passed+=RouteGeometry.distanceMeters(path[i-1],path[i])
            if(passed >= next) { samples.add(path[i]); next += step }
        }
        val result=linkedMapOf<String,Place>()
        for(sample in samples.take(7)) {
            val location="${sample.lon},${sample.lat}"
            val json=fetch("https://api.geoapify.com/v2/places?categories=${encode(code)}&filter=${encode("circle:$location,10000")}&bias=${encode("proximity:$location")}&limit=12&apiKey=${encode(key)}")
            val features=features(json)
            for(i in 0 until features.length()) {
                val f=features.getJSONObject(i); val props=f.optJSONObject("properties")?:continue
                val xy=f.optJSONObject("geometry")?.optJSONArray("coordinates")?:continue
                val coordinate=Point(xy.getDouble(1),xy.getDouble(0))
                val id=props.optString("place_id").ifBlank { "${coordinate.lat}:${coordinate.lon}" }
                val name=props.optString("name").ifBlank { props.optString("address_line1", "Без названия") }
                val deviation=RouteGeometry.approximateDistanceToRoute(coordinate,path)/1000.0
                val description=props.optString("description").takeIf { it.isNotBlank() } ?: "Информация о месте уточняется"
                val minutes=when(category) { "Кафе" -> 45; "Отели" -> 60; else -> 40 }
                result[id]=Place(id,name,category,coordinate,props.optString("formatted"),description,minutes,deviation)
            }
        }
        return result.values.sortedBy { it.distanceFromRouteKm }.take(45)
    }
    override suspend fun details(place: Place): Place {
        if(!place.id.contains('-') && place.id.contains(':')) return place
        val json=fetch("https://api.geoapify.com/v2/place-details?id=${encode(place.id)}&apiKey=${encode(key)}")
        val arr=features(json)
        val details=(0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optJSONObject("properties") }.firstOrNull() ?: return place
        val description=details.optString("description").ifBlank { place.description }
        val website=details.optString("website").takeIf { it.startsWith("https://") }
        return place.copy(description=description,website=website)
    }
    private fun placeToJson(p: Place): JSONObject = JSONObject()
        .put("id",p.id).put("name",p.name).put("category",p.category)
        .put("lat",p.coordinate.lat).put("lon",p.coordinate.lon)
        .put("address",p.address).put("description",p.description)
        .put("visitMinutes",p.visitMinutes).put("distanceFromRouteKm",p.distanceFromRouteKm)
        .put("website",p.website ?: "")
    private fun readPlace(o: JSONObject)=Place(o.getString("id"),o.getString("name"),o.getString("category"),
        Point(o.getDouble("lat"),o.getDouble("lon")),o.optString("address"),
        o.optString("description"),o.optInt("visitMinutes",30),o.optDouble("distanceFromRouteKm",0.0),
        o.optString("website").takeIf { it.isNotBlank() })
    override suspend fun save(trip: Trip): Long {
        val stops=JSONArray().apply { trip.places.forEach { put(placeToJson(it)) } }
        val route=trip.route?.let { r -> JSONObject().put("distance",r.distanceMeters).put("duration",r.durationSeconds)
            .put("points",JSONArray().apply { r.points.forEach { put(JSONArray().put(it.lat).put(it.lon)) } }) }?.toString() ?: ""
        val settings=JSONObject().put("maxDetourKm",trip.settings.maxDetourKm)
            .put("visitMinutes",trip.settings.visitMinutes).put("maxTotalMinutes",trip.settings.maxTotalMinutes)
        return dao.insert(TripEntity(trip.id,trip.from,trip.to,stops.toString(),trip.createdAt,route,settings.toString()))
    }
    override fun observeTrips(): Flow<List<Trip>> = dao.all().map { rows -> rows.map { e ->
        val arr=JSONArray(e.stopsJson)
        val places=(0 until arr.length()).map { readPlace(arr.getJSONObject(it)) }
        val r=runCatching { if(e.routeJson.isBlank()) null else JSONObject(e.routeJson).let { obj ->
            val arr2=obj.getJSONArray("points")
            Route((0 until arr2.length()).map { i -> val c=arr2.getJSONArray(i); Point(c.getDouble(0),c.getDouble(1)) },
                obj.getDouble("distance"),obj.getDouble("duration")) } }.getOrNull()
        val s=runCatching { JSONObject(e.settingsJson) }.getOrNull()
        Trip(e.id,e.start,e.destination,places,e.createdAt,r,
            PlanSettings(s?.optDouble("maxDetourKm",20.0) ?: 20.0,
                s?.optInt("visitMinutes",30) ?: 30,s?.optInt("maxTotalMinutes",480) ?: 480))
    } }
    override suspend fun delete(id: Long) = dao.delete(id)
}
