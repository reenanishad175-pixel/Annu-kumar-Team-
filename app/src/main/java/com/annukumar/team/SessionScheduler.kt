package com.annukumar.team

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import kotlin.concurrent.thread

object SessionScheduler {
    private const val CHANNEL_ID = "annu_session_alerts"
    private val sessionHours = intArrayOf(10, 15, 18, 21)

    fun scheduleAll(context: Context) {
        sessionHours.forEach { hour ->
            schedule(context, hour, 0, "READY")
            schedule(context, hour, 0, "START")
        }
    }

    private fun schedule(context: Context, hour: Int, minute: Int, kind: String) {
        val now = Calendar.getInstance()
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, if (kind == "READY") 55 else minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val id = hour * 10 + if (kind == "READY") 1 else if (kind == "WAIT") 3 else 2
        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            action = "com.annukumar.team.$kind.$hour"
            putExtra("kind", kind)
            putExtra("hour", hour)
        }
        val pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.timeInMillis, pending)
    }

    fun rescheduleOne(context: Context, hour: Int, kind: String) {
        schedule(context, hour, if (kind == "READY") 0 else 0, kind)
    }

    fun show(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Session reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context, 500, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setLights(0xFFFFC107.toInt(), 700, 500)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    fun sendTelegram(context: Context, message: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        val prefs = context.getSharedPreferences("paper", Context.MODE_PRIVATE)
        val token = prefs.getString("telegram_bot_token", "").orEmpty()
        val chatId = prefs.getString("telegram_chat_id", "").orEmpty()
        if (token.isBlank() || chatId.isBlank()) { onComplete?.invoke(false, "Bot token or Chat ID missing"); return }
        thread(name = "session-telegram") {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.telegram.org/bot" + token + "/sendMessage")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                    "&text=" + URLEncoder.encode(message, "UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val ok = responseCode in 200..299 && JSONObject(response).optBoolean("ok", false)
                if (!ok) android.util.Log.w("SessionScheduler", "Telegram alert failed: HTTP " + responseCode + " " + response)
                onComplete?.invoke(ok, if (ok) "sent" else "HTTP " + responseCode + ": " + response)
            } catch (error: Exception) {
                android.util.Log.e("SessionScheduler", "Telegram alert error", error)
                onComplete?.invoke(false, error.localizedMessage ?: "network error")
            } finally {
                connection?.disconnect()
            }
        }
    }
}

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val hour = intent.getIntExtra("hour", -1)
        val time = String.format("%02d:00", hour)
        val title: String
        val message: String
        if (kind == "READY") {
            title = "ANNU KUMAR TEAM"
            message = "🔥 ARE YOU READY? आज का सेशन " + time + " बजे शुरू होगा। सभी तैयार रहें!"
        } else {
            title = "SESSION UPDATE"
            message = "SESSION UPDATE\n\nसेशन शुरू होने में तकनीकी देरी हो रही है। कृपया 15 मिनट प्रतीक्षा करें। अपडेट जल्द साझा किया जाएगा।"
        }
        SessionScheduler.show(context, title, message)
        SessionScheduler.sendTelegram(context, message)
        if (hour >= 0) SessionScheduler.rescheduleOne(context, hour, if (kind == "READY") "READY" else "WAIT")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SessionScheduler.scheduleAll(context)
    }
}
