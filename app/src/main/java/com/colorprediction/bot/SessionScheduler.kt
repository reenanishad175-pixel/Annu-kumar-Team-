package com.colorprediction.bot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Inexact idle-friendly alarms for the four daily session reminders.
 * Android may defer these alarms under battery optimization; they are not guaranteed exact.
 */
object SessionScheduler {
    private val hours = intArrayOf(10, 15, 18, 21)

    fun scheduleAll(context: Context) {
        for (index in hours.indices) {
            scheduleNext(context, index, true)
            scheduleNext(context, index, false)
        }
    }

    fun scheduleNext(context: Context, index: Int, ready: Boolean) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hours[index])
            set(Calendar.MINUTE, if (ready)  -5 else 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val action = if (ready) "READY" else "SESSION"
        val intent = Intent(context, SessionAlarmReceiver::class.java)
            .setAction("com.colorprediction.bot.$action.$index")
            .putExtra("session_index", index)
            .putExtra("is_ready", ready)
        val requestCode = index * 2 + if (ready) 1 else 0
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
    }
}

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra("session_index", 0).coerceIn(0, 3)
        val ready = intent.getBooleanExtra("is_ready", false)
        SessionScheduler.scheduleNext(context, index, ready)
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        val token = prefs.getString("tg_token", "").orEmpty()
        val chat = prefs.getString("tg_chat", "").orEmpty()
        if (token.isBlank() || chat.isBlank()) return
        val hour = intArrayOf(10, 15, 18, 21)[index]
        val message = if (ready) {
            "ARE YOU READY? Your $hour:00 prediction session starts in 5 minutes."
        } else {
            "Your $hour:00 prediction session has started. Waiting for verified BDG period/result data."
        }
        val pendingResult = goAsync()
        TelegramNotifier.send(token, chat, message) {
            pendingResult.finish()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) SessionScheduler.scheduleAll(context)
    }
}
