package com.example.fitnesscheckinapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class DBHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "fitness_check_in.db";
    public static final int DATABASE_VERSION = 2;

    public static final String TABLE_SPORT_RECORD = "sport_record";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_SPORT_TYPE = "sport_type";
    public static final String COLUMN_DURATION = "duration";
    public static final String COLUMN_CALORIE = "calorie";
    public static final String COLUMN_CREATE_TIME = "create_time";

    public static final String TABLE_USER_INFO = "user_info";
    public static final String COLUMN_USER_ID = "id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_NICKNAME = "nickname";
    public static final String COLUMN_AVATAR_PATH = "avatar_path";
    public static final String COLUMN_USER_CREATE_TIME = "create_time";

    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(buildCreateSportRecordTableSql());
        db.execSQL(buildCreateUserInfoTableSql());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(buildCreateUserInfoTableSql());
        }
    }

    private String buildCreateSportRecordTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_SPORT_RECORD + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_SPORT_TYPE + " TEXT NOT NULL, "
                + COLUMN_DURATION + " INTEGER NOT NULL, "
                + COLUMN_CALORIE + " INTEGER NOT NULL, "
                + COLUMN_CREATE_TIME + " TEXT NOT NULL)";
    }

    private String buildCreateUserInfoTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_USER_INFO + " ("
                + COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, "
                + COLUMN_PASSWORD + " TEXT NOT NULL, "
                + COLUMN_NICKNAME + " TEXT, "
                + COLUMN_AVATAR_PATH + " TEXT, "
                + COLUMN_USER_CREATE_TIME + " TEXT NOT NULL)";
    }
}
