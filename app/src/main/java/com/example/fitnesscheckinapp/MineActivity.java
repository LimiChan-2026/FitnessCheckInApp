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

/**
 * “我的”个人中心页面：提供用户信息展示（头像、昵称）、运动成就统计、勋章系统、个人资料修改及退出登录等功能。
 */
public class MineActivity extends AppCompatActivity {

    // SharedPreferences 存储键名
    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID = "user_id";

    // UI 控件
    private ImageView ivAvatar;         // 用户头像
    private TextView tvNickname;        // 用户昵称显示
    private TextView tvMineTotalCount;  // 累计运动次数
    private TextView tvMineTotalMinutes;// 累计运动时长
    private TextView tvBadgeList;       // 勋章列表显示
    
    private SharedPreferences sp;
    private DBHelper dbHelper;

    private int userId = -1;            // 当前登录用户的数据库 ID
    private String username = "";       // 当前登录的用户名

    // Android 13+ 权限及图片选择器的启动器
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mine);

        sp = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        dbHelper = new DBHelper(this);

        // 初始化权限请求启动器
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        // 权限授权成功，打开相册
                        pickImageLauncher.launch("image/*");
                    } else {
                        Toast.makeText(this, R.string.toast_permission_required, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 初始化图片选择启动器
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && userId > 0) {
                        saveAvatar(uri); // 用户选择了图片后，保存并更新头像
                    }
                }
        );

        // 绑定 UI 控件
        ivAvatar = findViewById(R.id.ivAvatar);
        tvNickname = findViewById(R.id.tvNickname);
        tvMineTotalCount = findViewById(R.id.tvMineTotalCount);
        tvMineTotalMinutes = findViewById(R.id.tvMineTotalMinutes);
        tvBadgeList = findViewById(R.id.tvBadgeList);

        // 设置各功能按钮点击事件
        findViewById(R.id.btnEditNickname).setOnClickListener(v -> showEditNicknameDialog());
        findViewById(R.id.btnEditAvatar).setOnClickListener(v -> chooseAvatar());
        findViewById(R.id.btnAboutSystem).setOnClickListener(v -> showAboutSystemDialog());
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
        findViewById(R.id.btnClearRecords).setOnClickListener(v -> showClearRecordsDialog());
        
        // 点击头像或昵称也可以进行修改
        ivAvatar.setOnClickListener(v -> chooseAvatar());
        tvNickname.setOnClickListener(v -> showEditNicknameDialog());

        setupBottomNavigation();
    }

    /**
     * 校验登录状态，若未登录则强制跳转至登录页
     */
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

    /**
     * 从数据库加载用户的最新信息（昵称、头像路径）。
     * 查询在后台线程执行，避免阻塞页面切换动画。
     */
    private void loadUserInfo() {
        Bg.io(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query(DBHelper.TABLE_USER_INFO,
                    new String[]{DBHelper.COLUMN_USERNAME, DBHelper.COLUMN_NICKNAME, DBHelper.COLUMN_AVATAR_PATH},
                    DBHelper.COLUMN_USER_ID + "=?",
                    new String[]{String.valueOf(userId)}, null, null, null, "1");

            String[] result;
            if (c.moveToFirst()) {
                result = new String[]{
                        c.getString(c.getColumnIndexOrThrow(DBHelper.COLUMN_USERNAME)),
                        c.getString(c.getColumnIndexOrThrow(DBHelper.COLUMN_NICKNAME)),
                        c.getString(c.getColumnIndexOrThrow(DBHelper.COLUMN_AVATAR_PATH))
                };
            } else {
                result = null;
            }
            c.close();
            return result;
        }, row -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (row == null) {
                toLoginAndFinish();
                return;
            }

            username = row[0];
            String nickname = row[1];
            String avatarPath = row[2];

            // 更新 UI 显示
            if (TextUtils.isEmpty(nickname)) nickname = username;
            tvNickname.setText(getString(R.string.mine_nickname_value, nickname));

            if (TextUtils.isEmpty(avatarPath)) {
                ivAvatar.setImageResource(R.drawable.default_avatar);
            } else {
                try {
                    // 加载保存的图片 URI
                    ivAvatar.setImageURI(Uri.parse(avatarPath));
                } catch (Exception e) {
                    ivAvatar.setImageResource(R.drawable.default_avatar);
                }
            }
        });
    }

    /**
     * 打开系统图片选择器，会先进行权限检查
     */
    private void chooseAvatar() {
        if (userId <= 0) return;
        
        // 根据系统版本选择对应的权限
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
                
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*");
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    /**
     * 将选中的图片 URI 保存到数据库，并更新头像显示
     */
    private void saveAvatar(Uri uri) {
        try {
            // 获取持久化的 URI 读取权限（防止手机重启后无法访问该图片）
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

    /**
     * 弹出修改昵称的对话框
     */
    private void showEditNicknameDialog() {
        if (userId <= 0) return;
        
        EditText editText = new EditText(this);
        editText.setHint(R.string.hint_nickname);
        editText.setText(getNicknameOnly()); // 预填当前昵称
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
                    
                    // 更新数据库中的昵称
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

    /**
     * 辅助方法：从“昵称：xxx”文案中提取出纯昵称部分
     */
    private String getNicknameOnly() {
        String full = tvNickname.getText() == null ? "" : tvNickname.getText().toString();
        String prefix = getString(R.string.mine_nickname_prefix);
        return full.startsWith(prefix) ? full.substring(prefix.length()).trim() : username;
    }

    /**
     * 退出登录：清空登录态缓存并跳转
     */
    private void logout() {
        sp.edit().putBoolean(KEY_IS_LOGIN, false).remove(KEY_USERNAME).remove(KEY_USER_ID).apply();
        Toast.makeText(this, R.string.toast_logout_success, Toast.LENGTH_SHORT).show();
        toLoginAndFinish();
    }

    /**
     * 跳转至登录页并关闭当前页面的所有 Task
     */
    private void toLoginAndFinish() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /**
     * 从运动记录表中汇总当前用户的运动成就数据。
     * 查询与勋章文案构建在后台线程执行，完成后回主线程刷新。
     */
    private void loadSportSummary() {
        Bg.io(() -> {
            int totalCount = 0;
            int totalDurationSeconds = 0;

            Cursor c = dbHelper.getReadableDatabase().query(
                    DBHelper.TABLE_SPORT_RECORD,
                    new String[]{DBHelper.COLUMN_ID, DBHelper.COLUMN_DURATION},
                    DBHelper.COLUMN_USERNAME + "=?",
                    new String[]{username},
                    null, null, null
            );

            while (c.moveToNext()) {
                totalCount++;
                totalDurationSeconds += c.getInt(c.getColumnIndexOrThrow(DBHelper.COLUMN_DURATION));
            }
            c.close();

            return new int[]{totalCount, totalDurationSeconds / 60};
        }, result -> {
            if (isFinishing() || isDestroyed() || result == null) {
                return;
            }
            int totalCount = result[0];
            int totalMinutes = result[1];

            // 更新 UI 统计
            tvMineTotalCount.setText(getString(R.string.mine_total_count_value, totalCount));
            tvMineTotalMinutes.setText(getString(R.string.mine_total_minutes_value, totalMinutes));

            // 勋章文案需要依赖 UI 上下文重建（buildBadgeText 内部调用 getString）
            tvBadgeList.setText(buildBadgeText(totalCount, totalMinutes));
        });
    }

    /**
     * 根据运动数据逻辑判断并生成勋章列表
     */
    private String buildBadgeText(int totalCount, int totalMinutes) {
        List<String> badges = new ArrayList<>();
        // 运动1次 -> 新手勋章
        badges.add(buildBadgeLine(totalCount >= 1, R.string.badge_newbie));
        // 运动3次 -> 坚持勋章
        badges.add(buildBadgeLine(totalCount >= 3, R.string.badge_persistence));
        // 运动7次 -> 达人勋章
        badges.add(buildBadgeLine(totalCount >= 7, R.string.badge_expert));
        // 时长满60分钟 -> 活力勋章
        badges.add(buildBadgeLine(totalMinutes >= 60, R.string.badge_energy));
        
        return TextUtils.join("\n", badges);
    }

    /**
     * 构建单行勋章文案：显示勋章名及[已解锁/未解锁]状态
     */
    private String buildBadgeLine(boolean unlocked, int badgeNameRes) {
        String status = unlocked ? getString(R.string.badge_status_unlocked) : getString(R.string.badge_status_locked);
        return getString(R.string.badge_item_format, getString(badgeNameRes), status);
    }

    /**
     * 显示“关于系统”对话框
     */
    private void showAboutSystemDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.mine_about_title)
                .setMessage(R.string.mine_about_message)
                .setPositiveButton(R.string.action_confirm, null).show();
    }

    /**
     * 弹出清空所有运动记录的二次确认对话框
     */
    private void showClearRecordsDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.clear_records_title)
                .setMessage(R.string.clear_records_message)
                .setPositiveButton(R.string.action_confirm, (d, w) -> clearRecords())
                .setNegativeButton(R.string.action_cancel, null).show();
    }

    /**
     * 执行清空当前用户所有运动记录的操作
     */
    private void clearRecords() {
        dbHelper.getWritableDatabase().delete(
                DBHelper.TABLE_SPORT_RECORD,
                DBHelper.COLUMN_USERNAME + "=?",
                new String[]{username}
        );
        loadSportSummary(); // 刷新数据展示
        Toast.makeText(this, R.string.toast_clear_records_success, Toast.LENGTH_SHORT).show();
    }

    /**
     * 底部导航跳转逻辑
     */
    private void setupBottomNavigation() {
        Button btnHome = findViewById(R.id.btnHome);
        Button btnCheckIn = findViewById(R.id.btnCheckIn);
        Button btnSocial = findViewById(R.id.btnSocial);
        Button btnData = findViewById(R.id.btnData);
        btnHome.setOnClickListener(v -> Nav.go(this, MainActivity.class));
        btnCheckIn.setOnClickListener(v -> Nav.go(this, CheckInActivity.class));
        btnSocial.setOnClickListener(v -> Nav.go(this, SocialActivity.class));
        btnData.setOnClickListener(v -> Nav.go(this, DataActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureLogin();
        if (!isFinishing()) {
            loadUserInfo();      // 刷新头像昵称
            loadSportSummary();  // 刷新运动成就和勋章
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close();
    }
}
