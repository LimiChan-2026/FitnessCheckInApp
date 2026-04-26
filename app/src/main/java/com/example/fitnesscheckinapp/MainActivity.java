package com.example.fitnesscheckinapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";

    private static final int MOCK_TODAY_STEPS = 5680;

    private TextView tvTodayCalorie;
    private TextView tvTodayMinutes;
    private TextView tvTodayCount;
    private TextView tvTodaySteps;
    private TextView tvTotalCount;
    private TextView tvTotalMinutes;
    private TextView tvStreakDays;
    private TextView tvEncouragement;
    private TextView tvLatestRecord;
    private DBHelper dbHelper;
    private String currentUsername = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DBHelper(this);
        initViews();
        setupBottomNavigation();
    }

    private boolean ensureLoginUser() {
        SharedPreferences sp = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        boolean isLogin = sp.getBoolean(KEY_IS_LOGIN, false);
        currentUsername = sp.getString(KEY_USERNAME, "");
        if (!isLogin || TextUtils.isEmpty(currentUsername)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }
        return true;
    }

    private void initViews() {
        tvTodayCalorie = findViewById(R.id.tvTodayCalorie);
        tvTodayMinutes = findViewById(R.id.tvTodayMinutes);
        tvTodayCount = findViewById(R.id.tvTodayCount);
        tvTodaySteps = findViewById(R.id.tvTodaySteps);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvTotalMinutes = findViewById(R.id.tvTotalMinutes);
        tvStreakDays = findViewById(R.id.tvStreakDays);
        tvEncouragement = findViewById(R.id.tvEncouragement);
        tvLatestRecord = findViewById(R.id.tvLatestRecord);
    }

    private void loadHomeData() {
        if (!ensureLoginUser()) {
            return;
        }

        int todayCalorie = 0;
        int todayDurationSeconds = 0;
        int todayCount = 0;
        int totalCount = 0;
        int totalDurationSeconds = 0;
        Set<String> checkInDateSet = new HashSet<>();

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        Cursor cursor = database.query(
                DBHelper.TABLE_SPORT_RECORD,
                new String[]{
                        DBHelper.COLUMN_DURATION,
                        DBHelper.COLUMN_CALORIE,
                        DBHelper.COLUMN_CREATE_TIME,
                        DBHelper.COLUMN_SPORT_TYPE
                },
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{currentUsername},
                null,
                null,
                DBHelper.COLUMN_ID + " DESC"
        );

        while (cursor.moveToNext()) {
            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_DURATION));
            int calorie = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_CALORIE));
            String createTime = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_CREATE_TIME));
            String recordDate = extractDate(createTime);

            totalCount++;
            totalDurationSeconds += duration;
            if (recordDate != null) {
                checkInDateSet.add(recordDate);
            }

            if (todayDate.equals(recordDate)) {
                todayDurationSeconds += duration;
                todayCalorie += calorie;
                todayCount++;
            }
        }
        cursor.close();

        tvTodayCalorie.setText(getString(R.string.home_today_calorie_value, todayCalorie));
        tvTodayMinutes.setText(getString(R.string.home_today_minutes_value, todayDurationSeconds / 60));
        tvTodayCount.setText(getString(R.string.home_today_count_value, todayCount));
        tvTodaySteps.setText(getString(R.string.home_today_steps_value, MOCK_TODAY_STEPS));
        tvTotalCount.setText(getString(R.string.home_total_count_value, totalCount));
        tvTotalMinutes.setText(getString(R.string.home_total_minutes_value, totalDurationSeconds / 60));
        tvStreakDays.setText(getString(R.string.home_streak_days_value, calculateStreakDays(checkInDateSet)));
        tvEncouragement.setText(todayCount > 0
                ? R.string.home_encouragement_done
                : R.string.home_encouragement_not_yet);

        loadLatestRecord(database);
    }

    private void loadLatestRecord(SQLiteDatabase database) {
        Cursor latestCursor = database.query(
                DBHelper.TABLE_SPORT_RECORD,
                null,
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{currentUsername},
                null,
                null,
                DBHelper.COLUMN_ID + " DESC",
                "1"
        );

        if (latestCursor.moveToFirst()) {
            String sportType = latestCursor.getString(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_SPORT_TYPE));
            int duration = latestCursor.getInt(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_DURATION));
            int calorie = latestCursor.getInt(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_CALORIE));
            String createTime = latestCursor.getString(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_CREATE_TIME));

            String latestRecord = getString(
                    R.string.home_latest_record_value,
                    sportType,
                    duration / 60,
                    duration % 60,
                    calorie,
                    createTime
            );
            tvLatestRecord.setText(latestRecord);
        } else {
            tvLatestRecord.setText(R.string.home_latest_record_empty);
        }
        latestCursor.close();
    }

    private String extractDate(String createTime) {
        if (createTime == null || createTime.length() < 10) {
            return null;
        }
        return createTime.substring(0, 10);
    }

    private int calculateStreakDays(Set<String> checkInDateSet) {
        if (checkInDateSet.isEmpty()) {
            return 0;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        int streakDays = 0;

        while (true) {
            String dateKey = dateFormat.format(calendar.getTime());
            if (checkInDateSet.contains(dateKey)) {
                streakDays++;
                calendar.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }

        return streakDays;
    }

    private void setupBottomNavigation() {
        Button btnCheckIn = findViewById(R.id.btnCheckIn);
        Button btnData = findViewById(R.id.btnData);
        Button btnMine = findViewById(R.id.btnMine);

        btnCheckIn.setOnClickListener(v -> startActivity(new Intent(this, CheckInActivity.class)));
        btnData.setOnClickListener(v -> startActivity(new Intent(this, DataActivity.class)));
        btnMine.setOnClickListener(v -> startActivity(new Intent(this, MineActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHomeData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
