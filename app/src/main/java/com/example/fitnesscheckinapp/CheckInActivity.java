package com.example.fitnesscheckinapp;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";

    private static final int STATE_IDLE = 0;
    private static final int STATE_TIMING = 1;
    private static final int STATE_PAUSED = 2;
    private static final int STATE_SAVING = 3;

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
    private int currentState = STATE_IDLE;
    private boolean isSavingDialogShowing = false;
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
        updateUiState();
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
                if (currentState != STATE_TIMING) {
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
            if (currentState == STATE_IDLE) {
                startTimer();
            } else if (currentState == STATE_TIMING || currentState == STATE_PAUSED) {
                endTimer();
            }
        });

        btnPause.setOnClickListener(v -> {
            if (currentState == STATE_TIMING) {
                pauseTimer();
            } else if (currentState == STATE_PAUSED) {
                resumeTimer();
            }
        });

        btnReset.setOnClickListener(v -> resetTraining(true));
    }

    private void startTimer() {
        if (spinnerSportType.getSelectedItemPosition() == 0) {
            Toast.makeText(this, R.string.toast_select_sport_type, Toast.LENGTH_SHORT).show();
            return;
        }
        currentState = STATE_TIMING;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000);
        updateUiState();
    }

    private void pauseTimer() {
        currentState = STATE_PAUSED;
        timerHandler.removeCallbacks(timerRunnable);
        updateUiState();
    }

    private void resumeTimer() {
        currentState = STATE_TIMING;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000);
        updateUiState();
    }

    private void endTimer() {
        timerHandler.removeCallbacks(timerRunnable);

        if (elapsedSeconds <= 0) {
            Toast.makeText(this, R.string.toast_duration_cannot_be_zero, Toast.LENGTH_SHORT).show();
            resetTraining(false);
            return;
        }

        int targetMinutes = parseTargetMinutes();
        if (targetMinutes < 0) {
            currentState = STATE_PAUSED;
            updateUiState();
            return;
        }

        currentState = STATE_SAVING;
        updateUiState();

        boolean targetReached = targetMinutes <= 0 || elapsedSeconds >= targetMinutes * 60L;
        if (targetMinutes > 0 && !targetReached) {
            showSaveConfirmDialog(
                    R.string.check_in_target_not_reached_title,
                    R.string.check_in_target_not_reached_message,
                    R.string.action_confirm_save
            );
        } else {
            showSaveConfirmDialog(
                    R.string.check_in_end_training_title,
                    R.string.check_in_end_training_message,
                    R.string.action_save
            );
        }
    }

    private void showSaveConfirmDialog(int titleRes, int messageRes, int positiveRes) {
        isSavingDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(positiveRes, (dialog, which) -> saveRecord())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> {
                    Toast.makeText(this, R.string.toast_save_cancelled, Toast.LENGTH_SHORT).show();
                    resetTraining(false);
                })
                .setOnCancelListener(dialog -> {
                    if (isSavingDialogShowing) {
                        Toast.makeText(this, R.string.toast_save_cancelled, Toast.LENGTH_SHORT).show();
                        resetTraining(false);
                    }
                })
                .show();
    }

    private int parseTargetMinutes() {
        String targetMinutesText = etTargetMinutes.getText().toString().trim();
        if (TextUtils.isEmpty(targetMinutesText)) {
            return 0;
        }
        try {
            return Integer.parseInt(targetMinutesText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.toast_invalid_target_minutes, Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    private void saveRecord() {
        isSavingDialogShowing = false;

        SharedPreferences sp = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        boolean isLogin = sp.getBoolean(KEY_IS_LOGIN, false);
        String username = sp.getString(KEY_USERNAME, "");
        if (!isLogin || TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.toast_please_login_first, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String sportType = spinnerSportType.getSelectedItem().toString();
        int duration = (int) elapsedSeconds;
        int calorie = calculateCalorie(duration);
        String remark = etRemark.getText().toString().trim();
        String createTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_SPORT_TYPE, sportType);
        values.put(DBHelper.COLUMN_DURATION, duration);
        values.put(DBHelper.COLUMN_CALORIE, calorie);
        values.put(DBHelper.COLUMN_CREATE_TIME, createTime);
        values.put(DBHelper.COLUMN_USERNAME, username);
        values.put(DBHelper.COLUMN_REMARK, remark);

        long result = database.insert(DBHelper.TABLE_SPORT_RECORD, null, values);
        if (result != -1) {
            Toast.makeText(this, R.string.toast_training_save_success, Toast.LENGTH_SHORT).show();
            resetTraining(true);
        } else {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show();
            resetTraining(false);
        }
    }

    private void resetTraining(boolean clearSportType) {
        timerHandler.removeCallbacks(timerRunnable);
        elapsedSeconds = 0;
        currentState = STATE_IDLE;
        isSavingDialogShowing = false;
        updateTimerText();
        etTargetMinutes.setText("");
        etRemark.setText("");
        if (clearSportType) {
            spinnerSportType.setSelection(0);
        }
        updateUiState();
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

    private void updateUiState() {
        boolean editingEnabled = currentState == STATE_IDLE;
        spinnerSportType.setEnabled(editingEnabled);
        etTargetMinutes.setEnabled(editingEnabled);
        etRemark.setEnabled(editingEnabled);
        btnReset.setEnabled(currentState != STATE_SAVING);

        if (currentState == STATE_IDLE) {
            btnStartEnd.setText(R.string.action_start);
            btnStartEnd.setEnabled(true);
            btnPause.setText(R.string.action_pause);
            btnPause.setEnabled(false);
        } else if (currentState == STATE_TIMING) {
            btnStartEnd.setText(R.string.action_end);
            btnStartEnd.setEnabled(true);
            btnPause.setText(R.string.action_pause);
            btnPause.setEnabled(true);
        } else if (currentState == STATE_PAUSED) {
            btnStartEnd.setText(R.string.action_end);
            btnStartEnd.setEnabled(true);
            btnPause.setText(R.string.action_resume);
            btnPause.setEnabled(true);
        } else {
            btnStartEnd.setText(R.string.action_end);
            btnStartEnd.setEnabled(false);
            btnPause.setText(R.string.action_pause);
            btnPause.setEnabled(false);
        }
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
