package com.example.fitnesscheckinapp;

import android.app.Activity;
import android.content.Intent;

/**
 * 页面导航工具：方向感知的过渡动画 + Activity 栈复用。
 * <p>
 * 底部导航的五个主页面彼此平级，用 {@link Intent#FLAG_ACTIVITY_REORDER_TO_FRONT}
 * 跳转：目标页面若已在栈中则直接调到前台（只走 onResume 刷新数据），
 * 不再重复创建实例——避免连续点击导航导致 Activity 栈无限叠加、
 * 按返回键要退多次的问题，切换也因免重建而更流畅。
 * <p>
 * 动画方向按底部 Tab 的左右顺序感知：
 * 切到右侧页面时新页从右滑入；切到左侧页面时新页从左滑入，
 * 与用户对"前进/后退"的空间直觉一致。
 */
public final class Nav {

    private Nav() {
    }

    /** 底部导航从左到右的页面顺序，用于判断切换方向 */
    private static int tabOrder(Class<?> c) {
        if (c == MainActivity.class) return 0;
        if (c == CheckInActivity.class) return 1;
        if (c == SocialActivity.class) return 2;
        if (c == DataActivity.class) return 3;
        if (c == MineActivity.class) return 4;
        return -1; // 非底部导航页面（如登录页）
    }

    /**
     * 底部导航跳转：栈内复用，瞬时切换。
     * <p>
     * 说明：曾尝试方向感知的滑动过渡动画，但窗口动画需要新旧两个窗口同时
     * 位移重绘，在模拟器（软件 GPU）上严重掉帧，故回退为无动画的瞬时切换——
     * 对底部平级 Tab 而言瞬时切换本身也是最跟手的体验。
     * 如需恢复动画（建议仅在真机上启用），加回下面这行：
     * <pre>
     * int fromOrder = tabOrder(from.getClass());
     * int toOrder = tabOrder(to);
     * if (fromOrder >= 0 && toOrder >= 0 && toOrder < fromOrder) {
     *     from.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
     * } else {
     *     from.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
     * }
     * </pre>
     */
    public static void go(Activity from, Class<?> to) {
        Intent intent = new Intent(from, to);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        from.startActivity(intent);
    }

    /**
     * 流程性跳转（登录→主页、注册→登录等）：淡入淡出动画，不改变原有 flags
     */
    public static void flow(Activity from, Intent intent) {
        from.startActivity(intent);
        from.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
