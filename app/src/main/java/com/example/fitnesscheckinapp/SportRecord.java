package com.example.fitnesscheckinapp;

/**
 * 运动记录实体类：用于封装数据库中的单条运动记录数据
 */
public class SportRecord {

    private final int id;               // 记录的唯一标识 ID
    private final String sportType;    // 运动类型（如：跑步、骑行等）
    private final int duration;         // 运动时长（单位：秒）
    private final int calorie;          // 消耗的卡路里
    private final String createTime;    // 记录创建的时间字符串
    private final String remark;        // 运动备注信息

    /**
     * 构造函数：初始化运动记录对象
     * @param id 数据库主键 ID
     * @param sportType 运动类型
     * @param duration 运动时长
     * @param calorie 卡路里消耗
     * @param createTime 记录时间
     * @param remark 备注
     */
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
