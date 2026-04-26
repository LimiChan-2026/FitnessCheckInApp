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

public class ForgetPasswordActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forget_password);

        dbHelper = new DBHelper(this);
        initViews();
        setupActions();
    }

    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
    }

    private void setupActions() {
        Button btnResetPassword = findViewById(R.id.btnResetPassword);
        btnResetPassword.setOnClickListener(v -> resetPassword());
        findViewById(R.id.tvBackToLogin).setOnClickListener(v -> finish());
    }

    private void resetPassword() {
        String username = etUsername.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmNewPassword = etConfirmNewPassword.getText().toString();

        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.toast_enter_username, Toast.LENGTH_SHORT).show();
            return;
        }

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
            Toast.makeText(this, R.string.toast_account_not_exists, Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(newPassword)) {
            Toast.makeText(this, R.string.toast_enter_new_password, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            Toast.makeText(this, R.string.toast_password_not_match, Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues values = new ContentValues();
        values.put(DBHelper.COLUMN_PASSWORD, newPassword);

        int rows = dbHelper.getWritableDatabase().update(
                DBHelper.TABLE_USER_INFO,
                values,
                DBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username}
        );

        if (rows <= 0) {
            Toast.makeText(this, R.string.toast_reset_password_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.toast_reset_password_success, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
