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
    private var readerWebView: WebView? = null
    private var readerPageUrl: String = ""
    private var readerMode: String = ""
    private var readerReady = false
    private var readerInFlight = false
    private val readerPoll = object : Runnable {
        override fun run() {
            checkReadyTelegramReminder()
            val reader = readerWebView
            if (reader != null && readerReady && !readerInFlight) {
                readerInFlight = true
                val selectMode = readerMode
                val selectScript = """(function(){
                  var wanted = ${org.json.JSONObject.quote(selectMode)};
                  var norm = function(s){return String(s||'').replace(/\\s+/g,' ').trim().toLowerCase();};
                  var nodes = Array.from(document.querySelectorAll('[role=tab],button,a,div,span'));
                  var target = nodes.find(function(el){
                    var t=norm(el.innerText||el.textContent);
                    return t && (t===norm(wanted) || (wanted.indexOf('30 Seconds')>=0 && /^(30\\s*(seconds?|secs?|sec|s))$/i.test(t)) || (wanted.indexOf('1 Minute')>=0 && /^(1\\s*(minutes?|mins?|min|m))$/i.test(t)) || (wanted.indexOf('3 Minutes')>=0 && /^(3\\s*(minutes?|mins?|min|m))$/i.test(t)) || (wanted.indexOf('5 Minutes')>=0 && /^(5\\s*(minutes?|mins?|min|m))$/i.test(t)));
                  });
                  if(target){var cls=String(target.className||''); if(target.getAttribute('aria-selected')!=='true' && !/(active|selected|current)/i.test(cls)) target.click();}
                  return true;
                })()""";
                reader.evaluateJavascript(selectScript, null)
                reader.evaluateJavascript(BdgHistoryReader.HISTORY_READER_JS) { raw ->
                    readerInFlight = false
                    if (!raw.isNullOrBlank() && raw != "null") process(raw)
                }
            }
            main.postDelayed(this, 3000L)
        }
    }
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
        // Create a service-owned read-only WebView. It shares this app's WebView cookie
        // and DOM-storage profile, then restores the last selected game tab reported by
        // the visible Activity. This lets polling continue when the Activity is hidden.
        createHistoryReader()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_HISTORY_UPDATE) {
            val snapshot = intent.getStringExtra(EXTRA_HISTORY_JSON)
            val url = intent.getStringExtra(EXTRA_PAGE_URL).orEmpty()
            val mode = intent.getStringExtra(EXTRA_SELECTED_MODE).orEmpty()
            if (url.startsWith("https://bdgwin", ignoreCase = true)) configureHistoryReader(url, mode)
            if (!snapshot.isNullOrBlank()) process(snapshot)
        }
        return START_STICKY
    }


    private fun createHistoryReader() {
        main.post {
            if (readerWebView != null) return@post
            readerWebView = WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        readerReady = false
                        updateMonitorDiagnostic("Background reader: BDG पेज लोड हो रहा है")
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        readerReady = true
                        view?.evaluateJavascript(BdgHistoryReader.NETWORK_HISTORY_HOOK_JS, null)
                        updateMonitorDiagnostic("Background reader: पेज खुला • History पढ़ना शुरू")
                    }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) {
                            readerReady = false
                            updateMonitorDiagnostic("Background reader page error: " + (error?.description ?: "load failed"))
                        }
                    }
                }
            }
            main.removeCallbacks(readerPoll)
            main.post(readerPoll)
            if (readerPageUrl.isNotBlank()) readerWebView?.loadUrl(readerPageUrl)
        }
    }

    /**
     * Sends the four session reminders from the already-running foreground monitor.
     * No AlarmManager, PendingIntent, phone alarm, or local reminder notification is used.
     * The monitor polls every 3 seconds and persists each date/session key to avoid duplicates.
     */
    private fun checkReadyTelegramReminder() {
        val now = Calendar.getInstance()
        if (now.get(Calendar.MINUTE) != 55) return
        val sessionHour = when (now.get(Calendar.HOUR_OF_DAY)) {
            9 -> 10
            14 -> 15
            17 -> 18
            20 -> 21
            else -> return
        }
        val dateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(now.time)
        val sentKey = "ready_telegram_sent_${dateKey}_${sessionHour}"
        if (prefs.getBoolean(sentKey, false)) return

        // Save before sending so repeated 3-second polls cannot post duplicate messages.
        if (!prefs.edit().putBoolean(sentKey, true).commit()) {
            updateMonitorDiagnostic("ARE YOU READY reminder save failed • Telegram नहीं भेजा गया")
            return
        }
        val sessionTime = String.format(Locale.US, "%02d:00", sessionHour)
        val message = "🔥 ARE YOU READY? आज का सेशन ${sessionTime} बजे शुरू होगा। सभी तैयार रहें!"
        updateMonitorDiagnostic("ARE YOU READY • Telegram भेजा जा रहा है • ${sessionTime} session")
        SessionScheduler.sendTelegram(this, message) { ok, detail ->
            if (ok) {
                updateMonitorDiagnostic("ARE YOU READY • Telegram पर भेज दिया गया • ${sessionTime} session")
            } else {
                android.util.Log.e("BackgroundMonitor", "ARE YOU READY Telegram failed: " + detail)
                updateMonitorDiagnostic("ARE YOU READY Telegram failed • " + detail)
            }
        }
    }

    private fun configureHistoryReader(url: String, mode: String) {
        main.post {
            readerMode = mode
            if (readerWebView == null) createHistoryReader()
            if (readerPageUrl != url) {
                readerPageUrl = url
                readerReady = false
                readerWebView?.loadUrl(url)
            }
        }
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
                prefs.edit().putString("bg_session",sessionKey).putLong("bg_session_started_at",System.currentTimeMillis())
                    .putInt("bg_wins",0).putInt("bg_level",1).putInt("bg_consecutive_losses",0)
                    .putBoolean("bg_stopped",false).putString("bg_predictions_json","[]")
                    .putInt("bg_ledger_schema",2)
                    .putString("bg_last_result_period","").putString("bg_last_outcome","—").commit()
            }
            // Retire unresolved legacy entries once: old records were not tied to a
            // session and did not require confirmed Telegram delivery.
            if (prefs.getInt("bg_ledger_schema",0) < 2) {
                val oldLedger = org.json.JSONArray(prefs.getString("bg_predictions_json","[]") ?: "[]")
                for (i in 0 until oldLedger.length()) {
                    val item = oldLedger.optJSONObject(i) ?: continue
                    if (item.optString("status") != "RESOLVED") item.put("status","IGNORED")
                }
                prefs.edit().putString("bg_predictions_json",oldLedger.toString())
                    .putInt("bg_level",1).putInt("bg_consecutive_losses",0).putInt("bg_ledger_schema",2).commit()
            }

            val predictions = org.json.JSONArray(prefs.getString("bg_predictions_json","[]") ?: "[]")
            val actualByPeriod = orderedRows.associate { it.first.toString() to it.second.optString("size").uppercase(Locale.ROOT) }
            val resolved = mutableListOf<Triple<java.math.BigInteger, Int, String>>()
            for (i in 0 until predictions.length()) {
                val item = predictions.optJSONObject(i) ?: continue
                val target = item.optString("period")
                val pick = item.optString("pick")
                // Only resolve predictions confirmed sent by Telegram and belonging to this session.
                if (item.optString("status") == "SENT" &&
                    item.optString("sessionKey") == sessionKey &&
                    pick in listOf("BIG","SMALL")) {
                    val actual = actualByPeriod[target]
                    if (actual in listOf("BIG","SMALL")) {
                        val win = pick == actual
                        item.put("status","RESOLVED").put("actual",actual).put("outcome",if (win) "WIN" else "LOSS")
                        try { resolved.add(Triple(java.math.BigInteger(target), i, if (win) "WIN" else "LOSS")) } catch (_: Exception) {}
                    }
                }
            }
            resolved.sortBy { it.first }
            var wins = prefs.getInt("bg_wins",0)
            var level = prefs.getInt("bg_level",1).coerceIn(1,8)
            var consecutiveLosses = prefs.getInt("bg_consecutive_losses",0).coerceIn(0,8)
            var stopAtLevelEight = false
            for ((_, index, outcome) in resolved) {
                val item = predictions.optJSONObject(index) ?: continue
                val target = item.optString("period")
                val pick = item.optString("pick")
                val assignedLevel = item.optInt("level",0)
                if (assignedLevel != level) {
                    item.put("status","IGNORED").put("ignoreReason","level_sequence_mismatch")
                    continue
                }
                if (outcome == "WIN") {
                    wins++
                    consecutiveLosses = 0
                    level = 1
                } else {
                    consecutiveLosses++
                    if (consecutiveLosses >= 8) stopAtLevelEight = true
                    level = (assignedLevel + 1).coerceAtMost(8)
                }
                prefs.edit().putString("bg_last_result_period",target).putString("bg_last_outcome",outcome).commit()
                val gameMode = item.optString("mode", mode)
                send("RESULT", "🎯 Period: $target\n🎮 Game: $gameMode\n⚡ Level: $assignedLevel\n\n$pick — $outcome " + if (outcome == "WIN") "✅" else "❌")
                if (stopAtLevelEight) break
            }
            prefs.edit().putString("bg_predictions_json",predictions.toString())
                .putInt("bg_wins",wins).putInt("bg_level",level)
                .putInt("bg_consecutive_losses",consecutiveLosses)
                .putBoolean("bg_stopped",prefs.getBoolean("bg_stopped",false) || stopAtLevelEight).commit()
            if (stopAtLevelEight) {
                send("सेशन समाप्त","लगातार 8 LOSS होने के कारण सेशन रोक दिया गया। हमें खेद है।")
                updateMonitorDiagnostic("लगातार 8 LOSS के बाद सेशन रोका गया")
                return
            }

            if (wins >= 5) {
                val promoKey = "bg_promo_sent_" + sessionKey
                if (!prefs.getBoolean(promoKey, false)) {
                    val link = "https://bdgwin98.com/#/register?invitationCode=5152785394"
                    val promo = "🎉 5 WINS COMPLETED! 🎉\n\nANNU KUMAR TEAM\n\n• Regular session updates\n• BIG / SMALL prediction updates\n• WIN / LOSS status updates\n• Structured Level 1–8 information\n• Active community updates\n\nJoin the community:\n" + link
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
            if (prefs.getBoolean("bg_stopped",false)) { updateMonitorDiagnostic("सेशन लगातार 8 LOSS के बाद रोका गया"); return }
            

            val targetPeriod = newestPeriod.add(java.math.BigInteger.ONE).toString()
            val alreadyRecorded = (0 until predictions.length()).any { i ->
                val item = predictions.optJSONObject(i)
                item?.optString("period") == targetPeriod && item.optString("status") != "FAILED"
            }
            if (alreadyRecorded) {
                updateMonitorDiagnostic("Period $targetPeriod की prediction पहले से सेव है • अगली history का इंतज़ार")
                return
            }
            val history = orderedRows.take(6).map { it.second.optString("size").uppercase(Locale.ROOT) }
            if (history.size < 3) {
                val reason = "केवल ${history.size} सत्यापित परिणाम मिले"
                sendSkipOnce(targetPeriod, mode, sessionKey, reason)
                return
            }
            val analysis = PatternAnalyzer.analyzeNewestFirst(history)
            val pick = analysis.signal
            if (pick == null) {
                sendSkipOnce(targetPeriod, mode, sessionKey, analysis.reason)
                return
            }
            val currentLevel = prefs.getInt("bg_level",1).coerceIn(1,8)
            if (predictionSendInFlight) { updateMonitorDiagnostic("Telegram भेजने की प्रक्रिया चल रही है…"); return }
            predictionSendInFlight = true
            val record = JSONObject().put("period",targetPeriod).put("pick",pick)
                .put("level",currentLevel).put("mode",mode).put("sessionKey",sessionKey).put("status","SENDING").put("createdAt",System.currentTimeMillis())
            predictions.put(record)
            // Commit the target reservation before the network call: duplicate snapshots cannot send it twice.
            if (!prefs.edit().putString("bg_predictions_json",predictions.toString()).commit()) {
                predictionSendInFlight = false
                updateMonitorDiagnostic("Prediction save failed • Telegram नहीं भेजा गया")
                return
            }
            val sessionLabel = when (activeHour) { 10 -> "10:00 AM"; 15 -> "3:00 PM"; 18 -> "6:00 PM"; else -> "9:00 PM" }
            val message = "🔥 ANNU KUMAR TEAM – LIVE SIGNAL 🔥\n\n🎯 NEW PREDICTION\n\n📅 Period: $targetPeriod\n📊 Prediction: $pick\n\n⚡ Level: $currentLevel\n🎮 Game: $mode\n🕒 Session: $sessionLabel\n\n━━━━━━━━━━━━━━\n💎 Stay Connected | Stay Updated\n━━━━━━━━━━━━━━"
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
                if (ok) { updateMonitorDiagnostic("Telegram sent • Prediction $pick • Period $targetPeriod") }
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

    /**
     * A SKIP is final for a target period. History polls repeatedly,
     * so persist its key before sending to prevent duplicate Telegram posts.
     */
    private fun sendSkipOnce(targetPeriod: String, mode: String, sessionKey: String, reason: String) {
        val skipKey = "bg_skip_sent_" + sessionKey + "_" + mode + "_" + targetPeriod
        if (prefs.getBoolean(skipKey, false)) {
            updateMonitorDiagnostic("SKIP • Period $targetPeriod पहले ही Telegram पर भेजा जा चुका है")
            return
        }
        // Commit before network call so duplicate snapshots cannot send twice.
        if (!prefs.edit().putBoolean(skipKey, true).commit()) {
            updateMonitorDiagnostic("SKIP रिकॉर्ड सेव नहीं हुआ • Telegram संदेश नहीं भेजा")
            return
        }
        updateMonitorDiagnostic("SKIP • Period $targetPeriod • $reason")
        send("SKIP", "⏭️ SKIP\nPeriod: $targetPeriod\nGame: $mode\nकारण: $reason")
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
            readerWebView?.stopLoading()
            readerWebView?.loadUrl("about:blank")
            readerWebView?.removeAllViews()
            readerWebView?.destroy()
            readerWebView = null
        } catch (e: Exception) {
            android.util.Log.w("BackgroundMonitor", "Reader WebView cleanup failed", e)
        }
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
        const val EXTRA_PAGE_URL="page_url"
        const val EXTRA_SELECTED_MODE="selected_mode"
        private const val CHANNEL="annu_background_monitor"
        fun start(context:Context) {
            val i=Intent(context,BackgroundMonitorService::class.java)
            if(Build.VERSION.SDK_INT>=26) context.startForegroundService(i) else context.startService(i)
        }
        fun stop(context:Context) { context.startService(Intent(context,BackgroundMonitorService::class.java).setAction(ACTION_STOP)) }
    }
}
