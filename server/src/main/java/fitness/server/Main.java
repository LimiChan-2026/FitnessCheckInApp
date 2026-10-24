package fitness.server;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

/**
 * 服务端入口：启动 Javalin REST API。
 * 默认监听 0.0.0.0:8080，Android 模拟器用 http://10.0.2.2:8080 访问本机。
 * 启动方式：gradlew :server:run
 */
public class Main {

    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        Javalin app = createApp(dbPath());
        app.start("0.0.0.0", port);
        // 横幅使用纯 ASCII，避免 Windows GBK 控制台显示 UTF-8 中文时乱码
        System.out.println("==============================================");
        System.out.println("  Fitness server started: http://0.0.0.0:" + port);
        System.out.println("  DB file : " + dbPath());
        System.out.println("  Emulator URL : http://10.0.2.2:" + port);
        System.out.println("  Real device URL : http://<PC-LAN-IP>:" + port);
        System.out.println("  Stop server: Ctrl + C");
        System.out.println("==============================================");
    }

    /** 构建路由（测试中直接复用，不真正监听端口） */
    public static Javalin createApp(String dbPath) {
        Database db = new Database(dbPath);
        FitnessService service = new FitnessService(db);

        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson()); // 用 Jackson 序列化 Map/List
        });

        // 所有响应强制 UTF-8 编码（防止 GBK 控制台环境下中文 JSON 乱码）
        app.before(ctx -> ctx.res().setCharacterEncoding("UTF-8"));

        // 全局业务异常处理：统一返回 {code, message}
        app.exception(FitnessService.ApiException.class, (e, ctx) -> {
            ctx.status(e.status).json(Maps.of("code", e.status, "message", e.getMessage()));
        });
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500).json(Maps.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        });

        // ================ 基础 ================
        app.get("/api/ping", ctx -> ctx.json(service.ping()));

        // ================ 用户 ================
        app.post("/api/register", ctx -> {
            var body = ctx.bodyAsClass(RegisterRequest.class);
            // 统一响应格式：{code:0, message, ...用户字段}
            var user = service.register(body.username, body.password, body.nickname);
            ctx.json(withCode(user, "注册成功"));
        });
        app.post("/api/login", ctx -> {
            var body = ctx.bodyAsClass(RegisterRequest.class);
            var user = service.login(body.username, body.password);
            ctx.json(withCode(user, "登录成功"));
        });
        app.post("/api/change-password", ctx -> {
            var body = ctx.bodyAsClass(ChangePasswordRequest.class);
            ctx.json(service.changePassword(body.username, body.oldPassword, body.newPassword));
        });

        // ================ 打卡记录 ================
        app.post("/api/records", ctx -> {
            var body = ctx.bodyAsClass(RecordRequest.class);
            ctx.json(service.addRecord(ctx.queryParam("username"), body.sportType,
                    body.duration, body.calorie, body.createTime, body.remark));
        });
        app.delete("/api/records/{id}", ctx -> {
            ctx.json(service.deleteRecord(ctx.queryParam("username"),
                    Integer.parseInt(ctx.pathParam("id"))));
        });
        app.delete("/api/records", ctx -> {
            ctx.json(service.clearRecords(ctx.queryParam("username")));
        });

        // ================ 排行榜 ================
        app.get("/api/leaderboard", ctx -> {
            int days = intParam(ctx.queryParam("days"), 0);
            int limit = intParam(ctx.queryParam("limit"), 20);
            ctx.json(service.leaderboard(days, limit));
        });

        // ================ 动态流与点赞 ================
        app.get("/api/feed", ctx -> {
            String username = ctx.queryParam("username");
            int limit = intParam(ctx.queryParam("limit"), 50);
            ctx.json(service.feed(username, limit));
        });
        app.post("/api/records/{id}/like", ctx -> {
            ctx.json(service.toggleLike(ctx.queryParam("username"),
                    Integer.parseInt(ctx.pathParam("id"))));
        });

        // ================ 关注 ================
        app.post("/api/follows", ctx -> {
            var body = ctx.bodyAsClass(FollowRequest.class);
            ctx.json(service.toggleFollow(ctx.queryParam("username"), body.followee));
        });
        app.get("/api/users/search", ctx -> {
            String keyword = ctx.queryParam("keyword");
            ctx.json(service.searchUsers(keyword, ctx.queryParam("username")));
        });
        app.get("/api/follows/my", ctx -> {
            ctx.json(service.myFollowInfo(ctx.queryParam("username")));
        });

        return app;
    }

    private static String dbPath() {
        String configured = System.getProperty("server.db.path");
        // 相对于服务端项目目录（server/），即 server/data/fitness_server.db
        return configured != null ? configured : "data/fitness_server.db";
    }

    /** 给用户信息响应统一补上 code/message 字段（客户端按 code==0 判断成功） */
    private static java.util.Map<String, Object> withCode(
            java.util.Map<String, Object> data, String message) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", message);
        if (data != null) {
            result.putAll(data);
        }
        return result;
    }

    private static int intParam(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ================ 请求体 DTO ================

    public static class RegisterRequest {
        public String username;
        public String password;
        public String nickname;
    }

    public static class ChangePasswordRequest {
        public String username;
        public String oldPassword;
        public String newPassword;
    }

    public static class RecordRequest {
        public String sportType;
        public int duration;
        public int calorie;
        public String createTime;
        public String remark;
    }

    public static class FollowRequest {
        public String followee;
    }

    /** 小工具：快速构建 Map */
    static final class Maps {
        private Maps() {
        }

        static java.util.Map<String, Object> of(Object... kv) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                m.put(String.valueOf(kv[i]), kv[i + 1]);
            }
            return m;
        }
    }
}
