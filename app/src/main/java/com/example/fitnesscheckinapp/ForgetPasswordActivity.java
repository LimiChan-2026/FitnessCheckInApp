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
 * 忘记密码页面：重置密码时优先改服务端，本地同步更新为加盐哈希。
 * 服务器不可达时降级为仅修改本地密码。
 */
public class ForgetPasswordActivity extends AppCompatActivity {

    // UI 控件变量
    private EditText etUsername;            // 用户名输入框
    private EditText etNewPassword;         // 新密码输入框
    private EditText etConfirmNewPassword;  // 确认新密码输入框
    private DBHelper dbHelper;              // 数据库辅助类
    private ApiClient apiClient;            // 网络客户端

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forget_password);

        // 初始化数据库助手并绑定视图
        dbHelper = new DBHelper(this);
        apiClient = new ApiClient(this);
        initViews();
        setupActions();
    }

    /**
     * 绑定布局文件中的 UI 控件
     */
    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
    }

    /**
     * 设置按钮的点击事件监听
     */
    private void setupActions() {
        Button btnResetPassword = findViewById(R.id.btnResetPassword);
        // “重置密码”按钮点击逻辑
        btnResetPassword.setOnClickListener(v -> resetPassword());

        // “返回登录”文本点击逻辑：直接关闭当前页面返回上一级
        findViewById(R.id.tvBackToLogin).setOnClickListener(v -> finish());
    }

    /**
     * 核心业务逻辑：重置密码（服务端优先，本地双写）
     */
    private void resetPassword() {
        // 1. 获取用户输入并处理
        String username = etUsername.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmNewPassword = etConfirmNewPassword.getText().toString();

        // 2. 基础非空校验：用户名
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.toast_enter_username, Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 本地账号存在性校验（服务器上的账号由服务端校验）
        if (!userExistsLocally(username)) {
            Toast.makeText(this, R.string.toast_account_not_exists, Toast.LENGTH_SHORT).show();
            return;
        }

        // 4. 密码校验：不能为空且两次输入必须一致
        if (TextUtils.isEmpty(newPassword)) {
            Toast.makeText(this, R.string.toast_enter_new_password, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPassword.equals(confirmNewPassword)) {
            Toast.makeText(this, R.string.toast_password_not_match, Toast.LENGTH_SHORT).show();
            return;
        }

        // 5. 本地先更新为加盐哈希（无论服务器是否可用，本地都要一致）
        updateLocalPassword(username, newPassword);

        // 6. 服务端同步更新（需要旧密码校验的场景由登录页流程保证，此处服务端直接按用户名改）
        apiClient.changePassword(username, newPassword, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishWithSuccess();
            }

            @Override
            public void onFailure(String message) {
                if (message != null && message.contains("无法连接服务器")) {
                    // 离线模式：本地已改好，直接成功
                    finishWithSuccess();
                } else {
                    // 服务端明确拒绝（如账号不存在）：本地已改，提示差异
                    Toast.makeText(ForgetPasswordActivity.this,
                            R.string.toast_reset_password_local_only, Toast.LENGTH_LONG).show();
                    finishWithSuccess();
                }
            }
        });
    }

    private void finishWithSuccess() {
        Toast.makeText(this, R.string.toast_reset_password_success, Toast.LENGTH_SHORT).show();
        Nav.flow(this, new Intent(this, LoginActivity.class));
        finish();
    }

    /**
     * 检查本地库中该用户名是否存在
     */
    private boolean userExistsLocally(String username) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor cursor = database.query(
                DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USER_ID},
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username},
                null, null, null, "1"
        );
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    /**
     * 更新本地密码为加盐哈希
     */
    private void updateLocalPassword(String username, String newPassword) {
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_PASSWORD, PasswordUtil.hash(newPassword));
        dbHelper.getWritableDatabase().update(
                DBHelper.TABLE_USER_INFO,
                values,
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username}
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 销毁 Activity 时关闭数据库连接，释放资源
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
