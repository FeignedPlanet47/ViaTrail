package com.viatrail.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viatrail.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TravelUiState(val loading: Boolean = false, val error: String? = null, val route: Route? = null, val candidates: List<Place> = emptyList(), val selected: List<Place> = emptyList(), val from: String = "", val to: String = "")
@HiltViewModel class TravelViewModel @Inject constructor(private val repository: TravelRepository): ViewModel() {
    private val _state = MutableStateFlow(TravelUiState())
    val state = _state.asStateFlow()
    val trips = repository.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private fun execute(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(loading=true, error=null) }
        try { block() } catch (e: Exception) { _state.update { it.copy(error=e.message ?: "Ошибка запроса") } }
        finally { _state.update { it.copy(loading=false) } }
    }
    fun search(from: String, to: String, category: String) = execute {
        require(from.isNotBlank() && to.isNotBlank()) { "Укажи два города" }
        val start = repository.geocode(from); val end = repository.geocode(to)
        val route = repository.route(listOf(start,end))
        _state.update { it.copy(route=route, from=from, to=to, candidates=emptyList(), selected=emptyList()) }
        val places = repository.discover(route, category)
        _state.update { it.copy(candidates=places) }
    }
    fun toggle(place: Place) = execute {
        val updated = _state.value.selected.toMutableList()
        if (updated.any { it.id == place.id }) updated.removeAll { it.id == place.id } else updated.add(place)
        val start = repository.geocode(_state.value.from); val end = repository.geocode(_state.value.to)
        val route = repository.route(listOf(start) + updated.map { it.coordinate } + end)
        _state.update { it.copy(selected=updated,route=route) }
    }
    fun move(index: Int, direction: Int) = execute {
        val current = _state.value.selected.toMutableList()
        val other = index+direction
        if (index !in current.indices || other !in current.indices) return@execute
        val value = current.removeAt(index); current.add(other,value)
        val start = repository.geocode(_state.value.from); val end = repository.geocode(_state.value.to)
        val route = repository.route(listOf(start) + current.map { it.coordinate } + end)
        _state.update { it.copy(selected=current,route=route) }
    }
    fun save() = execute {
        check(_state.value.route != null) { "Сначала построй маршрут" }
        repository.save(Trip(from=_state.value.from,to=_state.value.to,places=_state.value.selected))
    }
    fun open(trip: Trip) = execute {
        val start=repository.geocode(trip.from); val end=repository.geocode(trip.to)
        val route=repository.route(listOf(start)+trip.places.map { it.coordinate }+end)
        _state.update { it.copy(from=trip.from,to=trip.to,selected=trip.places,candidates=emptyList(),route=route) }
    }
    fun delete(id:Long) = viewModelScope.launch { repository.delete(id) }
}
