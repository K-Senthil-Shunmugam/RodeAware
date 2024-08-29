package com.nullbyte.rodeaware
import CLocation
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Formatter
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), LocationListener , SensorEventListener {
    private lateinit var mSensorManager : SensorManager
    private var mAccelerometer : Sensor ?= null
    private var resume = false;
    private var tspeed: TextView? = null
    private var tacc: TextView? = null
    private var databaseReference: DatabaseReference? = null
    private var currentSessionId: String? = null
    private var googleId: String? = null
    private val speedSamples = ArrayList<Float>()
    private var totalAcceleration: Float = 0.0f // Initialize total acceleration variable
    private var xacc: Float = 0.0f
    private var yacc: Float = 0.0f
    private var zacc: Float = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_main)
        // Acclerometer Init
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        // Check if user is already signed in
        val currentUser = FirebaseAuth.getInstance().currentUser
        googleId = currentUser?.uid ?: "No User"
        updateSpeed(null)

        currentSessionId = generateSessionId()
        // Initialize Google Play Services
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            googleApiAvailability.getErrorDialog(this, resultCode, 0)?.show()
        }
        databaseReference = FirebaseDatabase.getInstance().reference

        tspeed = findViewById(R.id.tspeed)
        tacc = findViewById(R.id.tacc)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000)
        } else {
            doStuff()
        }

    }
    private fun generateSessionId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString()
        return "$timestamp-$uuid"
    }

    override fun onLocationChanged(location: Location) {
        if (location != null) {
            val myLocation = CLocation(location)
            updateSpeed(myLocation)

            speedSamples.add(myLocation.speed)

            if (speedSamples.size >= 3) {
                // Calculate average speed
                val averageSpeed = speedSamples.average().toFloat()
                val acceleration = totalAcceleration

                // Create SpeedData instance
                val speedData = SpeedData()
                speedData.setSpeedValue(averageSpeed.toDouble())// Convert Float to Double
                speedData.latitude = location.latitude
                speedData.longitude = location.longitude
                speedData.acceleration = acceleration.toDouble()

                // Upload to Firebase with the unique session ID
                val sessionRef = databaseReference!!.child(googleId.toString()).child("sessions").child(currentSessionId!!).child(
                    System.currentTimeMillis().toString()
                )
                sessionRef.setValue(speedData)
                    .addOnSuccessListener { Log.d("Firebase", "Data stored successfully") }
                    .addOnFailureListener { e -> Log.e("Firebase", "Error storing data: " + e.message) }

                // Clear the samples
                speedSamples.clear()
            }
        }
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            xacc = event.values[0]
            yacc = event.values[1]
            zacc = event.values[2]

            // Calculate total acceleration magnitude
            totalAcceleration = calculateTotalAcceleration(xacc, yacc, zacc)
            // Round the acceleration value to 2 decimal points
            val roundedAcceleration = String.format("%.2f", totalAcceleration)
            tacc?.text = "$roundedAcceleration m/s2" // Update TextView with rounded acceleration value
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // This method is intentionally left blank
    }


    private fun calculateTotalAcceleration(xacc: Float, yacc: Float, zacc: Float): Float {
        return sqrt(xacc * xacc + yacc * yacc + zacc * zacc)
    }




    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doStuff()
            } else {
                finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun doStuff() {
        val locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0f, this)
        Toast.makeText(this, "Waiting for GPS", Toast.LENGTH_SHORT).show()
    }

    private fun updateSpeed(location: CLocation?) {
        var nCurrentSpeed = 0f
        if (location != null) {
            nCurrentSpeed = location.speed
        }
        val fmt = Formatter(StringBuilder())
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed)
        var strCurrentSpeed = fmt.toString()
        strCurrentSpeed = strCurrentSpeed.replace(" ", "0")
        tspeed?.text = strCurrentSpeed + " km/h"
    }
}