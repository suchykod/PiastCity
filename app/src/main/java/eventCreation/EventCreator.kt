package eventCreation

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.piastcity.MyLocationDemoActivity
import com.example.piastcity.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.maps.android.compose.*
import event.Event
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// ViewModel do zarządzania stanem kreatora/edycji
class EventCreatorViewModel(private val eventId: String?) : ViewModel() {

    private val firestore = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val isEditMode = eventId != null

    // Stany formularza jako LiveData
    private val _name = MutableLiveData("")
    val name: LiveData<String> = _name
    fun onNameChange(newName: String) { _name.value = newName }

    private val _description = MutableLiveData("")
    val description: LiveData<String> = _description
    fun onDescriptionChange(newDesc: String) { _description.value = newDesc }

    private val _imageUri = MutableLiveData<Uri?>()
    val imageUri: LiveData<Uri?> = _imageUri
    fun onImageUriChange(newUri: Uri?) { _imageUri.value = newUri }
    private var originalImageUrl: String? = null // Do sprawdzania czy zdjęcie się zmieniło

    private val _startDate = MutableLiveData<Date?>()
    val startDate: LiveData<Date?> = _startDate
    fun onStartDateChange(newDate: Date?) { _startDate.value = newDate }

    private val _endDate = MutableLiveData<Date?>()
    val endDate: LiveData<Date?> = _endDate
    fun onEndDateChange(newDate: Date?) { _endDate.value = newDate }

    private val _tags = MutableLiveData<List<String>>(emptyList())
    val tags: LiveData<List<String>> = _tags
    fun onTagsChange(newTags: List<String>) { _tags.value = newTags }

    private val _latitude = MutableLiveData<Double?>()
    val latitude: LiveData<Double?> = _latitude
    fun onLatitudeChange(newLat: Double?) { _latitude.value = newLat }

    private val _longitude = MutableLiveData<Double?>()
    val longitude: LiveData<Double?> = _longitude
    fun onLongitudeChange(newLon: Double?) { _longitude.value = newLon }

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        if (isEditMode && eventId != null) {
            _isLoading.value = true
            firestore.collection("events2").document(eventId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        // ZMIANA: Ręczne i bezpieczne pobieranie wartości
                        _name.value = doc.getString("name") ?: ""
                        _description.value = doc.getString("description") ?: ""
                        val imageUrl = doc.getString("imageUrl")
                        if (imageUrl != null) {
                            _imageUri.value = imageUrl.toUri()
                            originalImageUrl = imageUrl
                        }
                        _startDate.value = doc.getTimestamp("startDate")?.toDate()
                        _endDate.value = doc.getTimestamp("endDate")?.toDate()
                        _tags.value = doc.get("tags") as? List<String> ?: emptyList()
                        _latitude.value = doc.getDouble("latitude")
                        _longitude.value = doc.getDouble("longitude")
                    }
                    _isLoading.value = false
                }
                .addOnFailureListener {
                    _isLoading.value = false
                    // Tutaj można dodać logikę błędu, np. wyświetlenie Toast
                }
        }
    }

    // Logika zapisu (tworzenie lub aktualizacja)
    fun saveEvent(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUserEmail = auth.currentUser?.email
        if (currentUserEmail == null) {
            onFailure("Musisz być zalogowany.")
            return
        }

        _isLoading.value = true
        val currentImageUri = _imageUri.value
        val isNewImage = currentImageUri != null && currentImageUri.toString() != originalImageUrl

        if (isEditMode && !isNewImage) {
            // Edycja bez zmiany zdjęcia
            updateEventInFirestore(originalImageUrl, onSuccess, onFailure)
        } else if (currentImageUri != null) {
            // Tworzenie lub edycja z nowym zdjęciem - wymaga uploadu
            val storageRef = storage.reference.child("events/${UUID.randomUUID()}.jpg")
            storageRef.putFile(currentImageUri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        updateEventInFirestore(downloadUrl.toString(), onSuccess, onFailure)
                    }
                }
                .addOnFailureListener { e ->
                    _isLoading.value = false
                    onFailure("Błąd przesyłania zdjęcia: ${e.message}")
                }
        } else {
            _isLoading.value = false
            onFailure("Zdjęcie jest wymagane.")
        }
    }

    private fun updateEventInFirestore(imageUrl: String?, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        // ZMIANA: Używamy asercji (!!) dla pól, które wiemy, że nie są nullem po walidacji UI
        // Zapewnia to bezpieczeństwo typów przy zmianie modelu na non-nullable.
        val eventDataMap = mutableMapOf(
            "name" to _name.value!!,
            "owner" to auth.currentUser?.email!!,
            "description" to _description.value?.ifBlank { null },
            "tags" to _tags.value?.ifEmpty { null },
            "longitude" to _longitude.value!!,
            "latitude" to _latitude.value!!,
            "startDate" to _startDate.value?.let { Timestamp(it) }!!,
            "endDate" to _endDate.value?.let { Timestamp(it) },
            "imageUrl" to imageUrl!!
        )

        val task = if (isEditMode && eventId != null) {
            firestore.collection("events2").document(eventId).update(eventDataMap)
        } else {
            eventDataMap["creation"] = Timestamp.now() // Dodaj datę utworzenia tylko dla nowych
            firestore.collection("events2").add(eventDataMap)
        }

        task.addOnSuccessListener {
            _isLoading.value = false
            onSuccess()
        }
            .addOnFailureListener { e ->
                _isLoading.value = false
                onFailure("Błąd zapisu danych: ${e.message}")
            }
    }
}

