package com.example.mapsapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ConfigActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private EditText inputPeso;
    private RadioGroup radioMap;
    private RadioGroup radioNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        setTitle("Configurações");

        sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        inputPeso = findViewById(R.id.input_peso);
        radioMap = findViewById(R.id.RadioGroupMap);
        radioNavigation = findViewById(R.id.RadioGroupNavigation);

        loadSettings();

        findViewById(R.id.button_save).setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        float weight = sharedPreferences.getFloat("user_weight", -1.0f);
        if (weight > 0) {
            inputPeso.setText(String.valueOf(weight));
        }

        String mapType = sharedPreferences.getString("map_type", "vetorial");
        if ("satelite".equals(mapType)) {
            radioMap.check(R.id.radio_satelite);
        } else {
            radioMap.check(R.id.radio_vetorial);
        }

        String navigationMode = sharedPreferences.getString("navigation_mode", "north_up");
        if ("course_up".equals(navigationMode)) {
            radioNavigation.check(R.id.radio_course_up);
        } else {
            radioNavigation.check(R.id.radio_north_up);
        }
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        try {
            float weight = Float.parseFloat(inputPeso.getText().toString());
            editor.putFloat("user_weight", weight);
        } catch (NumberFormatException e) {
            editor.putFloat("user_weight", -1.0f);
        }

        int selectedMapTypeId = radioMap.getCheckedRadioButtonId();
        if (selectedMapTypeId == R.id.radio_satelite) {
            editor.putString("map_type", "satelite");
        } else {
            editor.putString("map_type", "vetorial");
        }

        int selectedNavigationId = radioNavigation.getCheckedRadioButtonId();
        if (selectedNavigationId == R.id.radio_course_up) {
            editor.putString("navigation_mode", "course_up");
        } else {
            editor.putString("navigation_mode", "north_up");
        }

        editor.apply();
        Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
