package com.example.piastcity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.*
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

class RegisterActivity : ComponentActivity() {

    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RegisterScreen(
                onRegisterClick = { email, password ->
                    signup(email, password)
                },
                onLoginClick = {
                    goToLoginActivity()
                }
            )
        }
    }

    // Obsługuje proces rejestracji nowego użytkownika w Firebase Authentication.
    private fun signup(email: String, password: String) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Rejestracja pomyślna!", Toast.LENGTH_SHORT).show()
                    goToLoginActivity()
                } else {
                    val error = task.exception?.message ?: "Nieznany błąd rejestracji."
                    Toast.makeText(this, "Błąd: $error", Toast.LENGTH_LONG).show()
                }
            }
    }

    // Nawiguje do ekranu logowania po udanej rejestracji.
    private fun goToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}

// Komponent UI dla ekranu rejestracji.
@Composable
private fun RegisterScreen(onRegisterClick: (String, String) -> Unit, onLoginClick: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Stany dla flag błędów walidacji.
    var isEmailError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var isPasswordConfirmError by remember { mutableStateOf(false) }

    // Sprawdza, czy podany ciąg znaków jest poprawnym adresem e-mail.
    fun isEmailValid(email: String): Boolean = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    // Sprawdza, czy hasło spełnia zdefiniowane kryteria bezpieczeństwa.
    fun isPasswordValid(password: String): Boolean {
        val lowercaseRegex = Regex("[a-z]")
        val uppercaseRegex = Regex("[A-Z]")
        val numberRegex = Regex("[0-9]")
        val specialCharRegex = Regex("[^A-Za-z0-9]")

        return password.length >= 8 &&
                lowercaseRegex.containsMatchIn(password) &&
                uppercaseRegex.containsMatchIn(password) &&
                numberRegex.containsMatchIn(password) &&
                specialCharRegex.containsMatchIn(password)
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
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "welcome to",
                fontSize = 40.sp,
            )

            Image(
                painter = painterResource(id = R.drawable.icon_piast_city),
                contentDescription = "Logo",
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; isEmailError = false },
                label = { Text("Adres e-mail") },
                singleLine = true,
                isError = isEmailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            if (isEmailError) {
                Text("Podaj poprawny adres e-mail", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; isPasswordError = false },
                label = { Text("Hasło") },
                singleLine = true,
                isError = isPasswordError,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (isPasswordError) {
                Text(
                    text = "Hasło musi mieć min. 8 znaków, cyfrę, wielką i małą literę oraz znak specjalny.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it; isPasswordConfirmError = false },
                label = { Text("Potwierdź hasło") },
                singleLine = true,
                isError = isPasswordConfirmError,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (isPasswordConfirmError) {
                Text("Hasła nie są takie same.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isEmailError = !isEmailValid(email)
                    isPasswordError = !isPasswordValid(password)
                    isPasswordConfirmError = password != passwordConfirm

                    if (!isEmailError && !isPasswordError && !isPasswordConfirmError) {
                        onRegisterClick(email, password)
                    } else {
                        Toast.makeText(context, "Popraw błędy w formularzu", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zarejestruj się")
            }

            Spacer(modifier = Modifier.height(8.dp))

            ClickableText(
                text = AnnotatedString("Masz już konto?"),
                onClick = { onLoginClick() },
                style = TextStyle(
                    color = Color(0xFF4a99de),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
