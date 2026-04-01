package com.example.text2speech;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class Text2SpeechActivity extends AppCompatActivity {

    private AppDatabase db;
    private SharedPreferences pref;
    // Biến lưu trạng thái file đang đọc.
    private String currentFilePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_text2_speech);

        // Khởi tạo Room Database.
        db = AppDatabase.getDatabase(getApplicationContext());

        // Khởi tạo SharedPreferences.
        pref = getSharedPreferences("tts_pref", MODE_PRIVATE);

        // Gọi hàm khôi phục trạng thái file đang đọc từ phiên trước.
        loadReadingState();
    }

    public void saveReadingState(String fileName, String filePath, int currentIndex) {
        // Dùng SharedPreferences để lưu nhanh đường dẫn file vừa đọc (định danh là last_file_path).
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("last_file_path", filePath); // Đường dẫn lấy từ Service TTS chạy ngầm.
        editor.apply(); // Dùng apply để lưu thay đổi mà không gián đoạn luồng chính.

        // Dùng Room để lưu tiến độ đọc.
        new Thread(() -> { //
            // Xem file đã được đọc trước đây chưa.
            ReadingHistory existingHistory = db.historyDao().getHistoryByPath(filePath);

            if (existingHistory == null) {
                // Nếu chưa từng đọc, tạo bản ghi mới và insert.
                ReadingHistory newHistory = new ReadingHistory(fileName, filePath, currentIndex);
                db.historyDao().insert(newHistory);
            } else {
                // Nếu đã từng đọc, cập nhật lại Index mới.
                existingHistory.lastReadIndex = currentIndex; // Service TTS phải gọi hàm này khi đọc xong để lưu tên file, path và index.
                db.historyDao().update(existingHistory);
            }
        }).start();
    }

    private void loadReadingState() {
        // Hỏi SharedPreferences xem phiên gần nhất đọc file nào (nhẹ nên có thể hỏi luôn trên luồng chính).
        currentFilePath = pref.getString("last_file_path", "");

        if (!currentFilePath.isEmpty()) { // Nếu không tìm được path, tức người dùng chưa đọc file nào thì bỏ qua hàm này.
            // Sau khi xác định được file, qua Room để biết file đọc được đến đâu rồi.
            new Thread(() -> { // Truy vấn database nặng nên phải cho vào luồng phụ.
                ReadingHistory history = db.historyDao().getHistoryByPath(currentFilePath);

                if (history != null) { // Lấy tên file và vị trí câu đang đọc ở phiên trước.
                    int savedIndex = history.lastReadIndex;
                    String savedName = history.fileName;

                    // Cập nhật lại giao diện trên luồng chính.
                    runOnUiThread(() -> {
                        /* Code giả tạm thời để hiện tên file, gọi hàm đọc file, hiển thị chữ lên giao diện, và cuộn đến đúng câu có index = savedIndex.
                        titleTextView.setText(savedName);
                        List<String> sentences = extractTextFromFile(currentFilePath);
                        adapter.setList(sentences);
                        recyclerView.scrollToPosition(savedIndex);
                        adapter.setHighlightPosition(savedIndex);
                         */
                    });
                }
            }).start();
        }
    }
}

