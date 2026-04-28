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

/**
 * 首页 Activity：展示用户今日运动概况、累计运动数据、连续打卡天数以及最近一次运动记录。
 */
public class MainActivity extends AppCompatActivity {

    // SharedPreferences 存储相关常量
    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";

    // 模拟的今日步数（由于未接入传感器，此处使用固定模拟值）
    private static final int MOCK_TODAY_STEPS = 5680;

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
     * 核心逻辑：从数据库加载并汇总首页展示所需的各项运动数据
     */
    private void loadHomeData() {
        if (!ensureLoginUser()) {
            return;
        }

        int todayCalorie = 0;
        int todayDurationSeconds = 0;
        int todayCount = 0;
        int totalCount = 0;
        int totalDurationSeconds = 0;
        // 用于存储所有有记录的日期，以便计算连续打卡天数
        Set<String> checkInDateSet = new HashSet<>();

        // 获取当前日期的字符串 (yyyy-MM-dd)
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        // 查询当前用户的所有记录
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
            String recordDate = extractDate(createTime); // 提取 yyyy-MM-dd

            // 1. 累计所有记录的数据
            totalCount++;
            totalDurationSeconds += duration;
            if (recordDate != null) {
                checkInDateSet.add(recordDate); // 加入日期集合（去重）
            }

            // 2. 统计今日的数据
            if (todayDate.equals(recordDate)) {
                todayDurationSeconds += duration;
                todayCalorie += calorie;
                todayCount++;
            }
        }
        cursor.close();

        // 3. 更新 UI 显示
        tvTodayCalorie.setText(getString(R.string.home_today_calorie_value, todayCalorie));
        tvTodayMinutes.setText(getString(R.string.home_today_minutes_value, todayDurationSeconds / 60));
        tvTodayCount.setText(getString(R.string.home_today_count_value, todayCount));
        tvTodaySteps.setText(getString(R.string.home_today_steps_value, MOCK_TODAY_STEPS));
        tvTotalCount.setText(getString(R.string.home_total_count_value, totalCount));
        tvTotalMinutes.setText(getString(R.string.home_total_minutes_value, totalDurationSeconds / 60));
        
        // 计算并显示连续打卡天数
        tvStreakDays.setText(getString(R.string.home_streak_days_value, calculateStreakDays(checkInDateSet)));
        
        // 根据今日是否有运动更新鼓励语
        tvEncouragement.setText(todayCount > 0
                ? R.string.home_encouragement_done
                : R.string.home_encouragement_not_yet);

        // 加载并展示最近的一条运动记录详情
        loadLatestRecord(database);
    }

    /**
     * 查询并显示该用户的最后一条运动记录
     */
    private void loadLatestRecord(SQLiteDatabase database) {
        Cursor latestCursor = database.query(
                DBHelper.TABLE_SPORT_RECORD,
                null,
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{currentUsername},
                null,
                null,
                DBHelper.COLUMN_ID + " DESC",
                "1" // 只取一条
        );

        if (latestCursor.moveToFirst()) {
            String sportType = latestCursor.getString(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_SPORT_TYPE));
            int duration = latestCursor.getInt(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_DURATION));
            int calorie = latestCursor.getInt(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_CALORIE));
            String createTime = latestCursor.getString(latestCursor.getColumnIndexOrThrow(DBHelper.COLUMN_CREATE_TIME));

            // 格式化文案：类型 + 分/秒时长 + 卡路里 + 时间
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
            // 无任何运动记录时的提示
            tvLatestRecord.setText(R.string.home_latest_record_empty);
        }
        latestCursor.close();
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
        Button btnData = findViewById(R.id.btnData);
        Button btnMine = findViewById(R.id.btnMine);

        // 跳转至打卡页
        btnCheckIn.setOnClickListener(v -> startActivity(new Intent(this, CheckInActivity.class)));
        // 跳转至数据统计页
        btnData.setOnClickListener(v -> startActivity(new Intent(this, DataActivity.class)));
        // 跳转至个人中心页
        btnMine.setOnClickListener(v -> startActivity(new Intent(this, MineActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到首页都重新加载数据，保证数据的实时性
        loadHomeData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close(); // 关闭数据库
        }
    }
}
