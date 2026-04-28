package com.example.fitnesscheckinapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.RecordViewHolder> {

    /**
     * 定义点击删除按钮的回调接口
     */
    public interface OnRecordDeleteClickListener {
        /**
         * 当点击某条记录的删除按钮时触发
         * @param record 被点击的运动记录对象
         */
        void onDeleteClick(SportRecord record);
    }

    private final List<SportRecord> recordList; // 运动记录数据集合
    private final OnRecordDeleteClickListener deleteClickListener; // 删除事件监听器

    /**
     * 构造函数
     * @param recordList 数据源
     * @param deleteClickListener 删除操作的回调实现
     */
    public RecordAdapter(List<SportRecord> recordList, OnRecordDeleteClickListener deleteClickListener) {
        this.recordList = recordList;
        this.deleteClickListener = deleteClickListener;
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载列表项的布局文件 item_sport_record.xml
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sport_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        // 获取当前位置的数据对象
        SportRecord record = recordList.get(position);

        // 设置运动类型文本
        holder.tvSportType.setText(record.getSportType());

        // 格式化并显示时长：将秒转换为“x分x秒”的格式
        holder.tvDuration.setText(holder.itemView.getContext().getString(
                R.string.record_duration_value,
                record.getDuration() / 60,
                record.getDuration() % 60
        ));

        // 设置卡路里消耗文本
        holder.tvCalorie.setText(holder.itemView.getContext().getString(
                R.string.record_calorie_value,
                record.getCalorie()
        ));

        // 设置记录创建时间文本
        holder.tvCreateTime.setText(holder.itemView.getContext().getString(
                R.string.record_time_value,
                record.getCreateTime()
        ));

        // 处理备注信息的显示与隐藏
        if (record.getRemark() == null || record.getRemark().trim().isEmpty()) {
            // 如果备注为空，则隐藏备注文本框
            holder.tvRemark.setVisibility(View.GONE);
        } else {
            // 如果有备注，则显示并填充内容
            holder.tvRemark.setVisibility(View.VISIBLE);
            holder.tvRemark.setText(holder.itemView.getContext().getString(
                    R.string.record_remark_value,
                    record.getRemark()
            ));
        }

        // 绑定删除按钮点击事件
        holder.btnDeleteRecord.setOnClickListener(v -> deleteClickListener.onDeleteClick(record));
    }

    @Override
    public int getItemCount() {
        return recordList.size(); // 返回数据源的总量
    }

    /**
     * ViewHolder 类：用于缓存列表项中的 UI 控件，避免重复 findViewById
     */
    static class RecordViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvSportType;   // 运动类型
        private final TextView tvDuration;    // 运动时长
        private final TextView tvCalorie;     // 卡路里
        private final TextView tvCreateTime;  // 时间
        private final TextView tvRemark;      // 备注
        private final Button btnDeleteRecord; // 删除按钮

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            // 绑定布局中的各个控件
            tvSportType = itemView.findViewById(R.id.tvItemSportType);
            tvDuration = itemView.findViewById(R.id.tvItemDuration);
            tvCalorie = itemView.findViewById(R.id.tvItemCalorie);
            tvCreateTime = itemView.findViewById(R.id.tvItemCreateTime);
            tvRemark = itemView.findViewById(R.id.tvItemRemark);
            btnDeleteRecord = itemView.findViewById(R.id.btnDeleteRecord);
        }
    }
}
