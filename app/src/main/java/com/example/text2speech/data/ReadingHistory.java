package com.example.text2speech.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

// Tạo bảng ReadingHistory, gán mác là @Entity.


/** Nhi bổ sung
 * Thêm mới:
 *  - timestamp : thời điểm lưu (ms)
 *  - sourceType: "FILE" hoặc "TEXT" (phân biệt nguồn file vs. text nhập tay)
 *  - textContent: nội dung text (chỉ dùng khi sourceType = "TEXT")
 */
@Entity(tableName = "reading_history")
public class ReadingHistory {

    // autoGenerate để id tự tăng thêm 1 cho mỗi bản ghi mới.
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String fileName; // Tên file (ví dụ: "Triết Mác-Lênin.pdf").
    public String filePath; // Đường dẫn của file trong máy.
    public int lastReadIndex; // Lưu vị trí câu đang đọc (ví dụ: câu số 15).

    //Nhi bổ sung
    public long timestamp; // Thời điểm lưu (ms)
    public String sourceType; // "FILE" hoặc "TEXT"
    public String textContent; // Nội dung text (chỉ dùng khi sourceType = "TEXT")


    public ReadingHistory(String fileName, String filePath, int lastReadIndex, long timestamp, String sourceType, String textContent) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.lastReadIndex = lastReadIndex;
        //Nhi bổ sung
        this.timestamp = timestamp;
        this.sourceType = sourceType;
        this.textContent = textContent;

    }

    @Ignore
    public ReadingHistory(String fileName, String filePath, int lastReadIndex) {
        this(fileName, filePath, lastReadIndex,
                System.currentTimeMillis(), "FILE", null);
    }
}