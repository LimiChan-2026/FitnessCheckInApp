package com.example.fitnesscheckinapp;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_TODAY = 1;
    private static final int FILTER_LAST_7_DAYS = 2;

    private TextView tvTotalCount;
    private TextView tvTotalMinutes;
    private TextView tvTotalCalorie;
    private TextView tvEmptyView;
    private RecyclerView rvRecords;
    private Button btnFilterAll;
    private Button btnFilterToday;
    private Button btnFilterLast7Days;
    private DBHelper dbHelper;
    private final List<SportRecord> recordList = new ArrayList<>();
    private RecordAdapter recordAdapter;
    private int currentFilter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        dbHelper = new DBHelper(this);
        initViews();
        setupRecyclerView();
        setupFilterButtons();
        setupBottomNavigation();
        loadRecords();
    }

    private void initViews() {
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvTotalMinutes = findViewById(R.id.tvTotalMinutes);
        tvTotalCalorie = findViewById(R.id.tvTotalCalorie);
        tvEmptyView = findViewById(R.id.tvEmptyView);
        rvRecords = findViewById(R.id.rvRecords);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterLast7Days = findViewById(R.id.btnFilterLast7Days);
    }

    private void setupRecyclerView() {
        recordAdapter = new RecordAdapter(recordList, this::showDeleteDialog);
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(recordAdapter);
    }

    private void setupFilterButtons() {
        btnFilterAll.setOnClickListener(v -> {
            currentFilter = FILTER_ALL;
            loadRecords();
        });
        btnFilterToday.setOnClickListener(v -> {
            currentFilter = FILTER_TODAY;
            loadRecords();
        });
        btnFilterLast7Days.setOnClickListener(v -> {
            currentFilter = FILTER_LAST_7_DAYS;
            loadRecords();
        });
        updateFilterButtonState();
    }

    private void loadRecords() {
        recordList.clear();

        int totalCount = 0;
        int totalDurationSeconds = 0;
        int totalCalorie = 0;

        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor cursor = database.query(
                DBHelper.TABLE_SPORT_RECORD,
                null,
                null,
                null,
                null,
                null,
                DBHelper.COLUMN_ID + " DESC"
        );

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        String last7DaysStart = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_ID));
            String sportType = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_SPORT_TYPE));
            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_DURATION));
            int calorie = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_CALORIE));
            String createTime = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_CREATE_TIME));
            String datePart = createTime.substring(0, 10);

            if (!matchesFilter(datePart, todayDate, last7DaysStart)) {
                continue;
            }

            recordList.add(new SportRecord(id, sportType, duration, calorie, createTime));
            totalCount++;
            totalDurationSeconds += duration;
            totalCalorie += calorie;
        }
        cursor.close();

        tvTotalCount.setText(getString(R.string.summary_total_count_value, totalCount));
        tvTotalMinutes.setText(getString(R.string.summary_total_minutes_value, totalDurationSeconds / 60));
        tvTotalCalorie.setText(getString(R.string.summary_total_calorie_value, totalCalorie));

        if (recordList.isEmpty()) {
            tvEmptyView.setVisibility(View.VISIBLE);
            rvRecords.setVisibility(View.GONE);
        } else {
            tvEmptyView.setVisibility(View.GONE);
            rvRecords.setVisibility(View.VISIBLE);
        }

        updateFilterButtonState();
        recordAdapter.notifyDataSetChanged();
    }

    private boolean matchesFilter(String datePart, String todayDate, String last7DaysStart) {
        if (currentFilter == FILTER_TODAY) {
            return todayDate.equals(datePart);
        }
        if (currentFilter == FILTER_LAST_7_DAYS) {
            return datePart.compareTo(last7DaysStart) >= 0 && datePart.compareTo(todayDate) <= 0;
        }
        return true;
    }

    private void updateFilterButtonState() {
        btnFilterAll.setEnabled(currentFilter != FILTER_ALL);
        btnFilterToday.setEnabled(currentFilter != FILTER_TODAY);
        btnFilterLast7Days.setEnabled(currentFilter != FILTER_LAST_7_DAYS);
    }

    private void showDeleteDialog(SportRecord record) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.data_delete_title)
                .setMessage(getString(R.string.data_delete_message, record.getSportType()))
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> deleteRecord(record))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteRecord(SportRecord record) {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.delete(
                DBHelper.TABLE_SPORT_RECORD,
                DBHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(record.getId())}
        );
        loadRecords();
    }

    private void setupBottomNavigation() {
        Button btnHome = findViewById(R.id.btnHome);
        Button btnCheckIn = findViewById(R.id.btnCheckIn);
        Button btnMine = findViewById(R.id.btnMine);

        btnHome.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        btnCheckIn.setOnClickListener(v -> startActivity(new Intent(this, CheckInActivity.class)));
        btnMine.setOnClickListener(v -> startActivity(new Intent(this, MineActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecords();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
