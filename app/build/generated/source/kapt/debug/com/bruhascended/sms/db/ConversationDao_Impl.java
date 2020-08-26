package com.bruhascended.sms.db;

import android.database.Cursor;
import androidx.lifecycle.LiveData;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
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
public final class ConversationDao_Impl implements ConversationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Conversation> __insertionAdapterOfConversation;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<Conversation> __deletionAdapterOfConversation;

  private final EntityDeletionOrUpdateAdapter<Conversation> __updateAdapterOfConversation;

  public ConversationDao_Impl(RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConversation = new EntityInsertionAdapter<Conversation>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR ABORT INTO `conversations` (`id`,`sender`,`name`,`dp`,`read`,`time`,`lastSMS`,`label`,`forceLabel`,`probs`,`isMuted`,`lastMMS`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, Conversation value) {
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
        if (value.getName() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getName());
        }
        if (value.getDp() == null) {
          stmt.bindNull(4);
        } else {
          stmt.bindString(4, value.getDp());
        }
        final int _tmp;
        _tmp = value.getRead() ? 1 : 0;
        stmt.bindLong(5, _tmp);
        stmt.bindLong(6, value.getTime());
        if (value.getLastSMS() == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindString(7, value.getLastSMS());
        }
        stmt.bindLong(8, value.getLabel());
        stmt.bindLong(9, value.getForceLabel());
        final String _tmp_1;
        _tmp_1 = __converters.listToJson(value.getProbs());
        if (_tmp_1 == null) {
          stmt.bindNull(10);
        } else {
          stmt.bindString(10, _tmp_1);
        }
        final int _tmp_2;
        _tmp_2 = value.isMuted() ? 1 : 0;
        stmt.bindLong(11, _tmp_2);
        final int _tmp_3;
        _tmp_3 = value.getLastMMS() ? 1 : 0;
        stmt.bindLong(12, _tmp_3);
      }
    };
    this.__deletionAdapterOfConversation = new EntityDeletionOrUpdateAdapter<Conversation>(__db) {
      @Override
      public String createQuery() {
        return "DELETE FROM `conversations` WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, Conversation value) {
        if (value.getId() == null) {
          stmt.bindNull(1);
        } else {
          stmt.bindLong(1, value.getId());
        }
      }
    };
    this.__updateAdapterOfConversation = new EntityDeletionOrUpdateAdapter<Conversation>(__db) {
      @Override
      public String createQuery() {
        return "UPDATE OR ABORT `conversations` SET `id` = ?,`sender` = ?,`name` = ?,`dp` = ?,`read` = ?,`time` = ?,`lastSMS` = ?,`label` = ?,`forceLabel` = ?,`probs` = ?,`isMuted` = ?,`lastMMS` = ? WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, Conversation value) {
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
        if (value.getName() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getName());
        }
        if (value.getDp() == null) {
          stmt.bindNull(4);
        } else {
          stmt.bindString(4, value.getDp());
        }
        final int _tmp;
        _tmp = value.getRead() ? 1 : 0;
        stmt.bindLong(5, _tmp);
        stmt.bindLong(6, value.getTime());
        if (value.getLastSMS() == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindString(7, value.getLastSMS());
        }
        stmt.bindLong(8, value.getLabel());
        stmt.bindLong(9, value.getForceLabel());
        final String _tmp_1;
        _tmp_1 = __converters.listToJson(value.getProbs());
        if (_tmp_1 == null) {
          stmt.bindNull(10);
        } else {
          stmt.bindString(10, _tmp_1);
        }
        final int _tmp_2;
        _tmp_2 = value.isMuted() ? 1 : 0;
        stmt.bindLong(11, _tmp_2);
        final int _tmp_3;
        _tmp_3 = value.getLastMMS() ? 1 : 0;
        stmt.bindLong(12, _tmp_3);
        if (value.getId() == null) {
          stmt.bindNull(13);
        } else {
          stmt.bindLong(13, value.getId());
        }
      }
    };
  }

  @Override
  public void insert(final Conversation conversation) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfConversation.insert(conversation);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void delete(final Conversation conversation) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfConversation.handle(conversation);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final Conversation conversation) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfConversation.handle(conversation);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public List<Conversation> findBySender(final String sender) {
    final String _sql = "SELECT * FROM conversations WHERE sender LIKE ? OR name LIKE ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    if (sender == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, sender);
    }
    _argIndex = 2;
    if (sender == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, sender);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfDp = CursorUtil.getColumnIndexOrThrow(_cursor, "dp");
      final int _cursorIndexOfRead = CursorUtil.getColumnIndexOrThrow(_cursor, "read");
      final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
      final int _cursorIndexOfLastSMS = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSMS");
      final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
      final int _cursorIndexOfForceLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "forceLabel");
      final int _cursorIndexOfProbs = CursorUtil.getColumnIndexOrThrow(_cursor, "probs");
      final int _cursorIndexOfIsMuted = CursorUtil.getColumnIndexOrThrow(_cursor, "isMuted");
      final int _cursorIndexOfLastMMS = CursorUtil.getColumnIndexOrThrow(_cursor, "lastMMS");
      final List<Conversation> _result = new ArrayList<Conversation>(_cursor.getCount());
      while(_cursor.moveToNext()) {
        final Conversation _item;
        final Long _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getLong(_cursorIndexOfId);
        }
        final String _tmpSender;
        _tmpSender = _cursor.getString(_cursorIndexOfSender);
        final String _tmpName;
        _tmpName = _cursor.getString(_cursorIndexOfName);
        final String _tmpDp;
        _tmpDp = _cursor.getString(_cursorIndexOfDp);
        final boolean _tmpRead;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfRead);
        _tmpRead = _tmp != 0;
        final long _tmpTime;
        _tmpTime = _cursor.getLong(_cursorIndexOfTime);
        final String _tmpLastSMS;
        _tmpLastSMS = _cursor.getString(_cursorIndexOfLastSMS);
        final int _tmpLabel;
        _tmpLabel = _cursor.getInt(_cursorIndexOfLabel);
        final int _tmpForceLabel;
        _tmpForceLabel = _cursor.getInt(_cursorIndexOfForceLabel);
        final float[] _tmpProbs;
        final String _tmp_1;
        _tmp_1 = _cursor.getString(_cursorIndexOfProbs);
        _tmpProbs = __converters.jsonToList(_tmp_1);
        final boolean _tmpIsMuted;
        final int _tmp_2;
        _tmp_2 = _cursor.getInt(_cursorIndexOfIsMuted);
        _tmpIsMuted = _tmp_2 != 0;
        final boolean _tmpLastMMS;
        final int _tmp_3;
        _tmp_3 = _cursor.getInt(_cursorIndexOfLastMMS);
        _tmpLastMMS = _tmp_3 != 0;
        _item = new Conversation(_tmpId,_tmpSender,_tmpName,_tmpDp,_tmpRead,_tmpTime,_tmpLastSMS,_tmpLabel,_tmpForceLabel,_tmpProbs,_tmpIsMuted,_tmpLastMMS);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<Conversation>> loadAll() {
    final String _sql = "SELECT * FROM conversations ORDER BY time DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[]{"conversations"}, false, new Callable<List<Conversation>>() {
      @Override
      public List<Conversation> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDp = CursorUtil.getColumnIndexOrThrow(_cursor, "dp");
          final int _cursorIndexOfRead = CursorUtil.getColumnIndexOrThrow(_cursor, "read");
          final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
          final int _cursorIndexOfLastSMS = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSMS");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfForceLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "forceLabel");
          final int _cursorIndexOfProbs = CursorUtil.getColumnIndexOrThrow(_cursor, "probs");
          final int _cursorIndexOfIsMuted = CursorUtil.getColumnIndexOrThrow(_cursor, "isMuted");
          final int _cursorIndexOfLastMMS = CursorUtil.getColumnIndexOrThrow(_cursor, "lastMMS");
          final List<Conversation> _result = new ArrayList<Conversation>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final Conversation _item;
            final Long _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getLong(_cursorIndexOfId);
            }
            final String _tmpSender;
            _tmpSender = _cursor.getString(_cursorIndexOfSender);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpDp;
            _tmpDp = _cursor.getString(_cursorIndexOfDp);
            final boolean _tmpRead;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRead);
            _tmpRead = _tmp != 0;
            final long _tmpTime;
            _tmpTime = _cursor.getLong(_cursorIndexOfTime);
            final String _tmpLastSMS;
            _tmpLastSMS = _cursor.getString(_cursorIndexOfLastSMS);
            final int _tmpLabel;
            _tmpLabel = _cursor.getInt(_cursorIndexOfLabel);
            final int _tmpForceLabel;
            _tmpForceLabel = _cursor.getInt(_cursorIndexOfForceLabel);
            final float[] _tmpProbs;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfProbs);
            _tmpProbs = __converters.jsonToList(_tmp_1);
            final boolean _tmpIsMuted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsMuted);
            _tmpIsMuted = _tmp_2 != 0;
            final boolean _tmpLastMMS;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfLastMMS);
            _tmpLastMMS = _tmp_3 != 0;
            _item = new Conversation(_tmpId,_tmpSender,_tmpName,_tmpDp,_tmpRead,_tmpTime,_tmpLastSMS,_tmpLabel,_tmpForceLabel,_tmpProbs,_tmpIsMuted,_tmpLastMMS);
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
  public List<Conversation> findByQuery(final SupportSQLiteQuery query) {
    final SupportSQLiteQuery _internalQuery = query;
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _internalQuery, false, null);
    try {
      final List<Conversation> _result = new ArrayList<Conversation>(_cursor.getCount());
      while(_cursor.moveToNext()) {
        final Conversation _item;
        _item = __entityCursorConverter_comBruhascendedSmsDbConversation(_cursor);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
    }
  }

  private Conversation __entityCursorConverter_comBruhascendedSmsDbConversation(Cursor cursor) {
    final Conversation _entity;
    final int _cursorIndexOfId = cursor.getColumnIndex("id");
    final int _cursorIndexOfSender = cursor.getColumnIndex("sender");
    final int _cursorIndexOfName = cursor.getColumnIndex("name");
    final int _cursorIndexOfDp = cursor.getColumnIndex("dp");
    final int _cursorIndexOfRead = cursor.getColumnIndex("read");
    final int _cursorIndexOfTime = cursor.getColumnIndex("time");
    final int _cursorIndexOfLastSMS = cursor.getColumnIndex("lastSMS");
    final int _cursorIndexOfLabel = cursor.getColumnIndex("label");
    final int _cursorIndexOfForceLabel = cursor.getColumnIndex("forceLabel");
    final int _cursorIndexOfProbs = cursor.getColumnIndex("probs");
    final int _cursorIndexOfIsMuted = cursor.getColumnIndex("isMuted");
    final int _cursorIndexOfLastMMS = cursor.getColumnIndex("lastMMS");
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
    final String _tmpName;
    if (_cursorIndexOfName == -1) {
      _tmpName = null;
    } else {
      _tmpName = cursor.getString(_cursorIndexOfName);
    }
    final String _tmpDp;
    if (_cursorIndexOfDp == -1) {
      _tmpDp = null;
    } else {
      _tmpDp = cursor.getString(_cursorIndexOfDp);
    }
    final boolean _tmpRead;
    if (_cursorIndexOfRead == -1) {
      _tmpRead = false;
    } else {
      final int _tmp;
      _tmp = cursor.getInt(_cursorIndexOfRead);
      _tmpRead = _tmp != 0;
    }
    final long _tmpTime;
    if (_cursorIndexOfTime == -1) {
      _tmpTime = 0;
    } else {
      _tmpTime = cursor.getLong(_cursorIndexOfTime);
    }
    final String _tmpLastSMS;
    if (_cursorIndexOfLastSMS == -1) {
      _tmpLastSMS = null;
    } else {
      _tmpLastSMS = cursor.getString(_cursorIndexOfLastSMS);
    }
    final int _tmpLabel;
    if (_cursorIndexOfLabel == -1) {
      _tmpLabel = 0;
    } else {
      _tmpLabel = cursor.getInt(_cursorIndexOfLabel);
    }
    final int _tmpForceLabel;
    if (_cursorIndexOfForceLabel == -1) {
      _tmpForceLabel = 0;
    } else {
      _tmpForceLabel = cursor.getInt(_cursorIndexOfForceLabel);
    }
    final float[] _tmpProbs;
    if (_cursorIndexOfProbs == -1) {
      _tmpProbs = null;
    } else {
      final String _tmp_1;
      _tmp_1 = cursor.getString(_cursorIndexOfProbs);
      _tmpProbs = __converters.jsonToList(_tmp_1);
    }
    final boolean _tmpIsMuted;
    if (_cursorIndexOfIsMuted == -1) {
      _tmpIsMuted = false;
    } else {
      final int _tmp_2;
      _tmp_2 = cursor.getInt(_cursorIndexOfIsMuted);
      _tmpIsMuted = _tmp_2 != 0;
    }
    final boolean _tmpLastMMS;
    if (_cursorIndexOfLastMMS == -1) {
      _tmpLastMMS = false;
    } else {
      final int _tmp_3;
      _tmp_3 = cursor.getInt(_cursorIndexOfLastMMS);
      _tmpLastMMS = _tmp_3 != 0;
    }
    _entity = new Conversation(_tmpId,_tmpSender,_tmpName,_tmpDp,_tmpRead,_tmpTime,_tmpLastSMS,_tmpLabel,_tmpForceLabel,_tmpProbs,_tmpIsMuted,_tmpLastMMS);
    return _entity;
  }
}
