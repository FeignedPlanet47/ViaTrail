package com.viatrail.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viatrail.domain.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                ViaTrailScreen(
                    onNavigate = { place ->
                        val lat = place.coordinate.lat
                        val lon = place.coordinate.lon

                        val geo = Uri.parse(
                            "geo:$lat,$lon?q=$lat,$lon"
                        )

                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            geo
                        )

                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        } else {
                            val url =
                                "https://www.google.com/maps/search/?api=1&query=$lat,$lon"

                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(url)
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

private fun km(value:Double) = "%.1f км".format(Locale.getDefault(),value)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViaTrailScreen(onNavigate:(Place)->Unit, vm:TravelViewModel=hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    var start by remember { mutableStateOf("Москва") }
    var end by remember { mutableStateOf("Тула") }
    var category by remember { mutableStateOf("Интересные места") }
    var detour by remember { mutableStateOf("20") }
    var visit by remember { mutableStateOf("40") }
    var total by remember { mutableStateOf("480") }
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Place?>(null) }
    val visitedIds=remember { mutableStateListOf<String>() }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var myLocation by remember { mutableStateOf<Point?>(null) }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if(grants.values.any { it }) scope.launch { myLocation=TripLocation.current(context) }
    }
    fun requestLocation() {
        if(TripLocation.hasPermission(context)) scope.launch { myLocation=TripLocation.current(context) }
        else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(state.activeTripId, state.from, state.to) {
        visitedIds.clear()
        if(state.activeTripId!=0L) {
            start=state.from;end=state.to
            detour=state.settings.maxDetourKm.toInt().toString()
            visit=state.settings.visitMinutes.toString()
            total=state.settings.maxTotalMinutes.toString()
        }
    }
    Scaffold(
        topBar={ TopAppBar(title={Text("ViaTrail",fontWeight=FontWeight.SemiBold)},actions={
            TextButton(onClick={tab=1}) { Text("Мои поездки") }
        }) },
        bottomBar={ Column {
            NavigationBar {
                NavigationBarItem(selected=tab==0,onClick={tab=0},icon={Text("⌖")},label={Text("Маршрут")})
                NavigationBarItem(selected=tab==1,onClick={tab=1},icon={Text("▤")},label={Text("Сохраненные")})
                NavigationBarItem(selected=tab==2,onClick={tab=2},icon={Text("➤")},label={Text("В поездке")})
            }
            Text("© OpenStreetMap contributors · Powered by Geoapify",modifier=Modifier.padding(horizontal=12.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall)
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(tab==1) {
                Text("Мои путешествия",Modifier.padding(16.dp),style=MaterialTheme.typography.headlineSmall)
                if(trips.isEmpty()) Text("Пока нет сохраненных маршрутов",Modifier.padding(16.dp))
                LazyColumn { items(trips,key={it.id}) { trip ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${trip.from} → ${trip.to}",style=MaterialTheme.typography.titleMedium)
                            Text("${trip.places.size} остановок · ${trip.route?.let { km(it.distanceMeters/1000) } ?: "старый маршрут"}")
                            Row { TextButton(onClick={vm.open(trip);tab=0}) { Text("Открыть") }
                                TextButton(onClick={vm.delete(trip.id)}) { Text("Удалить") } }
                        }
                    }
                } }
            } else if(tab==2) {
                MapPanel(state.route,state.selected,Modifier.fillMaxWidth().weight(0.5f),myLocation)
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Режим поездки",style=MaterialTheme.typography.headlineSmall)
                    Text("Отображается примерная дистанция по прямой до следующей остановки, не дорожный прогноз.")
                    Button(
                        onClick = { requestLocation() }
                    ) {
                        Text("Обновить мое местоположение")
                    }
                    val target=state.selected.firstOrNull { it.id !in visitedIds } ?: state.endPoint?.let { Place("end",state.to,"Финиш",it,state.to) }
                    if(target!=null) {
                        Text("Следующая точка: ${target.name}",style=MaterialTheme.typography.titleMedium)
                        myLocation?.let { Text("До точки по прямой: ${km(RouteGeometry.distanceMeters(it,target.coordinate)/1000)}") }
                        Button(onClick={onNavigate(target)}) { Text("Открыть в навигаторе") }
                        if(target.id != "end") OutlinedButton(onClick={visitedIds.add(target.id)}) { Text("Остановка посещена") }
                    } else Text("Сначала построй или открой маршрут")
                    Text("Приложение не выполняет голосовую навигацию и не отслеживает движение в фоне.",style=MaterialTheme.typography.bodySmall)
                }
            } else {
                MapPanel(state.route,state.selected,Modifier.fillMaxWidth().weight(0.4f))
                Column(Modifier.fillMaxWidth().weight(0.6f).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(start,{start=it},label={Text("Откуда")},modifier=Modifier.weight(1f),singleLine=true)
                        OutlinedTextField(end,{end=it},label={Text("Куда")},modifier=Modifier.weight(1f),singleLine=true)
                    }
                    Text("Параметры поездки",style=MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        NumberInput("Макс. крюк, км",detour,{detour=it},Modifier.weight(1f))
                        NumberInput("Остановка, мин",visit,{visit=it},Modifier.weight(1f))
                        NumberInput("Вся поездка, мин",total,{total=it},Modifier.weight(1f))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("Интересные места","Кафе","Отели").forEach { item ->
                            FilterChip(selected=category==item,onClick={category=item},label={Text(item)})
                        }
                    }
                    Button(onClick={
                        vm.updateSettings(detour.toDoubleOrNull()?:20.0,visit.toIntOrNull()?:40,total.toIntOrNull()?:480)
                        vm.search(start,end,category)
                    },enabled=!state.loading,modifier=Modifier.fillMaxWidth()) { Text("Построить маршрут и найти места") }
                    if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    state.info?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
                    state.route?.let { route ->
                        Text("${km(route.distanceMeters/1000)} · ${(route.durationSeconds/60).toInt()} мин в пути",style=MaterialTheme.typography.titleMedium)
                        Text("Дополнительный путь: ${km(state.extraKm)} · посещения: ${state.visitMinutes} мин · всего ≈ ${state.totalMinutes.toInt()} мин")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick=vm::save,enabled=!state.loading) { Text("Сохранить") }
                            OutlinedButton(onClick={tab=2}) { Text("Начать поездку") }
                        }
                        if(state.offlineCopy) {
                            Text("Сохраненный маршрут доступен без сети. Фоновые плитки карты и изменение маршрута могут требовать интернет.")
                            OutlinedButton(onClick=vm::refreshSaved,enabled=!state.loading) { Text("Обновить маршрут онлайн") }
                        }
                    }
                    if(state.selected.isNotEmpty()) {
                        Text("Выбранные остановки",style=MaterialTheme.typography.titleMedium)
                        state.selected.forEachIndexed { index,place ->
                            ElevatedCard { Column(Modifier.padding(10.dp)) {
                                Text(place.name,fontWeight=FontWeight.SemiBold)
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("${place.visitMinutes} мин",Modifier.weight(1f))
                                    TextButton(onClick={vm.updatePlaceDuration(place,place.visitMinutes-15)},enabled=!state.loading && !state.offlineCopy) { Text("−15") }
                                    TextButton(onClick={vm.updatePlaceDuration(place,place.visitMinutes+15)},enabled=!state.loading && !state.offlineCopy) { Text("+15") }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                                    TextButton(onClick={vm.move(index,-1)},enabled=!state.loading && !state.offlineCopy) { Text("↑") }
                                    TextButton(onClick={vm.move(index,1)},enabled=!state.loading && !state.offlineCopy) { Text("↓") }
                                    TextButton(onClick={onNavigate(place)}) { Text("Ехать") }
                                    TextButton(onClick={vm.toggle(place)},enabled=!state.loading && !state.offlineCopy) { Text("Убрать") }
                                }
                            } }
                        }
                    }
                    val visible=state.candidates.filterNot { it.id in state.skippedIds }
                    if(visible.isNotEmpty()) Text("Места по пути",style=MaterialTheme.typography.titleMedium)
                    visible.forEach { place ->
                        val chosen=state.selected.any { it.id==place.id }
                        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(place.name,style=MaterialTheme.typography.titleSmall)
                            Text(place.address,style=MaterialTheme.typography.bodySmall,maxLines=2)
                            Text("≈ ${km(place.distanceFromRouteKm)} от линии маршрута · ${state.settings.visitMinutes} мин на остановку",style=MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                                TextButton(onClick={ vm.loadDetails(place){ detail=it } }) { Text("Подробнее") }
                                TextButton(onClick={vm.toggle(place)},enabled=!state.loading) { Text(if(chosen) "Убрать" else "Добавить") }
                                TextButton(onClick={vm.skip(place)}) { Text("Пропустить") }
                            }
                        } }
                    }
                }
            }
        }
    }
    detail?.let { place -> ModalBottomSheet(onDismissRequest={detail=null}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(place.name,style=MaterialTheme.typography.headlineSmall)
            Text(place.category,style=MaterialTheme.typography.labelLarge)
            Text(place.address)
            Text(place.description.ifBlank { "Описание места пока недоступно" })
            Text("Координаты: ${place.coordinate.lat}, ${place.coordinate.lon}")
            Text("Расстояние от линии маршрута: ${km(place.distanceFromRouteKm)} (приблизительно)")
            Text("Планируемая остановка: ${state.settings.visitMinutes} мин")
            Text("Фотографии не загружаются без проверенных прав и атрибуции.",style=MaterialTheme.typography.bodySmall)
            place.website?.let { url -> TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}) { Text("Сайт места") } }
            Button(onClick={vm.toggle(place);detail=null},enabled=!state.loading) { Text("Добавить / убрать из маршрута") }
            Spacer(Modifier.height(20.dp))
        }
    } }
}
@Composable
private fun NumberInput(label:String,value:String,onChange:(String)->Unit,modifier:Modifier=Modifier) {
    OutlinedTextField(value,onValueChange={onChange(it.filter { c -> c.isDigit() || c=='.' }.take(6))},label={Text(label)},modifier=modifier,
        singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),textStyle=MaterialTheme.typography.bodySmall)
}
