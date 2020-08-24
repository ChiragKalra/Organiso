package com.bruhascended.sms.db;

import android.database.Cursor;
import androidx.lifecycle.LiveData;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@SuppressWarnings({"unchecked", "deprecation"})
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Message> __insertionAdapterOfMessage;

  private final EntityDeletionOrUpdateAdapter<Message> __deletionAdapterOfMessage;

  private final EntityDeletionOrUpdateAdapter<Message> __updateAdapterOfMessage;

  private final SharedSQLiteStatement __preparedStmtOfNukeTable;

  public MessageDao_Impl(RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessage = new EntityInsertionAdapter<Message>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR ABORT INTO `messages` (`id`,`sender`,`text`,`type`,`time`,`label`,`delivered`,`path`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, Message value) {
        if (value.getId() == null) {
          stmt.bindNull(1);
        } else {
          stmt.bindLong(1, value.getId());
        }
        if (value.getSender() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getSender());
        }
        if (value.getText() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getText());
        }
        stmt.bindLong(4, value.getType());
        stmt.bindLong(5, value.getTime());
        stmt.bindLong(6, value.getLabel());
        final int _tmp;
        _tmp = value.getDelivered() ? 1 : 0;
        stmt.bindLong(7, _tmp);
        if (value.getPath() == null) {
          stmt.bindNull(8);
        } else {
          stmt.bindString(8, value.getPath());
        }
      }
    };
    this.__deletionAdapterOfMessage = new EntityDeletionOrUpdateAdapter<Message>(__db) {
      @Override
      public String createQuery() {
        return "DELETE FROM `messages` WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, Message value) {
        if (value.getId() == null) {
          stmt.bindNull(1);
        } else {
          stmt.bindLong(1, value.getId());
        }
      }
    };
    this.__updateAdapterOfMessage = new EntityDeletionOrUpdateAdapter<Message>(__db) {
      @Override
      public String createQuery() {
        return "UPDATE OR ABORT `messages` SET `id` = ?,`sender` = ?,`text` = ?,`type` = ?,`time` = ?,`label` = ?,`delivered` = ?,`path` = ? WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, Message value) {
        if (value.getId() == null) {
          stmt.bindNull(1);
        } else {
          stmt.bindLong(1, value.getId());
        }
        if (value.getSender() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getSender());
        }
        if (value.getText() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getText());
        }
        stmt.bindLong(4, value.getType());
        stmt.bindLong(5, value.getTime());
        stmt.bindLong(6, value.getLabel());
        final int _tmp;
        _tmp = value.getDelivered() ? 1 : 0;
        stmt.bindLong(7, _tmp);
        if (value.getPath() == null) {
          stmt.bindNull(8);
        } else {
          stmt.bindString(8, value.getPath());
        }
        if (value.getId() == null) {
          stmt.bindNull(9);
        } else {
          stmt.bindLong(9, value.getId());
        }
      }
    };
    this.__preparedStmtOfNukeTable = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM messages";
        return _query;
      }
    };
  }

  @Override
  public void insert(final Message message) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfMessage.insert(message);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void delete(final Message message) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfMessage.handle(message);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final Message message) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfMessage.handle(message);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void nukeTable() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfNukeTable.acquire();
    __db.beginTransaction();
    try {
      _stmt.executeUpdateDelete();
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
      __preparedStmtOfNukeTable.release(_stmt);
    }
  }

  @Override
  public List<Message> search(final String key) {
    final String _sql = "SELECT * FROM messages WHERE text LIKE ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (key == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, key);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
      final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
      final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
      final int _cursorIndexOfDelivered = CursorUtil.getColumnIndexOrThrow(_cursor, "delivered");
      final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
      final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
      while(_cursor.moveToNext()) {
        final Message _item;
        final Long _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getLong(_cursorIndexOfId);
        }
        final String _tmpSender;
        _tmpSender = _cursor.getString(_cursorIndexOfSender);
        final String _tmpText;
        _tmpText = _cursor.getString(_cursorIndexOfText);
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final long _tmpTime;
        _tmpTime = _cursor.getLong(_cursorIndexOfTime);
        final int _tmpLabel;
        _tmpLabel = _cursor.getInt(_cursorIndexOfLabel);
        final boolean _tmpDelivered;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDelivered);
        _tmpDelivered = _tmp != 0;
        final String _tmpPath;
        _tmpPath = _cursor.getString(_cursorIndexOfPath);
        _item = new Message(_tmpId,_tmpSender,_tmpText,_tmpType,_tmpTime,_tmpLabel,_tmpDelivered,_tmpPath);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<Message> search(final long time) {
    final String _sql = "SELECT * FROM messages WHERE time LIKE ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, time);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
      final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
      final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
      final int _cursorIndexOfDelivered = CursorUtil.getColumnIndexOrThrow(_cursor, "delivered");
      final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
      final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
      while(_cursor.moveToNext()) {
        final Message _item;
        final Long _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getLong(_cursorIndexOfId);
        }
        final String _tmpSender;
        _tmpSender = _cursor.getString(_cursorIndexOfSender);
        final String _tmpText;
        _tmpText = _cursor.getString(_cursorIndexOfText);
        final int _tmpType;
        _tmpType = _cursor.getInt(_cursorIndexOfType);
        final long _tmpTime;
        _tmpTime = _cursor.getLong(_cursorIndexOfTime);
        final int _tmpLabel;
        _tmpLabel = _cursor.getInt(_cursorIndexOfLabel);
        final boolean _tmpDelivered;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDelivered);
        _tmpDelivered = _tmp != 0;
        final String _tmpPath;
        _tmpPath = _cursor.getString(_cursorIndexOfPath);
        _item = new Message(_tmpId,_tmpSender,_tmpText,_tmpType,_tmpTime,_tmpLabel,_tmpDelivered,_tmpPath);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<Message>> loadAll() {
    final String _sql = "SELECT * FROM messages ORDER BY time ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[]{"messages"}, false, new Callable<List<Message>>() {
      @Override
      public List<Message> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfDelivered = CursorUtil.getColumnIndexOrThrow(_cursor, "delivered");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final Message _item;
            final Long _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getLong(_cursorIndexOfId);
            }
            final String _tmpSender;
            _tmpSender = _cursor.getString(_cursorIndexOfSender);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final long _tmpTime;
            _tmpTime = _cursor.getLong(_cursorIndexOfTime);
            final int _tmpLabel;
            _tmpLabel = _cursor.getInt(_cursorIndexOfLabel);
            final boolean _tmpDelivered;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDelivered);
            _tmpDelivered = _tmp != 0;
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            _item = new Message(_tmpId,_tmpSender,_tmpText,_tmpType,_tmpTime,_tmpLabel,_tmpDelivered,_tmpPath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<Message> findByQuery(final SupportSQLiteQuery query) {
    final SupportSQLiteQuery _internalQuery = query;
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _internalQuery, false, null);
    try {
      final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
      while(_cursor.moveToNext()) {
        final Message _item;
        _item = __entityCursorConverter_comBruhascendedSmsDbMessage(_cursor);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
    }
  }

  private Message __entityCursorConverter_comBruhascendedSmsDbMessage(Cursor cursor) {
    final Message _entity;
    final int _cursorIndexOfId = cursor.getColumnIndex("id");
    final int _cursorIndexOfSender = cursor.getColumnIndex("sender");
    final int _cursorIndexOfText = cursor.getColumnIndex("text");
    final int _cursorIndexOfType = cursor.getColumnIndex("type");
    final int _cursorIndexOfTime = cursor.getColumnIndex("time");
    final int _cursorIndexOfLabel = cursor.getColumnIndex("label");
    final int _cursorIndexOfDelivered = cursor.getColumnIndex("delivered");
    final int _cursorIndexOfPath = cursor.getColumnIndex("path");
    final Long _tmpId;
    if (_cursorIndexOfId == -1) {
      _tmpId = null;
    } else {
      if (cursor.isNull(_cursorIndexOfId)) {
        _tmpId = null;
      } else {
        _tmpId = cursor.getLong(_cursorIndexOfId);
      }
    }
    final String _tmpSender;
    if (_cursorIndexOfSender == -1) {
      _tmpSender = null;
    } else {
      _tmpSender = cursor.getString(_cursorIndexOfSender);
    }
    final String _tmpText;
    if (_cursorIndexOfText == -1) {
      _tmpText = null;
    } else {
      _tmpText = cursor.getString(_cursorIndexOfText);
    }
    final int _tmpType;
    if (_cursorIndexOfType == -1) {
      _tmpType = 0;
    } else {
      _tmpType = cursor.getInt(_cursorIndexOfType);
    }
    final long _tmpTime;
    if (_cursorIndexOfTime == -1) {
      _tmpTime = 0;
    } else {
      _tmpTime = cursor.getLong(_cursorIndexOfTime);
    }
    final int _tmpLabel;
    if (_cursorIndexOfLabel == -1) {
      _tmpLabel = 0;
    } else {
      _tmpLabel = cursor.getInt(_cursorIndexOfLabel);
    }
    final boolean _tmpDelivered;
    if (_cursorIndexOfDelivered == -1) {
      _tmpDelivered = false;
    } else {
      final int _tmp;
      _tmp = cursor.getInt(_cursorIndexOfDelivered);
      _tmpDelivered = _tmp != 0;
    }
    final String _tmpPath;
    if (_cursorIndexOfPath == -1) {
      _tmpPath = null;
    } else {
      _tmpPath = cursor.getString(_cursorIndexOfPath);
    }
    _entity = new Message(_tmpId,_tmpSender,_tmpText,_tmpType,_tmpTime,_tmpLabel,_tmpDelivered,_tmpPath);
    return _entity;
  }
}
