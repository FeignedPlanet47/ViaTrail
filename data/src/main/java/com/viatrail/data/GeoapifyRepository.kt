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
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class GeoapifyRepository(private val dao: TripsDao, private val client: OkHttpClient) : TravelRepository {
    private val key get() = BuildConfig.GEOAPIFY_KEY
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
        check(key.isNotBlank()) { "Добавь GEOAPIFY_KEY в ~/.gradle/gradle.properties" }
        val request = Request.Builder().url(url).header("User-Agent", "ViaTrail/0.1 (Android; contact: https://github.com)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Сервис карт вернул HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Пустой ответ сервера"))
        }
    }
    private fun features(obj: JSONObject) = obj.optJSONArray("features") ?: JSONArray()
    override suspend fun geocode(address: String): Point {
        val json = fetch("https://api.geoapify.com/v1/geocode/search?text=${encode(address)}&limit=1&apiKey=${encode(key)}")
        val props = features(json).optJSONObject(0)?.optJSONObject("properties") ?: error("Адрес не найден: $address")
        return Point(props.getDouble("lat"), props.getDouble("lon"))
    }
    override suspend fun route(points: List<Point>): Route {
        require(points.size >= 2)
        val waypoints = points.joinToString("|") { "${it.lat},${it.lon}" }
        val json = fetch("https://api.geoapify.com/v1/routing?waypoints=${encode(waypoints)}&mode=drive&apiKey=${encode(key)}")
        val feature = features(json).optJSONObject(0) ?: error("Маршрут не найден")
        val properties = feature.getJSONObject("properties")
        val geometry = feature.getJSONObject("geometry")
        val coordinates = geometry.getJSONArray("coordinates")
        val all = mutableListOf<Point>()
        fun append(line: JSONArray) {
            for (i in 0 until line.length()) {
                val coord = line.getJSONArray(i)
                all.add(Point(coord.getDouble(1), coord.getDouble(0)))
            }
        }
        when (geometry.getString("type")) {
            "LineString" -> append(coordinates)
            "MultiLineString" -> for (i in 0 until coordinates.length()) append(coordinates.getJSONArray(i))
            else -> error("Неизвестная геометрия маршрута")
        }
        if (all.isEmpty()) error("В ответе нет геометрии маршрута")
        return Route(all, properties.optDouble("distance"), properties.optDouble("time"))
    }
    override suspend fun discover(route: Route, category: String): List<Place> {
        val allowed = mapOf("Интересные места" to "tourism.attraction", "Кафе" to "catering.cafe", "Отели" to "accommodation.hotel")
        val code = allowed[category] ?: return emptyList()
        // Three samples to avoid hitting API limits on every polyline vertex.
        val samples = listOf(1, 2, 3).map { route.points[(route.points.lastIndex * it / 4).coerceIn(route.points.indices)] }
        val result = linkedMapOf<String, Place>()
        for (sample in samples) {
            val location = "${sample.lon},${sample.lat}"
            val json = fetch("https://api.geoapify.com/v2/places?categories=${encode(code)}&filter=${encode("circle:$location,5000")}&bias=${encode("proximity:$location")}&limit=12&apiKey=${encode(key)}")
            val features = features(json)
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.optJSONObject("properties") ?: continue
                val geo = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val id = props.optString("place_id").ifBlank { "${geo.optDouble(1)}:${geo.optDouble(0)}" }
                val name = props.optString("name").ifBlank { props.optString("address_line1", "Без названия") }
                result[id] = Place(id, name, category, Point(geo.getDouble(1), geo.getDouble(0)), props.optString("formatted"))
            }
        }
        return result.values.take(30)
    }
    override suspend fun save(trip: Trip) {
        val stops = JSONArray().apply { trip.places.forEach { p -> put(JSONObject().put("id",p.id).put("name",p.name).put("category",p.category).put("lat",p.coordinate.lat).put("lon",p.coordinate.lon).put("address",p.address)) } }
        dao.insert(TripEntity(start=trip.from, destination=trip.to, stopsJson=stops.toString(), createdAt=trip.createdAt))
    }
    override fun observeTrips(): Flow<List<Trip>> = dao.all().map { rows -> rows.map { e ->
        val arr = JSONArray(e.stopsJson)
        val places = (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); Place(o.getString("id"), o.getString("name"), o.getString("category"), Point(o.getDouble("lat"),o.getDouble("lon")),o.optString("address")) }
        Trip(e.id,e.start,e.destination,places,e.createdAt)
    } }
    override suspend fun delete(id: Long) = dao.delete(id)
}
