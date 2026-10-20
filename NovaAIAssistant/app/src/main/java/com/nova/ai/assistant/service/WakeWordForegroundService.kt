package com.nova.ai.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nova.ai.assistant.MainActivity

/**
 * Foreground Service لاستماع كلمة التنبيه «يا نوفا» في الخلفية.
 * ملاحظة هندسية: على Android 14+، يتطلب تشغيل الميكروفون بالخلفية
 * صلاحية FOREGROUND_SERVICE_MICROPHONE وإشعار دائم واضح للمستخدم.
 */
class WakeWordForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "nova_wake_word_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_WAKE_WORD"
        const val ACTION_STOP = "ACTION_STOP_WAKE_WORD"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // هنا يبدأ محرك كشف الكلمة المفتاحية (Keyword Spotter)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOVA AI يعمل بالخلفية")
            .setContentText("المساعد الصوتي يستمع لكلمة التنبيه «يا نوفا»")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVA AI Voice Listener",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
