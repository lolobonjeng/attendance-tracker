package com.cph.att;

import android.app.Application;

import com.cph.att.backup.BackupManager;
import com.cph.att.data.Repo;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Restore before anything opens the database.
        BackupManager.get(this).autoRestoreIfNeeded();

        Repo repo = Repo.get(this);
        repo.ensureSeed();
        Repo.setChangeHook(() -> BackupManager.get(this).requestAutoSave());
    }
}
