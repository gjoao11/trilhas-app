package com.example.mapsapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.GoogleMap;

public class ConfigActivity extends AppCompatActivity {
    private SharedPreferences sharedPreferences;
    private EditText inputPeso;
    private EditText inputAltura;
    private RadioGroup radioGenero;
    private EditText inputNascimento;
    private RadioGroup radioMap;
    private RadioGroup radioNavigation;

    private static final int NAV_MODE_NORTH_UP = 0;
    private static final int NAV_MODE_COURSE_UP = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        setTitle("Configurações");

        sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = sharedPreferences.edit();

        inputPeso = findViewById(R.id.input_peso);
        inputAltura = findViewById(R.id.input_altura);
        radioGenero = findViewById(R.id.RadioGroupSex);
        inputNascimento = findViewById(R.id.input_birth);

        radioMap = findViewById(R.id.RadioGroupMap);

        radioNavigation = findViewById(R.id.RadioGroupNavigation);

        float savedPeso = sharedPreferences.getFloat("peso_salvo", 0f);
        float savedAltura = sharedPreferences.getFloat("altura_salvo", 0f);
        String savedNascimento = sharedPreferences.getString("nascimento_salvo", "");

        if(savedPeso != 0) inputPeso.setText(String.valueOf(savedPeso));
        if (savedAltura !=0 ) inputAltura.setText(String.valueOf(savedAltura));
        inputNascimento.setText(savedNascimento);

        int generoIdSalvo = sharedPreferences.getInt("genero_selecionado", -1);
        if (generoIdSalvo != -1 && radioGenero != null) {
            radioGenero.check(generoIdSalvo);
        }

        int mapaIdSalvo = sharedPreferences.getInt("mapa_id_selecionado", -1);
        if (mapaIdSalvo != -1 && radioMap != null) {
            radioMap.check(mapaIdSalvo);
        }

        int navegacaoIdSalva = sharedPreferences.getInt("navegacao_id_selecionada", -1);
        if (navegacaoIdSalva != -1 && radioNavigation != null) {
            radioNavigation.check(navegacaoIdSalva);
        }



        if (radioGenero != null) {
            radioGenero.setOnCheckedChangeListener((group, checkedId) -> {
                e.putInt("genero_selecionado", checkedId);
                e.apply();
            });
        }

        if (radioMap != null) {
            radioMap.setOnCheckedChangeListener((group, checkedId) -> {
                int mapType;
                if(checkedId == R.id.input_sat){
                    mapType = GoogleMap.MAP_TYPE_SATELLITE;
                } else {
                    mapType = GoogleMap.MAP_TYPE_NORMAL;
                }

                // Salva o ID do RadioButton para que a tela saiba qual marcar depois
                e.putInt("mapa_id_selecionado", checkedId);
                // Salva o valor da configuração (MAP_TYPE_NORMAL ou MAP_TYPE_SATELLITE) para uso no mapa
                e.putInt("mapa_tipo_valor", mapType);
                e.apply();
            });
        }

        if (radioNavigation != null) {
            radioNavigation.setOnCheckedChangeListener((group, checkedId) -> {
                int navMode = NAV_MODE_NORTH_UP;

                if (checkedId == R.id.input_north) {
                    navMode = NAV_MODE_NORTH_UP;
                } else if (checkedId == R.id.input_course) {
                    navMode = NAV_MODE_COURSE_UP;
                }

                // Salva o ID do RadioButton para que a tela saiba qual marcar depois
                e.putInt("navegacao_id_selecionada", checkedId);
                // Salva o valor da configuração (0 ou 1) para uso no mapa
                e.putInt("navegacao_modo_valor", navMode);
                e.apply();
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor e = sharedPreferences.edit();

        String pesoStr = inputPeso.getText().toString();
        if (!pesoStr.isEmpty()) {
            try {
                float pesoFloat = Float.parseFloat(pesoStr);
                e.putFloat("peso_salvo", pesoFloat);
            } catch (NumberFormatException ignored) {
                // Se falhar na conversão (ex: usuário digitou texto), ignora a alteração
            }
        }

        String alturaStr = inputAltura.getText().toString();
        if (!alturaStr.isEmpty()) {
            try {
                float alturaFloat = Float.parseFloat(alturaStr);
                e.putFloat("altura_salvo", alturaFloat);
            } catch (NumberFormatException ignored) {
                // Se falhar na conversão, ignora a alteração
            }
        }

        e.putString("nascimento_salvo", inputNascimento.getText().toString());

        e.apply();
    }
}