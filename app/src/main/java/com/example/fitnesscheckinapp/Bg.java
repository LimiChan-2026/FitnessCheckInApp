package com.example.fitnesscheckinapp;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 轻量后台任务工具：数据库查询等耗时操作移出主线程，避免阻塞页面切换动画。
 * <p>
 * 用法：Bg.io(() -> 查询并返回结果, result -> 用结果更新UI)
 * 后台任务抛出的异常会被吞掉并回调 null，回调方需自行判空。
 */
public final class Bg {

    /** 单线程池：SQLite 本身串行访问，单线程既避免争用也足够快 */
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    /** 主线程 Handler：把结果送回 UI 线程 */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Bg() {
    }

    public static <T> void io(Supplier<T> task, Consumer<T> onUi) {
        POOL.execute(() -> {
            T result;
            try {
                result = task.get();
            } catch (Exception e) {
                result = null;
            }
            final T r = result;
            MAIN.post(() -> onUi.accept(r));
        });
    }
}
