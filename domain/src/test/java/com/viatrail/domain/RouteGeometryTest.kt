package com.viatrail.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {
    @Test fun zeroDistance() {
        assertEquals(0.0, RouteGeometry.distanceMeters(Point(55.75,37.61), Point(55.75,37.61)), 0.001)
    }
    @Test fun locationOnSegmentIsNearLine() {
        val path=listOf(Point(55.0,37.0),Point(55.0,38.0))
        assertTrue(RouteGeometry.approximateDistanceToRoute(Point(55.0,37.5),path)<50.0)
    }
    @Test fun distantLocationIsRejectedByApproximateFilter() {
        val path=listOf(Point(55.0,37.0),Point(55.0,38.0))
        assertTrue(RouteGeometry.approximateDistanceToRoute(Point(56.0,37.5),path)>100000.0)
    }
}
