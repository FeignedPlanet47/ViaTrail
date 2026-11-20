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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.viatrail.domain.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                ViaTrailScreen(onNavigate = { place ->
                    val lat = place.coordinate.lat
                    val lon = place.coordinate.lon
                    val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                    val appIntent = Intent(Intent.ACTION_VIEW, geo)
                    if (appIntent.resolveActivity(packageManager) != null) startActivity(appIntent)
                    else startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")))
                })
            }
        }
    }
}

private fun km(meters: Double) = "%.1f км".format(Locale.getDefault(), meters / 1000)
private fun minutes(seconds: Double) = "${(seconds / 60).toInt()} мин"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViaTrailScreen(onNavigate: (Place) -> Unit, vm: TravelViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf("Москва") }
    var end by remember { mutableStateOf("Тула") }
    var category by remember { mutableStateOf("Интересные места") }
    var detour by remember { mutableStateOf("20") }
    var visit by remember { mutableStateOf("40") }
    var total by remember { mutableStateOf("480") }
    var detail by remember { mutableStateOf<Place?>(null) }
    var photo by remember { mutableStateOf<LicensedPhoto?>(null) }
    var myLocation by remember { mutableStateOf<Point?>(null) }
    var locateEnabled by remember { mutableStateOf(false) }
    var offlineStatus by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locateEnabled = true
    }
    LaunchedEffect(state.activeTripId, state.from, state.to) {
        if (state.activeTripId != 0L) {
            start = state.from; end = state.to
            detour = state.settings.maxDetourKm.toInt().toString()
            visit = state.settings.visitMinutes.toString()
            total = state.settings.maxTotalMinutes.toString()
        }
    }
    // Location is refreshed only while the trip screen is visible. No background tracking.
    LaunchedEffect(tab, locateEnabled) {
        if (tab == 2 && locateEnabled) {
            while (isActive) {
                myLocation = TripLocation.current(context)
                delay(15_000)
            }
        }
    }
    fun enableLocation() {
        if (TripLocation.hasPermission(context)) locateEnabled = true
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(detail?.id) {
        photo = null
        detail?.let { place -> photo = CommonsPhotos.find(place) }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("ViaTrail", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            Column {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Map, null) }, label = { Text("Маршрут") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Bookmark, null) }, label = { Text("Поездки") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Navigation, null) }, label = { Text("В пути") })
                }
                Text("© OpenStreetMap contributors · Powered by Geoapify",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when (tab) {
                0 -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(0.43f)) {
                        MapPanel(state.route, state.selected, Modifier.fillMaxSize())
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                    Column(Modifier.weight(0.57f).verticalScroll(rememberScrollState())
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Спланировать путешествие", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                        OutlinedTextField(start, { start = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Откуда") }, singleLine = true)
                        OutlinedTextField(end, { end = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Куда") }, singleLine = true)
                        Text("Интересы", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Интересные места", "Кафе", "Отели").forEach { c ->
                                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                            }
                        }
                        Text("Ограничения поездки", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Крюк, км", detour, { detour = it }, Modifier.weight(1f))
                            NumberField("Стоянка, мин", visit, { visit = it }, Modifier.weight(1f))
                            NumberField("Всего, мин", total, { total = it }, Modifier.weight(1f))
                        }
                        Button(onClick = {
                            vm.updateSettings(detour.toDoubleOrNull() ?: 20.0,
                                visit.toIntOrNull() ?: 40, total.toIntOrNull() ?: 480)
                            vm.search(start, end, category)
                        }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Route, null); Spacer(Modifier.width(8.dp)); Text("Построить маршрут")
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        state.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        state.route?.let { route ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${km(route.distanceMeters)} · ${minutes(route.durationSeconds)} в пути",
                                        style = MaterialTheme.typography.titleMedium)
                                    Text("Крюк: ${"%.1f".format(state.extraKm)} км · Остановки: ${state.visitMinutes} мин · Всего: ${state.totalMinutes.toInt()} мин")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { vm.save() }, enabled = !state.loading) { Text("Сохранить") }
                                        OutlinedButton(onClick = { tab = 2 }) { Text("В поездку") }
                                    }
                                    if (state.startPoint != null) {
                                        OutlinedButton(onClick = {
                                            OfflineRegionDownloader.download(context, state.startPoint!!) { offlineStatus = it }
                                        }) { Text("Скачать карту возле старта (~10 км)") }
                                        if (offlineStatus.isNotBlank()) Text(offlineStatus, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (state.offlineCopy) {
                                        Text("Сохраненная геометрия и точки доступны без интернета. Фон карты может требовать сеть.",
                                            style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { vm.refreshSaved() }, enabled = !state.loading) {
                                            Text("Обновить онлайн")
                                        }
                                    }
                                }
                            }
                        }
                        if (state.candidates.isNotEmpty() && !state.offlineCopy) {
                            FilledTonalButton(onClick = { vm.autoPlan() }, enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp))
                                Text("Подобрать остановки автоматически")
                            }
                        }
                        if (state.selected.isNotEmpty()) {
                            Text("Выбранные остановки", style = MaterialTheme.typography.titleMedium)
                            state.selected.forEachIndexed { index, place ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(place.name, fontWeight = FontWeight.SemiBold)
                                        Text("${place.visitMinutes} мин", style = MaterialTheme.typography.labelMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { vm.move(index, -1) }, enabled = !state.loading && !state.offlineCopy) { Text("↑") }
                                            TextButton(onClick = { vm.move(index, 1) }, enabled = !state.loading && !state.offlineCopy) { Text("↓") }
                                            TextButton(onClick = { vm.updatePlaceDuration(place, place.visitMinutes + 15) },
                                                enabled = !state.loading && !state.offlineCopy) { Text("+15 мин") }
                                            TextButton(onClick = { vm.toggle(place) }, enabled = !state.loading && !state.offlineCopy) { Text("Убрать") }
                                        }
                                    }
                                }
                            }
                        }
                        val visible = state.candidates.filterNot { it.id in state.skippedIds }
                        if (visible.isNotEmpty()) Text("Места по пути", style = MaterialTheme.typography.titleMedium)
                        visible.forEach { place ->
                            PlaceCard(place, chosen = state.selected.any { it.id == place.id },
                                loading = state.loading, onDetails = { vm.loadDetails(place) { detail = it } },
                                onToggle = { vm.toggle(place) }, onSkip = { vm.skip(place) })
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Мои путешествия", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    if (trips.isEmpty()) {
                        Text("Пока нет сохраненных поездок. Построй маршрут и нажми «Сохранить».")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(trips, key = { it.id }) { trip ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${trip.from} → ${trip.to}", style = MaterialTheme.typography.titleMedium)
                                    Text("${trip.places.size} мест · ${trip.visitedIds.size} посещено · ${trip.route?.let { km(it.distanceMeters) } ?: "маршрут без геометрии"}")
                                    Row {
                                        TextButton(onClick = { vm.open(trip); tab = 0 }) { Text("Открыть") }
                                        TextButton(onClick = { vm.open(trip); tab = 2 }) { Text("Продолжить") }
                                        TextButton(onClick = { vm.delete(trip.id) }) { Text("Удалить") }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize()) {
                    MapPanel(state.route, state.selected, Modifier.fillMaxWidth().weight(0.46f), myLocation)
                    Column(Modifier.weight(0.54f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Режим поездки", style = MaterialTheme.typography.headlineSmall)
                        Text("Геопозиция обновляется каждые 15 секунд, пока открыт этот экран. Фоновое отслеживание выключено.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { enableLocation() }) {
                            Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp))
                            Text(if (locateEnabled) "Обновить геопозицию" else "Включить геопозицию")
                        }
                        val next = state.selected.firstOrNull { it.id !in state.visitedIds } ?:
                            state.endPoint?.let { Place("destination", state.to, "Финиш", it, state.to) }
                        if (next != null) {
                            Text("Следующая остановка: ${next.name}", style = MaterialTheme.typography.titleMedium)
                            myLocation?.let { here ->
                                val meters = RouteGeometry.distanceMeters(here, next.coordinate)
                                Text("По прямой: ${km(meters)} (не дорожная дистанция)")
                                if (meters <= 200 && next.id != "destination")
                                    Text("Вы рядом с остановкой. Отметить как посещенную?", color = MaterialTheme.colorScheme.primary)
                            }
                            Button(onClick = { onNavigate(next) }) { Text("Открыть в навигаторе") }
                            if (next.id != "destination") OutlinedButton(onClick = { vm.markVisited(next) }) {
                                Text("Остановка посещена")
                            }
                        } else Text("Для начала построй или открой маршрут")
                        LinearProgressIndicator(
                            progress = { if (state.selected.isEmpty()) 0f else state.visitedIds.size.toFloat() / state.selected.size },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Посещено: ${state.visitedIds.size} из ${state.selected.size}")
                    }
                }
            }
        }
    }
    detail?.let { place ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (photo != null) {
                    val image = photo!!
                    AsyncImage(model = image.thumbUrl, contentDescription = "Фото из Wikimedia Commons",
                        modifier = Modifier.fillMaxWidth().height(180.dp))
                    Text("${image.license} · ${image.artist}", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(image.descriptionUrl))) }) {
                        Text("Источник изображения и лицензия")
                    }
                } else Text("Проверенное свободное фото не найдено", style = MaterialTheme.typography.labelSmall)
                Text(place.name, style = MaterialTheme.typography.headlineSmall)
                Text(place.category)
                Text(place.address)
                Text(place.description.ifBlank { "Описание пока отсутствует" })
                Text("Координаты: ${place.coordinate.lat}, ${place.coordinate.lon}")
                Text("От маршрута: ~${"%.1f".format(place.distanceFromRouteKm)} км")
                place.website?.let { url ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Text("Сайт места")
                    }
                }
                Button(onClick = { vm.toggle(place); detail = null }, enabled = !state.loading && !state.offlineCopy) {
                    Text(if (state.selected.any { it.id == place.id }) "Убрать из маршрута" else "Добавить в маршрут")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, chosen: Boolean, loading: Boolean,
                      onDetails: () -> Unit, onToggle: () -> Unit, onSkip: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(place.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(place.address, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            Text("~${"%.1f".format(place.distanceFromRouteKm)} км от маршрута",
                style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDetails) { Text("Подробнее") }
                TextButton(onClick = onToggle, enabled = !loading) { Text(if (chosen) "Убрать" else "Добавить") }
                TextButton(onClick = onSkip) { Text("Пропустить") }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(6)) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodySmall)
}
