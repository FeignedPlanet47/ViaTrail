package com.viatrail.domain

import kotlinx.coroutines.flow.Flow
import kotlin.math.*

data class Point(val lat: Double, val lon: Double)
data class Place(
    val id: String, val name: String, val category: String,
    val coordinate: Point, val address: String,
    val description: String = "", val visitMinutes: Int = 30,
    val distanceFromRouteKm: Double = 0.0, val website: String? = null
)
data class Route(val points: List<Point>, val distanceMeters: Double, val durationSeconds: Double)
data class PlanSettings(
    val maxDetourKm: Double = 20.0,
    val visitMinutes: Int = 30,
    val maxTotalMinutes: Int = 480
)
data class Trip(
    val id: Long = 0L, val from: String, val to: String,
    val places: List<Place>, val createdAt: Long = System.currentTimeMillis(),
    val route: Route? = null, val settings: PlanSettings = PlanSettings()
)
interface TravelRepository {
    suspend fun geocode(address: String): Point
    suspend fun route(points: List<Point>): Route
    suspend fun discover(route: Route, category: String): List<Place>
    suspend fun details(place: Place): Place
    suspend fun save(trip: Trip): Long
    suspend fun delete(id: Long)
    fun observeTrips(): Flow<List<Trip>>
}
/** Approximate shortest great-circle distance to the supplied route vertices.
 * Screening only; an exact driving detour requires a routing API request. */
object RouteGeometry {
    fun distanceMeters(a: Point, b: Point): Double {
        val la1 = Math.toRadians(a.lat); val la2 = Math.toRadians(b.lat)
        val dlat = la2 - la1; val dlon = Math.toRadians(b.lon - a.lon)
        val x = sin(dlat/2).pow(2) + cos(la1)*cos(la2)*sin(dlon/2).pow(2)
        return 6371000.0 * 2 * atan2(sqrt(x.coerceIn(0.0,1.0)),sqrt((1-x).coerceAtLeast(0.0)))
    }
    fun approximateDistanceToRoute(point: Point, path: List<Point>): Double {
        if (path.isEmpty()) return Double.POSITIVE_INFINITY
        // Project onto local tangent-plane segments (sufficient for initial POI filtering).
        val metersPerLat = 111195.0
        val metersPerLon = metersPerLat * cos(Math.toRadians(point.lat)).coerceAtLeast(0.01)
        fun xy(p:Point) = Pair((p.lon-point.lon)*metersPerLon,(p.lat-point.lat)*metersPerLat)
        if(path.size==1) return distanceMeters(point,path[0])
        return path.zipWithNext().minOf { (a,b) ->
            val (ax,ay)=xy(a); val (bx,by)=xy(b)
            val dx=bx-ax; val dy=by-ay
            val t=if(dx*dx+dy*dy<1e-6) 0.0 else (-(ax*dx+ay*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0)
            hypot(ax+dx*t,ay+dy*t)
        }
    }
}
