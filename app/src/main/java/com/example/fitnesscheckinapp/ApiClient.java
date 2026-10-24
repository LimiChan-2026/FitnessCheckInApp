package com.example.fitnesscheckinapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网络层：封装与服务端 REST API 的 HTTP 通信。
 * <p>
 * 设计要点：
 * 1. 基于 Android 自带的 HttpURLConnection + org.json，不引入任何第三方依赖；
 * 2. 所有请求跑在后台线程池，通过回调把结果送回主线程；
 * 3. 服务器地址集中管理（SharedPreferences 持久化，默认模拟器访问宿主机 10.0.2.2）；
 * 4. 统一错误模型：code != 0 视为业务失败，message 为用户可读的提示。
 */
public class ApiClient {

    /** 网络请求回调 */
    public interface Callback {
        /** 成功：返回服务端 JSON 中 data 部分或整个响应体 */
        void onSuccess(JSONObject data);

        /** 失败：message 为可展示给用户的错误提示 */
        void onFailure(String message);
    }

    private static final String PREF_SERVER = "pref_server";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);

    private final Context context;

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 获取当前配置的服务器地址（末尾不带斜杠） */
    public String getBaseUrl() {
        SharedPreferences sp = context.getSharedPreferences(PREF_SERVER, Context.MODE_PRIVATE);
        return sp.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    /** 修改服务器地址（设置页/关于对话框中可改） */
    public void setBaseUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            String normalized = url.trim();
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            context.getSharedPreferences(PREF_SERVER, Context.MODE_PRIVATE)
                    .edit().putString(KEY_BASE_URL, normalized).apply();
        }
    }

    // ==================== API 方法 ====================

    /** 连通性测试 */
    public void ping(Callback cb) {
        get("/api/ping", cb);
    }

    /** 注册（服务端存加盐哈希，本地同时存一份哈希用于离线登录） */
    public void register(String username, String password, String nickname, Callback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            body.put("nickname", nickname);
        } catch (Exception ignored) {
        }
        post("/api/register", body, cb);
    }

    /** 登录 */
    public void login(String username, String password, Callback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
        } catch (Exception ignored) {
        }
        post("/api/login", body, cb);
    }

    /** 修改密码 */
    public void changePassword(String username, String newPassword, Callback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("newPassword", newPassword);
        } catch (Exception ignored) {
        }
        post("/api/change-password", body, cb);
    }

    /** 上报一条打卡记录（服务器按 query 参数识别用户） */
    public void addRecord(String username, String sportType, int duration, int calorie,
                          String createTime, String remark, Callback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("sportType", sportType);
            body.put("duration", duration);
            body.put("calorie", calorie);
            body.put("createTime", createTime);
            body.put("remark", remark == null ? "" : remark);
        } catch (Exception ignored) {
        }
        post("/api/records?username=" + urlEncode(username), body, cb);
    }

    /** 删除云端一条记录 */
    public void deleteRecord(String username, long recordId, Callback cb) {
        delete("/api/records/" + recordId + "?username=" + urlEncode(username), cb);
    }

    /** 清空云端记录 */
    public void clearRecords(String username, Callback cb) {
        delete("/api/records?username=" + urlEncode(username), cb);
    }

    /** 排行榜：days<=0 为总榜，否则为最近 N 天榜 */
    public void leaderboard(int days, Callback cb) {
        get("/api/leaderboard?days=" + days + "&limit=20", cb);
    }

    /** 好友动态流（服务端返回 JSONArray） */
    public void feed(String username, Callback cb) {
        get("/api/feed?username=" + urlEncode(username) + "&limit=50", cb);
    }

    /** 点赞/取消点赞切换 */
    public void toggleLike(String username, long recordId, Callback cb) {
        post("/api/records/" + recordId + "/like?username=" + urlEncode(username),
                new JSONObject(), cb);
    }

    /** 关注/取关切换 */
    public void toggleFollow(String username, String followee, Callback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("followee", followee);
        } catch (Exception ignored) {
        }
        post("/api/follows?username=" + urlEncode(username), body, cb);
    }

    /** 搜索用户（用于添加关注） */
    public void searchUsers(String keyword, String excludeUsername, Callback cb) {
        get("/api/users/search?keyword=" + urlEncode(keyword)
                + "&username=" + urlEncode(excludeUsername), cb);
    }

    /** 获取我的关注/粉丝列表（用于“发现好友”中标记已关注状态） */
    public void getMyFollows(String username, Callback cb) {
        get("/api/follows/my?username=" + urlEncode(username), cb);
    }

    // ==================== HTTP 基础方法 ====================

    private void get(String path, Callback cb) {
        request("GET", path, null, cb);
    }

    private void post(String path, JSONObject body, Callback cb) {
        request("POST", path, body, cb);
    }

    private void delete(String path, Callback cb) {
        request("DELETE", path, null, cb);
    }

    private void request(String method, String path, JSONObject body, Callback cb) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(getBaseUrl() + path);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/json");

                if (body != null) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }

                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String responseText = readAll(is);

                // 判断响应是数组还是对象：feed/leaderboard/search 直接返回 JSON 数组
                String trimmed = responseText.trim();
                JSONObject json;
                JSONObject data;
                if (trimmed.startsWith("[")) {
                    // 数组响应统一包装为 {list: [...]}
                    data = new JSONObject();
                    data.put("list", new org.json.JSONArray(trimmed));
                    json = data;
                } else {
                    json = new JSONObject(trimmed);
                    if (json.has("data")) {
                        data = json.getJSONObject("data");
                    } else {
                        // 服务端多数接口（登录/注册/点赞/关注等）把业务字段放在顶层：
                        // {code, message, username..., liked, likeCount, following...}
                        // 统一把整个响应对象作为数据交给回调，由调用方按需取字段
                        data = json;
                    }
                }

                int bizCode = json.optInt("code", 0);
                if (code >= 200 && code < 300 && bizCode == 0) {
                    final JSONObject result = data;
                    postMain(() -> cb.onSuccess(result));
                } else {
                    String message = json.optString("message", "请求失败 (HTTP " + code + ")");
                    final String msg = message;
                    postMain(() -> cb.onFailure(msg));
                }
            } catch (Exception e) {
                String msg;
                if (e instanceof java.net.UnknownHostException
                        || e instanceof java.net.ConnectException
                        || e instanceof java.net.SocketTimeoutException) {
                    msg = "无法连接服务器，请检查服务端是否启动";
                } else {
                    msg = "网络请求失败: " + e.getMessage();
                }
                final String m = msg;
                postMain(() -> cb.onFailure(m));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /** 读取响应流为字符串 */
    private String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /** 回调切换到主线程执行 */
    private void postMain(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
