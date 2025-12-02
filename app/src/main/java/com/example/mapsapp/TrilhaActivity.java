package com.example.mapsapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
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

public class TrilhaActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityTrilhaBinding binding;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private boolean isTracking = false;
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

    private String mapTypeSetting;
    private String navigationModeSetting;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTrilhaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new TrilhaDBHelper(this);

        loadSettings();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        createLocationRequest();
        createLocationCallback();

        binding.buttonIniciar.setOnClickListener(v -> {
            if (!isTracking) {
                startTracking();
            } else {
                stopTracking();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        applyMapSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        userWeight = prefs.getFloat("user_weight", -1.0f);
        mapTypeSetting = prefs.getString("map_type", "vetorial");
        navigationModeSetting = prefs.getString("navigation_mode", "north_up");
    }

    private void applyMapSettings() {
        if (mMap == null) {
            return;
        }

        if ("satelite".equals(mapTypeSetting)) {
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else {
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }

        if ("north_up".equals(navigationModeSetting)) {
            mMap.getUiSettings().setRotateGesturesEnabled(false);
        } else {
            mMap.getUiSettings().setRotateGesturesEnabled(true);
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        updateMetrics(location);
                    }
                }
            }
        };
    }

    private void updateMetrics(Location currentLocation) {
        float currentSpeedKmh = currentLocation.getSpeed() * 3.6f;

        if (currentSpeedKmh < 0.5f) {
            currentSpeedKmh = 0.0f;
        }

        if (currentSpeedKmh > maxSpeed) {
            maxSpeed = currentSpeedKmh;
        }

        if (lastLocation != null && currentSpeedKmh > 0) {
            totalDistance += lastLocation.distanceTo(currentLocation) / 1000f;

            if (userWeight > 0) {
                float timeDeltaMinutes = (currentLocation.getTime() - lastLocation.getTime()) / (1000f * 60f);
                float calorieBurnPerMinute = currentSpeedKmh * userWeight * 0.0175f;
                totalCalories += calorieBurnPerMinute * timeDeltaMinutes;
            }
        }
        lastLocation = currentLocation;

        if (isTracking) {
            CameraPosition.Builder cameraBuilder = new CameraPosition.Builder()
                    .target(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()))
                    .zoom(17f);

            if ("course_up".equals(navigationModeSetting) && currentLocation.hasBearing() && currentLocation.getSpeed() > 0.5) {
                cameraBuilder.bearing(currentLocation.getBearing());
            } else {
                cameraBuilder.bearing(0);
            }
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraBuilder.build()));
        }

        pathPoints.add(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()));
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

        totalDistance = 0;
        maxSpeed = 0;
        lastLocation = null;
        totalCalories = 0;
        pathPoints.clear();
        if (pathPolyline != null) {
            pathPolyline.remove();
        }
        pathPolyline = mMap.addPolyline(new PolylineOptions().color(Color.BLUE).width(10).addAll(pathPoints));

        startTime = SystemClock.uptimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopTracking() {
        isTracking = false;
        binding.buttonIniciar.setText("Iniciar");
        timerHandler.removeCallbacks(timerRunnable);
        fusedLocationClient.removeLocationUpdates(locationCallback);

        showSaveDialog();
    }

    private void showSaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Salvar Trilha");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nome da trilha");
        builder.setView(input);

        builder.setPositiveButton("Salvar", (dialog, which) -> {
            String nomeTrilha = input.getText().toString();
            if (nomeTrilha.isEmpty()) {
                Toast.makeText(this, "O nome da trilha não pode ser vazio.", Toast.LENGTH_SHORT).show();
                return;
            }
            saveTrilha(nomeTrilha);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

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
            
            if (userWeight > 0) {
                detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_CALORIAS, totalCalories);
            } else {
                detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_CALORIAS, -1);
            }
            
            Gson gson = new Gson();
            String pathJson = gson.toJson(pathPoints);
            detalhesValues.put(TrilhaDBHelper.COLUMN_DETALHE_PATH, pathJson);

            long detalheId = db.insert(TrilhaDBHelper.TABLE_DETALHES, null, detalhesValues);
            if (detalheId != -1) {
                Toast.makeText(this, "Trilha salva com sucesso!", Toast.LENGTH_SHORT).show();
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
                    LatLng minhaLocalizacao = new LatLng(location.getLatitude(), location.getLongitude());
                    if (!isTracking) { 
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(minhaLocalizacao, 15f));
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
                LatLng locationPadrao = new LatLng(-23.550520, -46.633308); // São Paulo
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locationPadrao, 10f));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isTracking) {
            stopTracking();
        }
    }
}