// Fabryka do przekazywania ID do ViewModel
class EventCreatorViewModelFactory(private val eventId: String?) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventCreatorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventCreatorViewModel(eventId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class EventCreator : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getStringExtra("EDIT_EVENT_ID")

        setContent {
            val viewModel: EventCreatorViewModel = viewModel(factory = EventCreatorViewModelFactory(eventId))
            EventCreatorScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCreatorScreen(viewModel: EventCreatorViewModel) {
    // Obserwuj stany z ViewModel
    val name by viewModel.name.observeAsState("")
    val description by viewModel.description.observeAsState("")
    val imageUri by viewModel.imageUri.observeAsState()
    val startDate by viewModel.startDate.observeAsState()
    val endDate by viewModel.endDate.observeAsState()
    val tags by viewModel.tags.observeAsState(emptyList())
    val latitude by viewModel.latitude.observeAsState()
    val longitude by viewModel.longitude.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)


    // Stany UI (walidacja, kontrolki)
    var currentTag by remember { mutableStateOf("") }
    var locationQuery by remember { mutableStateOf("") }

    var isNameError by remember { mutableStateOf(false) }
    var isStartDateError by remember { mutableStateOf(false) }
    var isImageError by remember { mutableStateOf(false) }
    var isDateOrderError by remember { mutableStateOf(false) }
    var isLocationError by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }


    val context = LocalContext.current
    val activity = (context as? Activity)
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val selectedLatLng by remember(latitude, longitude) {
        derivedStateOf {
            if (latitude != null && longitude != null) LatLng(latitude!!, longitude!!) else null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onImageUriChange(it); isImageError = false }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { viewModel.onImageUriChange(tempCameraUri); isImageError = false }
    }

    val mapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onLatitudeChange(result.data?.getDoubleExtra("latitude", 0.0))
            viewModel.onLongitudeChange(result.data?.getDoubleExtra("longitude", 0.0))
            isLocationError = false
        }
    }

    fun searchLocation() {
        if (locationQuery.isBlank()) return
        try {
            val geocoder = Geocoder(context)
            val addressList = geocoder.getFromLocationName(locationQuery, 1)
            if (addressList?.isNotEmpty() == true) {
                val address = addressList[0]
                viewModel.onLatitudeChange(address.latitude)
                viewModel.onLongitudeChange(address.longitude)
                isLocationError = false
                focusManager.clearFocus()
            } else {
                Toast.makeText(context, "Nie znaleziono lokalizacji", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(context, "Błąd usługi geokodowania:" + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun addTag() {
        val trimmedTag = currentTag.trim()
        val currentTags = tags
        when {
            trimmedTag.isBlank() -> {}
            currentTags.size >= 10 -> Toast.makeText(context, "Możesz dodać maksymalnie 10 tagów.", Toast.LENGTH_SHORT).show()
            trimmedTag.length > 25 -> Toast.makeText(context, "Tag może mieć maksymalnie 25 znaków.", Toast.LENGTH_SHORT).show()
            currentTags.contains(trimmedTag) -> Toast.makeText(context, "Ten tag już został dodany.", Toast.LENGTH_SHORT).show()
            else -> {
                viewModel.onTagsChange(currentTags + trimmedTag)
                currentTag = ""
                focusManager.clearFocus()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edytuj wydarzenie" else "Stwórz nowe wydarzenie") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Button(
                    onClick = {
                    // Walidacja pól pozostaje bez zmian
                    isNameError = name.isBlank()
                    isStartDateError = (startDate == null)
                    isImageError = (imageUri == null)
                    isLocationError = (latitude == null || longitude == null)
                    isDateOrderError = if (startDate != null && endDate != null) startDate!! >= endDate!! else false

                    if (!isNameError && !isStartDateError && !isImageError && !isDateOrderError && !isLocationError) {
                        // ZMIANA: Pokaż dialog jeśli jesteśmy w trybie edycji
                        if (viewModel.isEditMode) {
                            showSaveDialog = true
                        } else {
                            // W trybie tworzenia zapisz od razu
                            viewModel.saveEvent(
                                onSuccess = {
                                    Toast.makeText(context, "Wydarzenie zapisane!", Toast.LENGTH_SHORT).show()
                                    activity?.finish()
                                },
                                onFailure = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    } else {
                        Toast.makeText(context, "Uzupełnij wymagane pola i popraw błędy", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Text(if(viewModel.isEditMode) "Zapisz zmiany" else "Zapisz wydarzenie", fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- SEKCJA ZDJĘCIA ---
                Card(shape = RoundedCornerShape(16.dp)) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Zdjęcie wydarzenia",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.login_bck),
                        error = painterResource(id = R.drawable.login_bck)
                    )
                }
                if (isImageError) {
                    Text("Zdjęcie jest wymagane", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Wybierz z galerii") }
                    Button(onClick = {
                        val newImageUri = context.createTempImageUri()
                        tempCameraUri = newImageUri
                        cameraLauncher.launch(newImageUri)
                    }) { Text("Zrób zdjęcie") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- SEKCJA DANYCH PODSTAWOWYCH ---
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        // NOWA LINIA: Sprawdzamy długość przed aktualizacją
                        if (it.length <= 25) {
                            viewModel.onNameChange(it)
                        }
                        isNameError = false
                    },
                    label = { Text("Nazwa wydarzenia (max 25 znaków)") }, // ZMIANA: Dodajemy informację w etykiecie
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isNameError
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 250) viewModel.onDescriptionChange(it) },
                    label = { Text("Krótki opis (opcjonalnie)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    supportingText = { Text("${description.length} / 250") }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // --- SEKCJA DATY I CZASU ---
                Text("Data i czas", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                DateTimePickerField(
                    label = "Początek*",
                    date = startDate,
                    onDateSelected = { viewModel.onStartDateChange(it); isStartDateError = false; isDateOrderError = false },
                    isError = isStartDateError || isDateOrderError
                )
                Spacer(modifier = Modifier.height(8.dp))
                DateTimePickerField(
                    label = "Koniec (opcjonalnie)",
                    date = endDate,
                    onDateSelected = { viewModel.onEndDateChange(it); isDateOrderError = false }
                )
                if (isDateOrderError) {
                    Text("Data zakończenia musi być po dacie rozpoczęcia", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- SEKCJA TAGÓW ---
                Text("Tagi (opcjonalnie, max 10)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentTag,
                    onValueChange = { if (it.length <= 25) currentTag = it },
                    label = { Text("Dodaj tag (max 25 znaków)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(onClick = { addTag() }) { Icon(Icons.Default.Add, "Dodaj tag") } },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() })
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags) { tag ->
                        InputChip(
                            selected = false,
                            onClick = { /* Nothing */ },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Default.Close, "Usuń tag", modifier = Modifier.size(18.dp).clickable { viewModel.onTagsChange(tags - tag) }) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- SEKCJA LOKALIZACJI ---
                Text("Lokalizacja*", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = locationQuery,
                    onValueChange = { locationQuery = it },
                    label = { Text("Wyszukaj adres lub miejsce") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { searchLocation() }) {
                            Icon(Icons.Default.Search, contentDescription = "Wyszukaj")
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchLocation() })
                )
                Spacer(Modifier.height(16.dp))

                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(51.1079, 17.0385), 12f)
                }
                LaunchedEffect(selectedLatLng) {
                    selectedLatLng?.let {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
                    }
                }

                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = if (isLocationError) 2.dp else 0.dp,
                            color = if (isLocationError) MaterialTheme.colorScheme.error else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng ->
                        viewModel.onLatitudeChange(latLng.latitude)
                        viewModel.onLongitudeChange(latLng.longitude)
                        isLocationError = false
                    }
                ) {
                    selectedLatLng?.let {
                        Marker(state = MarkerState(position = it))
                    }
                }
                if (isLocationError) {
                    Text("Lokalizacja jest wymagana", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, MyLocationDemoActivity::class.java).apply {
                            putExtra("isCreator", true)
                        }
                        mapLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Mapa pełnoekranowa", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Otwórz mapę w trybie pełnoekranowym")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Potwierdź zapis") },
                    text = { Text("Czy na pewno chcesz zapisać zmiany w tym wydarzeniu?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSaveDialog = false
                                viewModel.saveEvent(
                                    onSuccess = {
                                        Toast.makeText(context, "Zmiany zapisane!", Toast.LENGTH_SHORT).show()
                                        activity?.finish()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        ) {
                            Text("Zapisz")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showSaveDialog = false }) {
                            Text("Anuluj")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DateTimePickerField(label: String, date: Date?, onDateSelected: (Date) -> Unit, isError: Boolean = false) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    if (date != null) calendar.time = date
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()) }

    OutlinedTextField(
        value = date?.let { dateFormat.format(it) } ?: "",
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        isError = isError,
        trailingIcon = {
            Icon(Icons.Default.DateRange, "Wybierz datę", modifier = Modifier.clickable {
                val timePickerDialog = TimePickerDialog(context, { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    onDateSelected(calendar.time)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
                DatePickerDialog(context, { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    timePickerDialog.show()
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            })
        },
        modifier = Modifier.fillMaxWidth()
    )
}

fun Context.createTempImageUri(): Uri {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
}
