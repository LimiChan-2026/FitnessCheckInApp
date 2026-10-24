package com.example.fitnesscheckinapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;

import java.util.List;

/**
 * 好友动态列表适配器：每条动态展示打卡信息 + Lottie 爱心点赞 + 关注按钮
 */
public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.FeedViewHolder> {

    /** 点赞按钮点击回调 */
    public interface OnLikeClickListener {
        void onLikeClick(FeedEntry entry, int position);
    }

    /** 关注按钮点击回调 */
    public interface OnFollowClickListener {
        void onFollowClick(FeedEntry entry, int position);
    }

    private final List<FeedEntry> feedList;
    private final OnLikeClickListener likeListener;
    private final OnFollowClickListener followListener;
    private final String currentUsername;

    public FeedAdapter(List<FeedEntry> feedList, OnLikeClickListener likeListener,
                       OnFollowClickListener followListener, String currentUsername) {
        this.feedList = feedList;
        this.likeListener = likeListener;
        this.followListener = followListener;
        this.currentUsername = currentUsername;
    }

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feed, parent, false);
        return new FeedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        FeedEntry entry = feedList.get(position);
        android.content.Context context = holder.itemView.getContext();

        // 昵称与运动信息
        holder.tvNickname.setText(entry.getNickname());
        holder.tvSportInfo.setText(context.getString(
                R.string.feed_sport_info_value,
                entry.getSportType(),
                entry.getDuration() / 60,
                entry.getDuration() % 60,
                entry.getCalorie()
        ));
        holder.tvCreateTime.setText(context.getString(
                R.string.feed_time_value, entry.getCreateTime()));

        // 备注（无备注则隐藏）
        if (entry.getRemark() == null || entry.getRemark().trim().isEmpty()) {
            holder.tvRemark.setVisibility(View.GONE);
        } else {
            holder.tvRemark.setVisibility(View.VISIBLE);
            holder.tvRemark.setText(context.getString(
                    R.string.record_remark_value, entry.getRemark()));
        }

        // 点赞区域：Lottie 爱心状态渲染
        if (entry.isLikedByMe()) {
            if (entry.isJustLiked()) {
                // 新点赞：完整播放爱心爆炸动画（绑定完成后启动，避免被 rebind 打断）
                entry.clearJustLiked();
                holder.lottieHeart.setMinAndMaxProgress(0f, 1f);
                holder.lottieHeart.setProgress(0f);
                holder.lottieHeart.playAnimation();
            } else {
                // 已赞的静态展示：停在动画最后一帧（完整爱心）
                holder.lottieHeart.cancelAnimation();
                holder.lottieHeart.setMinAndMaxProgress(0.55f, 1f);
                holder.lottieHeart.setProgress(1f);
            }
        } else {
            // 未赞：回到动画起点（空心/初始状态）
            holder.lottieHeart.cancelAnimation();
            holder.lottieHeart.setMinAndMaxProgress(0f, 1f);
            holder.lottieHeart.setProgress(0f);
        }
        holder.tvLikeCount.setText(context.getString(R.string.feed_like_count, entry.getLikeCount()));
        holder.btnLike.setOnClickListener(v -> {
            if (likeListener != null) {
                likeListener.onLikeClick(entry, holder.getAdapterPosition());
            }
        });

        // 关注按钮：自己的动态不显示关注按钮
        if (entry.getUsername().equals(currentUsername)) {
            holder.btnFollow.setVisibility(View.GONE);
        } else {
            holder.btnFollow.setVisibility(View.VISIBLE);
            holder.btnFollow.setText(context.getString(R.string.feed_follow));
            holder.btnFollow.setOnClickListener(v -> {
                if (followListener != null) {
                    followListener.onFollowClick(entry, holder.getAdapterPosition());
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return feedList.size();
    }

    /**
     * ViewHolder：缓存动态条目中的控件
     */
    static class FeedViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvNickname;
        private final TextView tvSportInfo;
        private final TextView tvCreateTime;
        private final TextView tvRemark;
        private final LinearLayout btnLike;
        final LottieAnimationView lottieHeart;
        private final TextView tvLikeCount;
        private final Button btnFollow;

        public FeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNickname = itemView.findViewById(R.id.tvFeedNickname);
            tvSportInfo = itemView.findViewById(R.id.tvFeedSportInfo);
            tvCreateTime = itemView.findViewById(R.id.tvFeedTime);
            tvRemark = itemView.findViewById(R.id.tvFeedRemark);
            btnLike = itemView.findViewById(R.id.btnFeedLike);
            lottieHeart = itemView.findViewById(R.id.lottieHeart);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            btnFollow = itemView.findViewById(R.id.btnFeedFollow);
        }
    }
}
