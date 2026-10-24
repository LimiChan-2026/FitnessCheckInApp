package fitness.server;

import io.javalin.Javalin;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.TestTool;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 集成测试：app 直接 start(0) 随机端口，TestTool 提供 HTTP 客户端，
 * 用真实 HTTP 请求覆盖「注册—登录—打卡—排行榜—点赞—关注—动态流」全链路。
 * 使用 org.json 解析响应（与服务端 Jackson 序列化无关，测试侧独立解析）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FitnessApiTest {

    private static Javalin app;
    private static HttpClient client;
    private static final String DB_PATH = "build/test-db/it_fitness.db";

    @BeforeAll
    static void setup() throws Exception {
        // 清空历史数据，保证测试可重复运行（幂等）
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM likes");
            st.executeUpdate("DELETE FROM follows");
            st.executeUpdate("DELETE FROM sport_records");
            st.executeUpdate("DELETE FROM users");
        }
        app = Main.createApp(DB_PATH);
        app.start(0); // 随机端口
        client = new HttpClient(app, new okhttp3.OkHttpClient());
    }

    @AfterAll
    static void teardown() {
        app.stop();
    }

    private static JSONObject obj(okhttp3.Response response) throws Exception {
        return new JSONObject(response.body().string());
    }

    private static JSONArray arr(okhttp3.Response response) throws Exception {
        return new JSONArray(response.body().string());
    }

    @Test
    @Order(1)
    void pingShouldReturnServerTime() throws Exception {
        var response = client.get("/api/ping");
        assertEquals(200, response.code());
        JSONObject json = obj(response);
        assertEquals("pong", json.getString("message"));
        assertTrue(json.has("serverTime"));
    }

    @Test
    @Order(2)
    void registerLoginAndRejectWrongPassword() throws Exception {
        // 注册
        var reg = client.post("/api/register",
                "{\"username\":\"alice\",\"password\":\"123456\",\"nickname\":\"爱丽丝\"}");
        assertEquals(200, reg.code());
        assertEquals(0, obj(reg).getInt("code"));

        // 正确密码登录
        var login = client.post("/api/login",
                "{\"username\":\"alice\",\"password\":\"123456\"}");
        assertEquals(200, login.code());
        assertEquals("alice", obj(login).getString("username"));

        // 错误密码登录应返回 401
        var bad = client.post("/api/login",
                "{\"username\":\"alice\",\"password\":\"wrong\"}");
        assertEquals(401, bad.code());
    }

    @Test
    @Order(3)
    void duplicateRegisterShouldReturn409() throws Exception {
        client.post("/api/register", "{\"username\":\"bob\",\"password\":\"abc123\"}");
        var dup = client.post("/api/register", "{\"username\":\"bob\",\"password\":\"xyz\"}");
        assertEquals(409, dup.code());
    }

    @Test
    @Order(4)
    void passwordShouldBeStoredAsHashNotPlaintext() throws Exception {
        // 直接检查数据库内容：密码字段不能是明文
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT password_hash FROM users WHERE username = 'alice'")) {
            assertTrue(rs.next());
            String stored = rs.getString(1);
            assertNotNull(stored);
            assertFalse(stored.equals("123456"));
            assertTrue(stored.contains("$"), "应存储为 salt$hash 格式");
        }
    }

    @Test
    @Order(5)
    void recordThenLeaderboardShowsUser() throws Exception {
        client.post("/api/register", "{\"username\":\"carol\",\"password\":\"pass1\"}");
        client.post("/api/records?username=carol",
                "{\"sportType\":\"跑步\",\"duration\":1800,\"calorie\":150,\"remark\":\"晨跑5公里\"}");

        var board = client.get("/api/leaderboard?days=7");
        assertEquals(200, board.code());
        JSONArray rows = arr(board);
        assertTrue(rows.length() >= 1, "排行榜应至少有一条记录");
        boolean found = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if ("carol".equals(row.getString("username"))) {
                found = true;
                assertEquals(1800, row.getInt("totalDuration"));
                assertEquals(1, row.getInt("checkInCount"));
            }
        }
        assertTrue(found, "排行榜应包含用户 carol");
    }

    @Test
    @Order(6)
    void likeToggleShouldBeIdempotent() throws Exception {
        client.post("/api/register", "{\"username\":\"dave\",\"password\":\"pass2\"}");
        var add = client.post("/api/records?username=dave",
                "{\"sportType\":\"骑行\",\"duration\":600,\"calorie\":80}");
        int recordId = obj(add).getInt("recordId");

        // 第一次点赞（okhttp 的 body().string() 只能读一次，先存 JSONObject）
        var like1 = client.post("/api/records/" + recordId + "/like?username=alice");
        JSONObject like1Json = obj(like1);
        assertTrue(like1Json.getBoolean("liked"));
        assertEquals(1, like1Json.getInt("likeCount"));

        // 再点一次 = 取消
        var like2 = client.post("/api/records/" + recordId + "/like?username=alice");
        JSONObject like2Json = obj(like2);
        assertFalse(like2Json.getBoolean("liked"));
        assertEquals(0, like2Json.getInt("likeCount"));
    }

    @Test
    @Order(7)
    void followThenFeedShowsFriendRecord() throws Exception {
        client.post("/api/register", "{\"username\":\"erin\",\"password\":\"pass3\"}");
        var follow = client.post("/api/follows?username=erin", "{\"followee\":\"carol\"}");
        assertTrue(obj(follow).getBoolean("following"));

        // erin 的动态流应能看到 carol 的打卡
        var feed = client.get("/api/feed?username=erin");
        JSONArray items = arr(feed);
        boolean found = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if ("carol".equals(item.getString("username"))) {
                found = true;
                assertEquals("跑步", item.getString("sportType"));
            }
        }
        assertTrue(found, "关注后动态流应包含 carol 的打卡记录");
    }

    @Test
    @Order(8)
    void feedShouldNotShowStrangerRecord() throws Exception {
        // 新注册的陌生人未关注任何人，自己也没有记录，动态流应为空
        client.post("/api/register", "{\"username\":\"grace\",\"password\":\"pass5\"}");
        var feed = client.get("/api/feed?username=grace");
        assertEquals(0, arr(feed).length());
    }

    @Test
    @Order(9)
    void searchUsersShouldExcludeSelf() throws Exception {
        var result = client.get("/api/users/search?keyword=a&username=alice");
        JSONArray users = arr(result);
        for (int i = 0; i < users.length(); i++) {
            assertFalse("alice".equals(users.getJSONObject(i).getString("username")),
                    "搜索结果不应包含搜索者自己");
        }
    }

    @Test
    @Order(10)
    void invalidDurationShouldBeRejected() {
        client.post("/api/register", "{\"username\":\"henry\",\"password\":\"pass6\"}");
        var bad = client.post("/api/records?username=henry",
                "{\"sportType\":\"跑步\",\"duration\":0,\"calorie\":0}");
        assertEquals(400, bad.code());
    }

    @Test
    @Order(11)
    void deleteAndClearRecordsWork() throws Exception {
        client.post("/api/register", "{\"username\":\"iris\",\"password\":\"pass7\"}");
        var add = client.post("/api/records?username=iris",
                "{\"sportType\":\"跳绳\",\"duration\":300,\"calorie\":50}");
        int recordId = obj(add).getInt("recordId");

        // 删除单条
        var del = client.delete("/api/records/" + recordId + "?username=iris");
        assertEquals(200, del.code());

        // 再添加两条后清空
        client.post("/api/records?username=iris",
                "{\"sportType\":\"跑步\",\"duration\":600,\"calorie\":100}");
        client.post("/api/records?username=iris",
                "{\"sportType\":\"健身\",\"duration\":900,\"calorie\":200}");
        var clear = client.delete("/api/records?username=iris");
        assertEquals(200, clear.code());

        // 排行榜不再包含 iris
        var board = client.get("/api/leaderboard");
        JSONArray rows = arr(board);
        for (int i = 0; i < rows.length(); i++) {
            assertFalse("iris".equals(rows.getJSONObject(i).getString("username")),
                    "清空记录后排行榜不应包含 iris");
        }
    }
}

