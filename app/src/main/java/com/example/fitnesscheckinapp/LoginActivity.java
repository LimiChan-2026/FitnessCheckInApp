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

import org.json.JSONObject;

/**
 * 登录页面：优先走服务端在线认证；服务器不可达时回退到本地数据库（离线模式）。
 * 在线登录成功后同步云端返回的昵称等信息到本地，保证两端口径一致。
 */
public class LoginActivity extends AppCompatActivity {

    // SharedPreferences 存储相关的常量
    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID = "user_id";

    // UI 控件变量
    private EditText etUsername;    // 用户名输入框
    private EditText etPassword;    // 密码输入框
    private DBHelper dbHelper;      // 数据库辅助类
    private ApiClient apiClient;    // 网络客户端

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 自动登录检查：如果本地已记录登录状态，则直接跳转至主页（淡入过渡）
        SharedPreferences preferences = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        if (preferences.getBoolean(KEY_IS_LOGIN, false)) {
            Nav.flow(this, new Intent(this, MainActivity.class));
            finish(); // 销毁当前页面，防止用户返回
            return;
        }

        setContentView(R.layout.activity_login);
        dbHelper = new DBHelper(this);
        apiClient = new ApiClient(this);

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
        TextView tvServerSetting = findViewById(R.id.tvServerSetting);

        // 登录按钮：触发身份验证逻辑
        btnLogin.setOnClickListener(v -> login());

        // “立即注册”：跳转到注册页面（淡入过渡）
        tvGoRegister.setOnClickListener(v -> Nav.flow(this, new Intent(this, RegisterActivity.class)));

        // “忘记密码”：跳转到找回密码页面（淡入过渡）
        tvForgetPassword.setOnClickListener(v -> Nav.flow(this, new Intent(this, ForgetPasswordActivity.class)));

        // 服务器地址设置（真机调试必备：改为电脑局域网IP）
        refreshServerSettingView(tvServerSetting);
        tvServerSetting.setOnClickListener(v -> showServerSettingDialog(tvServerSetting));
    }

    /** 显示当前服务器地址 */
    private void refreshServerSettingView(TextView tv) {
        tv.setText(getString(R.string.server_setting_entry, apiClient.getBaseUrl()));
    }

    /**
     * 服务器地址设置对话框：真机与电脑连同一WiFi后，
     * 把地址改为电脑局域网IP（如 http://192.168.1.3:8080）。
     */
    private void showServerSettingDialog(TextView tvServerSetting) {
        android.widget.EditText etAddress = new android.widget.EditText(this);
        etAddress.setText(apiClient.getBaseUrl());
        etAddress.setHint(R.string.server_setting_hint);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        etAddress.setPadding(p, p, p, p);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.server_setting_title)
                .setMessage(R.string.server_setting_hint)
                .setView(etAddress)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    apiClient.setBaseUrl(etAddress.getText().toString().trim());
                    refreshServerSettingView(tvServerSetting);
                    Toast.makeText(this, R.string.toast_server_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * 核心登录逻辑：
     * 1. 先尝试服务端在线认证（网络请求）；
     * 2. 服务器不可达时，回退本地数据库校验（密码为加盐哈希，兼容旧版明文）。
     */
    private void login() {
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

        // 优先走服务端认证
        apiClient.login(username, password, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                // 在线登录成功：同步服务端昵称到本地，再保存登录态
                String nickname = data.optString("nickname", username);
                syncLocalNickname(username, nickname);
                saveLoginStateAndEnterMain(username);
            }

            @Override
            public void onFailure(String message) {
                // 分两种失败：业务失败（密码错）直接提示；网络不可达则回退本地校验
                if (message != null && message.contains("无法连接服务器")) {
                    localLogin(username, password);
                } else {
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 离线回退：用本地数据库校验账号密码。
     * 兼容两类历史数据：新版存 salt$hash，旧版存明文（登录成功后顺手升级为哈希）。
     */
    private void localLogin(String username, String password) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor cursor = database.query(
                DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USER_ID, DBHelper.COLUMN_PASSWORD},
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username},
                null, null, null,
                "1"
        );

        boolean success = false;
        int userId = -1;
        String storedPassword = null;
        if (cursor.moveToFirst()) {
            userId = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_USER_ID));
            storedPassword = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_PASSWORD));
            success = PasswordUtil.verify(password, storedPassword)
                    || password.equals(storedPassword); // 兼容旧明文数据
        }
        cursor.close();

        if (!success) {
            Toast.makeText(this, R.string.toast_login_failed_offline, Toast.LENGTH_SHORT).show();
            return;
        }

        // 旧版明文密码升级为哈希存储
        if (storedPassword != null && !storedPassword.contains("$")) {
            database = dbHelper.getWritableDatabase();
            String hashed = PasswordUtil.hash(password);
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(DBHelper.COLUMN_PASSWORD, hashed);
            database.update(DBHelper.TABLE_USER_INFO, values,
                    DBHelper.COLUMN_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        }

        Toast.makeText(this, R.string.toast_login_offline_mode, Toast.LENGTH_SHORT).show();
        saveLoginStateAndEnterMain(username);
    }

    /**
     * 把服务端昵称同步到本地用户表（在线登录时保持两端一致）
     */
    private void syncLocalNickname(String username, String nickname) {
        if (TextUtils.isEmpty(nickname)) {
            return;
        }
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(DBHelper.COLUMN_NICKNAME, nickname);
        database.update(DBHelper.TABLE_USER_INFO, values,
                DBHelper.COLUMN_USERNAME + " = ?", new String[]{username});
    }

    /**
     * 保存登录态并进入主页
     */
    private void saveLoginStateAndEnterMain(String username) {
        // 本地无此账号（例如在其他设备注册过）时补一条本地记录，保证本地模块可用
        int userId = ensureLocalUser(username);

        getSharedPreferences(PREF_USER, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_LOGIN, true)     // 标记已登录
                .putString(KEY_USERNAME, username)  // 存储用户名
                .putInt(KEY_USER_ID, userId)       // 存储用户 ID
                .apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Nav.flow(this, intent);
        finish();
    }

    /**
     * 确保本地库存在该用户，返回本地 userId（不存在则创建占位记录）
     */
    private int ensureLocalUser(String username) {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        Cursor cursor = database.query(
                DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USER_ID},
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username},
                null, null, null, "1"
        );
        int userId = -1;
        if (cursor.moveToFirst()) {
            userId = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_USER_ID));
        }
        cursor.close();

        if (userId == -1) {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(DBHelper.COLUMN_USERNAME, username);
            values.put(DBHelper.COLUMN_PASSWORD, "");       // 密码以服务端为准，本地留空
            values.put(DBHelper.COLUMN_NICKNAME, username);
            values.put(DBHelper.COLUMN_AVATAR_PATH, "");
            values.put(DBHelper.COLUMN_USER_CREATE_TIME,
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            .format(new java.util.Date()));
            long id = database.insert(DBHelper.TABLE_USER_INFO, null, values);
            userId = (int) id;
        }
        return userId;
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
