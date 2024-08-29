package com.nullbyte.rodeaware

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

data class SpeedDataRead(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0,
    val acceleration: Double = 0.0
)

data class SessionMetrics(
    val maxSpeed: Double = 0.0,
    val maxAcceleration: Double = 0.0,
    val avgSafetyScore: Double = 0.0
)



class DashBoardActivity : AppCompatActivity() {

    private lateinit var spinnerSessions: Spinner
    private lateinit var tvMaxSpeed: TextView
    private lateinit var tvMaxAcceleration: TextView
    private lateinit var tvAvgSafety: TextView


    var safetyEntries = mutableListOf<Entry>()

    private lateinit var mapView: MapView

    private lateinit var chart: LineChart

    private var databaseReference: DatabaseReference? = null
    private var googleId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Initialize Firebase Database
        databaseReference = FirebaseDatabase.getInstance().reference
        googleId = FirebaseAuth.getInstance().currentUser?.uid ?: "No User"

        // Initialize UI components
        spinnerSessions = findViewById(R.id.spinner_sessions)
        tvMaxSpeed = findViewById(R.id.tv_max_speed)
        tvMaxAcceleration = findViewById(R.id.tv_max_acceleration)
        tvAvgSafety = findViewById(R.id.tv_avg_safety_score)
        chart = findViewById(R.id.chart)


        mapView = findViewById(R.id.map_view)

