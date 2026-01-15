package com.example.piastcity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

class MyLocationDemoActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val isCreator = intent.getBooleanExtra("isCreator", false)
        val initialLatitude = intent.getDoubleExtra("localization_latitude", 0.0)
        val initialLongitude = intent.getDoubleExtra("localization_longitude", 0.0)

        setContent {
            MapScreen(
                isCreator = isCreator,
                initialPosition = if (initialLatitude != 0.0 && initialLongitude != 0.0) LatLng(initialLatitude, initialLongitude) else null,
                fusedLocationClient = fusedLocationClient
            )
        }
    }
}

@Composable
fun MapScreen(
    isCreator: Boolean,
    initialPosition: LatLng?,
    fusedLocationClient: FusedLocationProviderClient
) {
    val context = LocalContext.current
    val activity = (context as Activity)

    var hasLocationPermission by remember {
        mutableStateOf(hasLocationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions.values.all { it }
        }
    )

    // Stan mapy
    var selectedPosition by remember { mutableStateOf<LatLng?>(initialPosition) }
    val defaultCameraPosition = LatLng(51.1079, 17.0385) // Wrocław
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition ?: defaultCameraPosition, 15f)
    }

    // Uruchom na początku, aby poprosić o uprawnienia
    LaunchedEffect(key1 = true) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission // Włącz warstwę "moja lokalizacja"
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = hasLocationPermission, // Pokaż przycisk "moja lokalizacja"
                    zoomControlsEnabled = true // Pokaż przyciski zoom
                ),
                onMapClick = {
                    if (isCreator) {
                        selectedPosition = it
                    }
                }
            ) {
                // Wyświetl znacznik, jeśli jakaś pozycja jest wybrana/przekazana
                selectedPosition?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = if (isCreator) "Wybrana lokalizacja" else "Lokalizacja wydarzenia"
                    )
                }
            }

            // Przycisk na dole ekranu
            Button(
                onClick = {
                    if (isCreator) {
                        selectedPosition?.let {
                            // Zwróć wynik do poprzedniej aktywności
                            val resultIntent = Intent().apply {
                                putExtra("latitude", it.latitude)
                                putExtra("longitude", it.longitude)
                            }
                            activity.setResult(Activity.RESULT_OK, resultIntent)
                            activity.finish()
                        }
                    } else {
                        // W trybie widza przycisk po prostu zamyka mapę
                        activity.finish()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                // Przycisk jest aktywny tylko w trybie kreatora, jeśli wybrano punkt
                enabled = if (isCreator) selectedPosition != null else true
            ) {
                Text(if (isCreator) "Wybierz tę lokalizację" else "Powrót")
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
