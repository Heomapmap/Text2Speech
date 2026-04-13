package com.example.text2speech;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * FavoritesActivity – Hiển thị lịch sử đọc từ Room Database
 * Dùng Room DB (AppDatabase) để query toàn bộ ReadingHistory
 * rồi đổ động vào LinearLayout — không cần RecyclerView cho danh sách ngắn.
 */
public class FavoritesActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private LinearLayout emptyState;
    private ScrollView   scrollHistory;
    private LinearLayout textTab;

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        historyContainer = findViewById(R.id.historyContainer);
        emptyState       = findViewById(R.id.emptyState);
        scrollHistory    = findViewById(R.id.scrollHistory);
        textTab          = findViewById(R.id.textTab);

        // Click "Text" tab → quay về MainActivity
        textTab.setOnClickListener(v -> finish());

        // Lấy Room DB (singleton, không tạo mới)
        db = AppDatabase.getDatabase(getApplicationContext());

        // Query trên background thread (Room không cho query trên main thread)
        loadHistory();
    }

    /**
     * Query toàn bộ lịch sử từ Room, rồi build UI trên main thread.
     * Dùng Thread thủ công (giống BE_Phong) thay vì LiveData để giữ đơn giản.
     */
    private void loadHistory() {
        new Thread(() -> {
            // getAllHistory() trả về danh sách sắp xếp mới nhất lên đầu
            List<ReadingHistory> historyList = db.historyDao().getAllHistory();

            runOnUiThread(() -> {
                if (historyList == null || historyList.isEmpty()) {
                    // Không có lịch sử → hiện empty state
                    emptyState.setVisibility(View.VISIBLE);
                    scrollHistory.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    scrollHistory.setVisibility(View.VISIBLE);
                    buildHistoryCards(historyList);
                }
            });
        }).start();
    }

    /**
     * Tạo card cho mỗi entry lịch sử và thêm vào historyContainer.
     */
    private void buildHistoryCards(List<ReadingHistory> list) {
        historyContainer.removeAllViews();  // Xóa card cũ nếu có

        for (ReadingHistory entry : list) {
            LinearLayout card = buildCard(entry);
            historyContainer.addView(card);
        }
    }

    /**
     * Build 1 card hiển thị: tên file + vị trí câu đọc dở.
     * Dùng View thuần (không inflate XML) để không cần thêm layout file.
     */
    private LinearLayout buildCard(ReadingHistory entry) {
        // Card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#2a2a2a"));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cardParams);
        card.setPadding(32, 24, 32, 24);

        // Tên file
        TextView tvName = new TextView(this);
        tvName.setText(entry.fileName != null ? entry.fileName : "File không tên");
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(tvName);

        // Vị trí câu đọc dở
        TextView tvIndex = new TextView(this);
        String indexText = entry.lastReadIndex > 0
                ? "Đọc đến câu " + (entry.lastReadIndex + 1)
                : "Chưa đọc / Mới bắt đầu";
        tvIndex.setText(indexText);
        tvIndex.setTextColor(Color.parseColor("#888888"));
        tvIndex.setTextSize(12);
        LinearLayout.LayoutParams indexParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        indexParams.topMargin = 6;
        tvIndex.setLayoutParams(indexParams);
        card.addView(tvIndex);

        // Đường dẫn file (rút gọn)
        if (entry.filePath != null && !entry.filePath.isEmpty()) {
            TextView tvPath = new TextView(this);
            String shortPath = entry.filePath.length() > 50
                    ? "..." + entry.filePath.substring(entry.filePath.length() - 47)
                    : entry.filePath;
            tvPath.setText(shortPath);
            tvPath.setTextColor(Color.parseColor("#555555"));
            tvPath.setTextSize(10);
            LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pathParams.topMargin = 4;
            tvPath.setLayoutParams(pathParams);
            card.addView(tvPath);
        }

        return card;
    }
}