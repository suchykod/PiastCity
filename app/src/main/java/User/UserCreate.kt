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

// Aktywność odpowiedzialna za stworzenie profilu użytkownika po pierwszej rejestracji.
class UserCreate : ComponentActivity() {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UserCreateScreen(
                onSaveProfile = { username, photoUri ->
                    saveProfile(username, photoUri)
                }
            )
        }
    }

    // Zapisuje profil użytkownika: zdjęcie do Storage, a dane do Firestore.
    private fun saveProfile(username: String, photoUri: Uri?) {
        val currentUserEmail = firebaseAuth.currentUser?.email
        if (currentUserEmail == null) {
            Toast.makeText(this, "Błąd: Użytkownik nie jest zalogowany.", Toast.LENGTH_SHORT).show()
            return
        }

        if (photoUri != null) {
            // Etap 1: Upload zdjęcia do Firebase Storage.
            val storageRef = storage.reference.child("users/${currentUserEmail}.jpg")
            storageRef.putFile(photoUri)
                .addOnSuccessListener {
                    // Etap 2: Pobranie adresu URL do przesłanego zdjęcia.
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        // Etap 3: Zapis obiektu User w Firestore.
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
        }
    }

    // Zapisuje obiekt użytkownika w kolekcji 'users'.
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

    // Nawiguje do głównego ekranu aplikacji i czyści stos aktywności.
    private fun goToApp() {
        val intent = Intent(this, EventSearchActivity::class.java)
        startActivity(intent)
        finishAffinity()
    }
}

// Komponent UI dla ekranu tworzenia profilu.
@Composable
private fun UserCreateScreen(
    onSaveProfile: (username: String, photoUri: Uri?) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Launcher do obsługi rezultatu zrobienia zdjęcia aparatem.
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                imageUri = tempImageUri
            }
        }
    )

    // Tworzy tymczasowy, unikalny URI dla nowego zdjęcia.
    fun getTempImageUri(): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            AsyncImage(
                model = imageUri,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
                    .border(2.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop
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
                    val newImageUri = getTempImageUri()
                    tempImageUri = newImageUri
                    cameraLauncher.launch(newImageUri)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zrób zdjęcie profilowe")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (username.isNotBlank() && imageUri != null) {
                        onSaveProfile(username, imageUri)
                    } else {
                        Toast.makeText(context, "Nazwa użytkownika i zdjęcie są wymagane.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                // Przycisk jest aktywny tylko, gdy oba wymagane pola są uzupełnione.
                enabled = username.isNotBlank() && imageUri != null
            ) {
                Text("Zapisz profil")
            }
        }
    }
}
