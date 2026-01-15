package eventSearch

import User.User
import android.content.Context // NOWOŚĆ: Potrzebne do Geocoder
import android.content.Intent
import android.location.Geocoder // NOWOŚĆ: Potrzebne do Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow // NOWOŚĆ: Do wyświetlania tagów
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow // NOWOŚĆ: Do przycinania opisu
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // NOWOŚĆ: Do korutyn
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.piastcity.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import event.Event
import kotlinx.coroutines.Dispatchers // NOWOŚĆ: Do operacji w tle
import kotlinx.coroutines.launch // NOWOŚĆ: Do korutyn
import java.io.IOException // NOWOŚĆ: Do obsługi błędów Geocoder
import java.util.* // NOWOŚĆ: Potrzebne do Geocoder

// --- KROK 1: ViewModel do zarządzania logiką i danymi ---
class EventSearchViewModel : ViewModel() {
    private val firestore = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    private val _events = MutableLiveData<List<Event>>()
    val events: LiveData<List<Event>> = _events

    private val _users = MutableLiveData<Map<String, User>>()
    val users: LiveData<Map<String, User>> = _users

    // NOWOŚĆ: LiveData do przechowywania adresów (klucz to ID eventu)
    private val _addresses = MutableLiveData<Map<String, String>>()
    val addresses: LiveData<Map<String, String>> = _addresses

    private val collectionPath = "events2"

    init {
        fetchEvents()
    }

    // NOWOŚĆ: Funkcja do zamiany współrzędnych na adres (odwrócone geokodowanie)
    fun getAddressFromCoordinates(context: Context, event: Event) {
        // Sprawdzamy, czy event ma koordynaty i czy nie mamy już dla niego adresu
        if (event.latitude != null && event.longitude != null && _addresses.value?.containsKey(event.creation.toString()) != true) {
            viewModelScope.launch(Dispatchers.IO) { // Operacja sieciowa w tle
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addressList = geocoder.getFromLocation(event.latitude, event.longitude, 1)
                    if (addressList?.isNotEmpty() == true) {
                        val address = addressList[0]
                        val addressText = address.getAddressLine(0) // Pobieramy pierwszą linię adresu
                        // Aktualizujemy LiveData w głównym wątku
                        launch(Dispatchers.Main) {
                            val currentAddresses = _addresses.value?.toMutableMap() ?: mutableMapOf()
                            currentAddresses[event.creation.toString()] = addressText
                            _addresses.value = currentAddresses
                        }
                    }
                } catch (e: IOException) {
                    // Obsługa błędu (np. brak sieci)
                }
            }
        }
    }


    fun fetchEvents() {
        firestore.collection(collectionPath)
            .orderBy("creation", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val eventList = snapshot.documents.mapNotNull { it.toObject<Event>() }
                _events.value = eventList
                fetchUsersForEvents(eventList)
            }
            .addOnFailureListener {
                _events.value = emptyList()
            }
    }

    private fun fetchUsersForEvents(events: List<Event>) {
        val ownerEmails = events.map { it.owner }.distinct().filterNotNull()
        if (ownerEmails.isEmpty()) return

        firestore.collection("users")
            .whereIn("firebaseUser", ownerEmails)
            .get()
            .addOnSuccessListener { userSnapshot ->
                val userMap = userSnapshot.documents.mapNotNull { it.toObject<User>() }
                    .associateBy { it.firebaseUser!! }
                _users.value = userMap
            }
    }

    fun deleteEvent(event: Event, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (event.owner != currentUserEmail) {
            onFailure("Możesz usuwać tylko własne wydarzenia.")
            return
        }

        firestore.collection(collectionPath)
            .whereEqualTo("creation", event.creation)
            .whereEqualTo("owner", event.owner)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    onFailure("Nie znaleziono wydarzenia do usunięcia.")
                } else {
                    val docId = documents.documents[0].id
                    firestore.collection(collectionPath).document(docId).delete()
                        .addOnSuccessListener {
                            event.imageUrl?.let { url ->
                                storage.getReferenceFromUrl(url).delete()
                            }
                            onSuccess()
                            fetchEvents()
                        }
                        .addOnFailureListener { e ->
                            onFailure("Błąd usuwania: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                onFailure("Błąd wyszukiwania: ${e.message}")
            }
    }
}


class EventSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EventSearchScreen()
        }
    }
}

