package com.example.fitnesscheckinapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 社区页面：健身社交的核心入口。
 * 上半屏为运动排行榜（总榜 / 最近7天切换），下半屏为好友动态流
 * （点赞、关注互动），数据全部来自服务端 REST API。
 * <p>
 * 数据更新机制：
 * 1. 进入页面 / 手动按钮 / 切换榜单时立即加载；
 * 2. 下拉刷新手势：同时刷新排行榜与动态流；
 * 3. 自动轮询：页面可见期间每 15 秒自动刷新一次（Handler 定时任务）。
 */
public class SocialActivity extends AppCompatActivity {

    private static final String PREF_USER = "pref_user";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_USERNAME = "username";

    // 排行榜时间窗
    private static final int BOARD_ALL = 0;      // 总榜
    private static final int BOARD_LAST_7_DAYS = 7; // 最近7天
    // 自动刷新间隔
    private static final long AUTO_REFRESH_INTERVAL_MS = 15_000;

    // UI 控件
    private TextView tvLeaderboard;
    private TextView tvFeedEmpty;
    private RecyclerView rvFeed;
    private Button btnBoardAll;
    private Button btnBoardLast7Days;
    private Button btnRefresh;
    private SwipeRefreshLayout swipeRefresh;

    private FeedAdapter feedAdapter;
    private final List<FeedEntry> feedList = new ArrayList<>();
    private ApiClient apiClient;
    private String currentUsername = "";
    private int currentBoard = BOARD_LAST_7_DAYS; // 默认展示本周榜，更有竞争感

