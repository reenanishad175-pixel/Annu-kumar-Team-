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
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("paper", MODE_PRIVATE) }
    private var ready = false
    private var lastPoll = 0L
    @Volatile private var predictionSendInFlight = false
    private val poller = object : Runnable {
        override fun run() {
            if (ready && isOnline()) web.evaluateJavascript(READER) { raw ->
                if (raw != null && raw != "null") process(raw)
            }
            main.postDelayed(this, 2500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7101, notification("बैकग्राउंड मॉनिटरिंग चालू है"))
        web = WebView(applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true }
                override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: WebResourceError?) {
                    if (r?.isForMainFrame == true) ready = false
                }
            }
            loadUrl("https://bdgwin68.com/#/login")
        }
        main.post(poller)
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
                send(if (outcome == "WIN") "✅ RESULT: WIN" else "❌ RESULT: LOSS",
                    "Prediction: $pick\nActual Result: $actual\nPeriod: $target\nLevel: $resolvedLevel\nSession Wins: $wins/5")
            }
            prefs.edit().putString("bg_predictions_json",predictions.toString())
                .putInt("bg_wins",wins).putInt("bg_level",level).commit()

            if (wins >= 5) { updateMonitorDiagnostic("आज के सेशन में 5 WIN पूरे • अगले सेशन का इंतज़ार"); return }
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
            val message = "🎮 $mode\n🆔 Period: $targetPeriod\n🎯 Prediction: $pick\n📶 Level: $currentLevel\n🏆 Session Wins: $wins/5"
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
                if (ok) updateMonitorDiagnostic("Telegram sent • Prediction $pick • Period $targetPeriod")
                else {
                    android.util.Log.e("BackgroundMonitor", "Prediction Telegram failed: $detail")
                    updateMonitorDiagnostic("Telegram send failed • " + detail)
                }
            }
            lastPoll = System.currentTimeMillis()
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
        if(::web.isInitialized) { web.stopLoading(); web.loadUrl("about:blank"); web.destroy() }
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
        private const val READER = """(function () {
          var found = [], seen = Object.create(null), roots = [];
          function visible(el) {
            if (!el || !el.getClientRects || !el.getClientRects().length) return false;
            var s = window.getComputedStyle(el);
            return s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0';
          }
          function textOf(el) {
            return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
          }
          var bodyText = (document.body && (document.body.innerText || document.body.textContent) || '').toLowerCase();
          var route = (location.href || '').toLowerCase();
          if (!/bingo|win[\s_-]*go/.test(route) && !/\bbingo\b|\bwin\s*go\b/.test(bodyText)) return JSON.stringify('WAIT_BINGO');

          var modes = [
            {name:'Win Go / Bingo 30 Seconds', re:/^(?:(?:win\s*go|bingo)\s*)?30\s*(?:seconds?|secs?|sec|s)$/i},
            {name:'Win Go / Bingo 1 Minute', re:/^(?:(?:win\s*go|bingo)\s*)?1\s*(?:minutes?|mins?|min|m)$/i},
            {name:'Win Go / Bingo 3 Minutes', re:/^(?:(?:win\s*go|bingo)\s*)?3\s*(?:minutes?|mins?|min|m)$/i},
            {name:'Win Go / Bingo 5 Minutes', re:/^(?:(?:win\s*go|bingo)\s*)?5\s*(?:minutes?|mins?|min|m)$/i}
          ];
          function modeFor(el) {
            var t = textOf(el).replace(/\s+/g, ' ').trim();
            if (t.length > 40) return null;
            return modes.find(function(m){ return m.re.test(t); }) || null;
          }
          function activeElement(el) {
            var cls = String(el.className && el.className.baseVal || el.className || '').toLowerCase();
            var parent = el.parentElement;
            var pcls = parent ? String(parent.className && parent.className.baseVal || parent.className || '').toLowerCase() : '';
            return el.getAttribute('aria-selected') === 'true' ||
              el.getAttribute('aria-pressed') === 'true' ||
              el.getAttribute('data-state') === 'active' ||
              /(^|[\s_-])(active|selected|current|is-active|tab-active)([\s_-]|$)/.test(cls) ||
              /(^|[\s_-])(active|selected|current|is-active|tab-active)([\s_-]|$)/.test(pcls);
          }
          var candidates = Array.from(document.querySelectorAll('[role="tab"],button,[aria-selected],[aria-pressed],a,div,span'));
          var selected = null, selectedEl = null;
          for (var i=0; i<candidates.length; i++) {
            var el = candidates[i];
            if (!visible(el)) continue;
            var mode = modeFor(el);
            if (mode && activeElement(el)) {
              selected = mode.name; selectedEl = el; break;
            }
          }
          if (!selected) return JSON.stringify('WAIT_MODE');

          function hasHistoryRows(root) {
            if (!root || !root.querySelectorAll) return false;
            var nodes = root.querySelectorAll('tr,[role="row"],article,li,[class*="record"],[class*="item"],[class*="row"]');
            for (var i=0; i<nodes.length; i++) {
              var el = nodes[i];
              if (!visible(el)) continue;
              var t = textOf(el);
              if (t.length <= 420 && /\b[0-9]{8,24}\b/.test(t) && /\b(BIG|SMALL)\b/i.test(t)) return true;
            }
            return false;
          }
          function findPanel(tab) {
            var controls = tab.getAttribute('aria-controls');
            if (controls) {
              var byId = document.getElementById(controls);
              if (byId && visible(byId)) return byId;
            }
            var rolePanel = tab.closest && tab.closest('[role="tabpanel"]');
            if (rolePanel && visible(rolePanel)) return rolePanel;

            var tabGroup = tab.closest && (tab.closest('[role="tablist"]') || tab.parentElement);
            for (var ancestor = tabGroup, depth = 0; ancestor && depth < 5; depth++, ancestor = ancestor.parentElement) {
              var children = Array.from(ancestor.children || []);
              for (var j=0; j<children.length; j++) {
                var child = children[j];
                if (child === tabGroup || child.contains(tabGroup) || !visible(child)) continue;
                if (hasHistoryRows(child)) return child;
              }
            }
            return null;
          }
          var scope = findPanel(selectedEl);
          if (!scope) return JSON.stringify('WAIT_PANEL');

          function addRow(el) {
            if (!visible(el)) return;
            var txt = textOf(el);
            if (!txt || txt.length > 420) return;
            var periods = txt.match(/\b[0-9]{8,24}\b/g) || [];
            var sizes = txt.match(/\b(BIG|SMALL)\b/ig) || [];
            if (periods.length !== 1 || sizes.length !== 1) return;
            var period = periods[0], size = sizes[0].toUpperCase();
            if (!seen[period]) {
              seen[period] = true;
              found.push({period:period, size:size});
            }
          }
          function scanRoot(root) {
            if (!root || roots.indexOf(root) >= 0 || !root.querySelectorAll) return;
            roots.push(root);
            root.querySelectorAll('tr,[role="row"],article,li,[class*="record"],[class*="item"],[class*="row"]').forEach(addRow);
            root.querySelectorAll('*').forEach(function(el) {
              if (el.shadowRoot) scanRoot(el.shadowRoot);
            });
            root.querySelectorAll('iframe').forEach(function(frame) {
              try { if (frame.contentDocument) scanRoot(frame.contentDocument); } catch (_) {}
            });
          }
          try { scanRoot(scope); } catch (_) {}
          return JSON.stringify({mode:selected, rows:found.slice(0,10)});
        })()"""
    }
}