@Composable
fun EventSearchScreen(viewModel: EventSearchViewModel = viewModel()) {
    val context = LocalContext.current
    val events by viewModel.events.observeAsState(initial = emptyList())
    val users by viewModel.users.observeAsState(initial = emptyMap())
    val addresses by viewModel.addresses.observeAsState(initial = emptyMap()) // NOWOŚĆ: Obserwujemy adresy
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }

    Scaffold(
        containerColor = Color(0xFF131313),
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(onClick = { viewModel.fetchEvents() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
                }
                FloatingActionButton(onClick = {
                    val intent = Intent(context, eventCreation.EventCreator::class.java)
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj wydarzenie")
                }
            }
        }
    ) { paddingValues ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak wydarzeń. Dodaj pierwsze!", color = Color.White, fontSize = 20.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events, key = { it.creation.toString() + it.owner }) { event ->
                    val user = users[event.owner]
                    val address = addresses[event.creation.toString()] // NOWOŚĆ: Pobieramy adres dla eventu

                    // NOWOŚĆ: Uruchamiamy pobieranie adresu, jeśli go nie ma
                    LaunchedEffect(key1 = event) {
                        viewModel.getAddressFromCoordinates(context, event)
                    }

                    EventCard(
                        event = event,                        user = user,
                        address = address, // NOWOŚĆ: Przekazujemy adres do karty
                        // ... wewnątrz EventSearchScreen
                        onItemClick = { clickedEvent ->
                            // Przekazujemy TYLKO ID wydarzenia
                            val intent = Intent(context, EventDetailActivity::class.java).apply {
                                putExtra("EVENT_ID", clickedEvent.id)
                            }
                            context.startActivity(intent)
                        },
                        onItemLongClick = {
                            // ZMIANA: Pokaż dialog zamiast usuwać od razu
                            eventToDelete = it
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
        // NOWOŚĆ: Dialog potwierdzający usunięcie
        if (showDeleteDialog && eventToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    eventToDelete = null
                },
                title = { Text("Potwierdź usunięcie") },
                text = { Text("Czy na pewno chcesz trwale usunąć wydarzenie '${eventToDelete?.name}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            eventToDelete?.let {
                                viewModel.deleteEvent(
                                    event = it,
                                    onSuccess = { Toast.makeText(context, "Wydarzenie usunięte", Toast.LENGTH_SHORT).show() },
                                    onFailure = { errorMsg -> Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show() }
                                )
                            }
                            showDeleteDialog = false
                            eventToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Usuń")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showDeleteDialog = false
                        eventToDelete = null
                    }) {
                        Text("Anuluj")
                    }
                }
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventCard(
    event: Event,
    user: User?,
    address: String?, // NOWOŚĆ: Przyjmujemy adres
    onItemClick: (Event) -> Unit,
    onItemLongClick: (Event) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onItemClick(event) },
                onLongClick = { onItemLongClick(event) }
            ),
        shape = RoundedCornerShape(25.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = user?.imageUrl,
                    contentDescription = "Avatar",
                    placeholder = painterResource(id = R.drawable.login_bck),
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user?.username ?: "Nieznany użytkownik", fontWeight = FontWeight.Bold, color = Color.White)
                    // ZMIANA: Wyświetlamy adres jeśli jest, w przeciwnym razie stary tekst
                    if (event.latitude != null) {
                        Text(
                            text = address ?: "Pobieranie adresu...",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onItemClick(event) }
                        )
                    } else {
                        Text("Brak lokalizacji", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            AsyncImage(
                model = event.imageUrl,
                contentDescription = event.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.login_bck)
            )

            // ZMIANA: Całkowicie nowa stopka karty
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.name ?: "Brak nazwy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

                // NOWOŚĆ: Wyświetlanie opisu, jeśli istnieje
                if (!event.description.isNullOrBlank()) {
                    Text(
                        text = event.description,
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        maxLines = 2, // Ograniczamy do 2 linii
                        overflow = TextOverflow.Ellipsis // Dodajemy "..." na końcu
                    )
                }

                // NOWOŚĆ: Wyświetlanie tagów, jeśli istnieją
                if (!event.tags.isNullOrEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(event.tags.take(10)) { tag -> // Ograniczamy do max 10 tagów
                            SuggestionChip(
                                onClick = { /* Kliknięcie na tag - można dodać filtrowanie */ },
                                label = { Text(tag) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.DarkGray,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
