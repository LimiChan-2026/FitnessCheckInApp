package com.example.fitnesscheckinapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 首页 Activity：展示用户今日运动概况、累计运动数据、连续打卡天数以及最近一次运动记录。
 * 今日步数通过硬件计步传感器（TYPE_STEP_COUNTER）实时获取。
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // SharedPreferences 存储相关常量
    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";

    // 计步相关：传感器计数器是"开机以来累计值"，需减去开机基准值得到今日步数
    private static final String PREF_STEPS = "pref_steps";
    private static final String KEY_STEP_BASE = "step_base";
    private static final String KEY_STEP_DATE = "step_date";

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int todaySteps = 0;           // 今日步数（传感器实时更新）

    // UI 控件变量
    private TextView tvTodayCalorie;   // 今日消耗卡路里
    private TextView tvTodayMinutes;   // 今日运动时长
    private TextView tvTodayCount;     // 今日运动次数
    private TextView tvTodaySteps;     // 今日步数
    private TextView tvTotalCount;     // 累计运动次数
    private TextView tvTotalMinutes;   // 累计运动时长
    private TextView tvStreakDays;     // 连续打卡天数
    private TextView tvEncouragement;  // 鼓励语（根据今日是否运动显示不同文案）
    private TextView tvLatestRecord;   // 最近一次运动记录简述

    private DBHelper dbHelper;         // 数据库辅助类
    private String currentUsername = "";// 当前登录的用户名

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DBHelper(this);
        initViews();               // 绑定 UI 控件
        setupBottomNavigation();   // 初始化底部导航跳转逻辑
        setupStepSensor();         // 初始化计步传感器
    }

    /**
     * 初始化计步传感器：Android 10+ 需要运行时申请 ACTIVITY_RECOGNITION 权限。
     * 传感器不可用（模拟器或无传感器设备）时步数显示保持 0。
     */
    private void setupStepSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            return;
        }
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACTIVITY_RECOGNITION}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限授予后重新注册监听
            registerStepListener();
        }
    }

    /**
     * 注册计步监听（onResume 时调用）
     */
    private void registerStepListener() {
        if (stepSensor != null
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION)
                                == PackageManager.PERMISSION_GRANTED)) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    /**
     * 传感器回调：TYPE_STEP_COUNTER 返回开机以来累计步数，
     * 减去当日基准值（跨天自动重置）得到今日步数。
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }
        float total = event.values[0];
        SharedPreferences sp = getSharedPreferences(PREF_STEPS, MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (!today.equals(sp.getString(KEY_STEP_DATE, ""))) {
            // 新的一天：以当前累计值作为基准
            sp.edit()
                    .putString(KEY_STEP_DATE, today)
                    .putFloat(KEY_STEP_BASE, total)
                    .apply();
        }

        float base = sp.getFloat(KEY_STEP_BASE, total);
        todaySteps = Math.max(0, (int) (total - base));
        tvTodaySteps.setText(getString(R.string.home_today_steps_value, todaySteps));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 计步传感器无需处理精度变化
    }

    /**
     * 校验登录状态：若未登录则跳转至登录页面并关闭首页
     * @return true 表示已登录且获取到用户名
     */
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

    /**
     * 初始化布局中的各 TextView 控件
     */
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

    /**
     * 核心逻辑：从数据库加载并汇总首页展示所需的各项运动数据。
     * 数据库查询与聚合在后台线程执行（避免阻塞页面切换动画），完成后回主线程刷新 UI。
     */
    private void loadHomeData() {
        if (!ensureLoginUser()) {
            return;
        }

        Bg.io(() -> {
            HomeStats stats = new HomeStats();
            // 用于存储所有有记录的日期，以便计算连续打卡天数
            Set<String> checkInDateSet = new HashSet<>();
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            boolean latestCaptured = false;

            SQLiteDatabase database = dbHelper.getReadableDatabase();
            // 查询当前用户的所有记录（按 ID 倒序，首条即最近记录，无需二次查询）
            Cursor cursor = database.query(
                    DBHelper.TABLE_SPORT_RECORD,
                    null,
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
                String recordDate = extractDate(createTime); // 提取 yyyy-MM-dd

                // 1. 累计所有记录的数据
                stats.totalCount++;
                stats.totalDurationSeconds += duration;
                if (recordDate != null) {
                    checkInDateSet.add(recordDate); // 加入日期集合（去重）
                }

                // 2. 统计今日的数据
                if (todayDate.equals(recordDate)) {
                    stats.todayDurationSeconds += duration;
                    stats.todayCalorie += calorie;
                    stats.todayCount++;
                }

                // 3. 首条即最近一次运动记录
                if (!latestCaptured) {
                    latestCaptured = true;
                    String sportType = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_SPORT_TYPE));
                    stats.latestRecordText = getString(R.string.home_latest_record_value,
                            sportType, duration / 60, duration % 60, calorie, createTime);
                }
            }
            cursor.close();

            stats.streakDays = calculateStreakDays(checkInDateSet);
            return stats;
        }, stats -> {
            // 主线程：仅做 UI 刷新
            if (isFinishing() || isDestroyed() || stats == null) {
                return;
            }
            tvTodayCalorie.setText(getString(R.string.home_today_calorie_value, stats.todayCalorie));
            tvTodayMinutes.setText(getString(R.string.home_today_minutes_value, stats.todayDurationSeconds / 60));
            tvTodayCount.setText(getString(R.string.home_today_count_value, stats.todayCount));
            tvTodaySteps.setText(getString(R.string.home_today_steps_value, todaySteps));
            tvTotalCount.setText(getString(R.string.home_total_count_value, stats.totalCount));
            tvTotalMinutes.setText(getString(R.string.home_total_minutes_value, stats.totalDurationSeconds / 60));
            tvStreakDays.setText(getString(R.string.home_streak_days_value, stats.streakDays));
            tvEncouragement.setText(stats.todayCount > 0
                    ? R.string.home_encouragement_done
                    : R.string.home_encouragement_not_yet);
            tvLatestRecord.setText(stats.latestRecordText != null
                    ? stats.latestRecordText
                    : getString(R.string.home_latest_record_empty));
        });
    }

    /**
     * 首页统计数据载体：由后台线程产出，主线程消费
     */
    private static class HomeStats {
        int todayCalorie;
        int todayDurationSeconds;
        int todayCount;
        int totalCount;
        int totalDurationSeconds;
        int streakDays;
        String latestRecordText;
    }

    /**
     * 辅助方法：从完整的时间字符串（yyyy-MM-dd HH:mm:ss）中截取日期部分
     */
    private String extractDate(String createTime) {
        if (createTime == null || createTime.length() < 10) {
            return null;
        }
        return createTime.substring(0, 10);
    }

    /**
     * 核心算法：计算从今天开始往回数，连续打卡的天数
     * @param checkInDateSet 包含所有打卡日期的 Set (yyyy-MM-dd)
     * @return 连续天数
     */
    private int calculateStreakDays(Set<String> checkInDateSet) {
        if (checkInDateSet.isEmpty()) {
            return 0;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance(); // 从今天开始往前倒查
        int streakDays = 0;

        while (true) {
            String dateKey = dateFormat.format(calendar.getTime());
            if (checkInDateSet.contains(dateKey)) {
                streakDays++;
                calendar.add(Calendar.DAY_OF_YEAR, -1); // 往前推一天
            } else {
                // 如果今天没打卡，streakDays 为 0；如果昨天断了，循环也结束
                break;
            }
        }

        return streakDays;
    }

    /**
     * 底部导航跳转逻辑
     */
    private void setupBottomNavigation() {
        Button btnCheckIn = findViewById(R.id.btnCheckIn);
        Button btnSocial = findViewById(R.id.btnSocial);
        Button btnData = findViewById(R.id.btnData);
        Button btnMine = findViewById(R.id.btnMine);

        // 跳转至打卡页
        btnCheckIn.setOnClickListener(v -> Nav.go(this, CheckInActivity.class));
        // 跳转至社区页
        btnSocial.setOnClickListener(v -> Nav.go(this, SocialActivity.class));
        // 跳转至数据统计页
        btnData.setOnClickListener(v -> Nav.go(this, DataActivity.class));
        // 跳转至个人中心页
        btnMine.setOnClickListener(v -> Nav.go(this, MineActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到首页都重新加载数据，保证数据的实时性
        loadHomeData();
        // 恢复计步监听
        registerStepListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 页面不可见时取消计步监听，省电
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close(); // 关闭数据库
        }
    }
}
