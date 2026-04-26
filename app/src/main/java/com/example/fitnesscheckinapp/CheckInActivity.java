package com.example.fitnesscheckinapp;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CheckInActivity extends AppCompatActivity {

    private Spinner spinnerSportType;
    private TextView tvTimer;
    private Button btnStartEnd;
    private Button btnPause;
    private Button btnReset;
    private EditText etTargetMinutes;
    private EditText etRemark;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long elapsedSeconds = 0;
    private boolean isRunning = false;
    private boolean isFinished = false;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in);

        dbHelper = new DBHelper(this);
        initViews();
        setupSpinner();
        setupTimer();
        setupActionButtons();
        setupBottomNavigation();
        updateTimerText();
        updateButtonState();
    }

    private void initViews() {
        spinnerSportType = findViewById(R.id.spinnerSportType);
        tvTimer = findViewById(R.id.tvTimer);
        btnStartEnd = findViewById(R.id.btnStartEnd);
        btnPause = findViewById(R.id.btnPause);
        btnReset = findViewById(R.id.btnReset);
        etTargetMinutes = findViewById(R.id.etTargetMinutes);
        etRemark = findViewById(R.id.etRemark);
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.sport_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSportType.setAdapter(adapter);
    }

    private void setupTimer() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) {
                    return;
                }
                elapsedSeconds++;
                updateTimerText();
                timerHandler.postDelayed(this, 1000);
            }
        };
    }

    private void setupActionButtons() {
        btnStartEnd.setOnClickListener(v -> {
            if (!isRunning && !isFinished) {
                startTimer();
            } else if (isRunning) {
                endTimer();
            } else {
                startTimer();
            }
        });

        btnPause.setOnClickListener(v -> pauseTimer());
        btnReset.setOnClickListener(v -> resetTimer());
        findViewById(R.id.btnSaveRecord).setOnClickListener(v -> validateAndConfirmSave());
    }

    private void startTimer() {
        isRunning = true;
        isFinished = false;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000);
        updateButtonState();
    }

    private void pauseTimer() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        updateButtonState();
    }

    private void endTimer() {
        isRunning = false;
        isFinished = true;
        timerHandler.removeCallbacks(timerRunnable);
        updateButtonState();
    }

    private void resetTimer() {
        isRunning = false;
        isFinished = false;
        elapsedSeconds = 0;
        timerHandler.removeCallbacks(timerRunnable);
        updateTimerText();
        updateButtonState();
    }

    private void validateAndConfirmSave() {
        if (spinnerSportType.getSelectedItemPosition() == 0) {
            Toast.makeText(this, R.string.toast_select_sport_type, Toast.LENGTH_SHORT).show();
            return;
        }

        if (elapsedSeconds == 0) {
            Toast.makeText(this, R.string.toast_start_training_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String targetMinutesText = etTargetMinutes.getText().toString().trim();
        int targetMinutes = 0;
        if (!TextUtils.isEmpty(targetMinutesText)) {
            try {
                targetMinutes = Integer.parseInt(targetMinutesText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.toast_invalid_target_minutes, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String sportType = spinnerSportType.getSelectedItem().toString();
        int duration = (int) elapsedSeconds;
        int calorie = calculateCalorie(duration);
        String remark = etRemark.getText().toString().trim();

        String message = getString(
                R.string.check_in_confirm_message,
                sportType,
                duration / 60,
                duration % 60,
                calorie,
                TextUtils.isEmpty(remark) ? getString(R.string.check_in_no_remark) : remark
        );

        int finalTargetMinutes = targetMinutes;
        new AlertDialog.Builder(this)
                .setTitle(R.string.check_in_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> saveRecord(finalTargetMinutes))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void saveRecord(int targetMinutes) {
        String sportType = spinnerSportType.getSelectedItem().toString();
        int duration = (int) elapsedSeconds;
        int calorie = calculateCalorie(duration);
        String createTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_SPORT_TYPE, sportType);
        values.put(DBHelper.COLUMN_DURATION, duration);
        values.put(DBHelper.COLUMN_CALORIE, calorie);
        values.put(DBHelper.COLUMN_CREATE_TIME, createTime);

        long result = database.insert(DBHelper.TABLE_SPORT_RECORD, null, values);
        if (result != -1) {
            if (targetMinutes > 0 && duration / 60 >= targetMinutes) {
                Toast.makeText(this, R.string.toast_target_completed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.toast_record_saved_keep_going, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private int calculateCalorie(int duration) {
        return duration / 2;
    }

    private void updateTimerText() {
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void updateButtonState() {
        btnStartEnd.setText(isRunning ? R.string.action_end : R.string.action_start);
        btnPause.setEnabled(isRunning);
    }

    private void setupBottomNavigation() {
        Button btnHome = findViewById(R.id.btnHome);
        Button btnData = findViewById(R.id.btnData);
        Button btnMine = findViewById(R.id.btnMine);

        btnHome.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        btnData.setOnClickListener(v -> startActivity(new Intent(this, DataActivity.class)));
        btnMine.setOnClickListener(v -> startActivity(new Intent(this, MineActivity.class)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
