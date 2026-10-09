package com.example.localupload.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile PendingPhotoDao _pendingPhotoDao;

  private volatile UploadHistoryDao _uploadHistoryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri_string` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `display_name` TEXT NOT NULL, `added_at` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `upload_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri_string` TEXT NOT NULL, `display_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `status` TEXT NOT NULL, `server_message` TEXT NOT NULL, `uploaded_at` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f04d7b0dda71caf7a9a612643e9f7360')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `pending_photos`");
        db.execSQL("DROP TABLE IF EXISTS `upload_history`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsPendingPhotos = new HashMap<String, TableInfo.Column>(5);
        _columnsPendingPhotos.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingPhotos.put("uri_string", new TableInfo.Column("uri_string", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingPhotos.put("mime_type", new TableInfo.Column("mime_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingPhotos.put("display_name", new TableInfo.Column("display_name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingPhotos.put("added_at", new TableInfo.Column("added_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPendingPhotos = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPendingPhotos = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPendingPhotos = new TableInfo("pending_photos", _columnsPendingPhotos, _foreignKeysPendingPhotos, _indicesPendingPhotos);
        final TableInfo _existingPendingPhotos = TableInfo.read(db, "pending_photos");
        if (!_infoPendingPhotos.equals(_existingPendingPhotos)) {
          return new RoomOpenHelper.ValidationResult(false, "pending_photos(com.example.localupload.database.PendingPhotoEntity).\n"
                  + " Expected:\n" + _infoPendingPhotos + "\n"
                  + " Found:\n" + _existingPendingPhotos);
        }
        final HashMap<String, TableInfo.Column> _columnsUploadHistory = new HashMap<String, TableInfo.Column>(7);
        _columnsUploadHistory.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUploadHistory.put("uri_string", new TableInfo.Column("uri_string", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUploadHistory.put("display_name", new TableInfo.Column("display_name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUploadHistory.put("mime_type", new TableInfo.Column("mime_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUploadHistory.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUploadHistory.put("server_message", new TableInfo.Column("server_message", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUploadHistory.put("uploaded_at", new TableInfo.Column("uploaded_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUploadHistory = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUploadHistory = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUploadHistory = new TableInfo("upload_history", _columnsUploadHistory, _foreignKeysUploadHistory, _indicesUploadHistory);
        final TableInfo _existingUploadHistory = TableInfo.read(db, "upload_history");
        if (!_infoUploadHistory.equals(_existingUploadHistory)) {
          return new RoomOpenHelper.ValidationResult(false, "upload_history(com.example.localupload.database.UploadHistoryEntity).\n"
                  + " Expected:\n" + _infoUploadHistory + "\n"
                  + " Found:\n" + _existingUploadHistory);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "f04d7b0dda71caf7a9a612643e9f7360", "3f73067831cf7884c439a7e8c6639741");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "pending_photos","upload_history");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `pending_photos`");
      _db.execSQL("DELETE FROM `upload_history`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(PendingPhotoDao.class, PendingPhotoDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UploadHistoryDao.class, UploadHistoryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public PendingPhotoDao pendingPhotoDao() {
    if (_pendingPhotoDao != null) {
      return _pendingPhotoDao;
    } else {
      synchronized(this) {
        if(_pendingPhotoDao == null) {
          _pendingPhotoDao = new PendingPhotoDao_Impl(this);
        }
        return _pendingPhotoDao;
      }
    }
  }

  @Override
  public UploadHistoryDao uploadHistoryDao() {
    if (_uploadHistoryDao != null) {
      return _uploadHistoryDao;
    } else {
      synchronized(this) {
        if(_uploadHistoryDao == null) {
          _uploadHistoryDao = new UploadHistoryDao_Impl(this);
        }
        return _uploadHistoryDao;
      }
    }
  }
}
