package com.example.text2speech.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ReadingHistoryDao {
    
    // Thêm lịch sử đọc.
    @Insert
    void insert(ReadingHistory history);

    // Cập nhật lịch sử đọc.
    @Update
    void update(ReadingHistory history);

    //Nhi bổ sung
    @Delete
    void delete(ReadingHistory history);

    @Query("DELETE FROM reading_history") //Xoa toan bo lich su
    void deleteAll();

    @Query("SELECT * FROM reading_history WHERE id = :id LIMIT 1")
    ReadingHistory getHistoryById(int id); //Lay lich su theo id


    // Tìm lịch sử của file người dùng đang mở.
    @Query("SELECT * FROM reading_history WHERE filePath = :path LIMIT 1")
    ReadingHistory getHistoryByPath(String path);

    // Lấy toàn bộ lịch sử để hiển thị lên màn hình "Danh sách file đã đọc"
    @Query("SELECT * FROM reading_history ORDER BY timestamp DESC") //Sap xep theo thoi gian luu
    List<ReadingHistory> getAllHistory();

}