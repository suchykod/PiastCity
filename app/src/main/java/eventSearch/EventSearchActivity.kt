package eventSearch

import User.User
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.piastcity.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import event.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Klasy pomocnicze definiujące opcje filtrowania i sortowania.
enum class FilterType { NONE, USERNAME, TAG, DISTANCE }
enum class SortType { CREATION_DATE, START_DATE, DISTANCE, NAME }

data class EventOptions(
    val filterType: FilterType = FilterType.NONE,
    val filterValue: Any? = null,
    val sortType: SortType = SortType.CREATION_DATE,
    val userLocation: Location? = null
)

// ViewModel dla ekranu wyszukiwania wydarzeń.
class EventSearchViewModel : ViewModel() {
    private val firestore = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    private val _events = MutableLiveData<List<Event>>()
    val events: LiveData<List<Event>> = _events

    private val _users = MutableLiveData<Map<String, User>>()
    val users: LiveData<Map<String, User>> = _users

    private val _addresses = MutableLiveData<Map<String, String>>()
    val addresses: LiveData<Map<String, String>> = _addresses

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    private val _options = MutableLiveData(EventOptions())
    val options: LiveData<EventOptions> = _options

    // Aktualizuje stan opcji filtrowania i sortowania.
    fun updateFilter(type: FilterType, value: Any?) {
        val currentOptions = _options.value ?: EventOptions()
        _options.value = currentOptions.copy(filterType = type, filterValue = value)
    }

    fun updateSort(type: SortType) { _options.value = _options.value?.copy(sortType = type) }
    fun updateUserLocation(location: Location) { _options.value = _options.value?.copy(userLocation = location) }
    fun clearOptions() { _options.value = EventOptions(userLocation = _options.value?.userLocation) }

    // Główna funkcja pobierająca i filtrująca wydarzenia.
    fun fetchEvents() {
        viewModelScope.launch {
            _isSearching.value = true
            val currentOptions = _options.value ?: EventOptions()
            var query: Query = firestore.collection("events2")

            // Etap 1: Filtrowanie po stronie serwera (Firestore).
            when (currentOptions.filterType) {
                FilterType.TAG -> {
                    val tag = currentOptions.filterValue as? String
                    if (!tag.isNullOrBlank()) query = query.whereArrayContains("tags", tag)
                }
                FilterType.USERNAME -> {
                    val username = currentOptions.filterValue as? String
                    if (!username.isNullOrBlank()) {
                        val userQuery = firestore.collection("users").whereEqualTo("username", username).limit(1).get().await()
                        if (!userQuery.isEmpty) {
                            val ownerEmail = userQuery.documents[0].getString("firebaseUser")
                            query = query.whereEqualTo("owner", ownerEmail)
                        } else {
                            // Jeśli użytkownik nie istnieje, zwróć pustą listę.
                            _events.postValue(emptyList())
                            _isSearching.postValue(false)
                            return@launch
                        }
                    }
                }
                // Filtrowanie po dystansie odbywa się po stronie klienta.
                FilterType.DISTANCE, FilterType.NONE -> { /* Brak filtrowania w zapytaniu */ }
            }

            // Aplikowanie sortowania, które może obsłużyć Firestore.
            when (currentOptions.sortType) {
                SortType.CREATION_DATE -> query = query.orderBy("creation", Query.Direction.DESCENDING)
                SortType.START_DATE -> query = query.orderBy("startDate", Query.Direction.ASCENDING)
                SortType.NAME -> query = query.orderBy("name", Query.Direction.ASCENDING)
                SortType.DISTANCE -> { /* Sortowanie po dystansie odbywa się po stronie klienta. */ }
            }

            try {
                var eventList = query.get().await().toObjects(Event::class.java)

                // Etap 2: Filtrowanie i sortowanie po stronie klienta.
                if (currentOptions.filterType == FilterType.DISTANCE && currentOptions.filterValue is Float && currentOptions.userLocation != null) {
                    val maxDistanceMeters = (currentOptions.filterValue as Float) * 1000
                    val userLocation = currentOptions.userLocation

                    eventList = eventList.filter { event ->
                        if (event.latitude != null && event.longitude != null) {
                            val eventLocation = Location("").apply {
                                latitude = event.latitude
                                longitude = event.longitude
                            }
                            userLocation.distanceTo(eventLocation) <= maxDistanceMeters
                        } else {
                            false // Odrzuca wydarzenia bez lokalizacji.
                        }
                    }
                }

                if (currentOptions.sortType == SortType.DISTANCE && currentOptions.userLocation != null) {
                    val userLocation = currentOptions.userLocation
                    eventList.sortWith(compareBy { event ->
                        if (event.latitude != null && event.longitude != null) {
                            val eventLocation = Location("").apply {
                                latitude = event.latitude
                                longitude = event.longitude
                            }
                            userLocation.distanceTo(eventLocation)
                        } else {
                            Float.MAX_VALUE // Wydarzenia bez lokalizacji na koniec.
                        }
                    })
                }

                _events.postValue(eventList)
                if (eventList.isNotEmpty()) {
                    fetchUsersForEvents(eventList)
                } else {
                    _users.postValue(emptyMap())
                }
            } catch (e: Exception) {
                Log.e("FirestoreError", "Błąd pobierania wydarzeń: ${e.message}", e)
                _events.postValue(emptyList())
            } finally {
                _isSearching.postValue(false)
            }
        }
    }

