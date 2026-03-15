package com.example.piastcity

import User.UserCreate
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import eventSearch.EventSearchActivity

class LoginActivity : ComponentActivity() {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoginScreen(
                onLoginClick = { email, password ->
                    loginUser(email, password)
                },
                onRegisterClick = {
                    goToRegisterActivity()
                }
            )
        }
    }

    // Obsługuje proces logowania użytkownika.
    private fun loginUser(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Logowanie pomyślne!", Toast.LENGTH_SHORT).show()
                    checkUserProfile(task.result.user?.email)
                } else {
                    val error = task.exception?.message ?: "Nieznany błąd logowania."
                    Toast.makeText(this, "Błąd: $error", Toast.LENGTH_LONG).show()
                }
            }
    }

    // Sprawdza, czy zalogowany użytkownik ma już utworzony profil w Firestore.
    private fun checkUserProfile(userEmail: String?) {
        if (userEmail == null) {
            Toast.makeText(this, "Błąd: Brak adresu email użytkownika.", Toast.LENGTH_LONG).show()
            return
        }

        firestore.collection("users")
            .whereEqualTo("firebaseUser", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // Jeśli profil istnieje, przejdź do głównego ekranu aplikacji.
                    goToApp()
                } else {
                    // W przeciwnym razie, skieruj do ekranu tworzenia profilu.
                    goToCreateUser()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Błąd pobierania profilu: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Metody nawigacyjne do przechodzenia między aktywnościami.
    private fun goToRegisterActivity() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun goToApp() {
        val intent = Intent(this, EventSearchActivity::class.java)
        startActivity(intent)
        finishAffinity() // Zamyka wszystkie poprzednie aktywności, uniemożliwiając powrót.
    }

    private fun goToCreateUser() {
        val intent = Intent(this, UserCreate::class.java)
        startActivity(intent)
        finishAffinity() // Zamyka wszystkie poprzednie aktywności.
    }
}

// Komponent UI dla ekranu logowania.
@Composable
private fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Prosta walidacja adresu e-mail.
    fun isEmailValid(email: String): Boolean = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

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
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "welcome back to",
                fontSize = 40.sp,
            )

            Image(
                painter = painterResource(id = R.drawable.icon_piast_city),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(180.dp)
                    .padding(vertical = 16.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Adres e-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Hasło") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        if (isEmailValid(email)) {
                            onLoginClick(email, password)
                        } else {
                            Toast.makeText(context, "Proszę podać poprawny adres e-mail.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Wszystkie pola są wymagane.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zaloguj się")
            }

            Spacer(modifier = Modifier.height(8.dp))

            ClickableText(
                text = AnnotatedString("Nie masz jeszcze konta?"),
                onClick = { onRegisterClick() },
                style = TextStyle(
                    color = Color(0xFF4a99de),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
