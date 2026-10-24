package com.example.fitnesscheckinapp;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.airbnb.lottie.LottieAnimationView;

/**
 * Lottie 全屏动画弹窗：打卡成功（庆祝彩带）/ 注册成功（打勾）等成就时刻使用。
 * <p>
 * 动画播完自动消失；点击任意位置可提前关闭。
 */
public class AnimDialog extends DialogFragment {

    /** 动画文件名（assets 内路径，不含扩展名场景由调用方拼好，如 animations/celebration.json） */
    private static final String ARG_FILE = "file";
    /** 弹窗文字（如"打卡成功！"），可为空 */
    private static final String ARG_TEXT = "text";

    private LottieAnimationView lottieView;

    public static AnimDialog show(androidx.fragment.app.FragmentActivity activity,
                                  String assetFile, String text) {
        AnimDialog dialog = new AnimDialog();
        Bundle args = new Bundle();
        args.putString(ARG_FILE, assetFile);
        args.putString(ARG_TEXT, text);
        dialog.setArguments(args);
        dialog.show(activity.getSupportFragmentManager(), "anim_dialog");
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String file = requireArguments().getString(ARG_FILE, "");
        String text = requireArguments().getString(ARG_TEXT, "");

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);

        lottieView = new LottieAnimationView(requireContext());
        lottieView.setAnimation(file);
        lottieView.setRepeatCount(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(lottieView, lp);

        if (!TextUtils.isEmpty(text)) {
            TextView tv = new TextView(requireContext());
            tv.setText(text);
            tv.setTextSize(18f);
            tv.setTextColor(requireContext().getColor(R.color.text_primary));
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, 8, 0, 32);
            root.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // 动画播放结束后自动关闭弹窗
        lottieView.addAnimatorUpdateListener(animation -> {
            if (animation.getAnimatedFraction() >= 0.999f) {
                dismissAllowingStateLoss();
            }
        });

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(root);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (lottieView != null) {
            lottieView.playAnimation();
        }
    }

    @Override
    public void dismissAllowingStateLoss() {
        super.dismissAllowingStateLoss();
    }
}
