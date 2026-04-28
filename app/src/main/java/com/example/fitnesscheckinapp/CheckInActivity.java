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

/**
 * 运动打卡页面：实现运动计时、运动类型选择、目标设定及运动记录保存功能
 */
public class CheckInActivity extends AppCompatActivity {

    // SharedPreferences 相关常量，用于读取登录用户信息
    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";

    // 运动计时的四种状态
    private static final int STATE_IDLE = 0;    // 空闲/停止状态
    private static final int STATE_TIMING = 1;  // 正在计时状态
    private static final int STATE_PAUSED = 2;  // 暂停状态
    private static final int STATE_SAVING = 3;  // 正在保存状态

    // UI 控件变量
    private Spinner spinnerSportType;    // 运动类型下拉选择框
    private TextView tvTimer;           // 计时显示文本
    private Button btnStartEnd;         // 开始/结束按钮
    private Button btnPause;            // 暂停/继续按钮
    private Button btnReset;            // 重置按钮
    private EditText etTargetMinutes;   // 目标时长输入框
    private EditText etRemark;          // 备注输入框

    // 计时器相关逻辑变量
    private Handler timerHandler;       // 用于定时发送任务的处理器
    private Runnable timerRunnable;     // 计时任务，每秒执行一次
    private long elapsedSeconds = 0;    // 已累计运动的秒数
    private int currentState = STATE_IDLE; // 当前运动状态
    private boolean isSavingDialogShowing = false; // 标记保存对话框是否正在显示

    private DBHelper dbHelper;          // 数据库辅助类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in);

        // 初始化数据库助手
        dbHelper = new DBHelper(this);
        
        // 执行初始化逻辑
        initViews();               // 绑定控件
        setupSpinner();            // 初始化下拉列表适配器
        setupTimer();              // 初始化计时器逻辑
        setupActionButtons();      // 设置按钮点击事件
        setupBottomNavigation();   // 设置底部导航跳转
        
