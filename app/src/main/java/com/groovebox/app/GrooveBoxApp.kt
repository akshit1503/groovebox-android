package com.groovebox.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class GrooveBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "playback_channel",
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Now playing controls" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        com.groovebox.app.playback.MediaEventsReceiver.register(this)
    }
}
