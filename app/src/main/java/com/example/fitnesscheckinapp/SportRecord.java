package com.example.fitnesscheckinapp;

public class SportRecord {

    private final int id;
    private final String sportType;
    private final int duration;
    private final int calorie;
    private final String createTime;
    private final String remark;

    public SportRecord(int id, String sportType, int duration, int calorie, String createTime, String remark) {
        this.id = id;
        this.sportType = sportType;
        this.duration = duration;
        this.calorie = calorie;
        this.createTime = createTime;
        this.remark = remark;
    }

    public int getId() {
        return id;
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
}