        // 初始 UI 刷新
        updateTimerText();
        updateUiState();
    }

    /**
     * 绑定布局中的 UI 控件
     */
    private void initViews() {
        spinnerSportType = findViewById(R.id.spinnerSportType);
        tvTimer = findViewById(R.id.tvTimer);
        btnStartEnd = findViewById(R.id.btnStartEnd);
        btnPause = findViewById(R.id.btnPause);
        btnReset = findViewById(R.id.btnReset);
        etTargetMinutes = findViewById(R.id.etTargetMinutes);
        etRemark = findViewById(R.id.etRemark);
    }

    /**
     * 设置运动类型下拉列表的适配器
     * 从资源文件 R.array.sport_types 中加载选项
     */
    private void setupSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.sport_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSportType.setAdapter(adapter);
    }

    /**
     * 初始化计时器逻辑
     * 使用 Handler 和 Runnable 实现每秒一次的循环调用
     */
    private void setupTimer() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState != STATE_TIMING) {
                    return; // 如果不是计时状态，则停止循环
                }
                elapsedSeconds++;   // 秒数自增
                updateTimerText();  // 更新界面显示
                timerHandler.postDelayed(this, 1000); // 1秒后再次执行
            }
        };
    }

    /**
     * 设置各功能按钮的点击事件监听器
     */
    private void setupActionButtons() {
        // “开始/结束”按钮逻辑
        btnStartEnd.setOnClickListener(v -> {
            if (currentState == STATE_IDLE) {
                startTimer(); // 当前为空闲，点击则开始
            } else if (currentState == STATE_TIMING || currentState == STATE_PAUSED) {
                endTimer();   // 正在运动，点击则结束
            }
        });

        // “暂停/继续”按钮逻辑
        btnPause.setOnClickListener(v -> {
            if (currentState == STATE_TIMING) {
                pauseTimer(); // 正在计时，点击则暂停
            } else if (currentState == STATE_PAUSED) {
                resumeTimer(); // 已暂停，点击则继续
            }
        });

        // “重置”按钮逻辑
        btnReset.setOnClickListener(v -> resetTraining(true));
    }

    /**
     * 开始计时逻辑
     */
    private void startTimer() {
        // 校验是否选择了运动类型
        if (spinnerSportType.getSelectedItemPosition() == 0) {
            Toast.makeText(this, R.string.toast_select_sport_type, Toast.LENGTH_SHORT).show();
            return;
        }
        currentState = STATE_TIMING;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000); // 启动循环
        updateUiState();
    }

    /**
     * 暂停计时逻辑
     */
    private void pauseTimer() {
        currentState = STATE_PAUSED;
        timerHandler.removeCallbacks(timerRunnable); // 停止循环
        updateUiState();
    }

    /**
     * 继续计时逻辑
     */
    private void resumeTimer() {
        currentState = STATE_TIMING;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000); // 恢复循环
        updateUiState();
    }

    /**
     * 结束计时逻辑：处理计时结束后的确认保存逻辑
     */
    private void endTimer() {
        timerHandler.removeCallbacks(timerRunnable);

        // 如果时长为0，不允许保存
        if (elapsedSeconds <= 0) {
            Toast.makeText(this, R.string.toast_duration_cannot_be_zero, Toast.LENGTH_SHORT).show();
            resetTraining(false);
            return;
        }

        // 解析目标时长
        int targetMinutes = parseTargetMinutes();
        if (targetMinutes < 0) {
            currentState = STATE_PAUSED; // 解析失败（格式错误）时回到暂停状态
            updateUiState();
            return;
        }

        currentState = STATE_SAVING;
        updateUiState();

        // 检查是否达到设定的目标时长
        boolean targetReached = targetMinutes <= 0 || elapsedSeconds >= targetMinutes * 60L;
        if (targetMinutes > 0 && !targetReached) {
            // 未达到目标，弹出提醒对话框
            showSaveConfirmDialog(
                    R.string.check_in_target_not_reached_title,
                    R.string.check_in_target_not_reached_message,
                    R.string.action_confirm_save
            );
        } else {
            // 达到目标或未设置目标，弹出常规保存对话框
            showSaveConfirmDialog(
                    R.string.check_in_end_training_title,
                    R.string.check_in_end_training_message,
                    R.string.action_save
            );
        }
    }

    /**
     * 显示保存确认对话框
     */
    private void showSaveConfirmDialog(int titleRes, int messageRes, int positiveRes) {
        isSavingDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(positiveRes, (dialog, which) -> saveRecord()) // 确认保存
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

    /**
     * 解析用户输入的目标时长（分钟）
     * @return 分钟数，若为空返回0，若格式非法返回-1
     */
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

    /**
     * 数据库保存逻辑：将本次运动记录写入数据库
     */
    private void saveRecord() {
        isSavingDialogShowing = false;

        // 1. 获取当前登录用户信息
        SharedPreferences sp = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        boolean isLogin = sp.getBoolean(KEY_IS_LOGIN, false);
        String username = sp.getString(KEY_USERNAME, "");
        if (!isLogin || TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.toast_please_login_first, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 2. 准备要保存的数据
        String sportType = spinnerSportType.getSelectedItem().toString();
        int duration = (int) elapsedSeconds;
        int calorie = calculateCalorie(duration); // 计算卡路里
        String remark = etRemark.getText().toString().trim();
        String createTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date()); // 生成当前时间戳

        // 3. 执行数据库插入操作
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
            resetTraining(true); // 保存成功，清空所有输入
        } else {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show();
            resetTraining(false); // 保存失败，保留输入内容供重试
        }
    }

    /**
     * 重置界面及计时器
     * @param clearSportType 是否同时重置运动类型下拉框
     */
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

    /**
     * 简单的卡路里计算逻辑
     * @param duration 秒
     * @return 卡路里值
     */
    private int calculateCalorie(int duration) {
        return duration / 2; // 每运动2秒消耗1卡路里
    }

    /**
     * 将秒数格式化并更新到计时文本框（HH:mm:ss）
     */
    private void updateTimerText() {
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    /**
     * 根据当前状态（空闲、计时中、暂停等）更新控件的可操作性和文案
     */
    private void updateUiState() {
        boolean editingEnabled = currentState == STATE_IDLE;
        // 运动开始后，禁止修改运动类型和目标时长
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
            // 保存状态中，禁用按钮
            btnStartEnd.setText(R.string.action_end);
            btnStartEnd.setEnabled(false);
            btnPause.setText(R.string.action_pause);
            btnPause.setEnabled(false);
        }
    }

    /**
     * 设置底部导航栏各按钮的跳转逻辑
     */
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
        // 销毁 Activity 时移除计时器任务，防止内存泄漏
        timerHandler.removeCallbacks(timerRunnable);
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
