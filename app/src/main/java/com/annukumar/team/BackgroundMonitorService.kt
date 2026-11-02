package com.annukumar.team

import android.app.*
import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.os.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

/**
 * User-started, read-only foreground monitor. It never clicks wager controls.
 * WebView session/cookie sharing and background DOM execution must be verified on-device.
 */
class BackgroundMonitorService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("paper", MODE_PRIVATE) }
    private var cpuWakeLock: PowerManager.WakeLock? = null
    @Volatile private var predictionSendInFlight = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7101, notification("बैकग्राउंड मॉनिटरिंग चालू है"))
        // Keep the CPU awake while the user-enabled foreground monitor is active.
        // This reduces Doze-related gaps but cannot force WebView JavaScript to run.
        try {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            cpuWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AnnuKumarTeam:HistoryMonitor").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            android.util.Log.e("BackgroundMonitor", "Could not acquire partial wake lock", e)
            updateMonitorDiagnostic("Battery keep-awake unavailable: " + (e.localizedMessage ?: "permission/system error"))
        }
        // History is read only by MainActivity's selected, authenticated WebView.
        // A second service WebView starts on the login route and reports WAIT_BINGO;
        // it cannot know which game/tab the user selected. The service only processes
        // validated history snapshots forwarded by MainActivity.
        SessionScheduler.scheduleAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_HISTORY_UPDATE) {
            val snapshot = intent.getStringExtra(EXTRA_HISTORY_JSON)
            if (!snapshot.isNullOrBlank()) process(snapshot)
        }
        return START_STICKY
    }

    private fun process(raw: String) {
        try {
            val decoded = org.json.JSONTokener(raw).nextValue() as? String ?: return
            if (decoded.startsWith("WAIT_")) { updateMonitorDiagnostic("Service reader: " + decoded); return }
            val obj = JSONObject(decoded)
            val mode = obj.optString("mode")
            val rows = obj.optJSONArray("rows") ?: run { updateMonitorDiagnostic("Service reader: rows missing"); return }
            if (rows.length() == 0) { updateMonitorDiagnostic("Service reader: history rows empty"); return }
            val orderedRows = (0 until rows.length()).mapNotNull { i ->
                rows.optJSONObject(i)?.let { row ->
                    val p = row.optString("period")
                    val s = row.optString("size").uppercase(Locale.ROOT)
                    if (p.matches(Regex("[0-9]{8,24}")) && s in listOf("BIG","SMALL")) {
                        try { java.math.BigInteger(p) to row } catch (_: Exception) { null }
                    } else null
                }
            }.sortedByDescending { it.first }
            if (orderedRows.isEmpty()) { updateMonitorDiagnostic("Service reader: no valid BIG/SMALL periods"); return }

            val newestPeriod = orderedRows.first().first
            val newestLabel = newestPeriod.toString()
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val sessions = intArrayOf(10,15,18,21)
            val activeHour = sessions.lastOrNull { it <= hour } ?: run { updateMonitorDiagnostic("अगला सेशन 10:00 बजे शुरू होगा"); return }
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val sessionKey = "$date-$activeHour"
            if (prefs.getString("bg_session","") != sessionKey) {
                prefs.edit().putString("bg_session",sessionKey).putInt("bg_wins",0).putInt("bg_level",1)
                    .putBoolean("bg_stopped",false).putString("bg_predictions_json","[]")
                    .putString("bg_last_result_period","").putString("bg_last_outcome","—").commit()
            }

            val predictions = org.json.JSONArray(prefs.getString("bg_predictions_json","[]") ?: "[]")
            val actualByPeriod = orderedRows.associate { it.first.toString() to it.second.optString("size").uppercase(Locale.ROOT) }
            val resolved = mutableListOf<Triple<java.math.BigInteger, Int, String>>()
            for (i in 0 until predictions.length()) {
                val item = predictions.optJSONObject(i) ?: continue
                val target = item.optString("period")
                val pick = item.optString("pick")
                if (item.optString("status") != "RESOLVED" && pick in listOf("BIG","SMALL")) {
                    val actual = actualByPeriod[target]
                    if (actual in listOf("BIG","SMALL")) {
                        val win = pick == actual
                        item.put("status","RESOLVED").put("actual",actual).put("outcome",if (win) "WIN" else "LOSS")
                        try { resolved.add(Triple(java.math.BigInteger(target), i, if (win) "WIN" else "LOSS")) } catch (_: Exception) {}
                    } else if (item.optString("status") == "SENDING" &&
                        System.currentTimeMillis() - item.optLong("createdAt") > 60000L) {
                        item.put("status","FAILED")
                    }
                }
            }
            resolved.sortBy { it.first }
            var wins = prefs.getInt("bg_wins",0)
            var level = prefs.getInt("bg_level",1).coerceIn(1,8)
            for ((_, index, outcome) in resolved) {
                val item = predictions.optJSONObject(index) ?: continue
                val target = item.optString("period")
                val pick = item.optString("pick")
                val actual = item.optString("actual")
                val resolvedLevel = item.optInt("level",level).coerceIn(1,8)
                if (outcome == "WIN") { wins++; level = 1 } else { level = (level + 1).coerceAtMost(8) }
                prefs.edit().putString("bg_last_result_period",target).putString("bg_last_outcome",outcome).commit()
                send("RESULT", "🎯 $target\n\n$pick — $outcome " + if (outcome == "WIN") "✅" else "❌")
            }
            prefs.edit().putString("bg_predictions_json",predictions.toString())
                .putInt("bg_wins",wins).putInt("bg_level",level).commit()

            if (wins >= 5) {
                val promoKey = "bg_promo_sent_" + sessionKey
                if (!prefs.getBoolean(promoKey, false)) {
                    val link = "https://bdgwin98.com/#/register?invitationCode=5152785394"
                    val promo = "🎉 5 WINS COMPLETED! 🎉\n\nANNU KUMAR TEAM\n\n• Regular session updates\n• BIG / SMALL prediction updates\n• WIN / LOSS status updates\n• Structured Level 1–8 information\n• Active community updates\n\nJoin the community:\n" + link + "\n" + link + "\n" + link
                    SessionScheduler.sendTelegram(this, "ANNU KUMAR TEAM\n" + promo) { ok, detail ->
                        if (ok) prefs.edit().putBoolean(promoKey, true).commit()
                        else android.util.Log.e("BackgroundMonitor", "Promo Telegram failed: " + detail)
                    }
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(7102, notification(promo))
                }
                updateMonitorDiagnostic("आज के सेशन में 5 WIN पूरे • अगले सेशन का इंतज़ार")
                return
            }
            if (prefs.getBoolean("bg_stopped",false)) { updateMonitorDiagnostic("सेशन Level 8 LOSS के बाद रोका गया"); return }
            if (level >= 8 && resolved.any { it.third == "LOSS" }) {
                val lastLoss = resolved.lastOrNull { it.third == "LOSS" }
                if (lastLoss != null) {
                    val lossItem = predictions.optJSONObject(lastLoss.second)
                    if (lossItem?.optInt("level",1) == 8) {
                        prefs.edit().putBoolean("bg_stopped",true).commit()
                        send("सेशन समाप्त","Level 8 पर LOSS आया; सुरक्षा के लिए सेशन रोक दिया गया।")
                        return
                    }
                }
            }

            val targetPeriod = newestPeriod.add(java.math.BigInteger.ONE).toString()
            val alreadyRecorded = (0 until predictions.length()).any { i ->
                val item = predictions.optJSONObject(i)
                item?.optString("period") == targetPeriod && item.optString("status") != "FAILED"
            }
            if (alreadyRecorded) {
                updateMonitorDiagnostic("Period $targetPeriod की prediction पहले से सेव है • अगली history का इंतज़ार")
                return
            }
            val history = orderedRows.take(10).map { it.second.optString("size").uppercase(Locale.ROOT) }
            if (history.size < 3) { updateMonitorDiagnostic("Service reader: only " + history.size + " valid results"); return }
            val tail = history.take(3)
            val bigCount = history.count { it == "BIG" }
            val smallCount = history.count { it == "SMALL" }
            val pick = when {
                tail.all { it == "BIG" } -> "BIG"
                tail.all { it == "SMALL" } -> "SMALL"
                tail[0] == "BIG" && tail[1] == "SMALL" && tail[2] == "BIG" -> "SMALL"
                tail[0] == "SMALL" && tail[1] == "BIG" && tail[2] == "SMALL" -> "BIG"
                bigCount > smallCount -> "BIG"
                smallCount > bigCount -> "SMALL"
                else -> tail.first()
            }
            val currentLevel = prefs.getInt("bg_level",1).coerceIn(1,8)
            if (predictionSendInFlight) { updateMonitorDiagnostic("Telegram भेजने की प्रक्रिया चल रही है…"); return }
            predictionSendInFlight = true
            val record = JSONObject().put("period",targetPeriod).put("pick",pick)
                .put("level",currentLevel).put("status","SENDING").put("createdAt",System.currentTimeMillis())
            predictions.put(record)
            // Commit the target reservation before the network call: duplicate snapshots cannot send it twice.
            if (!prefs.edit().putString("bg_predictions_json",predictions.toString()).commit()) {
                predictionSendInFlight = false
                updateMonitorDiagnostic("Prediction save failed • Telegram नहीं भेजा गया")
                return
            }
            val sessionLabel = when (activeHour) { 10 -> "10:00 AM"; 15 -> "3:00 PM"; 18 -> "6:00 PM"; else -> "9:00 PM" }
            val message = "🔥 ANNU KUMAR TEAM – LIVE SIGNAL 🔥\n\n🎯 NEW PREDICTION\n\n📅 Period: $targetPeriod\n📊 Prediction: $pick\n\n⚡ Level: $currentLevel\n🎮 Session: $sessionLabel\n\n━━━━━━━━━━━━━━\n💎 Stay Connected | Stay Updated\n━━━━━━━━━━━━━━"
            updateMonitorDiagnostic("Prediction ready: $targetPeriod • Telegram sending…")
            SessionScheduler.sendTelegram(this, message) { ok, detail ->
                predictionSendInFlight = false
                val latestJson = org.json.JSONArray(prefs.getString("bg_predictions_json","[]") ?: "[]")
                for (i in 0 until latestJson.length()) {
                    val item = latestJson.optJSONObject(i) ?: continue
                    if (item.optString("period") == targetPeriod && item.optString("status") == "SENDING") {
                        item.put("status",if (ok) "SENT" else "FAILED")
                    }
                }
                prefs.edit().putString("bg_predictions_json",latestJson.toString()).commit()
                if (ok) { SessionScheduler.markFirstSignalSent(this, activeHour); updateMonitorDiagnostic("Telegram sent • Prediction $pick • Period $targetPeriod") }
                else {
                    android.util.Log.e("BackgroundMonitor", "Prediction Telegram failed: $detail")
                    updateMonitorDiagnostic("Telegram send failed • " + detail)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BackgroundMonitor", "Prediction pipeline failed", e)
            val reason = e.localizedMessage ?: e.javaClass.simpleName
            updateMonitorDiagnostic("Prediction pipeline error: " + reason.take(180))
        }
    }

    private fun updateMonitorDiagnostic(message: String) {
        prefs.edit().putString("telegram_last_diagnostic", message).putLong("telegram_last_diagnostic_at", System.currentTimeMillis()).apply()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(7102, notification(message))
    }

    private fun isOnline(): Boolean {
        val cm=getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if(Build.VERSION.SDK_INT>=23) cm.activeNetwork?.let { cm.getNetworkCapabilities(it) != null } == true else true
    }
    private fun send(title:String, body:String) {
        SessionScheduler.sendTelegram(this, "$title\n$body")
        val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(7102, notification("$title • $body"))
    }
    private fun notification(msg:String)=NotificationCompat.Builder(this,CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Annu Kumar Team")
        .setContentText(msg.take(120)).setStyle(NotificationCompat.BigTextStyle().bigText(msg))
        .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(CHANNEL,"Background monitor",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        try {
            cpuWakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            android.util.Log.w("BackgroundMonitor", "Wake lock release failed", e)
        }
        cpuWakeLock = null
        super.onDestroy()
    }
    override fun onBind(intent:Intent?)=null
    companion object {
        const val ACTION_STOP="com.annukumar.team.STOP_MONITOR"
        const val ACTION_HISTORY_UPDATE="com.annukumar.team.HISTORY_UPDATE"
        const val EXTRA_HISTORY_JSON="history_json"
        private const val CHANNEL="annu_background_monitor"
        fun start(context:Context) {
            val i=Intent(context,BackgroundMonitorService::class.java)
            if(Build.VERSION.SDK_INT>=26) context.startForegroundService(i) else context.startService(i)
        }
        fun stop(context:Context) { context.startService(Intent(context,BackgroundMonitorService::class.java).setAction(ACTION_STOP)) }
    }
}
