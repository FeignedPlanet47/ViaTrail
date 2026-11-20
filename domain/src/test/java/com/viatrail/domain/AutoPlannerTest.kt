package com.viatrail.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlannerTest {
    private class FakeRepository : TravelRepository {
        override suspend fun geocode(address: String) = Point(0.0, 0.0)
        override suspend fun route(points: List<Point>): Route {
            val distance = (points.size - 2) * 12_000.0 + 40_000.0
            return Route(points, distance, distance / 15.0)
        }
        override suspend fun discover(route: Route, category: String) = emptyList<Place>()
        override suspend fun details(place: Place) = place
        override suspend fun save(trip: Trip) = 1L
        override suspend fun delete(id: Long) {}
        override fun observeTrips(): Flow<List<Trip>> = flowOf(emptyList())
    }

    @Test fun rejectsStopsWhenDetourLimitIsExceeded() = runBlocking {
        val start = Point(10.0, 10.0)
        val end = Point(11.0, 11.0)
        val base = FakeRepository().route(listOf(start, end))
        val candidate = Place("one", "Museum", "Интересные места", Point(10.5, 10.5), "", distanceFromRouteKm = 1.0)
        val result = AutoPlanner(FakeRepository()).optimize(start, end, base,
            listOf(candidate), PlanSettings(maxDetourKm = 5.0, visitMinutes = 30, maxTotalMinutes = 240))
        assertTrue(result.places.isEmpty())
        assertEquals(base, result.route)
    }
    @Test fun addsStopWhenBothLimitsAllowIt() = runBlocking {
        val start = Point(10.0, 10.0)
        val end = Point(11.0, 11.0)
        val base = FakeRepository().route(listOf(start, end))
        val candidate = Place("one", "Museum", "Интересные места", Point(10.5, 10.5), "", distanceFromRouteKm = 1.0)
        val result = AutoPlanner(FakeRepository()).optimize(start, end, base,
            listOf(candidate), PlanSettings(maxDetourKm = 20.0, visitMinutes = 30, maxTotalMinutes = 240))
        assertEquals(1, result.places.size)
    }
}
