package com.example.text2speech.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.text2speech.R;
import com.example.text2speech.data.AppDatabase;
import com.example.text2speech.data.ReadingHistory;
import com.example.text2speech.service.PlaybackService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HistoriesActivity – Hiển thị lịch sử đọc từ Room Database
 * Dùng Room DB (AppDatabase) để query toàn bộ ReadingHistory
 * rồi đổ động vào LinearLayout — không cần RecyclerView cho danh sách ngắn.
 */


/**Nhi bo sung
 * - Hiển thị ngày giờ lưu (timestamp).
 * - Badge phân biệt FILE  vs TEXT 📝
 * - Nút ▶Play: nếu sourceType="TEXT" → đọc textContent trực tiếp.
 *                nếu sourceType="FILE" → mở lại MainActivity với file đó.
 * - Nút 🗑 Xóa: xóa từng bản ghi sau khi xác nhận.
 * - Long press → confirm xóa.
 * */
public class HistoriesActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private LinearLayout emptyState;
    private ScrollView   scrollHistory;
    private LinearLayout textTab;

    private AppDatabase db;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_histories);

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

    @Override
    protected void onResume(){
        super.onResume();
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
        float dp = getResources().getDisplayMetrics().density;

        // ── Card container ────────────────────────────────────────────
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#2a2a2a"));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, (int)(10 * dp));
        card.setLayoutParams(cardParams);
        card.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));

        // Long press → xóa
        card.setLongClickable(true);
        card.setOnLongClickListener(v -> {
            confirmDelete(entry);
            return true;
        });

        // ── Hàng 1: badge loại + tên ──────────────────────────────────
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean isText = "TEXT".equals(entry.sourceType);

        // Badge
        TextView badge = new TextView(this);
        badge.setText(isText ? "📝 TEXT" : "📄 FILE");
        badge.setTextSize(9);
        badge.setTextColor(isText ? Color.parseColor("#FFA726") : Color.parseColor("#42A5F5"));
        badge.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.setMargins(0, 0, (int)(8*dp), 0);
        badge.setLayoutParams(badgeParams);
        row1.addView(badge);

        // Tên
        TextView tvName = new TextView(this);
        tvName.setText(entry.fileName != null ? entry.fileName : "Không có tên");
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvName.setMaxLines(2);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row1.addView(tvName);

        card.addView(row1);

        // ── Hàng 2: ngày giờ + vị trí câu ───────────────────────────
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row2params.topMargin = (int)(4*dp);
        row2.setLayoutParams(row2params);

        // Ngày giờ
        TextView tvDate = new TextView(this);
        String dateStr = entry.timestamp > 0
                ? DATE_FMT.format(new Date(entry.timestamp))
                : "—";
        tvDate.setText(dateStr);
        tvDate.setTextColor(Color.parseColor("#666666"));
        tvDate.setTextSize(11);
        tvDate.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row2.addView(tvDate);

        // Vị trí câu (chỉ hiện với FILE)
        if (!isText && entry.lastReadIndex > 0) {
            TextView tvIdx = new TextView(this);
            tvIdx.setText("câu " + (entry.lastReadIndex + 1));
            tvIdx.setTextColor(Color.parseColor("#2196F3"));
            tvIdx.setTextSize(11);
            row2.addView(tvIdx);
        }

        card.addView(row2);

        // ── Nếu là TEXT: hiện preview nội dung ───────────────────────
        if (isText && entry.textContent != null && !entry.textContent.isEmpty()) {
            TextView tvPreview = new TextView(this);
            String preview = entry.textContent.length() > 80
                    ? entry.textContent.substring(0, 80) + "…"
                    : entry.textContent;
            tvPreview.setText(preview);
            tvPreview.setTextColor(Color.parseColor("#888888"));
            tvPreview.setTextSize(12);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            previewParams.topMargin = (int)(6*dp);
            tvPreview.setLayoutParams(previewParams);
            tvPreview.setMaxLines(2);
            tvPreview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(tvPreview);
        }

        // ── Hàng nút hành động ────────────────────────────────────────
        LinearLayout rowActions = new LinearLayout(this);
        rowActions.setOrientation(LinearLayout.HORIZONTAL);
        rowActions.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams actParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actParams.topMargin = (int)(10*dp);
        rowActions.setLayoutParams(actParams);

        // Nút Xóa
        TextView btnDelete = new TextView(this);
        btnDelete.setText("🗑 Xóa");
        btnDelete.setTextColor(Color.parseColor("#EF5350"));
        btnDelete.setTextSize(12);
        btnDelete.setPadding((int)(10*dp), (int)(4*dp), (int)(10*dp), (int)(4*dp));
        btnDelete.setOnClickListener(v -> confirmDelete(entry));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        deleteParams.setMargins(0, 0, (int)(8*dp), 0);
        btnDelete.setLayoutParams(deleteParams);
        rowActions.addView(btnDelete);

        // Nút Nghe lại ▶
        TextView btnPlay = new TextView(this);
        btnPlay.setText("▶ Nghe lại");
        btnPlay.setTextColor(Color.parseColor("#4CAF50"));
        btnPlay.setTextSize(12);
        btnPlay.setTypeface(null, Typeface.BOLD);
        btnPlay.setPadding((int)(10*dp), (int)(4*dp), (int)(10*dp), (int)(4*dp));
        btnPlay.setOnClickListener(v -> playHistory(entry));
        rowActions.addView(btnPlay);

        card.addView(rowActions);

        return card;
    }

    // ────────────────────────────────────────────────────────────────────
    //  ACTION: PLAY
    // ────────────────────────────────────────────────────────────────────

    /**
     * Phát lại lịch sử:
     *  - TEXT: gửi textContent sang MainActivity để đọc ngay
     *  - FILE: mở MainActivity + truyền URI file để tải lại
     */
    private void playHistory(ReadingHistory entry) {
        if ("TEXT".equals(entry.sourceType)) {
            // ── TEXT: navigate về MainActivity, điền vào ô text rồi phát ──
            if (entry.textContent == null || entry.textContent.isEmpty()) {
                Toast.makeText(this, "Nội dung text không còn được lưu trữ",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("history_text_content", entry.textContent);
            intent.putExtra("history_text_name",
                    entry.fileName != null ? entry.fileName : "");
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();  // đóng HistoriesActivity để MainActivity hiển thị rõ

        } else {
            // ── FILE: mở MainActivity → load file → resume từ vị trí lưu ──
            if (entry.filePath == null || entry.filePath.isEmpty()
                    || entry.filePath.startsWith("text://")) {
                Toast.makeText(this, "Không tìm thấy đường dẫn file", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("history_file_path",  entry.filePath);
            intent.putExtra("history_file_name",  entry.fileName != null ? entry.fileName : "");
            intent.putExtra("history_last_index", entry.lastReadIndex);
            // FLAG_ACTIVITY_SINGLE_TOP: nếu MainActivity đang ở top → gọi onNewIntent
            // FLAG_ACTIVITY_CLEAR_TOP : đưa MainActivity lên đầu stack
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();  // đóng HistoriesActivity để MainActivity hiển thị rõ
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  ACTION: XÓA
    // ────────────────────────────────────────────────────────────────────

    private void confirmDelete(ReadingHistory entry) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa khỏi lịch sử?")
                .setMessage("\"" + entry.fileName + "\" sẽ bị xóa vĩnh viễn.")
                .setPositiveButton("Xóa", (d, w) -> new Thread(() -> {
                    db.historyDao().delete(entry);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Đã xóa", Toast.LENGTH_SHORT).show();
                        loadHistory();
                    });
                }).start())
                .setNegativeButton("Hủy", null)
                .show();
    }
}