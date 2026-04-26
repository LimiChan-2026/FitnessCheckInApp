package com.example.fitnesscheckinapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MineActivity extends AppCompatActivity {

    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID = "user_id";

    private ImageView ivAvatar;
    private TextView tvNickname;
    private TextView tvMineTotalCount;
    private TextView tvMineTotalMinutes;
    private TextView tvBadgeList;
    private SharedPreferences sp;
    private DBHelper dbHelper;

    private int userId = -1;
    private String username = "";

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mine);

        sp = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        dbHelper = new DBHelper(this);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        pickImageLauncher.launch("image/*");
                    } else {
                        Toast.makeText(this, R.string.toast_permission_required, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && userId > 0) {
                        saveAvatar(uri);
                    }
                }
        );

        ivAvatar = findViewById(R.id.ivAvatar);
        tvNickname = findViewById(R.id.tvNickname);
        tvMineTotalCount = findViewById(R.id.tvMineTotalCount);
        tvMineTotalMinutes = findViewById(R.id.tvMineTotalMinutes);
        tvBadgeList = findViewById(R.id.tvBadgeList);

        findViewById(R.id.btnEditNickname).setOnClickListener(v -> showEditNicknameDialog());
        findViewById(R.id.btnEditAvatar).setOnClickListener(v -> chooseAvatar());
        findViewById(R.id.btnAboutSystem).setOnClickListener(v -> showAboutSystemDialog());
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
        findViewById(R.id.btnClearRecords).setOnClickListener(v -> showClearRecordsDialog());
        ivAvatar.setOnClickListener(v -> chooseAvatar());
        tvNickname.setOnClickListener(v -> showEditNicknameDialog());

        setupBottomNavigation();
    }

    private void ensureLogin() {
        if (!sp.getBoolean(KEY_IS_LOGIN, false)) {
            toLoginAndFinish();
            return;
        }
        userId = sp.getInt(KEY_USER_ID, -1);
        username = sp.getString(KEY_USERNAME, "");
        if (userId <= 0 || TextUtils.isEmpty(username)) {
            toLoginAndFinish();
        }
    }

    private void loadUserInfo() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DBHelper.TABLE_USER_INFO,
                new String[]{DBHelper.COLUMN_USERNAME, DBHelper.COLUMN_NICKNAME, DBHelper.COLUMN_AVATAR_PATH},
                DBHelper.COLUMN_USER_ID + "=?",
                new String[]{String.valueOf(userId)}, null, null, null, "1");
        if (!c.moveToFirst()) {
            c.close();
            toLoginAndFinish();
            return;
        }
        username = c.getString(c.getColumnIndexOrThrow(DBHelper.COLUMN_USERNAME));
        String nickname = c.getString(c.getColumnIndexOrThrow(DBHelper.COLUMN_NICKNAME));
        String avatarPath = c.getString(c.getColumnIndexOrThrow(DBHelper.COLUMN_AVATAR_PATH));
        c.close();

        if (TextUtils.isEmpty(nickname)) nickname = username;
        tvNickname.setText(getString(R.string.mine_nickname_value, nickname));
        if (TextUtils.isEmpty(avatarPath)) {
            ivAvatar.setImageResource(R.drawable.default_avatar);
        } else {
            try {
                ivAvatar.setImageURI(Uri.parse(avatarPath));
            } catch (Exception e) {
                ivAvatar.setImageResource(R.drawable.default_avatar);
            }
        }
    }

    private void chooseAvatar() {
        if (userId <= 0) return;
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*");
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void saveAvatar(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        ContentValues v = new ContentValues();
        v.put(DBHelper.COLUMN_AVATAR_PATH, uri.toString());
        int rows = dbHelper.getWritableDatabase().update(DBHelper.TABLE_USER_INFO, v,
                DBHelper.COLUMN_USER_ID + "=?", new String[]{String.valueOf(userId)});
        if (rows > 0) {
            ivAvatar.setImageURI(uri);
            Toast.makeText(this, R.string.toast_avatar_update_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.toast_avatar_update_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditNicknameDialog() {
        if (userId <= 0) return;
        EditText editText = new EditText(this);
        editText.setHint(R.string.hint_nickname);
        editText.setText(getNicknameOnly());
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(p, p, p, p);

        new AlertDialog.Builder(this)
                .setTitle(R.string.mine_edit_nickname_title)
                .setView(editText)
                .setPositiveButton(R.string.action_confirm, (d, w) -> {
                    String newNickname = editText.getText().toString().trim();
                    if (TextUtils.isEmpty(newNickname)) {
                        Toast.makeText(this, R.string.toast_enter_nickname, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ContentValues v = new ContentValues();
                    v.put(DBHelper.COLUMN_NICKNAME, newNickname);
                    int rows = dbHelper.getWritableDatabase().update(DBHelper.TABLE_USER_INFO, v,
                            DBHelper.COLUMN_USER_ID + "=?", new String[]{String.valueOf(userId)});
                    if (rows > 0) {
                        tvNickname.setText(getString(R.string.mine_nickname_value, newNickname));
                        Toast.makeText(this, R.string.toast_nickname_update_success, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private String getNicknameOnly() {
        String full = tvNickname.getText() == null ? "" : tvNickname.getText().toString();
        String prefix = getString(R.string.mine_nickname_prefix);
        return full.startsWith(prefix) ? full.substring(prefix.length()).trim() : username;
    }

    private void logout() {
        sp.edit().putBoolean(KEY_IS_LOGIN, false).remove(KEY_USERNAME).remove(KEY_USER_ID).apply();
        Toast.makeText(this, R.string.toast_logout_success, Toast.LENGTH_SHORT).show();
        toLoginAndFinish();
    }

    private void toLoginAndFinish() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void loadSportSummary() {
        int totalCount = 0, totalDurationSeconds = 0;
        Cursor c = dbHelper.getReadableDatabase().query(DBHelper.TABLE_SPORT_RECORD,
                new String[]{DBHelper.COLUMN_ID, DBHelper.COLUMN_DURATION}, null, null, null, null, null);
        while (c.moveToNext()) {
            totalCount++;
            totalDurationSeconds += c.getInt(c.getColumnIndexOrThrow(DBHelper.COLUMN_DURATION));
        }
        c.close();
        int totalMinutes = totalDurationSeconds / 60;
        tvMineTotalCount.setText(getString(R.string.mine_total_count_value, totalCount));
        tvMineTotalMinutes.setText(getString(R.string.mine_total_minutes_value, totalMinutes));
        tvBadgeList.setText(buildBadgeText(totalCount, totalMinutes));
    }

    private String buildBadgeText(int totalCount, int totalMinutes) {
        List<String> badges = new ArrayList<>();
        badges.add(buildBadgeLine(totalCount >= 1, R.string.badge_newbie));
        badges.add(buildBadgeLine(totalCount >= 3, R.string.badge_persistence));
        badges.add(buildBadgeLine(totalCount >= 7, R.string.badge_expert));
        badges.add(buildBadgeLine(totalMinutes >= 60, R.string.badge_energy));
        return TextUtils.join("\n", badges);
    }

    private String buildBadgeLine(boolean unlocked, int badgeNameRes) {
        String status = unlocked ? getString(R.string.badge_status_unlocked) : getString(R.string.badge_status_locked);
        return getString(R.string.badge_item_format, getString(badgeNameRes), status);
    }

    private void showAboutSystemDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.mine_about_title)
                .setMessage(R.string.mine_about_message)
                .setPositiveButton(R.string.action_confirm, null).show();
    }

    private void showClearRecordsDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.clear_records_title)
                .setMessage(R.string.clear_records_message)
                .setPositiveButton(R.string.action_confirm, (d, w) -> clearRecords())
                .setNegativeButton(R.string.action_cancel, null).show();
    }

    private void clearRecords() {
        dbHelper.getWritableDatabase().delete(DBHelper.TABLE_SPORT_RECORD, null, null);
        loadSportSummary();
        Toast.makeText(this, R.string.toast_clear_records_success, Toast.LENGTH_SHORT).show();
    }

    private void setupBottomNavigation() {
        Button btnHome = findViewById(R.id.btnHome);
        Button btnCheckIn = findViewById(R.id.btnCheckIn);
        Button btnData = findViewById(R.id.btnData);
        btnHome.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        btnCheckIn.setOnClickListener(v -> startActivity(new Intent(this, CheckInActivity.class)));
        btnData.setOnClickListener(v -> startActivity(new Intent(this, DataActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureLogin();
        if (!isFinishing()) {
            loadUserInfo();
            loadSportSummary();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close();
    }
}
