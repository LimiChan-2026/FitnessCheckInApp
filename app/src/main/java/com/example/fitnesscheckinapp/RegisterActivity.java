package com.example.fitnesscheckinapp;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 注册页面：负责新用户账号的创建，包含用户名唯一性检查及基本信息初始化。
 */
public class RegisterActivity extends AppCompatActivity {

    // UI 控件
    private EditText etUsername;        // 用户名输入框
    private EditText etPassword;        // 密码输入框
    private EditText etConfirmPassword; // 确认密码输入框
    private DBHelper dbHelper;          // 数据库辅助类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        dbHelper = new DBHelper(this);
        initViews();    // 初始化控件
        setupActions(); // 设置点击事件
    }

    /**
     * 绑定布局中的 UI 控件
     */
    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
    }

    /**
     * 设置按钮的点击跳转逻辑
     */
    private void setupActions() {
        Button btnRegister = findViewById(R.id.btnRegister);
        // “立即注册”按钮逻辑
        btnRegister.setOnClickListener(v -> register());
        // “已有账号？去登录”文本逻辑：关闭当前页返回登录页
        findViewById(R.id.tvGoLogin).setOnClickListener(v -> finish());
    }

    /**
     * 核心注册逻辑：校验输入、检查重复账号并保存新用户信息
     */
    private void register() {
        // 获取用户输入并处理
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        // 1. 基础非空校验
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.toast_enter_username, Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.toast_enter_password, Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. 密码一致性校验
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, R.string.toast_password_not_match, Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 唯一性检查：查询数据库确认用户名是否已被占用
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor checkCursor = database.query(
                DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USER_ID},
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username},
                null,
                null,
                null,
                "1"
        );
        boolean exists = checkCursor.moveToFirst();
        checkCursor.close();

        if (exists) {
            Toast.makeText(this, R.string.toast_account_exists, Toast.LENGTH_SHORT).show();
            return;
        }

        // 4. 执行注册：将新用户信息插入 user_info 表
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_USERNAME, username);
        values.put(DBHelper.COLUMN_PASSWORD, password);
        values.put(DBHelper.COLUMN_NICKNAME, username); // 默认昵称设为用户名
        values.put(DBHelper.COLUMN_AVATAR_PATH, "");    // 初始头像路径为空
        values.put(DBHelper.COLUMN_USER_CREATE_TIME,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

        long insertResult = dbHelper.getWritableDatabase().insert(DBHelper.TABLE_USER_INFO, null, values);
        
        // 5. 结果处理
        if (insertResult == -1) {
            Toast.makeText(this, R.string.toast_register_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // 注册成功，提示并跳转至登录页
        Toast.makeText(this, R.string.toast_register_success, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 销毁 Activity 时关闭数据库连接
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
