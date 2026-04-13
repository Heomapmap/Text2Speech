package com.example.text2speech;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// Tạo Database, gán mác @Database.
@Database(entities = {ReadingHistory.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase { // Kế thừa RoomDatabase.

    private static AppDatabase INSTANCE;

    // Khai báo hàm abstract để lấy DAO.
    public abstract ReadingHistoryDao historyDao();
    
    // Gọi Database.
    public static synchronized AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            // Nếu chưa có, tiến hành tạo file database "tts_database"
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "tts_database")
                    .build();
        }
        return INSTANCE;
    }
}