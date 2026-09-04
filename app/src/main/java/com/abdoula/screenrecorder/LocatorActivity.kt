package com.abdoula.screenrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class LocatorActivity : AppCompatActivity() {

    private lateinit var coordinatesText: TextView
    private lateinit var updatedAtText: TextView
    private lateinit var openMapsButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var locationManager: LocationManager

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    private val locationListener = LocationListener { location ->
        onLocationReceived(location)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_locator)

        coordinatesText = findViewById(R.id.coordinatesText)
        updatedAtText = findViewById(R.id.updatedAtText)
        openMapsButton = findViewById(R.id.openMapsButton)
        deviceIdText = findViewById(R.id.deviceIdText)

        deviceIdText.text = "ID de cet appareil : ${AnalyticsManager.getDeviceId(this)}"

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        openMapsButton.setOnClickListener {
            val lat = lastLatitude
            val lng = lastLongitude
            if (lat != null && lng != null) {
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            }
        }

        findViewById<Button>(R.id.refreshLocationButton).setOnClickListener {
            requestLocationUpdate()
        }

        requestLocationUpdate()
    }

    private fun requestLocationUpdate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                300
            )
            return
        }

        coordinatesText.text = "Recherche en cours…"

        try {
            val lastKnown = getBestLastKnownLocation()
            if (lastKnown != null) onLocationReceived(lastKnown)

            val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }

            locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener, Looper.getMainLooper())
        } catch (e: Exception) {
            coordinatesText.text = "Position indisponible sur ce téléphone"
        }
    }

    private fun getBestLastKnownLocation(): Location? {
        val providers = locationManager.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (best == null || location.accuracy < best!!.accuracy) best = location
            } catch (e: SecurityException) {
            }
        }
        return best
    }

    private fun onLocationReceived(location: Location) {
        lastLatitude = location.latitude
        lastLongitude = location.longitude

        coordinatesText.text = String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)
        updatedAtText.text = "Mis à jour : ${SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())}"
        openMapsButton.isEnabled = true

        AnalyticsManager.logLocation(this, location.latitude, location.longitude) { success ->
            runOnUiThread {
                if (!success) {
                    Toast.makeText(this, "Position affichée mais pas sauvegardée en ligne", Toast.LENGTH_SHORT).show()
                }
            }
        }

        try { locationManager.removeUpdates(locationListener) } catch (e: Exception) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 300 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocationUpdate()
        } else if (requestCode == 300) {
            coordinatesText.text = "Permission de localisation refusée"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { locationManager.removeUpdates(locationListener) } catch (e: Exception) {}
    }
}