package com.example.questionaire;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class QuestionDao_Impl implements QuestionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Question> __insertionAdapterOfQuestion;

  private final EntityDeletionOrUpdateAdapter<Question> __deletionAdapterOfQuestion;

  private final EntityDeletionOrUpdateAdapter<Question> __updateAdapterOfQuestion;

  public QuestionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfQuestion = new EntityInsertionAdapter<Question>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `Question` (`id`,`content`,`options`,`answer`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Question entity) {
        statement.bindLong(1, entity.id);
        if (entity.content == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.content);
        }
        if (entity.options == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.options);
        }
        if (entity.answer == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.answer);
        }
      }
    };
    this.__deletionAdapterOfQuestion = new EntityDeletionOrUpdateAdapter<Question>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `Question` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Question entity) {
        statement.bindLong(1, entity.id);
      }
    };
    this.__updateAdapterOfQuestion = new EntityDeletionOrUpdateAdapter<Question>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `Question` SET `id` = ?,`content` = ?,`options` = ?,`answer` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Question entity) {
        statement.bindLong(1, entity.id);
        if (entity.content == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.content);
        }
        if (entity.options == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.options);
        }
        if (entity.answer == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.answer);
        }
        statement.bindLong(5, entity.id);
      }
    };
  }

  @Override
  public void insert(final Question question) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfQuestion.insert(question);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insertAll(final List<Question> questions) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfQuestion.insert(questions);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void delete(final Question question) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfQuestion.handle(question);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final Question question) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfQuestion.handle(question);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public Question getById(final int questionId) {
    final String _sql = "SELECT * FROM question WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, questionId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfOptions = CursorUtil.getColumnIndexOrThrow(_cursor, "options");
      final int _cursorIndexOfAnswer = CursorUtil.getColumnIndexOrThrow(_cursor, "answer");
      final Question _result;
      if (_cursor.moveToFirst()) {
        _result = new Question();
        _result.id = _cursor.getInt(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfContent)) {
          _result.content = null;
        } else {
          _result.content = _cursor.getString(_cursorIndexOfContent);
        }
        if (_cursor.isNull(_cursorIndexOfOptions)) {
          _result.options = null;
        } else {
          _result.options = _cursor.getString(_cursorIndexOfOptions);
        }
        if (_cursor.isNull(_cursorIndexOfAnswer)) {
          _result.answer = null;
        } else {
          _result.answer = _cursor.getString(_cursorIndexOfAnswer);
        }
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<Question> getAll() {
    final String _sql = "SELECT * FROM question";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfOptions = CursorUtil.getColumnIndexOrThrow(_cursor, "options");
      final int _cursorIndexOfAnswer = CursorUtil.getColumnIndexOrThrow(_cursor, "answer");
      final List<Question> _result = new ArrayList<Question>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Question _item;
        _item = new Question();
        _item.id = _cursor.getInt(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfContent)) {
          _item.content = null;
        } else {
          _item.content = _cursor.getString(_cursorIndexOfContent);
        }
        if (_cursor.isNull(_cursorIndexOfOptions)) {
          _item.options = null;
        } else {
          _item.options = _cursor.getString(_cursorIndexOfOptions);
        }
        if (_cursor.isNull(_cursorIndexOfAnswer)) {
          _item.answer = null;
        } else {
          _item.answer = _cursor.getString(_cursorIndexOfAnswer);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
