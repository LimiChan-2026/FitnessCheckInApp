package com.example.fitnesscheckinapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

/**
 * 数据库辅助类：负责数据库的创建、表结构定义及版本升级管理
 */
public class DBHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "fitness_check_in.db"; // 数据库文件名
    public static final int DATABASE_VERSION = 4;

    // 运动记录表 (sport_record) 的表名及列名定义
    public static final String TABLE_SPORT_RECORD = "sport_record";
    public static final String COLUMN_ID = "id";                // 自增主键
    public static final String COLUMN_SPORT_TYPE = "sport_type"; // 运动类型（如：跑步、骑行）
    public static final String COLUMN_DURATION = "duration";     // 运动时长（秒）
    public static final String COLUMN_CALORIE = "calorie";       // 消耗卡路里
    public static final String COLUMN_CREATE_TIME = "create_time"; // 记录创建时间
    public static final String COLUMN_USERNAME = "username";     // 所属用户名（用于区分不同用户的数据）
    public static final String COLUMN_REMARK = "remark";         // 运动备注

    // 用户信息表 (user_info)
    public static final String TABLE_USER_INFO = "user_info";
    public static final String COLUMN_USER_ID = "id";           // 自增主键
    public static final String COLUMN_PASSWORD = "password";     // 登录密码
    public static final String COLUMN_NICKNAME = "nickname";     // 用户昵称
    public static final String COLUMN_AVATAR_PATH = "avatar_path"; // 头像图片本地路径
    public static final String COLUMN_USER_CREATE_TIME = "create_time"; // 账号注册时间

    /**
     * 构造函数
     */
    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * 数据库第一次创建时调用，在此创建所有的表结构
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(buildCreateSportRecordTableSql()); // 创建运动记录表
        db.execSQL(buildCreateUserInfoTableSql());    // 创建用户信息表
    }

    /**
     * 数据库版本升级时调用
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 版本 1 升级到 2：增加用户信息表
        if (oldVersion < 2) {
            db.execSQL(buildCreateUserInfoTableSql());
        }
        // 版本 2 升级到 3：在运动记录表中增加用户名列，用于多用户隔离
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SPORT_RECORD + " ADD COLUMN " + COLUMN_USERNAME + " TEXT");
            } catch (Exception ignored) {
            }
        }
        // 版本 3 升级到 4：在运动记录表中增加备注列
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SPORT_RECORD + " ADD COLUMN " + COLUMN_REMARK + " TEXT");
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 构建创建运动记录表的 SQL 语句
     */
    private String buildCreateSportRecordTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_SPORT_RECORD + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_SPORT_TYPE + " TEXT NOT NULL, "
                + COLUMN_DURATION + " INTEGER NOT NULL, "
                + COLUMN_CALORIE + " INTEGER NOT NULL, "
                + COLUMN_CREATE_TIME + " TEXT NOT NULL, "
                + COLUMN_USERNAME + " TEXT, "
                + COLUMN_REMARK + " TEXT)";
    }

    /**
     * 构建创建用户信息表的 SQL 语句
     */
    private String buildCreateUserInfoTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_USER_INFO + " ("
                + COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, "
                + COLUMN_PASSWORD + " TEXT NOT NULL, "
                + COLUMN_NICKNAME + " TEXT, "
                + COLUMN_AVATAR_PATH + " TEXT, "
                + COLUMN_USER_CREATE_TIME + " TEXT NOT NULL)";
    }
}
