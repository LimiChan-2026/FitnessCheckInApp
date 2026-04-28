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

/**
 * 忘记密码页面：允许用户通过核对用户名来重置登录密码。
 */
public class ForgetPasswordActivity extends AppCompatActivity {

    // UI 控件变量
    private EditText etUsername;            // 用户名输入框
    private EditText etNewPassword;         // 新密码输入框
    private EditText etConfirmNewPassword;  // 确认新密码输入框
    private DBHelper dbHelper;              // 数据库辅助类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forget_password);

        // 初始化数据库助手并绑定视图
        dbHelper = new DBHelper(this);
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
     * 核心业务逻辑：重置密码
     * 包含账号校验、密码一致性校验及数据库更新操作
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

        // 3. 账号存在性校验：查询数据库确认该用户名是否已注册
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        Cursor cursor = database.query(
                DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USER_ID},
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username},
                null,
                null,
                null,
                "1"
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        if (!exists) {
            // 如果账号不存在，提示错误并拦截
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

        // 5. 数据库更新逻辑：执行密码修改
        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_PASSWORD, newPassword);

        int rows = dbHelper.getWritableDatabase().update(
                DBHelper.TABLE_USER_INFO,
                values,
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username}
        );

        // 6. 结果处理
        if (rows <= 0) {
            Toast.makeText(this, R.string.toast_reset_password_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // 修改成功，提示用户并跳转回登录页面
        Toast.makeText(this, R.string.toast_reset_password_success, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish(); // 销毁当前重置密码页面
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
