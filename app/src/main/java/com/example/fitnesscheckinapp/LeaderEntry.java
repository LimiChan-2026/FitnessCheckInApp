package com.example.fitnesscheckinapp;

/**
 * 排行榜条目模型：与服务端 /api/leaderboard 返回的 JSON 字段一一对应
 */
public class LeaderEntry {
    private final String username;
    private final String nickname;
    private final int checkInCount;
    private final int totalDuration;  // 秒
    private final int totalCalorie;

    public LeaderEntry(String username, String nickname, int checkInCount,
                       int totalDuration, int totalCalorie) {
        this.username = username;
        this.nickname = nickname;
        this.checkInCount = checkInCount;
        this.totalDuration = totalDuration;
        this.totalCalorie = totalCalorie;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public int getCheckInCount() {
        return checkInCount;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public int getTotalCalorie() {
        return totalCalorie;
    }
}
