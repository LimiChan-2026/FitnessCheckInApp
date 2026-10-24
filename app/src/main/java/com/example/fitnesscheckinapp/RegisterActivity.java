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

import org.json.JSONObject;

/**
 * 注册页面：优先在服务端创建账号（全局唯一）；同时在本地库写入一份
 * 加盐哈希密码，供离线登录使用。服务器不可达时降级为纯本地注册。
 */
public class RegisterActivity extends AppCompatActivity {

    // UI 控件
    private EditText etUsername;        // 用户名输入框
    private EditText etPassword;        // 密码输入框
    private EditText etConfirmPassword; // 确认密码输入框
    private DBHelper dbHelper;          // 数据库辅助类
    private ApiClient apiClient;        // 网络客户端

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        dbHelper = new DBHelper(this);
        apiClient = new ApiClient(this);
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
     * 设置按钮及文本的点击跳转逻辑
     */
    private void setupActions() {
        Button btnRegister = findViewById(R.id.btnRegister);
        // “立即注册”按钮逻辑
        btnRegister.setOnClickListener(v -> register());
        // “已有账号？去登录”文本逻辑：关闭当前页返回登录页
        findViewById(R.id.tvGoLogin).setOnClickListener(v -> finish());
    }

    /**
     * 核心注册逻辑：输入校验 → 服务端注册 → 本地双写（哈希密码）
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

        // 3. 服务端注册（云端账号全局唯一）
        apiClient.register(username, password, username, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                // 云端注册成功 → 本地双写
                if (saveLocalUser(username, password)) {
                    Toast.makeText(RegisterActivity.this,
                            R.string.toast_register_success, Toast.LENGTH_SHORT).show();
                    Nav.flow(RegisterActivity.this, new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                } else {
                    // 云端成功但本地已同名（历史数据冲突），仍允许去登录
                    Toast.makeText(RegisterActivity.this,
                            R.string.toast_register_success_local_exists, Toast.LENGTH_SHORT).show();
                    Nav.flow(RegisterActivity.this, new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                }
            }

            @Override
            public void onFailure(String message) {
                if (message != null && message.contains("无法连接服务器")) {
                    // 服务器不可达：降级为纯本地注册（离线模式）
                    localRegister(username, password);
                } else if (message != null && message.contains("已存在")) {
                    Toast.makeText(RegisterActivity.this,
                            R.string.toast_account_exists, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 离线降级注册：账号只在本机有效，联网后仍可登录同一账号
     */
    private void localRegister(String username, String password) {
        if (userExistsLocally(username)) {
            Toast.makeText(this, R.string.toast_account_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        if (saveLocalUser(username, password)) {
            Toast.makeText(this, R.string.toast_register_success_offline,
                    Toast.LENGTH_SHORT).show();
            Nav.flow(this, new Intent(this, LoginActivity.class));
            finish();
        } else {
            Toast.makeText(this, R.string.toast_register_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查本地库中用户名是否已存在
     */
    private boolean userExistsLocally(String username) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor checkCursor = database.query(
                DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USER_ID},
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username},
                null, null, null, "1"
        );
        boolean exists = checkCursor.moveToFirst();
        checkCursor.close();
        return exists;
    }

    /**
     * 将新用户写入本地 user_info 表：密码存加盐哈希（不再是明文）
     * @return true 表示插入成功
     */
    private boolean saveLocalUser(String username, String password) {
        if (userExistsLocally(username)) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_USERNAME, username);
        values.put(DBHelper.COLUMN_PASSWORD, PasswordUtil.hash(password)); // 加盐哈希
        values.put(DBHelper.COLUMN_NICKNAME, username); // 默认昵称设为用户名
        values.put(DBHelper.COLUMN_AVATAR_PATH, "");    // 初始头像路径为空
        values.put(DBHelper.COLUMN_USER_CREATE_TIME,
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date()));

        long insertResult = dbHelper.getWritableDatabase().insert(DBHelper.TABLE_USER_INFO, null, values);
        return insertResult != -1;
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
