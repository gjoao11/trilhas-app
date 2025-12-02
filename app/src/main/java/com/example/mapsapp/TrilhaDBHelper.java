package com.example.mapsapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class TrilhaDBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "trilhas.db";
    private static final int DATABASE_VERSION = 1;

    // Tabela de Trilhas
    public static final String TABLE_TRILHAS = "trilhas";
    public static final String COLUMN_TRILHA_ID = "_id";
    public static final String COLUMN_TRILHA_NOME = "nome";
    public static final String COLUMN_TRILHA_DATA = "data";

    // Tabela de Detalhes da Trilha
    public static final String TABLE_DETALHES = "detalhes_trilha";
    public static final String COLUMN_DETALHE_ID = "_id";
    public static final String COLUMN_DETALHE_TRILHA_ID = "trilha_id";
    public static final String COLUMN_DETALHE_DISTANCIA = "distancia_total";
    public static final String COLUMN_DETALHE_VELOCIDADE_MAX = "velocidade_maxima";
    public static final String COLUMN_DETALHE_TEMPO = "tempo_total";
    public static final String COLUMN_DETALHE_CALORIAS = "calorias_totais";
    public static final String COLUMN_DETALHE_PATH = "path_pontos"; // Armazenaremos o JSON dos pontos

    private static final String TABLE_CREATE_TRILHAS = 
        "CREATE TABLE " + TABLE_TRILHAS + " (" +
        COLUMN_TRILHA_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_TRILHA_NOME + " TEXT NOT NULL, " +
        COLUMN_TRILHA_DATA + " TEXT NOT NULL);";

    private static final String TABLE_CREATE_DETALHES = 
        "CREATE TABLE " + TABLE_DETALHES + " (" +
        COLUMN_DETALHE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_DETALHE_TRILHA_ID + " INTEGER NOT NULL, " +
        COLUMN_DETALHE_DISTANCIA + " REAL NOT NULL, " +
        COLUMN_DETALHE_VELOCIDADE_MAX + " REAL NOT NULL, " +
        COLUMN_DETALHE_TEMPO + " TEXT NOT NULL, " +
        COLUMN_DETALHE_CALORIAS + " REAL NOT NULL, " +
        COLUMN_DETALHE_PATH + " TEXT NOT NULL, " +
        "FOREIGN KEY(" + COLUMN_DETALHE_TRILHA_ID + ") REFERENCES " + TABLE_TRILHAS + "(" + COLUMN_TRILHA_ID + "));";

    public TrilhaDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE_TRILHAS);
        db.execSQL(TABLE_CREATE_DETALHES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DETALHES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRILHAS);
        onCreate(db);
    }
}