package com.example.mapsapp;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class TrilhasSalvasActivity extends AppCompatActivity {

    private TrilhaDBHelper dbHelper;
    private SQLiteDatabase database;
    private ListView listViewTrilhas;
    private SimpleCursorAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trilhas_salvas);

        dbHelper = new TrilhaDBHelper(this);
        database = dbHelper.getReadableDatabase();
        listViewTrilhas = findViewById(R.id.listview_trilhas);

        listViewTrilhas.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(TrilhasSalvasActivity.this, DetalhesTrilhaActivity.class);
            intent.putExtra("TRILHA_ID", id);
            startActivity(intent);
        });

        listViewTrilhas.setOnItemLongClickListener((parent, view, position, id) -> {
            showEditDialog(id);
            return true;
        });
    }

    private void showEditDialog(long trilhaId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Nome da Trilha");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Novo nome da trilha");
        builder.setView(input);

        builder.setPositiveButton("Salvar", (dialog, which) -> {
            String novoNome = input.getText().toString();
            if (novoNome.isEmpty()) {
                Toast.makeText(this, "O nome não pode ser vazio.", Toast.LENGTH_SHORT).show();
                return;
            }
            atualizarNomeTrilha(trilhaId, novoNome);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void atualizarNomeTrilha(long trilhaId, String novoNome) {
        ContentValues values = new ContentValues();
        values.put(TrilhaDBHelper.COLUMN_TRILHA_NOME, novoNome);

        int rowsAffected = database.update(TrilhaDBHelper.TABLE_TRILHAS, values, TrilhaDBHelper.COLUMN_TRILHA_ID + "=?", new String[]{String.valueOf(trilhaId)});

        if (rowsAffected > 0) {
            Toast.makeText(this, "Nome atualizado com sucesso!", Toast.LENGTH_SHORT).show();
            carregarTrilhas(); // Recarrega a lista para mostrar a alteração
        } else {
            Toast.makeText(this, "Erro ao atualizar o nome.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarTrilhas();
    }

    private void carregarTrilhas() {
        String[] from = {TrilhaDBHelper.COLUMN_TRILHA_NOME, TrilhaDBHelper.COLUMN_TRILHA_DATA};
        int[] to = {android.R.id.text1, android.R.id.text2};

        Cursor cursor = database.query(TrilhaDBHelper.TABLE_TRILHAS, 
            new String[]{TrilhaDBHelper.COLUMN_TRILHA_ID, TrilhaDBHelper.COLUMN_TRILHA_NOME, TrilhaDBHelper.COLUMN_TRILHA_DATA}, 
            null, null, null, null, TrilhaDBHelper.COLUMN_TRILHA_DATA + " DESC");

        adapter = new SimpleCursorAdapter(this, 
            android.R.layout.simple_list_item_2, 
            cursor, 
            from, 
            to, 
            0);

        listViewTrilhas.setAdapter(adapter);
    }
}