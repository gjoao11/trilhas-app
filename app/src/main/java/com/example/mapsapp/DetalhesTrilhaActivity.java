package com.example.mapsapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.example.mapsapp.databinding.ActivityDetalhesTrilhaBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;

public class DetalhesTrilhaActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityDetalhesTrilhaBinding binding;
    private TrilhaDBHelper dbHelper;
    private long trilhaId;
    private String mapTypeSetting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDetalhesTrilhaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new TrilhaDBHelper(this);
        trilhaId = getIntent().getLongExtra("TRILHA_ID", -1);

        if (trilhaId == -1) {
            Toast.makeText(this, "ID da Trilha inválido!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadSettings();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        carregarDetalhesTrilha();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        applyMapSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        mapTypeSetting = prefs.getString("map_type", "vetorial");
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
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(false);

        applyMapSettings();

        desenharTrilhaNoMapa();
    }

    private void carregarDetalhesTrilha() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(TrilhaDBHelper.TABLE_DETALHES,
                null,
                TrilhaDBHelper.COLUMN_DETALHE_TRILHA_ID + "=?",
                new String[]{String.valueOf(trilhaId)},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            binding.tvVelocidadeValor.setText("--");

            binding.tvCronometroValor.setText(cursor.getString(cursor.getColumnIndexOrThrow(TrilhaDBHelper.COLUMN_DETALHE_TEMPO)));
            binding.tvDistanciaValor.setText(String.format(Locale.getDefault(), "%.2f km", cursor.getFloat(cursor.getColumnIndexOrThrow(TrilhaDBHelper.COLUMN_DETALHE_DISTANCIA))));
            binding.tvVelocidadeMaxValor.setText(String.format(Locale.getDefault(), "%.1f km/h", cursor.getFloat(cursor.getColumnIndexOrThrow(TrilhaDBHelper.COLUMN_DETALHE_VELOCIDADE_MAX))));

            float calorias = cursor.getFloat(cursor.getColumnIndexOrThrow(TrilhaDBHelper.COLUMN_DETALHE_CALORIAS));
            if (calorias >= 0) {
                binding.tvCaloriasValor.setText(String.format(Locale.getDefault(), "%.0f kcal", calorias));
            } else {
                binding.tvCaloriasValor.setText("N/A");
            }

            cursor.close();
        }
    }

    private void desenharTrilhaNoMapa() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(TrilhaDBHelper.TABLE_DETALHES,
                new String[]{TrilhaDBHelper.COLUMN_DETALHE_PATH},
                TrilhaDBHelper.COLUMN_DETALHE_TRILHA_ID + "=?",
                new String[]{String.valueOf(trilhaId)},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String pathJson = cursor.getString(cursor.getColumnIndexOrThrow(TrilhaDBHelper.COLUMN_DETALHE_PATH));
            cursor.close();

            Gson gson = new Gson();
            Type type = new TypeToken<List<LatLng>>() {}.getType();
            List<LatLng> pathPoints = gson.fromJson(pathJson, type);

            if (mMap != null && pathPoints != null && !pathPoints.isEmpty()) {
                mMap.addPolyline(new PolylineOptions().addAll(pathPoints).color(Color.BLUE).width(10));

                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (LatLng point : pathPoints) {
                    builder.include(point);
                }
                LatLngBounds bounds = builder.build();
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            }
        }
    }
}