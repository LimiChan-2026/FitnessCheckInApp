package fitness.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 服务端数据库：SQLite 单文件数据库，负责建表与连接管理。
 * 表结构：
 * - users          用户表（用户名唯一，密码存加盐哈希）
 * - sport_records  运动打卡记录表（与客户端本地表字段对齐）
 * - likes          动态点赞表（记录谁给哪条动态点过赞）
 * - follows        关注关系表（谁关注了谁）
 */
public class Database {

    private final String dbPath;

    public Database(String dbPath) {
        this.dbPath = dbPath;
        try {
            Files.createDirectories(Path.of(dbPath).toAbsolutePath().getParent());
        } catch (Exception e) {
            throw new RuntimeException("无法创建数据库目录: " + dbPath, e);
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT UNIQUE NOT NULL,"
                    + "password_hash TEXT NOT NULL,"
                    + "nickname TEXT,"
                    + "create_time TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS sport_records ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT NOT NULL,"
                    + "sport_type TEXT NOT NULL,"
                    + "duration INTEGER NOT NULL,"
                    + "calorie INTEGER NOT NULL,"
                    + "create_time TEXT NOT NULL,"
                    + "remark TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS likes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "record_id INTEGER NOT NULL,"
                    + "username TEXT NOT NULL,"
                    + "create_time TEXT NOT NULL,"
                    + "UNIQUE(record_id, username))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS follows ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "follower TEXT NOT NULL,"       // 关注者
                    + "followee TEXT NOT NULL,"       // 被关注者
                    + "create_time TEXT NOT NULL,"
                    + "UNIQUE(follower, followee))");
        } catch (SQLException e) {
            throw new RuntimeException("初始化数据库失败", e);
        }
    }

    /** 打开一个新的数据库连接（调用方负责关闭） */
    public Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /** 便捷方法：执行更新并返回受影响行数 */
    public int update(String sql, Object... args) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    /** 便捷方法：执行查询并把结果集映射为单个对象 */
    public <T> T queryOne(String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapper.map(rs) : null;
            }
        }
    }

    /** 便捷方法：执行查询并把结果集收集为列表 */
    public <T> java.util.List<T> queryList(String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        java.util.List<T> list = new java.util.ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapper.map(rs));
                }
            }
        }
        return list;
    }

    private void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
