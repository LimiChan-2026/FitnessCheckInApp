package com.example.fitnesscheckinapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 登录页面：负责处理用户登录逻辑，包含自动登录检查、数据库账号验证以及登录会话管理。
 */
public class LoginActivity extends AppCompatActivity {

    // SharedPreferences 存储相关的常量，用于持久化登录状态
    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID = "user_id";

    // UI 控件变量
    private EditText etUsername;    // 用户名输入框
    private EditText etPassword;    // 密码输入框
    private DBHelper dbHelper;      // 数据库辅助类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 自动登录检查：如果本地已记录登录状态，则直接跳转至主页
        SharedPreferences preferences = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        if (preferences.getBoolean(KEY_IS_LOGIN, false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish(); // 销毁当前页面，防止用户返回
            return;
        }

        setContentView(R.layout.activity_login);
        dbHelper = new DBHelper(this);
        
        initViews();    // 初始化 UI 控件
        setupActions(); // 设置交互事件
    }

    /**
     * 绑定布局文件中的控件
     */
    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
    }

    /**
     * 设置按钮及文本的点击跳转逻辑
     */
    private void setupActions() {
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvGoRegister = findViewById(R.id.tvGoRegister);
        TextView tvForgetPassword = findViewById(R.id.tvForgetPassword);

        // 登录按钮：触发身份验证逻辑
        btnLogin.setOnClickListener(v -> login());
        
        // “立即注册”：跳转到注册页面
        tvGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        
        // “忘记密码”：跳转到找回密码页面
        tvForgetPassword.setOnClickListener(v -> startActivity(new Intent(this, ForgetPasswordActivity.class)));
    }

    /**
     * 核心登录逻辑：校验输入，查询数据库验证身份，成功后保存登录态并跳转
     */
    private void login() {
        // 获取输入内容
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        // 基础非空校验
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.toast_enter_username, Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.toast_enter_password, Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. 数据库查询：比对用户名和密码
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor cursor = database.query(
                DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USER_ID}, // 仅需获取用户 ID
                DBHelper.COLUMN_USERNAME + " = ? AND " + DBHelper.COLUMN_PASSWORD + " = ?",
                new String[]{username, password},
                null,
                null,
                null,
                "1" // 只取第一条匹配结果
        );

        // 检查是否存在匹配的用户记录
        boolean success = cursor.moveToFirst();
        int userId = success ? cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_USER_ID)) : -1;
        cursor.close();

        if (!success) {
            // 验证失败提示
            Toast.makeText(this, R.string.toast_login_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 登录成功：将信息保存到 SharedPreferences 供全应用使用
        getSharedPreferences(PREF_USER, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_LOGIN, true)     // 标记已登录
                .putString(KEY_USERNAME, username)  // 存储用户名
                .putInt(KEY_USER_ID, userId)       // 存储用户 ID
                .apply();

        // 4. 跳转至主页，并清理 Activity 栈（确保主页为栈顶，点击返回不会回到登录页）
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 销毁 Activity 时释放数据库资源，防止内存泄漏
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
