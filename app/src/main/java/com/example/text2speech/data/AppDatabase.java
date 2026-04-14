package com.example.text2speech.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.jspecify.annotations.NonNull;

// Tạo Database, gán mác @Database.

/*Nhi bo sung
* Thêm entity ReadingHistory: timestamp, sourceType, textContent vao bang reading_history
* Migration tu dong them cot, khong xoa du lieu cu
* */
@Database(entities = {ReadingHistory.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase { // Kế thừa RoomDatabase.

    private static AppDatabase INSTANCE;

    // Khai báo hàm abstract để lấy DAO.
    public abstract ReadingHistoryDao historyDao();


    //Nhi bo sung
    //Sua database them cot timestamp, sourceType, textContent
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE reading_history ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0");
            database.execSQL(
                    "ALTER TABLE reading_history ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'FILE'");
            database.execSQL(
                    "ALTER TABLE reading_history ADD COLUMN textContent TEXT");
        }
    };
    
    // Gọi Database.
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "tts_history_db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}