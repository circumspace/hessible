package com.circumspace.contactstr.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SyncAdapterService : Service() {
    private val syncAdapter by lazy { ContactsSyncAdapter(this, true) }

    override fun onBind(intent: Intent): IBinder = syncAdapter.syncAdapterBinder
}
