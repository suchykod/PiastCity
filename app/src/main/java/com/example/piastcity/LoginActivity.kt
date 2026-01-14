package com.example.piastcity

import User.UserCreate
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.piastcity.databinding.ActivityLoginBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import eventSearch.EventSearchActivity

class LoginActivity : AppCompatActivity() {

    private var firestore = Firebase.firestore;
    private lateinit var binding: ActivityLoginBinding;
    private lateinit var firebaseAuth: FirebaseAuth;
    private var email: String = ""
    private var password: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firebaseAuth = FirebaseAuth.getInstance()

        email = binding.loginEmail.text.toString()
        password = binding.loginPassword.text.toString()

    }

    fun Login(view: View) {
        if (isEmailValid(view) and isPasswordValid(view)){
            loginUser(email, password)

            firestore.collection("users")
                .whereEqualTo("firebaseUser", email)
                .get()
                .addOnSuccessListener {
                    if(!it.isEmpty)
                        goToApp(view)
                    else
                        goToCreateUser(view)
                }
        }

    }
    private fun loginUser(email: String, password: String) {
        Log.i("DUP", email)
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task: Task<AuthResult> ->
                if (task.isSuccessful) {
                    // Login successful
                    val user: FirebaseUser? = firebaseAuth.currentUser
                    Toast.makeText(this,"login succesful", Toast.LENGTH_LONG)
                    // You can perform additional operations here, such as retrieving user data

                } else {
                    // Login failed
                    Toast.makeText(
                        applicationContext,
                        "Login failed. ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    fun isPasswordValid(view: View): Boolean{
        password = binding.loginPassword.text.toString()
        val lowercaseRegex = Regex("[a-z]")
        val uppercaseRegex = Regex("[A-Z]")
        val numberRegex = Regex("[0-9]")
        val specialCharRegex = Regex("[^A-Za-z0-9]")

        val hasLowercase = lowercaseRegex.containsMatchIn(password)
        val hasUppercase = uppercaseRegex.containsMatchIn(password)
        val hasNumber = numberRegex.containsMatchIn(password)
        val hasSpecialChar = specialCharRegex.containsMatchIn(password)
        val isLongerThan8char = password.length >= 8

        return hasLowercase && hasUppercase && hasNumber && hasSpecialChar && isLongerThan8char
    }

    fun isEmailValid(view: View): Boolean{
        email = binding.loginEmail.text.toString()
        val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

        return emailRegex.matches(email)
    }

    fun goToRegisterActivity(view: View) {
        val registerIntent = Intent(this, RegisterActivity::class.java)
        startActivity(registerIntent)
    }

    fun goToApp(view: View){
        val appIntent = Intent(this, EventSearchActivity::class.java)
        startActivity(appIntent)
        // zabij aktywność po przejściu dalej
        finish()
    }

    fun goToCreateUser(view: View){
        val crtIntent = Intent(this, UserCreate::class.java)
        startActivity(crtIntent)
        finish()
    }

}
