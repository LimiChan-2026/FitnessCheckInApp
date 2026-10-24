package fitness.server;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 业务逻辑层：用户、打卡记录、排行榜、动态流、点赞、关注。
 * 所有方法返回可直接被 Jackson 序列化为 JSON 的 Map/List 结构。
 */
public class FitnessService {

    private final Database db;

    public FitnessService(Database db) {
        this.db = db;
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    // ==================== 用户 ====================

    /** 注册：用户名唯一性检查 + 密码哈希入库 */
    public Map<String, Object> register(String username, String password, String nickname) throws SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new ApiException(400, "用户名或密码不能为空");
        }
        Integer existing = db.queryOne(
                "SELECT id FROM users WHERE username = ?", rs -> rs.getInt("id"), username);
        if (existing != null) {
            throw new ApiException(409, "账号已存在");
        }
        db.update("INSERT INTO users(username, password_hash, nickname, create_time) VALUES(?,?,?,?)",
                username, PasswordUtil.hash(password),
                (nickname == null || nickname.trim().isEmpty()) ? username : nickname.trim(), now());
        return userBrief(username);
    }

    /** 登录：校验密码哈希 */
    public Map<String, Object> login(String username, String password) throws SQLException {
        String stored = db.queryOne(
                "SELECT password_hash FROM users WHERE username = ?", rs -> rs.getString(1), username);
        if (stored == null || !PasswordUtil.verify(password, stored)) {
            throw new ApiException(401, "账号或密码错误");
        }
        return userBrief(username);
    }

    /** 修改密码 */
    public Map<String, Object> changePassword(String username, String oldPassword, String newPassword) throws SQLException {
        String stored = db.queryOne(
                "SELECT password_hash FROM users WHERE username = ?", rs -> rs.getString(1), username);
        if (stored == null || !PasswordUtil.verify(oldPassword, stored)) {
            throw new ApiException(401, "原密码错误");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new ApiException(400, "新密码不能为空");
        }
        db.update("UPDATE users SET password_hash = ? WHERE username = ?",
                PasswordUtil.hash(newPassword), username);
        return ok("密码修改成功");
    }

    private Map<String, Object> userBrief(String username) throws SQLException {
        return db.queryOne(
                "SELECT id, username, nickname, create_time FROM users WHERE username = ?",
                rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("username", rs.getString("username"));
                    m.put("nickname", rs.getString("nickname"));
                    m.put("createTime", rs.getString("create_time"));
                    return m;
                }, username);
    }

    // ==================== 打卡记录 ====================

    /** 上报一条运动打卡记录（客户端保存成功后同步上云） */
    public Map<String, Object> addRecord(String username, String sportType, int duration,
                                         int calorie, String createTime, String remark) throws SQLException {
        if (sportType == null || sportType.trim().isEmpty()) {
            throw new ApiException(400, "运动类型不能为空");
        }
        if (duration <= 0) {
            throw new ApiException(400, "运动时长必须大于0");
        }
        if (createTime == null || createTime.trim().isEmpty()) {
            createTime = now();
        }
        // 与插入共用同一连接读取自增ID（last_insert_rowid 按连接隔离，跨连接会读到 0）
        Integer id;
        try (var conn = db.open();
             var ps = conn.prepareStatement(
                     "INSERT INTO sport_records(username, sport_type, duration, calorie, create_time, remark) VALUES(?,?,?,?,?,?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, sportType);
            ps.setInt(3, duration);
            ps.setInt(4, calorie);
            ps.setString(5, createTime);
            ps.setString(6, remark);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                id = keys.next() ? keys.getInt(1) : null;
            }
        }
        Map<String, Object> m = ok("打卡记录已同步");
        m.put("recordId", id);
        return m;
    }

    /** 删除一条打卡记录（连同其点赞数据） */
    public Map<String, Object> deleteRecord(String username, int recordId) throws SQLException {
        int rows = db.update("DELETE FROM sport_records WHERE id = ? AND username = ?", recordId, username);
        if (rows == 0) {
            throw new ApiException(404, "记录不存在或无权删除");
        }
        db.update("DELETE FROM likes WHERE record_id = ?", recordId);
        return ok("记录已删除");
    }

    /** 清空某用户的全部打卡记录（连同点赞数据） */
    public Map<String, Object> clearRecords(String username) throws SQLException {
        List<Integer> ids = db.queryList(
                "SELECT id FROM sport_records WHERE username = ?", rs -> rs.getInt(1), username);
        for (Integer id : ids) {
            db.update("DELETE FROM likes WHERE record_id = ?", id);
        }
        db.update("DELETE FROM sport_records WHERE username = ?", username);
        return ok("记录已清空");
    }

    // ==================== 排行榜 ====================

    /**
     * 排行榜：按最近 days 天的运动总时长排名。
     * days <= 0 表示总榜（全部历史）。
     */
    public List<Map<String, Object>> leaderboard(int days, int limit) throws SQLException {
        String sql;
        if (days > 0) {
            // SQLite 的 datetime('now','-N days') 直接在 SQL 里算时间窗
            sql = "SELECT u.username, IFNULL(u.nickname, u.username) AS nickname,"
                    + " COUNT(r.id) AS check_in_count,"
                    + " IFNULL(SUM(r.duration), 0) AS total_duration,"
                    + " IFNULL(SUM(r.calorie), 0) AS total_calorie"
                    + " FROM users u"
                    + " LEFT JOIN sport_records r ON r.username = u.username"
                    + " AND r.create_time >= datetime('now', 'localtime', ?)"
                    + " GROUP BY u.username"
                    + " HAVING check_in_count > 0"
                    + " ORDER BY total_duration DESC"
                    + " LIMIT ?";
            return db.queryList(sql, FitnessService::leaderboardRow, "-" + days + " days", limit);
        }
        sql = "SELECT u.username, IFNULL(u.nickname, u.username) AS nickname,"
                + " COUNT(r.id) AS check_in_count,"
                + " IFNULL(SUM(r.duration), 0) AS total_duration,"
                + " IFNULL(SUM(r.calorie), 0) AS total_calorie"
                + " FROM users u"
                + " LEFT JOIN sport_records r ON r.username = u.username"
                + " GROUP BY u.username"
                + " HAVING check_in_count > 0"
                + " ORDER BY total_duration DESC"
                + " LIMIT ?";
        return db.queryList(sql, FitnessService::leaderboardRow, limit);
    }

    private static Map<String, Object> leaderboardRow(java.sql.ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", rs.getString("username"));
        m.put("nickname", rs.getString("nickname"));
        m.put("checkInCount", rs.getInt("check_in_count"));
        m.put("totalDuration", rs.getInt("total_duration"));
        m.put("totalCalorie", rs.getInt("total_calorie"));
        return m;
    }

    // ==================== 动态流 ====================

    /**
     * 好友动态流：返回我关注的人（含我自己）的打卡记录，按时间倒序。
     * 每条动态附带点赞数、评论数（本课程实现点赞，评论以点赞+详情呈现）与我是否已点赞。
     */
    public List<Map<String, Object>> feed(String username, int limit) throws SQLException {
        String sql = "SELECT r.id, r.username, IFNULL(u.nickname, r.username) AS nickname,"
                + " r.sport_type, r.duration, r.calorie, r.create_time, r.remark,"
                + " (SELECT COUNT(*) FROM likes l WHERE l.record_id = r.id) AS like_count,"
                + " EXISTS(SELECT 1 FROM likes l2 WHERE l2.record_id = r.id AND l2.username = ?) AS liked_by_me"
                + " FROM sport_records r"
                + " JOIN users u ON u.username = r.username"
                + " WHERE r.username = ? OR r.username IN (SELECT followee FROM follows WHERE follower = ?)"
                + " ORDER BY r.id DESC"
                + " LIMIT ?";
        return db.queryList(sql, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", rs.getInt("id"));
            m.put("username", rs.getString("username"));
            m.put("nickname", rs.getString("nickname"));
            m.put("sportType", rs.getString("sport_type"));
            m.put("duration", rs.getInt("duration"));
            m.put("calorie", rs.getInt("calorie"));
            m.put("createTime", rs.getString("create_time"));
            m.put("remark", rs.getString("remark"));
            m.put("likeCount", rs.getInt("like_count"));
            m.put("likedByMe", rs.getInt("liked_by_me") == 1);
            return m;
        }, username, username, username, limit);
    }

    /** 点赞 / 取消点赞（幂等：重复点赞不报错，再点一次即取消） */
    public Map<String, Object> toggleLike(String username, int recordId) throws SQLException {
        Integer owner = db.queryOne(
                "SELECT id FROM sport_records WHERE id = ?", rs -> rs.getInt(1), recordId);
        if (owner == null) {
            throw new ApiException(404, "动态不存在");
        }
        Integer existing = db.queryOne(
                "SELECT id FROM likes WHERE record_id = ? AND username = ?", rs -> rs.getInt(1),
                recordId, username);
        boolean liked;
        if (existing == null) {
            db.update("INSERT INTO likes(record_id, username, create_time) VALUES(?,?,?)",
                    recordId, username, now());
            liked = true;
        } else {
            db.update("DELETE FROM likes WHERE record_id = ? AND username = ?", recordId, username);
            liked = false;
        }
        Integer count = db.queryOne(
                "SELECT COUNT(*) FROM likes WHERE record_id = ?", rs -> rs.getInt(1), recordId);
        Map<String, Object> m = ok(liked ? "点赞成功" : "已取消点赞");
        m.put("liked", liked);
        m.put("likeCount", count);
        return m;
    }

    // ==================== 关注 ====================

    /** 关注 / 取消关注（幂等切换） */
    public Map<String, Object> toggleFollow(String follower, String followee) throws SQLException {
        if (follower.equals(followee)) {
            throw new ApiException(400, "不能关注自己");
        }
        Integer target = db.queryOne(
                "SELECT id FROM users WHERE username = ?", rs -> rs.getInt(1), followee);
        if (target == null) {
            throw new ApiException(404, "用户不存在");
        }
        Integer existing = db.queryOne(
                "SELECT id FROM follows WHERE follower = ? AND followee = ?", rs -> rs.getInt(1),
                follower, followee);
        boolean following;
        if (existing == null) {
            db.update("INSERT INTO follows(follower, followee, create_time) VALUES(?,?,?)",
                    follower, followee, now());
            following = true;
        } else {
            db.update("DELETE FROM follows WHERE follower = ? AND followee = ?", follower, followee);
            following = false;
        }
        Map<String, Object> m = ok(following ? "关注成功" : "已取消关注");
        m.put("following", following);
        return m;
    }

    /** 用户搜索（用于添加关注）：按用户名或昵称模糊匹配，排除自己 */
    public List<Map<String, Object>> searchUsers(String keyword, String excludeUsername) throws SQLException {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return db.queryList(
                "SELECT username, nickname FROM users"
                        + " WHERE (username LIKE ? OR IFNULL(nickname, '') LIKE ?) AND username <> ?"
                        + " ORDER BY username LIMIT 20",
                rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", rs.getString("username"));
                    m.put("nickname", rs.getString("nickname"));
                    return m;
                }, like, like, excludeUsername);
    }

    /** 我关注了谁 / 谁关注我 的数量与列表 */
    public Map<String, Object> myFollowInfo(String username) throws SQLException {
        List<String> following = db.queryList(
                "SELECT followee FROM follows WHERE follower = ? ORDER BY id DESC",
                rs -> rs.getString(1), username);
        List<String> followers = db.queryList(
                "SELECT follower FROM follows WHERE followee = ? ORDER BY id DESC",
                rs -> rs.getString(1), username);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("following", following);
        m.put("followers", followers);
        return m;
    }

    /** 服务器连通性测试 */
    public Map<String, Object> ping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 0);
        m.put("message", "pong");
        m.put("serverTime", now());
        return m;
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 0);
        m.put("message", message);
        return m;
    }

    /** 业务异常：message 会作为 JSON 错误体返回给客户端 */
    public static class ApiException extends RuntimeException {
        public final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
