package com.example.fitnesscheckinapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

/**
 * 运动数据统计页面：展示历史运动记录，支持按时间（今天、最近7天、特定日期）筛选，并统计总时长和消耗。
 */
public class DataActivity extends AppCompatActivity {

    // SharedPreferences 登录状态相关常量
    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";

    // 筛选类型常量
    private static final int FILTER_ALL = 0;           // 全部
    private static final int FILTER_TODAY = 1;         // 今天
    private static final int FILTER_LAST_7_DAYS = 2;   // 最近7天
    private static final int FILTER_SPECIFIC_DATE = 3; // 特定日期

    // UI 统计视图
    private TextView tvTotalCount;    // 总次数显示
    private TextView tvTotalMinutes;  // 总时长显示
    private TextView tvTotalCalorie;  // 总卡路里显示
    private TextView tvEmptyView;     // 无数据时的提示视图
    
    // 列表相关
    private RecyclerView rvRecords;   // 运动记录列表
    private RecordAdapter recordAdapter;
    private final List<SportRecord> recordList = new ArrayList<>();
    
    // 筛选按钮
    private Button btnFilterAll;
    private Button btnFilterToday;
    private Button btnFilterLast7Days;
    private Button btnFilterDate;

    private DBHelper dbHelper;
    private int currentFilter = FILTER_ALL; // 当前选中的筛选模式
    private String selectedDate = "";       // 用户选择的特定日期 (yyyy-MM-dd)
    private String currentUsername = "";    // 当前登录的用户名

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        dbHelper = new DBHelper(this);
        initViews();               // 绑定 UI 控件
        setupRecyclerView();       // 初始化列表
        setupFilterButtons();      // 设置筛选按钮监听
        setupBottomNavigation();   // 设置底部导航跳转
    }

    /**
     * 绑定布局文件中的控件
     */
    private void initViews() {
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvTotalMinutes = findViewById(R.id.tvTotalMinutes);
        tvTotalCalorie = findViewById(R.id.tvTotalCalorie);
        tvEmptyView = findViewById(R.id.tvEmptyView);
        rvRecords = findViewById(R.id.rvRecords);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterLast7Days = findViewById(R.id.btnFilterLast7Days);
        btnFilterDate = findViewById(R.id.btnFilterDate);
    }

    /**
     * 初始化 RecyclerView 及其适配器
     * 绑定删除记录的回调逻辑
     */
    private void setupRecyclerView() {
        recordAdapter = new RecordAdapter(recordList, this::showDeleteDialog);
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(recordAdapter);
    }

    /**
     * 设置筛选按钮的点击事件
     */
    private void setupFilterButtons() {
        // 全部记录
        btnFilterAll.setOnClickListener(v -> {
            currentFilter = FILTER_ALL;
            selectedDate = "";
            loadRecords();
        });
        // 今天的记录
        btnFilterToday.setOnClickListener(v -> {
            currentFilter = FILTER_TODAY;
            selectedDate = "";
            loadRecords();
        });
        // 最近7天的记录
        btnFilterLast7Days.setOnClickListener(v -> {
            currentFilter = FILTER_LAST_7_DAYS;
            selectedDate = "";
            loadRecords();
        });
        // 选择特定日期筛选
        btnFilterDate.setOnClickListener(v -> showDatePicker());
        
        updateFilterButtonState();
    }

    /**
     * 确保用户已登录，并获取当前用户名
     * @return true 表示已登录
     */
    private boolean ensureLoginUser() {
        SharedPreferences sp = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        boolean isLogin = sp.getBoolean(KEY_IS_LOGIN, false);
        currentUsername = sp.getString(KEY_USERNAME, "");
        if (!isLogin || TextUtils.isEmpty(currentUsername)) {
            Toast.makeText(this, R.string.toast_please_login_first, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }
        return true;
    }

    /**
     * 弹出日期选择器对话框
     */
    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    currentFilter = FILTER_SPECIFIC_DATE;
                    loadRecords();
                    updateFilterButtonState();
                    if (recordList.isEmpty()) {
                        Toast.makeText(this, R.string.toast_no_record_on_date, Toast.LENGTH_SHORT).show();
                    }
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    /**
     * 核心逻辑：从数据库加载运动记录并进行过滤与统计
     */
    private void loadRecords() {
        if (!ensureLoginUser()) {
            return;
        }

        recordList.clear();
        int totalCount = 0;
        int totalDurationSeconds = 0;
        int totalCalorie = 0;

        // 1. 查询当前用户的所有记录（按 ID 倒序，即时间从新到旧）
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor cursor = database.query(
                DBHelper.TABLE_SPORT_RECORD,
                null,
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{currentUsername},
                null,
                null,
                DBHelper.COLUMN_ID + " DESC"
        );

        // 2. 准备筛选比较所需的日期字符串
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        String last7DaysStart = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());

        // 3. 遍历查询结果并进行内存筛选
        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_ID));
            String sportType = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_SPORT_TYPE));
            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_DURATION));
            int calorie = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_CALORIE));
            String createTime = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_CREATE_TIME));
            String remark = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_REMARK));
            
            // 提取日期部分用于比较 (yyyy-MM-dd)
            String datePart = createTime != null && createTime.length() >= 10 ? createTime.substring(0, 10) : "";

            // 根据当前选中的过滤器判断是否包含该记录
            if (!matchesFilter(datePart, todayDate, last7DaysStart)) {
                continue;
            }

            // 添加到显示列表并累加统计数据
            recordList.add(new SportRecord(id, sportType, duration, calorie, createTime, remark));
            totalCount++;
            totalDurationSeconds += duration;
            totalCalorie += calorie;
        }
        cursor.close();

        // 4. 更新 UI 统计文案
        tvTotalCount.setText(getString(R.string.summary_total_count_value, totalCount));
        tvTotalMinutes.setText(getString(R.string.summary_total_minutes_value, totalDurationSeconds / 60));
        tvTotalCalorie.setText(getString(R.string.summary_total_calorie_value, totalCalorie));

        // 5. 根据结果是否为空切换显示视图
        if (recordList.isEmpty()) {
            tvEmptyView.setVisibility(View.VISIBLE);
            rvRecords.setVisibility(View.GONE);
        } else {
            tvEmptyView.setVisibility(View.GONE);
            rvRecords.setVisibility(View.VISIBLE);
        }

        updateFilterButtonState();
        recordAdapter.notifyDataSetChanged(); // 刷新列表展示
    }

    /**
     * 判断某条记录的日期是否符合当前的筛选条件
     */
    private boolean matchesFilter(String datePart, String todayDate, String last7DaysStart) {
        if (currentFilter == FILTER_TODAY) {
            return todayDate.equals(datePart);
        }
        if (currentFilter == FILTER_LAST_7_DAYS) {
            // 在最近7天范围内
            return datePart.compareTo(last7DaysStart) >= 0 && datePart.compareTo(todayDate) <= 0;
        }
        if (currentFilter == FILTER_SPECIFIC_DATE) {
            return !TextUtils.isEmpty(selectedDate) && selectedDate.equals(datePart);
        }
        return true; // FILTER_ALL 模式，全部匹配
    }

    /**
     * 更新筛选按钮的文字显示
     */
    private void updateFilterButtonState() {
        btnFilterAll.setEnabled(true);
        btnFilterToday.setEnabled(true);
        btnFilterLast7Days.setEnabled(true);
        btnFilterDate.setEnabled(true);

        if (currentFilter == FILTER_ALL) {
            btnFilterAll.setEnabled(false);
        } else if (currentFilter == FILTER_TODAY) {
            btnFilterToday.setEnabled(false);
        } else if (currentFilter == FILTER_LAST_7_DAYS) {
            btnFilterLast7Days.setEnabled(false);
        }

        if (currentFilter == FILTER_SPECIFIC_DATE && !TextUtils.isEmpty(selectedDate)) {
            btnFilterDate.setText(getString(R.string.filter_specific_date_value, selectedDate));
        } else {
            btnFilterDate.setText(R.string.filter_specific_date);
        }
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteDialog(SportRecord record) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.data_delete_title)
                .setMessage(getString(R.string.data_delete_message, record.getSportType()))
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> deleteRecord(record))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * 数据库删除逻辑：根据记录 ID 和用户名执行删除
     */
    private void deleteRecord(SportRecord record) {
        if (!ensureLoginUser()) {
            return;
        }
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.delete(
                DBHelper.TABLE_SPORT_RECORD,
                DBHelper.COLUMN_ID + " = ? AND " + DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{String.valueOf(record.getId()), currentUsername}
        );
        loadRecords(); // 删除后重新加载并统计
    }

    /**
     * 设置底部导航跳转
     */
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
        // 每次进入页面都刷新数据
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
