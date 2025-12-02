package com.example.mapsapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.mapsapp.databinding.ActivityTrilhaBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrilhaActivity extends FragmentActivity implements OnMapReadyCallback, SensorEventListener {

    private GoogleMap mMap;
    private ActivityTrilhaBinding binding;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private boolean isTracking = false;
    private boolean hasUnsavedTrack = false;
    private long startTime = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());

    private final List<LatLng> pathPoints = new ArrayList<>();
    private Location lastLocation;
    private float totalDistance = 0;
    private float maxSpeed = 0;
    private float userWeight = -1.0f;
    private float totalCalories = 0;
    private Polyline pathPolyline;
    private TrilhaDBHelper dbHelper;

    private int mapTypeSetting;
    private int navigationModeSetting;

    private static final int NAVIGATION_MODE_NORTH_UP = 0;
    private static final int NAVIGATION_MODE_COURSE_UP = 1;

    // Sensor-related fields
    private SensorManager sensorManager;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    // Smoothing factor for the low-pass filter.
    private static final float BEARING_SMOOTHING_FACTOR = 0.1f;
    private float smoothedBearing = 0f;

    // High-frequency handler for smooth camera updates
    private final Handler cameraUpdateHandler = new Handler(Looper.getMainLooper());
    private LatLng currentLatLng;


    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = SystemClock.uptimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds %= 60;
            int hours = minutes / 60;
            minutes %= 60;

            binding.tvCronometroValor.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));

            timerHandler.postDelayed(this, 500);
        }
    };

    private final Runnable cameraUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mMap != null && isTracking && navigationModeSetting == NAVIGATION_MODE_COURSE_UP && currentLatLng != null) {
                CameraPosition newPosition = new CameraPosition.Builder()
                        .target(currentLatLng)
                        .zoom(mMap.getCameraPosition().zoom) // Maintain current zoom
                        .bearing(smoothedBearing)
                        .tilt(mMap.getCameraPosition().tilt) // Maintain current tilt
                        .build();
                // Use moveCamera for frequent, non-animated updates
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(newPosition));
            }
            // Schedule the next update
            cameraUpdateHandler.postDelayed(this, 16); // ~60 FPS
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTrilhaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new TrilhaDBHelper(this);
        loadSettings();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        createLocationRequest();
        createLocationCallback();

        binding.buttonIniciar.setOnClickListener(v -> {
            if (isTracking) {
                stopTracking();
            } else if (hasUnsavedTrack) {
                showSaveDialog();
            } else {
                startTracking();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        applyMapSettings();
        if (isTracking && navigationModeSetting == NAVIGATION_MODE_COURSE_UP) {
            startSensorUpdates();
            cameraUpdateHandler.post(cameraUpdateRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location, sensor, and camera updates to save battery
        fusedLocationClient.removeLocationUpdates(locationCallback);
        stopSensorUpdates();
        cameraUpdateHandler.removeCallbacks(cameraUpdateRunnable);

        if (isTracking) {
            // If tracking was active, treat it as being stopped to prompt for saving
            stopTracking();
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        userWeight = prefs.getFloat("peso_salvo", 0f);
        mapTypeSetting = prefs.getInt("mapa_tipo_valor", GoogleMap.MAP_TYPE_NORMAL);
        navigationModeSetting = prefs.getInt("navegacao_modo_valor", NAVIGATION_MODE_NORTH_UP);
    }

    private void applyMapSettings() {
        if (mMap == null) return;
        mMap.setMapType(mapTypeSetting);
        mMap.getUiSettings().setRotateGesturesEnabled(navigationModeSetting != NAVIGATION_MODE_NORTH_UP);
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(1000)
                .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        updateMetrics(location);
                    }
                }
            }
        };
    }

    private void updateMetrics(Location currentLocation) {
        float currentSpeedKmh = currentLocation.getSpeed() * 3.6f;
        if (currentSpeedKmh < 0.5f) currentSpeedKmh = 0.0f;

        if (currentSpeedKmh > maxSpeed) maxSpeed = currentSpeedKmh;

        if (lastLocation != null) {
            totalDistance += lastLocation.distanceTo(currentLocation) / 1000f;
            if (userWeight > 0) {
                float timeDeltaMinutes = (currentLocation.getTime() - lastLocation.getTime()) / (1000f * 60f);
                float calorieBurnPerMinute = currentSpeedKmh * userWeight * 0.0175f;
                totalCalories += calorieBurnPerMinute * timeDeltaMinutes;
            }
        }
        lastLocation = currentLocation;

        // In COURSE_UP mode, camera is handled by the cameraUpdateRunnable.
        // In NORTH_UP mode, we can update it here.
        if (isTracking && navigationModeSetting == NAVIGATION_MODE_NORTH_UP) {
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(17f) // Or maintain zoom: mMap.getCameraPosition().zoom
                    .bearing(0)
                    .tilt(0)
                    .build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }

        pathPoints.add(currentLatLng);
        if (pathPolyline != null) {
            pathPolyline.setPoints(pathPoints);
        }

        binding.tvVelocidadeValor.setText(String.format(Locale.getDefault(), "%.1f km/h", currentSpeedKmh));
        binding.tvVelocidadeMaxValor.setText(String.format(Locale.getDefault(), "%.1f km/h", maxSpeed));
        binding.tvDistanciaValor.setText(String.format(Locale.getDefault(), "%.2f km", totalDistance));

        if (userWeight > 0) {
            binding.tvCaloriasValor.setText(String.format(Locale.getDefault(), "%.0f kcal", totalCalories));
        } else {
            binding.tvCaloriasValor.setText("N/A");
        }
    }

    private void startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        isTracking = true;
        binding.buttonIniciar.setText("Parar");

        // Reset metrics
        totalDistance = 0;
        maxSpeed = 0;
        lastLocation = null;
        totalCalories = 0;
        pathPoints.clear();
        currentLatLng = null;

        if (pathPolyline != null) pathPolyline.remove();
        pathPolyline = mMap.addPolyline(new PolylineOptions().color(Color.BLUE).width(10));

        startTime = SystemClock.uptimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

        if (navigationModeSetting == NAVIGATION_MODE_COURSE_UP) {
            startSensorUpdates();
            cameraUpdateHandler.post(cameraUpdateRunnable);
        }
    }

    private void stopTracking() {
        isTracking = false;

        timerHandler.removeCallbacks(timerRunnable);
        fusedLocationClient.removeLocationUpdates(locationCallback);

        if (navigationModeSetting == NAVIGATION_MODE_COURSE_UP) {
            stopSensorUpdates();
            cameraUpdateHandler.removeCallbacks(cameraUpdateRunnable);
        }

        // Reset bearing for next time if in course up
        if (mMap != null && navigationModeSetting == NAVIGATION_MODE_COURSE_UP) {
             mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(mMap.getCameraPosition()).bearing(0).build()
            ));
        }

        if (pathPoints.size() > 1) { // Only save if there's a path
            hasUnsavedTrack = true;
            binding.buttonIniciar.setText("Salvar");
            showSaveDialog();
        } else {
            binding.buttonIniciar.setText("Iniciar");
            hasUnsavedTrack = false;
        }
    }

    private void showSaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Salvar Trilha");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nome da trilha");
        builder.setView(input);

        builder.setPositiveButton("Salvar", (dialog, which) -> {
            String nomeTrilha = input.getText().toString().trim();
            if (nomeTrilha.isEmpty()) {
                Toast.makeText(this, "O nome da trilha não pode ser vazio.", Toast.LENGTH_SHORT).show();
            } else {
                saveTrilha(nomeTrilha);
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.setNeutralButton("Descartar", (dialog, which) -> {
            hasUnsavedTrack = false;
            binding.buttonIniciar.setText("Iniciar");
            dialog.dismiss();
        });

        builder.show();
    }

    private void saveTrilha(String nomeTrilha) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues trilhaValues = new ContentValues();
        trilhaValues.put(TrilhaDBHelper.COLUMN_TRILHA_NOME, nomeTrilha);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateandTime = sdf.format(new Date());
        trilhaValues.put(TrilhaDBHelper.COLUMN_TRILHA_DATA, currentDateandTime);

        long trilhaId = db.insert(TrilhaDBHelper.TABLE_TRILHAS, null, trilhaValues);

        if (trilhaId != -1) {
            ContentValues detalhesValues = new ContentValues();
            detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_TRILHA_ID, trilhaId);
            detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_DISTANCIA, totalDistance);
            detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_VELOCIDADE_MAX, maxSpeed);
            detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_TEMPO, binding.tvCronometroValor.getText().toString());
            detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_CALORIAS, userWeight > 0 ? totalCalories : -1);

            Gson gson = new Gson();
            String pathJson = gson.toJson(pathPoints);
            detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_PATH, pathJson);

            long detalheId = db.insert(TrilhaDBHelper.TABLE_DETALHES, null, detalhesValues);
            if (detalheId != -1) {
                Toast.makeText(this, "Trilha salva com sucesso!", Toast.LENGTH_SHORT).show();
                hasUnsavedTrack = false;
                binding.buttonIniciar.setText("Iniciar");
            } else {
                Toast.makeText(this, "Erro ao salvar detalhes da trilha.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Erro ao salvar a trilha.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        applyMapSettings();
        checarPermissaoEConfigurarMapa();
    }

    private void checarPermissaoEConfigurarMapa() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            configurarMapaComLocalizacao();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void configurarMapaComLocalizacao() {
        try {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    if (!isTracking) { 
                         currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                    }
                } else {
                    Toast.makeText(TrilhaActivity.this, "Não foi possível obter a localização atual.", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                configurarMapaComLocalizacao();
            } else {
                Toast.makeText(this, "Permissão de localização negada.", Toast.LENGTH_LONG).show();
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-23.550520, -46.633308), 10f)); // São Paulo
            }
        }
    }

    // Sensor-related methods
    private void startSensorUpdates() {
        Sensor rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void stopSensorUpdates() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Can be ignored for now
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            
            float rawBearing = (float) Math.toDegrees(orientationAngles[0]);
            rawBearing = (rawBearing + 360) % 360;

            // Apply low-pass filter to smooth the bearing
            // Calculate the shortest angle difference
            float angleDiff = rawBearing - smoothedBearing;
            if (angleDiff > 180) angleDiff -= 360;
            if (angleDiff < -180) angleDiff += 360;

            smoothedBearing += BEARING_SMOOTHING_FACTOR * angleDiff;
            smoothedBearing = (smoothedBearing + 360) % 360;
        }
    }
}
