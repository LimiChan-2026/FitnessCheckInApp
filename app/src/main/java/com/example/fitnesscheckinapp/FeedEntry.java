package com.example.fitnesscheckinapp;

/**
 * 动态条目模型：对应服务端 /api/feed 返回的一条好友动态
 * （本质是关注列表内用户的打卡记录，附带点赞信息）
 */
public class FeedEntry {
    private final long recordId;
    private final String username;
    private final String nickname;
    private final String sportType;
    private final int duration;   // 秒
    private final int calorie;
    private final String createTime;
    private final String remark;
    private int likeCount;
    private boolean likedByMe;

    public FeedEntry(long recordId, String username, String nickname, String sportType,
                     int duration, int calorie, String createTime, String remark,
                     int likeCount, boolean likedByMe) {
        this.recordId = recordId;
        this.username = username;
        this.nickname = nickname;
        this.sportType = sportType;
        this.duration = duration;
        this.calorie = calorie;
        this.createTime = createTime;
        this.remark = remark;
        this.likeCount = likeCount;
        this.likedByMe = likedByMe;
    }

    public long getRecordId() {
        return recordId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getSportType() {
        return sportType;
    }

    public int getDuration() {
        return duration;
    }

    public int getCalorie() {
        return calorie;
    }

    public String getCreateTime() {
        return createTime;
    }

    public String getRemark() {
        return remark;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public boolean isLikedByMe() {
        return likedByMe;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public void setLikedByMe(boolean likedByMe) {
        this.likedByMe = likedByMe;
    }

    /** 刚完成一次"新点赞"的标记：Adapter 绑定时据此播放爱心爆炸动画（一次性） */
    private boolean justLiked;

    public void markJustLiked() {
        this.justLiked = true;
    }

    public boolean isJustLiked() {
        return justLiked;
    }

    public void clearJustLiked() {
        this.justLiked = false;
    }
}
