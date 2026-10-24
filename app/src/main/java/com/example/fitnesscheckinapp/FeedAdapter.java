package com.example.fitnesscheckinapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 好友动态列表适配器：每条动态展示打卡信息 + 点赞按钮 + 关注按钮
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

        // 点赞按钮：状态随 likedByMe 变化
        holder.btnLike.setText(context.getString(
                entry.isLikedByMe() ? R.string.feed_liked : R.string.feed_like,
                entry.getLikeCount()));
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
        private final Button btnLike;
        private final Button btnFollow;

        public FeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNickname = itemView.findViewById(R.id.tvFeedNickname);
            tvSportInfo = itemView.findViewById(R.id.tvFeedSportInfo);
            tvCreateTime = itemView.findViewById(R.id.tvFeedTime);
            tvRemark = itemView.findViewById(R.id.tvFeedRemark);
            btnLike = itemView.findViewById(R.id.btnFeedLike);
            btnFollow = itemView.findViewById(R.id.btnFeedFollow);
        }
    }
}
