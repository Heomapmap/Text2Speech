package com.example.text2speech;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ContextMenuActivity extends AppCompatActivity {

    String text = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Không gọi layout nào ở đây cả, tiến trình chạy mà không ảnh hưởng đến UI của người dùng.

        // Tham khảo từ https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT và https://developer.android.com/reference/java/lang/CharSequence
        try {
            CharSequence data = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT); // Dùng EXTRA_PROCESS_TEXT để lấy văn bản về, nhưng ở đây phải có CharSequence vì nó bao quát hơn String thông thường và có thể hiểu được text có định dạng đặc biệt như in đậm, in nghiêng.
            if (data != null) {
                text = data.toString(); // Lấy văn bản về và loại bỏ các định dạng đặc biệt, chỉ giữ text thô.
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi lấy văn bản", Toast.LENGTH_SHORT).show(); // Báo lỗi.
            finish();
            return; // Dừng tiến trình ngay khi có lỗi.
        }

        if (!text.isEmpty()) {
            // Code giả tạm thời để bắn dữ liệu qua Service TTS:
            /*
            Intent serviceIntent = new Intent(this, TextReaderService.class);
            serviceIntent.putExtra("abc", text);
            startService(serviceIntent);
            */
            Toast.makeText(this, "Đang chuẩn bị đọc...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Không có văn bản để đọc", Toast.LENGTH_SHORT).show();
        }

        // Kết thúc tiến trình.
        finish();
    }
}