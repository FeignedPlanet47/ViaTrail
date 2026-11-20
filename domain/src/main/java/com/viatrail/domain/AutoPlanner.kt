package com.viatrail.domain

/** Greedy trip planner. The routing service validates each actual detour and duration. */
class AutoPlanner(private val repository: TravelRepository) {
    suspend fun optimize(
        start: Point, end: Point, base: Route, candidates: List<Place>, settings: PlanSettings,
        maxStops: Int = 5
    ): AutoPlanResult {
        val selected = mutableListOf<Place>()
        var current = base
        val unique = candidates.distinctBy { it.id }.filter {
            it.coordinate != start && it.coordinate != end && it.distanceFromRouteKm <= settings.maxDetourKm
        }
        val remaining = unique.toMutableList()
        while (selected.size < maxStops && remaining.isNotEmpty()) {
            var best: Pair<Place, Route>? = null
            var bestScore = Double.POSITIVE_INFINITY
            // Hard cap protects the quota of routing requests on public/low-tier API plans.
            for (place in remaining.take(12)) {
                val trial = repository.route(listOf(start) + selected.map { it.coordinate } + place.coordinate + end)
                val extraKm = ((trial.distanceMeters - base.distanceMeters) / 1000).coerceAtLeast(0.0)
                val visitTime = selected.sumOf { it.visitMinutes } + settings.visitMinutes
                val totalMinutes = trial.durationSeconds / 60.0 + visitTime
                if (extraKm > settings.maxDetourKm || totalMinutes > settings.maxTotalMinutes) continue
                // Rank by marginal driving minutes plus deviation; preserves discovered order as tie-breaker.
                val score = (trial.durationSeconds - current.durationSeconds).coerceAtLeast(0.0) / 60.0 +
                    place.distanceFromRouteKm * 1.5
                if (score < bestScore) {
                    bestScore = score
                    best = place.copy(visitMinutes = settings.visitMinutes) to trial
                }
            }
            val choice = best ?: break
            selected += choice.first
            current = choice.second
            remaining.removeAll { it.id == choice.first.id }
        }
        return AutoPlanResult(selected, current)
    }
}

data class AutoPlanResult(val places: List<Place>, val route: Route)
