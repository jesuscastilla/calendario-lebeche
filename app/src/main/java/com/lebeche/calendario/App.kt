package com.lebeche.calendario

import android.app.Application
import com.lebeche.calendario.notif.NotificationPublisher
import com.lebeche.calendario.sync.SyncWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            NotificationPublisher.ensureChannel(this)
            SyncWorker.schedule(this)
        } catch (e: Exception) {
            // Log or handle initial setup failure without crashing the entire app
            e.printStackTrace()
        }
    }
}
