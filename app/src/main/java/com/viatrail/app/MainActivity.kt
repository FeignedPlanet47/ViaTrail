package com.viatrail.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viatrail.domain.Place
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { MainScreen(onNavigate = { place ->
            startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${place.coordinate.lat},${place.coordinate.lon}?q=${place.coordinate.lat},${place.coordinate.lon}")))
        }) } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(onNavigate: (Place)->Unit, vm: TravelViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    var start by remember { mutableStateOf("Москва") }
    var end by remember { mutableStateOf("Тула") }
    var category by remember { mutableStateOf("Интересные места") }
    var showTrips by remember { mutableStateOf(false) }
    Scaffold(topBar={TopAppBar(title={Text("ViaTrail")},actions={TextButton(onClick={showTrips=true}) { Text("Мои поездки") }})},
        bottomBar={Surface(tonalElevation=3.dp) { Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text("© OpenStreetMap contributors · Powered by Geoapify",style=MaterialTheme.typography.labelSmall)
            Text("Картографические данные: OpenStreetMap (ODbL).",style=MaterialTheme.typography.labelSmall)
        } }}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MapPanel(state.route,state.selected,Modifier.fillMaxWidth().weight(0.47f))
            Column(Modifier.fillMaxWidth().weight(0.53f).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start,{start=it},label={Text("Откуда")},modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(end,{end=it},label={Text("Куда")},modifier=Modifier.weight(1f),singleLine=true)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Интересные места","Кафе","Отели").forEach { item -> FilterChip(selected=category==item,onClick={category=item},label={Text(item)}) }
                }
                Button(onClick={vm.search(start,end,category)},enabled=!state.loading,modifier=Modifier.fillMaxWidth()) { Text("Построить маршрут и найти места") }
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                state.route?.let { route ->
                    Text("%.1f км · %.0f мин · остановок: %d".format(Locale.getDefault(),route.distanceMeters/1000,route.durationSeconds/60,state.selected.size),style=MaterialTheme.typography.titleMedium)
                    Button(onClick=vm::save, enabled=!state.loading) { Text("Сохранить поездку") }
                }
                if(state.selected.isNotEmpty()) {
                    Text("Мои остановки",style=MaterialTheme.typography.titleMedium)
                    state.selected.forEachIndexed { i, place -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(place.name,Modifier.weight(1f))
                        TextButton(onClick={vm.move(i,-1)}) { Text("↑") }
                        TextButton(onClick={vm.move(i,1)}) { Text("↓") }
                        TextButton(onClick={onNavigate(place)}) { Text("Ехать") }
                        TextButton(onClick={vm.toggle(place)}) { Text("×") }
                    } }
                }
                if(state.candidates.isNotEmpty()) Text("Что посмотреть по пути",style=MaterialTheme.typography.titleMedium)
                state.candidates.forEach { place -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Text(place.name,style=MaterialTheme.typography.titleSmall); Text(place.address,style=MaterialTheme.typography.bodySmall,maxLines=2) }
                    val selected=state.selected.any { it.id==place.id }
                    TextButton(onClick={vm.toggle(place)},enabled=!state.loading) { Text(if(selected) "Убрать" else "Добавить") }
                } } }
            }
        }
    }
    if(showTrips) ModalBottomSheet(onDismissRequest={showTrips=false}) {
        Text("Сохраненные поездки",Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp)) { items(trips,key={it.id}) { trip ->
            ListItem(headlineContent={Text("${trip.from} → ${trip.to}")},supportingContent={Text("Остановок: ${trip.places.size}")},
                trailingContent={ Row { TextButton(onClick={start=trip.from;end=trip.to;vm.open(trip);showTrips=false}){ Text("Открыть") }; TextButton(onClick={vm.delete(trip.id)}){Text("Удалить")} } })
            HorizontalDivider()
        } }
    }
}
