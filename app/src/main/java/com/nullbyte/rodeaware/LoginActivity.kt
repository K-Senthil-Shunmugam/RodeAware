package com.nullbyte.rodeaware

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private val RC_SIGN_IN = 123
    private val PREFS_NAME = "login_preferences"
    private val KEY_LOGGED_IN = "logged_in"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        // Set click listener for Google sign-in button
        val googleSignInButton = findViewById<Button>(R.id.googleSignInButton)
        googleSignInButton.setOnClickListener {
            // Sign out before starting the sign-in flow
            signOutAndStartGoogleSignInFlow()
        }
    }

    // Method to sign out and then start Google sign-in flow
    private fun signOutAndStartGoogleSignInFlow() {
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                startGoogleSignInFlow()
            }
    }

    // Method to start Google sign-in flow
    private fun startGoogleSignInFlow() {
        val providers = arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build())

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN
        )
    }

    // Handle the result of the Google sign-in flow
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                saveLoginStatus(true)
                startHomeActivity()
                finish()
            } else {
                // Sign in failed
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Method to start HomeActivity
    private fun startHomeActivity() {
        startActivity(Intent(this, HomeActivity::class.java))
    }

    // Method to save login status
    private fun saveLoginStatus(loggedIn: Boolean) {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean(KEY_LOGGED_IN, loggedIn)
            apply()
        }
    }

    // Method to check if user is already logged in
    private fun isUserLoggedIn(): Boolean {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(KEY_LOGGED_IN, false)
    }
}