        // Configure osmdroid
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Load sessions
        loadSessions()
    }

    private fun loadSessions() {
        // Retrieve the list of sessions for the current user
        val sessionsRef = databaseReference!!.child(googleId!!).child("sessions")
        sessionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val sessions = mutableListOf<String>()
                for (snapshot in dataSnapshot.children) {
                    sessions.add(snapshot.key.toString())
                }
                setupSpinner(sessions)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Error loading sessions: ${databaseError.message}")
            }
        })
    }

    private fun convertTimestampToDateString(timestamp: String): String {
        val millis = timestamp.toLongOrNull() ?: 0L
        val date = Date(millis)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(date)
    }

    private fun setupSpinner(sessions: List<String>) {
        val formattedSessions = sessions.map { sessionId ->
            val timestamp = sessionId.split("-")[0]
            convertTimestampToDateString(timestamp)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, formattedSessions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSessions.adapter = adapter

        spinnerSessions.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSessionId = sessions[position]
                loadSessionData(selectedSessionId)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadSessionData(sessionId: String) {
        val metricsRef = databaseReference!!.child(googleId!!).child("session-metrics").child(sessionId)
        val safetyRef = databaseReference!!.child(googleId!!).child("session-safety").child(sessionId)

        // Check if metrics are already cached
        metricsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("DefaultLocale")
            override fun onDataChange(metricsSnapshot: DataSnapshot) {
                if (metricsSnapshot.exists()) {
                    // Metrics are cached
                    val metrics = metricsSnapshot.getValue(SessionMetrics::class.java)
                    if (metrics != null) {
                        // Use cached metrics
                        tvMaxSpeed.text = String.format("Max Speed: %.2f km/h", metrics.maxSpeed)
                        tvMaxAcceleration.text = String.format("Max Acceleration: %.2f m/s²", metrics.maxAcceleration)
                        tvAvgSafety.text = String.format("Ride Safety Score: %.2f", metrics.avgSafetyScore)
                        // Load path points
                        loadPathPoints(sessionId)

                        // Load chart data
                        loadChartData(sessionId)
                    } else {
                        // Metrics cached but not valid
                        // Fall back to calculating metrics
                        calculateAndCacheMetrics(sessionId)
                    }
                } else {
                    // Metrics not cached, calculate and cache them
                    calculateAndCacheMetrics(sessionId)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Error checking session metrics: ${databaseError.message}")
            }
        })

        // Load safety data
        safetyRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(safetySnapshot: DataSnapshot) {
                val safetyPoints = mutableListOf<Double>()

                if (safetySnapshot.exists()) {
                    // Safety data exists, load it into the mutable list
                    for (snapshot in safetySnapshot.children) {
                        val safetyScore = snapshot.getValue(Double::class.java)
                        if (safetyScore != null) {
                            safetyPoints.add(safetyScore)
                        }
                    }
                    Log.d("Firebase", "Safety data loaded successfully.")
                } else {
                    // Safety data not cached, calculate and cache them
                    calculateAndCacheSafety(sessionId, safetyPoints, safetyRef)
                }

                // Use the safetyPoints list as needed
                // safetyPoints
                safetyEntries = convertDoublesToEntries(safetyPoints)
                Log.d("Firebase", "Safety Points: $safetyPoints")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Error checking session safety data: ${databaseError.message}")
            }
        })
    }

    private fun loadChartData(sessionId: String) {
        val sessionRef = databaseReference!!.child(googleId!!).child("sessions").child(sessionId)

        val speedEntries = mutableListOf<Entry>()
        val accelerationEntries = mutableListOf<Entry>()

        sessionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                var index = 0

                for (snapshot in dataSnapshot.children) {
                    val speedData = snapshot.getValue(SpeedDataRead::class.java)

                    if (speedData != null) {
                        speedEntries.add(Entry(index.toFloat(), speedData.speed.toFloat()))
                        accelerationEntries.add(Entry(index.toFloat(), speedData.acceleration.toFloat()))
                        // Safety score might need to be computed or fetched separately
                        index++
                    }
                }

                val speedDataSet = LineDataSet(speedEntries, "Speed km/h")
                speedDataSet.color = android.graphics.Color.BLUE
                speedDataSet.valueTextColor = android.graphics.Color.BLACK

                val accelerationDataSet = LineDataSet(accelerationEntries, "Acceleration m/s2")
                accelerationDataSet.color = android.graphics.Color.RED
                accelerationDataSet.valueTextColor = android.graphics.Color.BLACK

                val safetyDataSet = LineDataSet(safetyEntries, "Safety")
                safetyDataSet.color = android.graphics.Color.GREEN
                safetyDataSet.valueTextColor = android.graphics.Color.BLACK

                val lineData = LineData(speedDataSet, accelerationDataSet, safetyDataSet)
                chart.data = lineData
                chart.description.isEnabled = false
                val xAxis = chart.xAxis
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 0f
                xAxis.setDrawGridLines(false)
                chart.invalidate()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Error loading session data: ${databaseError.message}")
            }
        })
    }

    private fun calculateAndCacheSafety(sessionId: String, safetyPoints: MutableList<Double>, safetyRef: DatabaseReference) {
        val sessionRef = databaseReference!!.child(googleId!!).child("sessions").child(sessionId)

        sessionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("DefaultLocale")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                var totalSafetyScore = 0.0
                var count = 0
                val speedThreshold = 60.0
                val accelerationThreshold = 12.0
                for (snapshot in dataSnapshot.children) {
                    val speedData = snapshot.getValue(SpeedDataRead::class.java)
                    val speedTimeStamp = snapshot.key
                    if (speedData != null && speedTimeStamp != null) {
                        var safetyScore = 100.0 // Starting with a full score

                        if (speedData.speed > speedThreshold) {
                            safetyScore -= 50.0 // Penalize for high speed
                        }
                        if (speedData.acceleration > accelerationThreshold) {
                            safetyScore -= 50.0 // Penalize for high acceleration
                        }

                        // Coerce the score to be within 0-100 range
                        safetyScore = safetyScore.coerceIn(0.0, 100.0)

                        // Add safety score to the list
                        safetyPoints.add(safetyScore)

                        // Save safety score under session-safety with the timestamp as key
                        safetyRef.child(speedTimeStamp).setValue(safetyScore)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Log.d("Firebase", "Safety score saved successfully for $speedTimeStamp.")
                                } else {
                                    Log.e("Firebase", "Error saving safety score for $speedTimeStamp: ${task.exception?.message}")
                                }
                            }
                        // Accumulate total safety score and count
                        totalSafetyScore += safetyScore
                        count++
                    }
                }

                // Calculate the average safety score
                val avgSafetyScore = if (count > 0) totalSafetyScore / count else 0.0

                // Store the average safety score in session-metrics
                val metricsRef = databaseReference!!.child(googleId!!).child("session-metrics").child(sessionId)
                metricsRef.child("avgSafetyScore").setValue(avgSafetyScore)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("Firebase", "Average safety score saved successfully.")
                        } else {
                            Log.e("Firebase", "Error saving average safety score: ${task.exception?.message}")
                        }
                    }

                Log.d("Firebase", "Safety Points after calculation: $safetyPoints")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Error loading session data: ${databaseError.message}")
            }
        })

    }
    private fun loadPathPoints(sessionId: String) {
        val sessionRef = databaseReference!!.child(googleId!!).child("sessions").child(sessionId)

        sessionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val pathPoints = mutableListOf<GeoPoint>()

                for (snapshot in dataSnapshot.children) {
                    val speedData = snapshot.getValue(SpeedDataRead::class.java)
                    if (speedData != null) {
                        pathPoints.add(GeoPoint(speedData.latitude, speedData.longitude))
                    }
                }

                // Update UI and Map with path points
                updateMap(pathPoints)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Error loading session data: ${databaseError.message}")
            }
        })
    }

    private fun calculateAndCacheMetrics(sessionId: String) {
        val sessionRef = databaseReference!!.child(googleId!!).child("sessions").child(sessionId)
        val metricsRef = databaseReference!!.child(googleId!!).child("session-metrics").child(sessionId)

        sessionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("DefaultLocale")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val pathPoints = mutableListOf<GeoPoint>()
                var maxSpeed = 0.0
                var maxAcceleration = 0.0


                for (snapshot in dataSnapshot.children) {
                    val speedData = snapshot.getValue(SpeedDataRead::class.java)
                    if (speedData != null) {
                        Log.d("DashBoardActivity", "SpeedData: $speedData") // Log the retrieved data

                        pathPoints.add(GeoPoint(speedData.latitude, speedData.longitude))

                        if (speedData.speed > maxSpeed) {
                            maxSpeed = speedData.speed
                        }
                        if (speedData.acceleration > maxAcceleration) {
                            maxAcceleration = speedData.acceleration
                        }
                    }
                }

                // Cache the metrics
                val sessionMetrics = SessionMetrics(maxSpeed, maxAcceleration)
                metricsRef.setValue(sessionMetrics).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("Firebase", "Session metrics saved successfully.")
                    } else {
                        Log.e("Firebase", "Error saving session metrics: ${task.exception?.message}")
                    }

                    // Update UI with metrics
                    tvMaxSpeed.text = String.format("Max Speed: %.2f km/h", maxSpeed)
                    tvMaxAcceleration.text = String.format("Max Acceleration: %.2f m/s²", maxAcceleration)

                    // Update Map
                    updateMap(pathPoints)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Firebase", "Error loading session data: ${databaseError.message}")
            }
        })
    }



    private fun updateMap(pathPoints: List<GeoPoint>) {
        val polyline = Polyline()
        polyline.setPoints(pathPoints)
        mapView.overlays.clear()
        mapView.overlays.add(polyline)
        if (pathPoints.isNotEmpty()) {
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(pathPoints[0])
        }
    }

    fun convertDoublesToEntries(values: MutableList<Double>): MutableList<Entry> {
        val entries = mutableListOf<Entry>()
        for ((index, value) in values.withIndex()) {
            // Create an Entry where index is the X value and value is the Y value
            entries.add(Entry(index.toFloat(), value.toFloat()))
        }
        return entries
    }
}
