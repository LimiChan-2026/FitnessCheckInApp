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

    public interface OnRecordDeleteClickListener {
        void onDeleteClick(SportRecord record);
    }

    private final List<SportRecord> recordList;
    private final OnRecordDeleteClickListener deleteClickListener;

    public RecordAdapter(List<SportRecord> recordList, OnRecordDeleteClickListener deleteClickListener) {
        this.recordList = recordList;
        this.deleteClickListener = deleteClickListener;
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sport_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        SportRecord record = recordList.get(position);
        holder.tvSportType.setText(record.getSportType());
        holder.tvDuration.setText(holder.itemView.getContext().getString(
                R.string.record_duration_value,
                record.getDuration() / 60,
                record.getDuration() % 60
        ));
        holder.tvCalorie.setText(holder.itemView.getContext().getString(
                R.string.record_calorie_value,
                record.getCalorie()
        ));
        holder.tvCreateTime.setText(holder.itemView.getContext().getString(
                R.string.record_time_value,
                record.getCreateTime()
        ));
        holder.btnDeleteRecord.setOnClickListener(v -> deleteClickListener.onDeleteClick(record));
    }

    @Override
    public int getItemCount() {
        return recordList.size();
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvSportType;
        private final TextView tvDuration;
        private final TextView tvCalorie;
        private final TextView tvCreateTime;
        private final Button btnDeleteRecord;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSportType = itemView.findViewById(R.id.tvItemSportType);
            tvDuration = itemView.findViewById(R.id.tvItemDuration);
            tvCalorie = itemView.findViewById(R.id.tvItemCalorie);
            tvCreateTime = itemView.findViewById(R.id.tvItemCreateTime);
            btnDeleteRecord = itemView.findViewById(R.id.btnDeleteRecord);
        }
    }
}
