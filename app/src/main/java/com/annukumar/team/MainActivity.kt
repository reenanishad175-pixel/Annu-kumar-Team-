package com.annukumar.team

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import android.content.res.ColorStateList
import android.view.Gravity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Minimal BDG WIN web login shell.
 * Read-only: does not automate login, place wagers, or interact with betting controls.
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("paper", MODE_PRIVATE) }
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var dashboard: FrameLayout
    private lateinit var floatingPanel: LinearLayout
    private lateinit var panelBody: LinearLayout
    private lateinit var panelToggle: Button
    private lateinit var selectedModeView: TextView
    private lateinit var predictionView: TextView
    private lateinit var trendView: TextView
    private lateinit var historyView: TextView
    private lateinit var telegramDiagnosticView: TextView
    private var panelMinimized = false
    private var selectedModeName = "अभी कोई गेम चयनित नहीं"
    private lateinit var settingsScreen: ScrollView
    private lateinit var tokenField: EditText
    private lateinit var chatField: EditText
    private lateinit var loginTab: Button
    private lateinit var settingsTab: Button
    private var pageReady = false
    private var historyReadInFlight = false
    private val seenPeriods = LinkedHashSet<String>()
    private val observedRows = mutableListOf<Pair<String, String>>()

    private val navy = Color.rgb(9, 15, 29)
    private val panel = Color.rgb(20, 30, 49)
    private val accent = Color.rgb(39, 211, 175)
    private val muted = Color.rgb(174, 188, 207)
    private val white = Color.WHITE
    private val bdgUrl = "https://bdgwin68.com/#/login"

    private val pageMonitor = object : Runnable {
        override fun run() {
            if (::web.isInitialized && pageReady && web.visibility == View.VISIBLE && !isFinishing && !historyReadInFlight) {
                historyReadInFlight = true
                web.evaluateJavascript(BdgHistoryReader.HISTORY_READER_JS) { raw ->
                    historyReadInFlight = false
                    if (!isFinishing && raw != null && raw != "null") consumeHistory(raw)
                }
            }
            refreshTelegramDiagnostic()
            handler.postDelayed(this, 2500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.systemUiVisibility = 0
        setContentView(buildScreen())
        SessionScheduler.scheduleAll(this)
        showLogin()
        if (savedInstanceState == null) web.loadUrl(bdgUrl)
        handler.postDelayed(pageMonitor, 2500)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 14f): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(white)
    }

    private fun makeTab(value: String): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        setTextColor(white)
        backgroundTintList = ColorStateList.valueOf(panel)
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(5)
        }
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val brand = label("ANNU  /  TEAM", 21f).apply {
            setTextColor(accent)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(3), 0, dp(8))
        }
        root.addView(brand, LinearLayout.LayoutParams(-1, dp(38)))

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        loginTab = makeTab("BDG WIN LOGIN")
        settingsTab = makeTab("SETTINGS")
        loginTab.setOnClickListener { showLogin() }
        settingsTab.setOnClickListener { showSettings() }
        tabs.addView(loginTab)
        tabs.addView(settingsTab)
        root.addView(tabs)

        status = label("BDG वेबसाइट लोड हो रही है…", 12f).apply {
            setTextColor(muted)
            setPadding(dp(5), dp(7), dp(5), dp(7))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        dashboard = FrameLayout(this)
        root.addView(dashboard, LinearLayout.LayoutParams(-1, 0, 1f))

        web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isNestedScrollingEnabled = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Advisory renderer priority while user-enabled monitoring is active.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
            }
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    pageReady = false
                    status.text = "BDG वेबसाइट खुल रही है…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    status.text = "BDG पेज तैयार है • अब वेबसाइट पर अपनी ID और Password से लॉगिन करें"
                    view?.evaluateJavascript(
                        "(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';})()",
                        null
                    )
                    view?.evaluateJavascript(BdgHistoryReader.NETWORK_HISTORY_HOOK_JS, null)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        pageReady = false
                        status.text = "वेबसाइट लोड नहीं हुई • इंटरनेट कनेक्शन जाँचें और दोबारा खोलें"
                    }
                }
            }
        }
        dashboard.addView(web, FrameLayout.LayoutParams(-1, -1))
        buildFloatingDashboard()

        settingsScreen = buildSettings()
        dashboard.addView(settingsScreen, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun buildFloatingDashboard() {
        floatingPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(16, 27, 43))
            elevation = dp(12).toFloat()
        }
        panelToggle = Button(this).apply {
            text = "▼  LIVE DASHBOARD  •  छोटा करें"
            isAllCaps = false
            textSize = 12f
            setTextColor(accent)
            backgroundTintList = ColorStateList.valueOf(panel)
            setOnClickListener { toggleDashboard() }
        }
        floatingPanel.addView(panelToggle, LinearLayout.LayoutParams(-1, dp(38)))
        panelBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(2))
        }
        selectedModeView = label("चयन: अभी कोई गेम चयनित नहीं", 13f)
        predictionView = label("अनुमान: डेटा का इंतज़ार", 21f).apply {
            setTextColor(Color.rgb(24, 201, 151))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        trendView = label("ट्रेंड: —", 12f).apply { setTextColor(muted) }
        historyView = label("हाल के नतीजे: —", 12f).apply { setTextColor(muted) }
        telegramDiagnosticView = label("Telegram: मॉनिटर स्थिति जाँची जा रही है…", 12f).apply { setTextColor(Color.rgb(255, 193, 7)) }
        panelBody.addView(selectedModeView)
        panelBody.addView(predictionView)
        panelBody.addView(trendView)
        panelBody.addView(historyView)
        panelBody.addView(telegramDiagnosticView)
        floatingPanel.addView(panelBody)
        val lp = FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        // Keep the live dashboard at the top so BDG betting controls near the bottom stay visible.
        lp.setMargins(dp(8), dp(6), dp(8), 0)
        dashboard.addView(floatingPanel, lp)
        floatingPanel.bringToFront()
    }

    private fun refreshTelegramDiagnostic() {
        if (!::telegramDiagnosticView.isInitialized) return
        val message = prefs.getString("telegram_last_diagnostic", "मॉनिटर ने अभी स्थिति नहीं दी") ?: "मॉनिटर ने अभी स्थिति नहीं दी"
        val at = prefs.getLong("telegram_last_diagnostic_at", 0L)
        val age = if (at > 0L) ((System.currentTimeMillis() - at) / 1000L).coerceAtLeast(0L) else -1L
        telegramDiagnosticView.text = if (age >= 0L) "Telegram / Monitor: $message  •  ${age}s पहले" else "Telegram / Monitor: $message"
    }

    private fun toggleDashboard() {
        panelMinimized = !panelMinimized
        panelBody.visibility = if (panelMinimized) View.GONE else View.VISIBLE
        panelToggle.text = if (panelMinimized) "▲  LIVE DASHBOARD  •  खोलें" else "▼  LIVE DASHBOARD  •  छोटा करें"
    }

    private fun updateDashboard(mode: String, rows: List<Pair<String, String>>) {
        // Always order by numeric period, not DOM position: highest period is newest.
        val orderedRows = rows.sortedByDescending {
            try { java.math.BigInteger(it.first) } catch (_: Exception) { java.math.BigInteger.ZERO }
        }
        selectedModeName = mode
        selectedModeView.text = "चयन: $mode  •  पढ़े गए: ${orderedRows.size}/10"
        refreshTelegramDiagnostic()
        if (orderedRows.isEmpty()) {
            predictionView.text = "अनुमान: पर्याप्त डेटा नहीं"
            trendView.text = "ट्रेंड: —"
            historyView.text = "हाल के नतीजे: —"
            return
        }
        val latestPeriod = orderedRows.first().first
        val targetPeriod = try { java.math.BigInteger(latestPeriod).add(java.math.BigInteger.ONE).toString() } catch (_: Exception) { latestPeriod }
        val latest = orderedRows.first().second
        val streak = orderedRows.takeWhile { it.second == latest }.size
        val big = orderedRows.count { it.second == "BIG" }
        val small = orderedRows.size - big
        val trend = "BIG: $big / SMALL: $small"
        // Dashboard preview uses the same four approved rules as the background sender.
        // No last-10 frequency/majority fallback is permitted.
        val preview = PatternAnalyzer.analyzeNewestFirst(orderedRows.take(6).map { it.second })
        val pick = preview.signal
        predictionView.text = if (pick != null) {
            "अगला Period: $targetPeriod  •  अनुमान: $pick  •  ${preview.strategy}"
        } else {
            "अगला Period: $targetPeriod  •  SKIP: ${preview.reason}"
        }
        trendView.text = "ट्रेंड: $trend  •  आखिरी $latest की लगातार लड़ी: $streak"
        historyView.text = "हाल के ${orderedRows.size}: " + orderedRows.joinToString("  ›  ") { it.second }

        // Telegram predictions/results are owned only by BackgroundMonitorService.
        // Keep this screen display-only to prevent duplicate messages for one target period.
    }

    private fun buildSettings(): ScrollView {
        val scroll = ScrollView(this).apply { visibility = View.GONE }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(18))
            setBackgroundColor(panel)
        }
        content.addView(label("TELEGRAM SETTINGS", 19f).apply {
            setTextColor(accent)
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(label("Bot Token", 13f).apply { setTextColor(muted) })
        tokenField = EditText(this).apply {
            hint = "Telegram Bot Token"
            setText(prefs.getString("telegram_bot_token", ""))
            setTextColor(white)
            setHintTextColor(muted)
            inputType = 129
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(accent)
        }
        content.addView(tokenField, LinearLayout.LayoutParams(-1, dp(52)))
        content.addView(label("Chat ID", 13f).apply {
            setTextColor(muted)
            setPadding(0, dp(12), 0, 0)
        })
        chatField = EditText(this).apply {
            hint = "Telegram Chat ID"
            setText(prefs.getString("telegram_chat_id", ""))
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(accent)
        }
        content.addView(chatField, LinearLayout.LayoutParams(-1, dp(52)))

        val save = Button(this).apply {
            text = "SAVE SETTINGS"
            isAllCaps = false
            setTextColor(navy)
            backgroundTintList = ColorStateList.valueOf(accent)
            setOnClickListener { saveSettings() }
        }
        content.addView(save, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(18) })
        val testTelegram = Button(this).apply {
            text = "SEND TEST MESSAGE"
            isAllCaps = false
            setOnClickListener { sendTelegram("Annu Kumar Team: Telegram test successful.") }
        }
        content.addView(testTelegram, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        val startMonitor = Button(this).apply {
            text = "START BACKGROUND MONITORING"
            isAllCaps = false
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
                } else {
                    // Do not clear a completed six-level session stop; the next scheduled session resets it.
                    BackgroundMonitorService.start(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Background monitoring started", Toast.LENGTH_LONG).show()
                }
            }
        }
        content.addView(startMonitor, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        val stopMonitor = Button(this).apply {
            text = "STOP BACKGROUND MONITORING"
            isAllCaps = false
            setOnClickListener {
                stopService(Intent(this@MainActivity, BackgroundMonitorService::class.java))
                Toast.makeText(this@MainActivity, "Monitoring stopped", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(stopMonitor, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        content.addView(label("Token और Chat ID इसी डिवाइस में सेव होंगे। इन्हें किसी के साथ साझा न करें।", 12f).apply {
            setTextColor(muted)
            setPadding(0, dp(14), 0, 0)
        })
        scroll.addView(content)
        return scroll
    }

    private fun showLogin() {
        if (!::web.isInitialized) return
        web.visibility = View.VISIBLE
        settingsScreen.visibility = View.GONE
        loginTab.backgroundTintList = ColorStateList.valueOf(accent)
        loginTab.setTextColor(navy)
        settingsTab.backgroundTintList = ColorStateList.valueOf(panel)
        settingsTab.setTextColor(white)
        status.text = if (pageReady) "BDG पेज तैयार है • वेबसाइट पर लॉगिन करें" else "BDG वेबसाइट लोड हो रही है…"
    }

    private fun showSettings() {
        web.visibility = View.GONE
        settingsScreen.visibility = View.VISIBLE
        settingsTab.backgroundTintList = ColorStateList.valueOf(accent)
        settingsTab.setTextColor(navy)
        loginTab.backgroundTintList = ColorStateList.valueOf(panel)
        loginTab.setTextColor(white)
        status.text = "Telegram सेटिंग्स"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 901 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) BackgroundMonitorService.start(this)
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("telegram_bot_token", tokenField.text.toString().trim())
            .putString("telegram_chat_id", chatField.text.toString().trim())
            .putBoolean("telegram_enabled", true)
            .apply()
        status.text = "Telegram सेटिंग्स इसी डिवाइस में सेव हो गईं"
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun consumeHistory(raw: String) {
        try {
            val decoded = org.json.JSONTokener(raw).nextValue() as? String ?: return
            if (decoded == "WAIT_BINGO") {
                status.text = "Lottery खोलकर Bingo चुनें • फिर 30 सेकंड / 1 / 3 / 5 मिनट में से गेम चुनें"
                return
            }
            if (decoded == "WAIT_PANEL") {
                status.text = "चुना हुआ Bingo टैब मिला, लेकिन उसका अलग History panel नहीं मिला • गलत टैब का डेटा नहीं पढ़ा गया"
                updateDashboard(selectedModeName, emptyList())
                return
            }
            if (decoded == "WAIT_MODE") {
                status.text = "Bingo खुला है • 30 सेकंड, 1 मिनट, 3 मिनट या 5 मिनट वाला टैब चुनें"
                updateDashboard("अभी कोई गेम चयनित नहीं", emptyList())
                return
            }
            val payload = JSONObject(decoded)
            val mode = payload.optString("mode", "Bingo")
            val rows = payload.optJSONArray("rows") ?: JSONArray()
            val pageRows = mutableListOf<Pair<String, String>>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val period = row.optString("period")
                val size = row.optString("size").uppercase()
                if (period.matches(Regex("[0-9]{8,24}")) && (size == "BIG" || size == "SMALL") &&
                    pageRows.none { it.first == period }) pageRows.add(period to size)
            }
            if (pageRows.isEmpty()) {
                status.text = "$mode खुला है • इसी टैब की History में पढ़ने योग्य रिजल्ट नहीं मिला"
                updateDashboard(mode, emptyList())
                return
            }
            // Send the exact history snapshot read from the currently selected visible tab.
            val monitorIntent = Intent(this, BackgroundMonitorService::class.java).apply {
                action = BackgroundMonitorService.ACTION_HISTORY_UPDATE
                putExtra(BackgroundMonitorService.EXTRA_HISTORY_JSON, raw)
                putExtra(BackgroundMonitorService.EXTRA_PAGE_URL, web.url)
                putExtra(BackgroundMonitorService.EXTRA_SELECTED_MODE, mode)
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(monitorIntent) else startService(monitorIntent)
            } catch (e: Exception) {
                prefs.edit().putString("telegram_last_diagnostic", "History पढ़ी गई, monitor तक नहीं पहुँची: " + (e.localizedMessage ?: "service error"))
                    .putLong("telegram_last_diagnostic_at", System.currentTimeMillis()).apply()
            }
            observedRows.clear()
            observedRows.addAll(pageRows.take(10))
            val latest = observedRows.first()
            status.text = "$mode में दिखाई देने वाली History: " + observedRows.size + "/10 • Period: " + latest.first + " • " + latest.second
            updateDashboard(mode, observedRows.toList())
        } catch (_: Exception) {
            status.text = "चुने हुए Bingo टैब की History अभी पढ़ी नहीं जा सकी"
            updateDashboard(selectedModeName, emptyList())
        }
    }

    private fun sendTelegram(message: String) {
        status.text = "Telegram: संदेश भेजा जा रहा है…"
        SessionScheduler.sendTelegram(this, message) { ok, detail ->
            runOnUiThread {
                status.text = if (ok) "Telegram message sent successfully"
                    else "Telegram failed • $detail"
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::settingsScreen.isInitialized && settingsScreen.visibility == View.VISIBLE) {
            showLogin()
        } else if (::web.isInitialized && web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        // Keep the selected BDG WebView's JavaScript/history polling alive while
        // the user temporarily backgrounds the app. Explicit WebView.onPause()
        // pauses JavaScript timers and caused the history hand-off to stop.
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) {
            web.onResume()
            if (pageReady && web.visibility == View.VISIBLE && !historyReadInFlight) {
                // Use the same in-flight guard as the regular poll so resume cannot
                // overlap a history read already started by the polling Runnable.
                historyReadInFlight = true
                web.evaluateJavascript(BdgHistoryReader.HISTORY_READER_JS) { raw ->
                    historyReadInFlight = false
                    if (!isFinishing && raw != null && raw != "null") consumeHistory(raw)
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::web.isInitialized) {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {

}
}
