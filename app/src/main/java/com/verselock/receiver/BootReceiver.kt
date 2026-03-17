package com.verselock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.verselock.service.LyricsService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Do not start LyricsService on boot to avoid ForegroundServiceStartNotAllowedException
            // MediaListenerService is automatically bound by the OS on boot and will start LyricsService when needed.
        }
    }
}
