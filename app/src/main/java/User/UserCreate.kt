package User

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.piastcity.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import eventSearch.EventSearchActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class UserCreate : ComponentActivity() {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Przekazujemy logikę do naszej funkcji kompozycyjnej
            UserCreateScreen(
                onSaveProfile = { username, photoUri ->
                    saveProfile(username, photoUri)
                }
            )
        }
    }

    private fun saveProfile(username: String, photoUri: Uri?) {
        val currentUserEmail = firebaseAuth.currentUser?.email
        if (currentUserEmail == null) {
            Toast.makeText(this, "Błąd: Użytkownik nie jest zalogowany.", Toast.LENGTH_SHORT).show()
            return
        }

        if (photoUri != null) {
            // Krok 1: Jeśli jest zdjęcie, najpierw wrzuć je do Storage
            val storageRef = storage.reference.child("users/${currentUserEmail}.jpg")
            storageRef.putFile(photoUri)
                .addOnSuccessListener {
                    // Krok 2: Po udanym uploadzie, pobierz URL do zdjęcia
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        // Krok 3: Stwórz obiekt User i zapisz go w Firestore
                        val user = User(
                            username = username,
                            firebaseUser = currentUserEmail,
                            imageUrl = downloadUrl.toString()
                        )
                        sendUserToFirestore(user)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Błąd wysyłania zdjęcia: ${it.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            // Jeśli nie ma zdjęcia, zapisz profil bez imageUrl
            val user = User(
                username = username,
                firebaseUser = currentUserEmail,
                imageUrl = null
            )
            sendUserToFirestore(user)
        }
    }

    private fun sendUserToFirestore(user: User) {
        firestore.collection("users").add(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Profil zapisany!", Toast.LENGTH_SHORT).show()
                goToApp()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd zapisu profilu: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun goToApp() {
        val intent = Intent(this, EventSearchActivity::class.java)
        startActivity(intent)
        finishAffinity()
    }
}


// --- Funkcja kompozycyjna dla UI ---
@Composable
private fun UserCreateScreen(
    onSaveProfile: (username: String, photoUri: Uri?) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Nowoczesny sposób obsługi aparatu, zastępuje `startActivityForResult`
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                imageUri = tempImageUri
            }
        }
    )

    // Funkcja do generowania URI dla nowego zdjęcia
    fun getTempImageUri(): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
        // Zwraca Uri z FileProvidera - to jest kluczowe dla nowoczesnego Androida
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", // Musi pasować do tego, co masz w Manifeście
            file
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tło, tak jak w poprzednich ekranach
        Image(
            painter = painterResource(id = R.drawable.login_bck),
            contentDescription = "Tło",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
// --- Podgląd zdjęcia ---
// Używamy biblioteki Coil do łatwego ładowania obrazów z Uri
            AsyncImage(
                model = imageUri,
                contentDescription = "Avatar",    // USUWAMY PLACEHOLDER - tło i kształt są już zdefiniowane przez modyfikatory!
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape) // 1. Nadaj okrągły kształt
                    .background(Color.Gray) // 2. Nadaj szare tło (widoczne gdy nie ma obrazka)
                    .border(2.dp, Color.White, CircleShape), // 3. Dodaj białą ramkę
                contentScale = ContentScale.Crop // Ważne, aby załadowane zdjęcie wypełniło koło
            )


            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Stwórz swój profil",
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nazwa użytkownika") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.DarkGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFF0F0F0),
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Krok 1: Stwórz nową, lokalną i niemodyfikowalną zmienną
                    val newImageUri = getTempImageUri()
                    // Krok 2: Przypisz jej wartość do stanu
                    tempImageUri = newImageUri
                    // Krok 3: Użyj bezpiecznej, lokalnej zmiennej do wywołania launchera
                    cameraLauncher.launch(newImageUri)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zrób zdjęcie profilowe")
            }


            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (username.isNotBlank()) {
                        onSaveProfile(username, imageUri)
                    } else {
                        Toast.makeText(context, "Nazwa użytkownika jest wymagana.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = username.isNotBlank() // Przycisk jest aktywny tylko, gdy nazwa jest wpisana
            ) {
                Text("Zapisz profil")
            }
        }
    }
}