    // Pobiera dane użytkowników (autorów) dla widocznych wydarzeń.
    private fun fetchUsersForEvents(events: List<Event>) = viewModelScope.launch {
        val ownerEmails = events.mapNotNull { it.owner }.distinct()
        if (ownerEmails.isNotEmpty()) {
            val userSnapshot = firestore.collection("users").whereIn("firebaseUser", ownerEmails).get().await()
            val usersMap = userSnapshot.documents.mapNotNull { it.toObject<User>() }
                .filter { it.firebaseUser != null }
                .associateBy { it.firebaseUser!! }
            _users.postValue(usersMap)
        }
    }

    // Konwertuje współrzędne na adres (Geocoding).
    fun getAddressFromCoordinates(context: Context, event: Event) {
        if (event.latitude != null && event.longitude != null && event.id != null && _addresses.value?.containsKey(event.id) != true) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addressList = geocoder.getFromLocation(event.latitude, event.longitude, 1)
                    if (addressList?.isNotEmpty() == true) {
                        launch(Dispatchers.Main) {
                            val currentAddresses = _addresses.value?.toMutableMap() ?: mutableMapOf()
                            currentAddresses[event.id!!] = addressList[0].getAddressLine(0)
                            _addresses.value = currentAddresses
                        }
                    }
                } catch (e: IOException) { /* Błąd jest ignorowany, aby nie blokować UI. */ }
            }
        }
    }

    // Usuwa wydarzenie z Firestore i powiązane zdjęcie ze Storage.
    fun deleteEvent(event: Event, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (event.owner != currentUserEmail) { onFailure("Możesz usuwać tylko własne wydarzenia."); return }
        if (event.id == null) { onFailure("Błąd: Brak ID wydarzenia."); return }

        firestore.collection("events2").document(event.id).delete()
            .addOnSuccessListener {
                event.imageUrl?.let { url -> storage.getReferenceFromUrl(url).delete() }
                onSuccess()
                fetchEvents() // Odświeża listę po usunięciu.
            }
            .addOnFailureListener { e -> onFailure("Błąd usuwania: ${e.message}") }
    }
}

class EventSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ui.PiastCityTheme {
                EventSearchScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSearchScreen(viewModel: EventSearchViewModel = viewModel()) {
    val context = LocalContext.current
    val events by viewModel.events.observeAsState(initial = emptyList())
    val users by viewModel.users.observeAsState(initial = emptyMap())
    val addresses by viewModel.addresses.observeAsState(initial = emptyMap())
    val isSearching by viewModel.isSearching.observeAsState(false)
    val options by viewModel.options.observeAsState(EventOptions())

    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    // Funkcja pomocnicza do pobierania ostatniej znanej lokalizacji.
    @SuppressLint("MissingPermission")
    fun fetchLastLocation(
        fusedLocationClient: FusedLocationProviderClient,
        onLocationFetched: (Location) -> Unit
    ) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let(onLocationFetched)
        }
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }

    // Launcher do obsługi prośby o uprawnienia do lokalizacji.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasLocationPermission = permissions.values.all { it }
        if (hasLocationPermission) {
            fetchLastLocation(fusedLocationClient) { location ->
                viewModel.updateUserLocation(location)
            }
        }
    }

    // Uruchamia proces sprawdzania/pytania o uprawnienia do lokalizacji.
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            fetchLastLocation(fusedLocationClient) { location ->
                viewModel.updateUserLocation(location)
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Pobiera wydarzenia przy pierwszym uruchomieniu ekranu.
    LaunchedEffect(Unit) {
        viewModel.fetchEvents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wydarzenia") },
                actions = {
                    IconButton(onClick = { viewModel.clearOptions(); viewModel.fetchEvents() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Odśwież i wyczyść filtry")
                    }
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Filtruj i Sortuj")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { context.startActivity(Intent(context, eventCreation.EventCreator::class.java)) }) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj wydarzenie")
            }
        }
    ) { paddingValues ->
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Brak wydarzeń. Zmień kryteria wyszukiwania lub dodaj nowe wydarzenie.", color = Color.Gray, fontSize = 20.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events, key = { it.id!! }) { event ->
                    val user = users[event.owner]
                    val address = event.id?.let { addresses[it] }

                    LaunchedEffect(event.id) { viewModel.getAddressFromCoordinates(context, event) }

                    EventCard(
                        event = event,
                        user = user,
                        address = address,
                        currentUserLocation = options.userLocation,
                        onItemClick = { clickedEvent ->
                            val intent = Intent(context, EventDetailActivity::class.java).apply { putExtra("EVENT_ID", clickedEvent.id) }
                            context.startActivity(intent)
                        },
                        onItemLongClick = { eventToDelete = it; showDeleteDialog = true }
                    )
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
                OptionsPanel(
                    options = options,
                    onFilterChanged = { type, value -> viewModel.updateFilter(type, value) },
                    onSortChanged = { type -> viewModel.updateSort(type) },
                    onSearchClick = { showBottomSheet = false; viewModel.fetchEvents() }
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Potwierdź usunięcie") },
                text = { Text("Czy na pewno chcesz trwale usunąć wydarzenie '${eventToDelete?.name}'?") },
                confirmButton = {
                    Button(onClick = {
                        eventToDelete?.let { viewModel.deleteEvent(it,
                            { Toast.makeText(context, "Wydarzenie usunięte", Toast.LENGTH_SHORT).show() },
                            { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }) }
                        showDeleteDialog = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Usuń") }
                },
                dismissButton = { Button(onClick = { showDeleteDialog = false }) { Text("Anuluj") } }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventCard(event: Event, user: User?, address: String?, currentUserLocation: Location?, onItemClick: (Event) -> Unit, onItemLongClick: (Event) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onItemClick(event) }, onLongClick = { onItemLongClick(event) }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = user?.imageUrl,
                    contentDescription = "Avatar",
                    placeholder = painterResource(id = R.drawable.login_bck),
                    modifier = Modifier.size(52.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user?.username ?: "...", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(address ?: "...", color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (currentUserLocation != null && event.latitude != null && event.longitude != null) {
                    val eventLocation = Location("").apply { latitude = event.latitude; longitude = event.longitude }
                    val distanceInKm = currentUserLocation.distanceTo(eventLocation) / 1000
                    Text(String.format(Locale.US, "%.1f km", distanceInKm), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            AsyncImage(
                model = event.imageUrl,
                contentDescription = event.name,
                modifier = Modifier.fillMaxWidth().height(350.dp),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.login_bck)
            )

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.name ?: "Brak nazwy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (!event.description.isNullOrBlank()) {
                    Text(event.description, fontSize = 14.sp, color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!event.tags.isNullOrEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(event.tags.take(10)) { tag ->
                            SuggestionChip(
                                onClick = { /* Możliwość rozszerzenia o filtrowanie po kliknięciu */ },
                                label = { Text(tag) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsPanel(
    options: EventOptions,
    onFilterChanged: (FilterType, Any?) -> Unit,
    onSortChanged: (SortType) -> Unit,
    onSearchClick: () -> Unit
) {
    // Stany lokalne dla pól tekstowych, aby uniknąć niechcianych rekompozycji.
    var usernameFilter by remember {
        mutableStateOf(if (options.filterType == FilterType.USERNAME) options.filterValue as? String ?: "" else "")
    }
    var tagFilter by remember {
        mutableStateOf(if (options.filterType == FilterType.TAG) options.filterValue as? String ?: "" else "")
    }
    var distanceFilter by remember {
        mutableStateOf(if (options.filterType == FilterType.DISTANCE) options.filterValue as? Float ?: 25f else 25f)
    }

    // Resetuje stany lokalne, jeśli zmieni się aktywny typ filtra w ViewModelu.
    LaunchedEffect(options.filterType) {
        if (options.filterType != FilterType.USERNAME) usernameFilter = ""
        if (options.filterType != FilterType.TAG) tagFilter = ""
        if (options.filterType != FilterType.DISTANCE) distanceFilter = 25f
    }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Filtruj i Sortuj", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Filtruj (tylko jedno pole aktywne)", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = usernameFilter,
            onValueChange = {
                usernameFilter = it
                onFilterChanged(FilterType.USERNAME, it.ifBlank { null })
            },
            label = { Text("Nazwa użytkownika") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = tagFilter,
            onValueChange = {
                tagFilter = it
                onFilterChanged(FilterType.TAG, it.ifBlank { null })
            },
            label = { Text("Tag") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Suwak do filtrowania po odległości.
        Column {
            Text("Maksymalna odległość: ${distanceFilter.roundToInt()} km", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = distanceFilter,
                onValueChange = {
                    distanceFilter = it
                    onFilterChanged(FilterType.DISTANCE, it)
                },
                valueRange = 1f..100f,
                steps = 98
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Sortuj według", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = options.sortType == SortType.CREATION_DATE, onClick = { onSortChanged(SortType.CREATION_DATE) }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)) { Text("Nowe") }
            SegmentedButton(selected = options.sortType == SortType.START_DATE, onClick = { onSortChanged(SortType.START_DATE) }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)) { Text("Data") }
            SegmentedButton(selected = options.sortType == SortType.DISTANCE, onClick = { onSortChanged(SortType.DISTANCE) }, shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)) { Text("Blisko") }
            SegmentedButton(selected = options.sortType == SortType.NAME, onClick = { onSortChanged(SortType.NAME) }, shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)) { Text("A-Z") }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onSearchClick, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Zastosuj", fontSize = 16.sp)
        }
    }
}

// Funkcja pomocnicza sprawdzająca uprawnienia do lokalizacji.
private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
