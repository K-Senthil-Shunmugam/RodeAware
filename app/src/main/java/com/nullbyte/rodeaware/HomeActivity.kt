package com.nullbyte.rodeaware


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser


class HomeActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var userNameTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)

        firebaseAuth = FirebaseAuth.getInstance()
        userNameTextView = findViewById(R.id.userNameTextView)

        // Set click listener for the start ride button
        val startRideButton = findViewById<Button>(R.id.startRideButton)
        startRideButton.setOnClickListener {
            // Start MainActivity to begin recording data
            val intent = Intent(this@HomeActivity, MainActivity::class.java)
            startActivity(intent)
        }

        // Set click listener for the dashboard button
        val dashBoardButton = findViewById<Button>(R.id.dashBoardButton)
        dashBoardButton.setOnClickListener {
            // Start DashBoardActivity to view data
            val intent = Intent(this@HomeActivity, DashBoardActivity::class.java)
            startActivity(intent)
        }

        // Logout button
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            clearLoginCache()
            firebaseAuth.signOut()
            startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
            finish()
        }

    }
    private fun clearLoginCache() {
        val sharedPref = getSharedPreferences("login_preferences", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()
    }
    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = firebaseAuth.currentUser
        updateUI(currentUser)
    }
    private fun updateUI(currentUser: FirebaseUser?) {
        if (currentUser != null) {
            // User is signed in
            val displayName = currentUser.displayName
            userNameTextView.text = "$displayName!"
        } else {
            // User is signed out
            userNameTextView.text = " "
        }
    }
}