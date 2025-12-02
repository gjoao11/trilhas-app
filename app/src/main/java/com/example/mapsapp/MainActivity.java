package com.example.mapsapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button buttonConfig = findViewById(R.id.button_config);
        Button buttonRegistrarTrilha = findViewById(R.id.button_registrar_trilha);
        Button buttonVisualizarTrilhas = findViewById(R.id.button_visualizar_trilhas);
        Button buttonCreditos = findViewById(R.id.button_creditos);

        buttonConfig.setOnClickListener(v -> {
            Intent i = new Intent(this, ConfigActivity.class);
            startActivity(i);
        });

        buttonRegistrarTrilha.setOnClickListener(v -> {
            Intent i = new Intent(this, TrilhaActivity.class);
            startActivity(i);
        });

        buttonVisualizarTrilhas.setOnClickListener(v -> {
            Intent i = new Intent(this, TrilhasSalvasActivity.class);
            startActivity(i);
        });

        buttonCreditos.setOnClickListener(v -> {
            Intent i = new Intent(this, CreditsActivity.class);
            startActivity(i);
        });
    }
}