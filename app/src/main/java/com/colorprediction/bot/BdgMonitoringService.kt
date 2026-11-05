package com.colorprediction.bot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigInteger

/**
 * Foreground, read-only BDG history monitor. This ports the previous Annu Kumar
 * Team reader: selected-tab/panel detection, fetch/XHR history capture, and a
 * service-owned WebView. Android may still defer work or stop it after force-stop.
 */
class BdgMonitoringService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var monitor: WebView? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var readerReady = false
    private var readInFlight = false
    private val channelId = "bdg_monitoring"

    private val poll = object : Runnable {
        override fun run() {
            val page = monitor
            if (page != null && readerReady && !readInFlight) {
                readInFlight = true
                page.evaluateJavascript(BdgHistoryReader.HISTORY_READER_JS) { raw ->
                    readInFlight = false
                    handleReaderResult(raw)
                }
            }
            main.postDelayed(this, 2000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "BDG Monitoring", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_color_bot_logo)
            .setContentTitle("Annu Kumar Team")
            .setContentText("BDG background history monitoring is active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(4107, notification)

        // Match the earlier Annu Team foreground monitor's CPU keep-awake behavior.
        try {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AnnuKumarTeam:HistoryMonitor")
                .apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) {
            android.util.Log.e("BdgMonitoringService", "Wake lock unavailable", e)
        }
        startMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitor == null) startMonitor()
        return START_NOT_STICKY
    }

    private fun startMonitor() {
        if (monitor != null) return
        val prefs = getSharedPreferences("bot_settings", MODE_PRIVATE)
        val url = prefs.getString("bdg_url", "https://bdgwin98.com") ?: "https://bdgwin98.com"
        monitor = WebView(applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            CookieManager.getInstance().flush()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                    readerReady = false
                    updateStatus("BACKGROUND READER • BDG पेज लोड हो रहा है")
                }

                override fun onPageFinished(view: WebView, pageUrl: String) {
                    super.onPageFinished(view, pageUrl)
                    readerReady = true
                    view.evaluateJavascript(BdgHistoryReader.NETWORK_HISTORY_HOOK_JS, null)
                    updateStatus("BACKGROUND READER • पेज तैयार • selected game history पढ़ रहे हैं")
                    main.removeCallbacks(poll)
                    main.post(poll)
                }

                override fun onReceivedError(
                    view: WebView?, request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        readerReady = false
                        updateStatus("BACKGROUND READER ERROR • ${error?.description ?: "page load failed"}")
                    }
                }
            }
            loadUrl(url)
        }
    }

    private fun handleReaderResult(raw: String?) {
        if (raw.isNullOrBlank() || raw == "null") return
        try {
            val decoded = JSONTokener(raw).nextValue() as? String ?: return
            if (decoded.startsWith("WAIT_")) {
                updateStatus(when (decoded) {
                    "WAIT_BINGO" -> "BDG GAME PAGE NOT DETECTED"
                    "WAIT_MODE" -> "GAME MODE NOT CONFIRMED • active tab नहीं मिला"
                    "WAIT_PANEL" -> "HISTORY PANEL NOT FOUND • selected tab का data नहीं मिला"
                    else -> "WAITING FOR BDG HISTORY"
                })
                return
            }
            val data = JSONObject(decoded)
            val mode = data.optString("mode")
            val rows = data.optJSONArray("rows") ?: return
            if (rows.length() < 3) {
                updateStatus("HISTORY INCOMPLETE • ${rows.length()}/10 results")
                return
            }
            val validRows = (0 until rows.length()).mapNotNull { i ->
                rows.optJSONObject(i)?.let { row ->
                    val period = row.optString("period")
                    val size = row.optString("size").uppercase()
                    if (period.matches(Regex("[0-9]{8,24}")) && size in listOf("BIG", "SMALL")) {
                        runCatching { BigInteger(period) to (period to size) }.getOrNull()
                    } else null
                }
            }.distinctBy { it.second.first }.sortedByDescending { it.first }.take(10)
            if (validRows.size < 3) {
                updateStatus("HISTORY INCOMPLETE • valid results ${validRows.size}/10")
                return
            }

            val duration = when {
                Regex("30\\s*(seconds?|secs?|sec|s)", RegexOption.IGNORE_CASE).containsMatchIn(mode) -> "30 Seconds"
                Regex("1\\s*(minutes?|mins?|min|m)", RegexOption.IGNORE_CASE).containsMatchIn(mode) -> "1 Minute"
                Regex("3\\s*(minutes?|mins?|min|m)", RegexOption.IGNORE_CASE).containsMatchIn(mode) -> "3 Minutes"
                Regex("5\\s*(minutes?|mins?|min|m)", RegexOption.IGNORE_CASE).containsMatchIn(mode) -> "5 Minutes"
                else -> null
            } ?: run { updateStatus("GAME MODE NOT CONFIRMED • $mode"); return }

            val nextPeriod = validRows.first().first.add(BigInteger.ONE).toString()
            val pageText = buildString {
                append("ACTIVE_GAME_MODE: Win Go $duration\n")
                append("Current Period: $nextPeriod\n")
                validRows.forEach { (_, row) -> append(row.first).append(' ').append(row.second).append('\n') }
            }
            val snapshot = BdgPageParser.parse(pageText)
            if (snapshot == null || snapshot.gameDuration == null) {
                updateStatus("BDG HISTORY PARSE FAILED • $duration • ${validRows.size} rows")
                return
            }
            val status = BdgAutomationController.process(this, snapshot)
            updateStatus("BACKGROUND READ OK • $duration • ${validRows.size}/10 results • $status")
        } catch (e: Exception) {
            android.util.Log.e("BdgMonitoringService", "History reader failed", e)
            updateStatus("BACKGROUND READER ERROR • ${e.localizedMessage ?: "parse error"}")
        }
    }

    private fun updateStatus(message: String) {
        getSharedPreferences("bot_settings", MODE_PRIVATE).edit()
            .putString("bdg_read_status", message)
            .putLong("bdg_read_status_at", System.currentTimeMillis())
            .apply()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        main.removeCallbacks(poll)
        monitor?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        monitor = null
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
