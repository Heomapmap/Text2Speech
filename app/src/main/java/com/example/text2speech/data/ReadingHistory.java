package com.example.text2speech.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Tạo bảng ReadingHistory, gán mác là @Entity.
@Entity(tableName = "reading_history")
public class ReadingHistory {

    // autoGenerate để id tự tăng thêm 1 cho mỗi bản ghi mới.
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String fileName; // Tên file (ví dụ: "Triết Mác-Lênin.pdf").
    public String filePath; // Đường dẫn của file trong máy.
    public int lastReadIndex; // Lưu vị trí câu đang đọc (ví dụ: câu số 15).
    public ReadingHistory(String fileName, String filePath, int lastReadIndex) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.lastReadIndex = lastReadIndex;
    }
}