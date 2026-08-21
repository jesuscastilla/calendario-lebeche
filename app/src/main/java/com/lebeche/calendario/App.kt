package com.lebeche.calendario

import android.app.Application
import com.lebeche.calendario.notif.NotificationPublisher
import com.lebeche.calendario.sync.SyncWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationPublisher.ensureChannel(this)
        SyncWorker.schedule(this)
    }
}
