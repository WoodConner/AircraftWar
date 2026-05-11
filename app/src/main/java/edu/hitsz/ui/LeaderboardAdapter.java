package edu.hitsz.ui;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;
import edu.hitsz.R;
import edu.hitsz.db.ScoreEntity;

/** 排行榜 RecyclerView Adapter */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.VH> {
    private List<ScoreEntity> data;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public LeaderboardAdapter(List<ScoreEntity> data) { this.data = data; }

    public void setData(List<ScoreEntity> d) { data = d; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ScoreEntity s = data.get(pos);
        h.tvRank.setText(String.valueOf(pos + 1));
        h.tvName.setText(s.playerName);
        h.tvScore.setText(String.valueOf(s.score));
        h.tvDiff.setText(s.difficulty);
        h.tvTime.setText(sdf.format(new Date(s.timestamp)));
    }

    @Override public int getItemCount() { return data == null ? 0 : data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvRank, tvName, tvScore, tvDiff, tvTime;
        VH(View v) {
            super(v);
            tvRank = v.findViewById(R.id.tv_rank);
            tvName = v.findViewById(R.id.tv_name);
            tvScore = v.findViewById(R.id.tv_score);
            tvDiff = v.findViewById(R.id.tv_diff);
            tvTime = v.findViewById(R.id.tv_time);
        }
    }
}
