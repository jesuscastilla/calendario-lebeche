package com.lebeche.calendario.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lebeche.calendario.sync.SyncWorker

/** Reprograma recordatorios y sincronización tras reiniciar el dispositivo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
            SyncWorker.schedule(context)
        }
    }
}
