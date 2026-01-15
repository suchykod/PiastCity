package eventSearch

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
import event.Event
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// NOWOŚĆ: ViewModel dla ekranu szczegółów
class EventDetailViewModel(private val eventId: String) : ViewModel() {
    private val firestore = Firebase.firestore

    private val _event = MutableLiveData<Event?>()
    val event: LiveData<Event?> = _event

    private val _address = MutableLiveData<String?>()
    val address: LiveData<String?> = _address

    init {
        fetchEventDetails()
    }

    private fun fetchEventDetails() {
        firestore.collection("events2").document(eventId).get()
            .addOnSuccessListener { document ->
                _event.value = document.toObject<Event>()
            }
            .addOnFailureListener {
                _event.value = null // Błąd pobierania
            }
    }

    fun getAddressFromCoordinates(context: Context, latitude: Double?, longitude: Double?) {
        if (latitude != null && longitude != null) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addressList = geocoder.getFromLocation(latitude, longitude, 1)
                _address.value = if (addressList?.isNotEmpty() == true) {
                    addressList[0].getAddressLine(0)
                } else {
                    "Nie udało się znaleźć adresu"
                }
            } catch (e: IOException) {
                _address.value = "Błąd usługi geokodowania"
            }
        }
    }
}

// NOWOŚĆ: Fabryka do przekazywania ID do ViewModel
class EventDetailViewModelFactory(private val eventId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventDetailViewModel(eventId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


class EventDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getStringExtra("EVENT_ID")

        if (eventId == null) {
            // Jeśli z jakiegoś powodu nie ma ID, zamknij aktywność
            finish()
            return
        }

        setContent {
            // Używamy fabryki, aby przekazać ID do ViewModel
            val viewModel: EventDetailViewModel = viewModel(factory = EventDetailViewModelFactory(eventId))
            EventDetailScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(viewModel: EventDetailViewModel) {
    val context = LocalContext.current
    val event by viewModel.event.observeAsState()
    val address by viewModel.address.observeAsState()

    // Uruchom pobieranie adresu, gdy tylko wydarzenie zostanie załadowane
    LaunchedEffect(event) {
        event?.let {
            viewModel.getAddressFromCoordinates(context, it.latitude, it.longitude)
        }
    }

    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val isOwner = event?.owner != null && event?.owner == currentUserEmail

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.name ?: "Ładowanie...", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, "Powrót")
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = {
                            // ZMIANA: Uruchom EventCreator w trybie edycji
                            val intent = Intent(context, eventCreation.EventCreator::class.java).apply {
                                putExtra("EDIT_EVENT_ID", event?.id)
                            }
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Edit, "Edytuj")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            // Stan ładowania
            event == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            // Stan po załadowaniu
            else -> {
                val loadedEvent = event!!
                val scrollState = rememberScrollState()
                val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState)
                ) {
                    AsyncImage(
                        model = loadedEvent.imageUrl,
                        contentDescription = loadedEvent.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = loadedEvent.name ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold)

                        // Data i czas
                        loadedEvent.startDate?.let {
                            Text("Data i czas", style = MaterialTheme.typography.titleMedium)
                            Column {
                                Text("Rozpoczęcie: ${dateFormatter.format(it.toDate())}", style = MaterialTheme.typography.bodyLarge)
                                loadedEvent.endDate?.let { endDate ->
                                    Text("Zakończenie: ${dateFormatter.format(endDate.toDate())}", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        // Opis
                        if (!loadedEvent.description.isNullOrBlank()) {
                            Text("Opis", style = MaterialTheme.typography.titleMedium)
                            Text(loadedEvent.description, style = MaterialTheme.typography.bodyLarge)
                        }

                        // Tagi
                        if (!loadedEvent.tags.isNullOrEmpty()) {
                            Text("Tagi", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(loadedEvent.tags) { tag ->
                                    SuggestionChip(onClick = {}, label = { Text(tag) })
                                }
                            }
                        }

                        // Lokalizacja
                        if (loadedEvent.latitude != null && loadedEvent.longitude != null) {
                            Text("Lokalizacja", style = MaterialTheme.typography.titleMedium)
                            Text(address ?: "Pobieranie adresu...", style = MaterialTheme.typography.bodyLarge)

                            val eventPosition = LatLng(loadedEvent.latitude, loadedEvent.longitude)
                            val cameraPositionState = rememberCameraPositionState {
                                position = CameraPosition.fromLatLngZoom(eventPosition, 15f)
                            }
                            GoogleMap(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                cameraPositionState = cameraPositionState,
                                uiSettings = MapUiSettings(zoomControlsEnabled = false)
                            ) {
                                Marker(state = MarkerState(position = eventPosition))
                            }
                        }
                    }
                }
            }
        }
    }
}