    /** 主线程 Handler：驱动 15 秒自动轮询 */
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    /** 自动轮询任务：刷新后重新排期，页面不可见时移除 */
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshAll();
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
        }
    };
    /** 一次刷新包含的并行请求计数（排行榜 + 动态流），归零时收起刷新动画 */
    private final AtomicInteger pendingRequests = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_social);

        apiClient = new ApiClient(this);
        initViews();
        setupRecyclerView();
        setupActions();
        setupSwipeRefresh();
        setupBottomNavigation();
    }

    /** 上次刷新时间（elapsedRealtime 时基），用于 onResume 节流：短时间内反复切回本页不重复拉取 */
    private long lastRefreshTime = 0;

    @Override
    protected void onResume() {
        super.onResume();
        if (ensureLoginUser()) {
            // 进入页面：距上次刷新超过一个轮询周期才立即刷新，否则交给轮询按期刷新
            // （避免快速来回切 Tab 时每次都拉网络 + 全列表刷新 + 转圈动画叠加导致卡顿）
            if (android.os.SystemClock.elapsedRealtime() - lastRefreshTime > AUTO_REFRESH_INTERVAL_MS) {
                refreshAll();
            }
            autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 页面不可见：停止轮询并收起刷新动画，避免后台耗电与内存泄漏
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    /**
     * 校验登录状态，未登录则跳转登录页
     */
    private boolean ensureLoginUser() {
        SharedPreferences sp = getSharedPreferences(PREF_USER, MODE_PRIVATE);
        boolean isLogin = sp.getBoolean(KEY_IS_LOGIN, false);
        currentUsername = sp.getString(KEY_USERNAME, "");
        if (!isLogin || TextUtils.isEmpty(currentUsername)) {
            Toast.makeText(this, R.string.toast_please_login_first, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }
        return true;
    }

    /**
     * 绑定布局中的 UI 控件
     */
    private void initViews() {
        tvLeaderboard = findViewById(R.id.tvLeaderboard);
        tvFeedEmpty = findViewById(R.id.tvFeedEmpty);
        rvFeed = findViewById(R.id.rvFeed);
        btnBoardAll = findViewById(R.id.btnBoardAll);
        btnBoardLast7Days = findViewById(R.id.btnBoardLast7Days);
        btnRefresh = findViewById(R.id.btnRefresh);
        swipeRefresh = findViewById(R.id.swipeRefresh);
    }

    /**
     * 初始化动态流列表
     */
    private void setupRecyclerView() {
        feedAdapter = new FeedAdapter(feedList, this::onLikeClicked, this::onFollowClicked, currentUsername);
        rvFeed.setLayoutManager(new LinearLayoutManager(this));
        rvFeed.setAdapter(feedAdapter);
    }

    /**
     * 设置按钮点击事件
     */
    private void setupActions() {
        btnBoardAll.setOnClickListener(v -> {
            currentBoard = BOARD_ALL;
            loadLeaderboard();
        });
        btnBoardLast7Days.setOnClickListener(v -> {
            currentBoard = BOARD_LAST_7_DAYS;
            loadLeaderboard();
        });
        btnRefresh.setOnClickListener(v -> refreshAll());
    }

    /**
     * 下拉刷新手势：同时刷新排行榜与动态流
     */
    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::refreshAll);
        // 使用主题色作为刷新圆圈颜色
        swipeRefresh.setColorSchemeResources(R.color.primary_dark);
    }

    /**
     * 统一刷新入口：并行请求排行榜与动态流，两者都完成后收起刷新动画
     */
    private void refreshAll() {
        pendingRequests.set(2);
        lastRefreshTime = android.os.SystemClock.elapsedRealtime();
        swipeRefresh.setRefreshing(true);
        loadLeaderboard();
        loadFeed();
    }

    /**
     * 单个请求完成（无论成败）后调用；计数归零时收起刷新动画
     */
    private void onRequestFinished() {
        if (pendingRequests.decrementAndGet() <= 0 && swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    /**
     * 加载排行榜并渲染为多行文本
     */
    private void loadLeaderboard() {
        apiClient.leaderboard(currentBoard, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONArray list = data.optJSONArray("list");
                if (list == null) {
                    tvLeaderboard.setText(R.string.leaderboard_empty);
                    updateBoardButtonState();
                    onRequestFinished();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                int rank = 1;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject row = list.optJSONObject(i);
                    if (row == null) continue;
                    sb.append(getString(R.string.leaderboard_row_value,
                            rank,
                            row.optString("nickname", row.optString("username")),
                            row.optInt("totalDuration") / 60,
                            row.optInt("checkInCount")));
                    sb.append("\n");
                    rank++;
                }
                if (sb.length() == 0) {
                    tvLeaderboard.setText(R.string.leaderboard_empty);
                } else {
                    tvLeaderboard.setText(sb.toString().trim());
                }
                updateBoardButtonState();
                onRequestFinished();
            }

            @Override
            public void onFailure(String message) {
                tvLeaderboard.setText(message);
                updateBoardButtonState();
                onRequestFinished();
            }
        });
    }

    /**
     * 加载好友动态流。
     * 弱网策略：请求失败时保留已有数据不清空，仅在列表本就为空时展示错误提示，
     * 避免 15 秒轮询期间服务器瞬断导致页面内容被清掉。
     */
    private void loadFeed() {
        apiClient.feed(currentUsername, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                feedList.clear();
                JSONArray list = data.optJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.optJSONObject(i);
                        if (item == null) continue;
                        feedList.add(new FeedEntry(
                                item.optLong("recordId"),
                                item.optString("username"),
                                item.optString("nickname"),
                                item.optString("sportType"),
                                item.optInt("duration"),
                                item.optInt("calorie"),
                                item.optString("createTime"),
                                item.optString("remark"),
                                item.optInt("likeCount"),
                                item.optBoolean("likedByMe")
                        ));
                    }
                }
                updateFeedView();
                onRequestFinished();
            }

            @Override
            public void onFailure(String message) {
                // 保留旧数据，只有列表为空时才把错误展示在空视图上
                if (feedList.isEmpty()) {
                    tvFeedEmpty.setText(message);
                }
                updateFeedView();
                onRequestFinished();
            }
        });
    }

    /**
     * 点赞/取消点赞
     */
    private void onLikeClicked(FeedEntry entry, int position) {
        apiClient.toggleLike(currentUsername, entry.getRecordId(), new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                entry.setLikedByMe(data.optBoolean("liked"));
                entry.setLikeCount(data.optInt("likeCount"));
                feedAdapter.notifyItemChanged(position);
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(SocialActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 关注/取消关注动态作者
     */
    private void onFollowClicked(FeedEntry entry, int position) {
        apiClient.toggleFollow(currentUsername, entry.getUsername(), new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(SocialActivity.this,
                        data.optString("message", "操作成功"), Toast.LENGTH_SHORT).show();
                loadFeed(); // 关注变化后刷新动态流
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(SocialActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 根据动态数据切换空视图/列表显示
     */
    private void updateFeedView() {
        if (feedList.isEmpty()) {
            tvFeedEmpty.setVisibility(View.VISIBLE);
            rvFeed.setVisibility(View.GONE);
        } else {
            tvFeedEmpty.setVisibility(View.GONE);
            rvFeed.setVisibility(View.VISIBLE);
        }
        feedAdapter.notifyDataSetChanged();
    }

    /**
     * 更新排行榜切换按钮的可用状态
     */
    private void updateBoardButtonState() {
        btnBoardAll.setEnabled(currentBoard != BOARD_ALL);
        btnBoardLast7Days.setEnabled(currentBoard != BOARD_LAST_7_DAYS);
    }

    /**
     * 底部导航跳转逻辑
     */
    private void setupBottomNavigation() {
        Button btnHome = findViewById(R.id.btnHome);
        Button btnCheckIn = findViewById(R.id.btnCheckIn);
        Button btnData = findViewById(R.id.btnData);
        Button btnMine = findViewById(R.id.btnMine);

        btnHome.setOnClickListener(v -> Nav.go(this, MainActivity.class));
        btnCheckIn.setOnClickListener(v -> Nav.go(this, CheckInActivity.class));
        btnData.setOnClickListener(v -> Nav.go(this, DataActivity.class));
        btnMine.setOnClickListener(v -> Nav.go(this, MineActivity.class));
    }
}
