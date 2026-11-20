package com.viatrail.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viatrail.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TravelUiState(
    val loading: Boolean = false, val error: String? = null, val info: String? = null,
    val route: Route? = null, val directRoute: Route? = null,
    val candidates: List<Place> = emptyList(), val selected: List<Place> = emptyList(),
    val skippedIds: Set<String> = emptySet(), val from: String = "", val to: String = "",
    val category: String = "Интересные места", val settings: PlanSettings = PlanSettings(),
    val startPoint: Point? = null, val endPoint: Point? = null,
    val activeTripId: Long = 0, val createdAt: Long = System.currentTimeMillis(),
    val offlineCopy: Boolean = false
) {
    val visitMinutes get()= selected.sumOf { it.visitMinutes }
    val totalMinutes get()=(route?.durationSeconds ?: 0.0)/60.0 + visitMinutes
    val extraKm get()= if(route==null || directRoute==null) 0.0 else
        ((route.distanceMeters-directRoute.distanceMeters)/1000.0).coerceAtLeast(0.0)
}
@HiltViewModel
class TravelViewModel @Inject constructor(private val repository: TravelRepository): ViewModel() {
    private val _state=MutableStateFlow(TravelUiState())
    val state=_state.asStateFlow()
    val trips=repository.observeTrips().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private fun execute(block: suspend () -> Unit) = viewModelScope.launch {
        if(_state.value.loading) return@launch
        _state.update { it.copy(loading=true,error=null,info=null) }
        try { block() }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { _state.update { it.copy(error=e.message ?: "Ошибка запроса") } }
        finally { _state.update { it.copy(loading=false) } }
    }
    fun updateSettings(maxDetourKm:Double,visitMinutes:Int,maxTotalMinutes:Int) {
        _state.update { it.copy(settings=PlanSettings(maxDetourKm.coerceIn(1.0,100.0),visitMinutes.coerceIn(5,240),maxTotalMinutes.coerceIn(60,10080))) }
    }
    fun search(from:String,to:String,category:String)=execute {
        require(from.isNotBlank() && to.isNotBlank()) { "Укажи два города" }
        val s=repository.geocode(from);val e=repository.geocode(to)
        val direct=repository.route(listOf(s,e))
        _state.update { it.copy(from=from,to=to,category=category,route=direct,directRoute=direct,
            startPoint=s,endPoint=e,selected=emptyList(),candidates=emptyList(),skippedIds=emptySet(),activeTripId=0,offlineCopy=false,createdAt=System.currentTimeMillis()) }
        val places=repository.discover(direct,category)
        val limit=_state.value.settings.maxDetourKm
        _state.update { it.copy(candidates=places.filter { p -> p.distanceFromRouteKm <= limit },
            info=if(places.isEmpty()) "По этому маршруту места не найдены" else null) }
    }
    /** On selection, validate the REAL driving detour and total trip time. */
    fun toggle(place:Place)=execute {
        val old=_state.value
        check(!old.offlineCopy) { "Офлайн-копия доступна для просмотра. Для изменений нужен интернет и новый расчет" }
        val updated=old.selected.toMutableList()
        if(updated.any { it.id==place.id }) updated.removeAll { it.id==place.id }
        else updated.add(place.copy(visitMinutes=old.settings.visitMinutes))
        val s=old.startPoint ?: error("Сначала построй маршрут")
        val e=old.endPoint ?: error("Сначала построй маршрут")
        val revised=repository.route(listOf(s)+updated.map { it.coordinate }+e)
        val base=old.directRoute ?: revised
        val extraKm=((revised.distanceMeters-base.distanceMeters)/1000).coerceAtLeast(0.0)
        require(extraKm<=old.settings.maxDetourKm) { "Крюк %.1f км больше лимита %.1f км".format(extraKm,old.settings.maxDetourKm) }
        val total=revised.durationSeconds/60+updated.sumOf { it.visitMinutes }
        require(total<=old.settings.maxTotalMinutes) { "Поездка займет %.0f мин, ограничение %d мин".format(total,old.settings.maxTotalMinutes) }
        _state.update { it.copy(selected=updated,route=revised,activeTripId=old.activeTripId,info="Маршрут обновлен") }
    }
    fun move(index:Int,delta:Int)=execute {
        val old=_state.value
        check(!old.offlineCopy) { "Офлайн-копия доступна только для просмотра" }
        val reordered=old.selected.toMutableList();val target=index+delta
        if(index !in reordered.indices || target !in reordered.indices) return@execute
        val p=reordered.removeAt(index);reordered.add(target,p)
        val s=old.startPoint ?: error("Нет начала маршрута");val e=old.endPoint ?: error("Нет конца маршрута")
        val revised=repository.route(listOf(s)+reordered.map { it.coordinate }+e)
        val base=old.directRoute ?: revised
        require((revised.distanceMeters-base.distanceMeters)/1000<=old.settings.maxDetourKm) { "Новый порядок превышает лимит крюка" }
        require(revised.durationSeconds/60+reordered.sumOf { it.visitMinutes }<=old.settings.maxTotalMinutes) { "Новый порядок превышает лимит времени" }
        _state.update { it.copy(selected=reordered,route=revised) }
    }
    fun skip(place:Place) { _state.update { it.copy(skippedIds=it.skippedIds+place.id) } }
    fun updatePlaceDuration(place:Place,minutes:Int)=execute {
        val old=_state.value
        val updated=old.selected.map { if(it.id==place.id) it.copy(visitMinutes=minutes.coerceIn(5,240)) else it }
        check(old.route==null || old.route.durationSeconds/60+updated.sumOf { it.visitMinutes }<=old.settings.maxTotalMinutes) { "Превышено допустимое время поездки" }
        _state.update { it.copy(selected=updated) }
    }
    fun loadDetails(place:Place,onResult:(Place)->Unit) = viewModelScope.launch {
        try { onResult(repository.details(place)) }
        catch(e:Exception) { onResult(place) }
    }
    fun save()=execute {
        val old=_state.value;check(old.route!=null) { "Сначала построй маршрут" }
        val id=repository.save(Trip(old.activeTripId,old.from,old.to,old.selected,old.createdAt,old.route,old.settings))
        _state.update { it.copy(activeTripId=id,info="Поездка сохранена на устройстве") }
    }
    fun open(trip:Trip) {
        _state.update { it.copy(from=trip.from,to=trip.to,selected=trip.places,route=trip.route,
            directRoute=null,candidates=emptyList(),skippedIds=emptySet(),settings=trip.settings,
            activeTripId=trip.id,createdAt=trip.createdAt,startPoint=trip.route?.points?.firstOrNull(),endPoint=trip.route?.points?.lastOrNull(),
            offlineCopy=true,info="Открыта сохраненная копия. Карта может потребовать интернет") }
    }
    fun refreshSaved()=execute {
        val old=_state.value
        val s=repository.geocode(old.from);val e=repository.geocode(old.to)
        val direct=repository.route(listOf(s,e))
        val route=repository.route(listOf(s)+old.selected.map { it.coordinate }+e)
        _state.update { it.copy(startPoint=s,endPoint=e,directRoute=direct,route=route,offlineCopy=false,info="Онлайн-маршрут обновлен") }
    }
    fun delete(id:Long)=viewModelScope.launch { runCatching { repository.delete(id) }
        .onFailure { e -> _state.update { it.copy(error=e.message ?: "Не удалось удалить") } } }
}
