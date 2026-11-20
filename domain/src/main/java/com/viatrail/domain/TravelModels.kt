package com.viatrail.domain

import kotlinx.coroutines.flow.Flow

data class Point(val lat: Double, val lon: Double)
data class Place(val id: String, val name: String, val category: String, val coordinate: Point, val address: String)
data class Route(val points: List<Point>, val distanceMeters: Double, val durationSeconds: Double)
data class Trip(val id: Long = 0L, val from: String, val to: String, val places: List<Place>, val createdAt: Long = System.currentTimeMillis())
interface TravelRepository {
    suspend fun geocode(address: String): Point
    suspend fun route(points: List<Point>): Route
    suspend fun discover(route: Route, category: String): List<Place>
    suspend fun save(trip: Trip)
    suspend fun delete(id: Long)
    fun observeTrips(): Flow<List<Trip>>
}
class GetRouteUseCase(private val repository: TravelRepository) {
    suspend operator fun invoke(from: String, to: String): Route = repository.route(listOf(repository.geocode(from), repository.geocode(to)))
}
