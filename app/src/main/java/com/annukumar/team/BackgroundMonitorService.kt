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
    private val poller = object : Runnable {
        override fun run() {
            if (ready && isOnline()) web.evaluateJavascript(READER) { raw ->
                if (raw != null && raw != "null") process(raw)
            }
            main.postDelayed(this, 8000)
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
        return START_STICKY
    }

    private fun process(raw: String) {
        try {
            val decoded = org.json.JSONTokener(raw).nextValue() as? String ?: return
            if (decoded.startsWith("WAIT_")) return
            val obj = JSONObject(decoded)
            val mode = obj.optString("mode")
            val rows = obj.optJSONArray("rows") ?: return
            if (rows.length() == 0) return
            val latest = rows.getJSONObject(0)
            val period = latest.optString("period")
            val result = latest.optString("size").uppercase(Locale.ROOT)
            if (!period.matches(Regex("[0-9]{8,24}")) || result !in listOf("BIG","SMALL")) return
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val sessions = intArrayOf(10,15,18,21)
            val activeHour = sessions.lastOrNull { it <= hour } ?: return
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val sessionKey = "$date-$activeHour"
            if (prefs.getString("bg_session","") != sessionKey) {
                prefs.edit().putString("bg_session",sessionKey).putInt("bg_wins",0).putInt("bg_level",1)
                    .putBoolean("bg_stopped",false)
                    .putString("bg_pending_period","").putString("bg_pending_pick","")
                    .putString("bg_last_result_period","").apply()
            }
            if (prefs.getInt("bg_wins",0) >= 5 || prefs.getBoolean("bg_stopped",false)) return
            val ed = prefs.edit()
            val oldPeriod = prefs.getString("bg_pending_period","").orEmpty()
            if (oldPeriod.isNotBlank() && oldPeriod != period && prefs.getString("bg_last_result_period","") != period) {
                val pick = prefs.getString("bg_pending_pick","").orEmpty()
                if (pick.isNotBlank()) {
                    val win = pick == result
                    val currentLevel = prefs.getInt("bg_level",1).coerceIn(1,8)
                    val wins = prefs.getInt("bg_wins",0) + if (win) 1 else 0
                    val nextLevel = if (win) 1 else currentLevel + 1
                    ed.putInt("bg_wins",wins).putInt("bg_level",nextLevel.coerceAtMost(8))
                        .putString("bg_last_result_period",period)
                        .putString("bg_last_outcome", if (win) "WIN" else "LOSS").apply()
                    send(if(win) "✅ RESULT: WIN" else "❌ RESULT: LOSS",
                        "Prediction: $pick\nActual Result: $result\nPeriod: $period\nLevel: $currentLevel\n" +
                        (if (win) "Next Level: 1" else if (currentLevel < 8) "Next Level: $nextLevel" else "Level 8 limit reached") +
                        "\nSession Wins: $wins/5")
                    if (wins >= 5 || (!win && currentLevel >= 8)) {
                        prefs.edit().putBoolean("bg_stopped",true).apply()
                        send("सेशन समाप्त", if(wins >= 5) "5 WIN पूरे हुए। अगला निर्धारित सेशन आने तक मॉनिटर रुकेगा।" else "Level 8 पर LOSS आया; सुरक्षा के लिए सेशन रोक दिया गया।")
                        return
                    }
                }
            }
            if (prefs.getString("bg_pending_period","") == period) return
            val history = (0 until minOf(rows.length(),10)).map {
                rows.getJSONObject(it).optString("size").uppercase(Locale.ROOT)
            }.filter { it == "BIG" || it == "SMALL" }
            if (history.size < 3) return
            val tail = history.take(3)
            val pick = when {
                tail[0] == tail[1] && tail[1] == tail[2] -> if (tail[2] == "BIG") "SMALL" else "BIG"
                tail[0] == tail[2] && tail[0] != tail[1] -> if (tail[2] == "BIG") "SMALL" else "BIG"
                else -> if (tail.last() == "BIG") "SMALL" else "BIG"
            }
            val level=prefs.getInt("bg_level",1).coerceIn(1,8)
            prefs.edit().putString("bg_pending_period",period).putString("bg_pending_pick",pick).apply()
            send("🎯 PREDICTION: $pick", "गेम: $mode\nPrediction: $pick\nResult आने वाला Period: अगला\nपिछला Period: $period\nLevel: $level\nSession Wins: ${prefs.getInt("bg_wins",0)}/5\nयह केवल पैटर्न अनुमान है, गारंटी नहीं।")
            lastPoll=now
        } catch (e: Exception) { android.util.Log.e("BackgroundMonitor","parse",e) }
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
        private const val CHANNEL="annu_background_monitor"
        fun start(context:Context) {
            val i=Intent(context,BackgroundMonitorService::class.java)
            if(Build.VERSION.SDK_INT>=26) context.startForegroundService(i) else context.startService(i)
        }
        fun stop(context:Context) { context.startService(Intent(context,BackgroundMonitorService::class.java).setAction(ACTION_STOP)) }
        private const val READER = """(function(){var t=document.body?document.body.innerText:'';var modes=['30 Seconds','1 Minute','3 Minutes','5 Minutes'];var mode=modes.find(x=>new RegExp(x.replace(' ','\s*'),'i').test(t));if(!mode)return JSON.stringify('WAIT_MODE');var els=[...document.querySelectorAll('tr,[role=row],li,article,[class*=record],[class*=row]')];var rows=[];for(var e of els){var s=(e.innerText||'').replace(/\s+/g,' ').trim();var p=s.match(/\b[0-9]{8,24}\b/g),r=s.match(/\b(BIG|SMALL)\b/ig);if(p&&r&&p.length===1&&r.length===1)rows.push({period:p[0],size:r[0].toUpperCase()});}return JSON.stringify({mode:mode,rows:rows.slice(0,10)});})()"""
    }
}
