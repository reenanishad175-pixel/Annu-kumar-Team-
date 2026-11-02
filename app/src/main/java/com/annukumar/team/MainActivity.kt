package com.annukumar.team

import android.app.Activity
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
    private var panelMinimized = false
    private var selectedModeName = "अभी कोई गेम चयनित नहीं"
    private lateinit var settingsScreen: ScrollView
    private lateinit var tokenField: EditText
    private lateinit var chatField: EditText
    private lateinit var loginTab: Button
    private lateinit var settingsTab: Button
    private var pageReady = false
    private val seenPeriods = LinkedHashSet<String>()
    private val observedRows = mutableListOf<Pair<String, String>>()
    private var lastTelegramPredictionKey: String? = null

    private val navy = Color.rgb(9, 15, 29)
    private val panel = Color.rgb(20, 30, 49)
    private val accent = Color.rgb(39, 211, 175)
    private val muted = Color.rgb(174, 188, 207)
    private val white = Color.WHITE
    private val bdgUrl = "https://bdgwin68.com/#/login"

    private val pageMonitor = object : Runnable {
        override fun run() {
            if (::web.isInitialized && pageReady && web.visibility == View.VISIBLE) {
                web.evaluateJavascript(HISTORY_READER_JS) { raw ->
                    if (!isFinishing && raw != null && raw != "null") consumeHistory(raw)
                }
            }
            handler.postDelayed(this, 6000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.systemUiVisibility = 0
        setContentView(buildScreen())
        showLogin()
        if (savedInstanceState == null) web.loadUrl(bdgUrl)
        handler.postDelayed(pageMonitor, 6000)
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
                    // Let the site use its own responsive viewport; do not force page position.
                    view?.evaluateJavascript(
                        "(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';})()",
                        null
                    )
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
        predictionView = label("अनुमान: डेटा का इंतज़ार", 17f).apply { setTextColor(accent) }
        trendView = label("ट्रेंड: —", 12f).apply { setTextColor(muted) }
        historyView = label("हाल के नतीजे: —", 12f).apply { setTextColor(muted) }
        panelBody.addView(selectedModeView)
        panelBody.addView(predictionView)
        panelBody.addView(trendView)
        panelBody.addView(historyView)
        floatingPanel.addView(panelBody)
        val lp = FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins(dp(8), 0, dp(8), dp(8))
        dashboard.addView(floatingPanel, lp)
        floatingPanel.bringToFront()
    }

    private fun toggleDashboard() {
        panelMinimized = !panelMinimized
        panelBody.visibility = if (panelMinimized) View.GONE else View.VISIBLE
        panelToggle.text = if (panelMinimized) "▲  LIVE DASHBOARD  •  खोलें" else "▼  LIVE DASHBOARD  •  छोटा करें"
    }

    private fun updateDashboard(mode: String, rows: List<Pair<String, String>>) {
        selectedModeName = mode
        selectedModeView.text = "चयन: $mode  •  पढ़े गए: ${rows.size}/10"
        if (rows.isEmpty()) {
            predictionView.text = "अनुमान: पर्याप्त डेटा नहीं"
            trendView.text = "ट्रेंड: —"
            historyView.text = "हाल के नतीजे: —"
            return
        }
        val big = rows.count { it.second == "BIG" }
        val small = rows.size - big
        val latest = rows.first().second
        val streak = rows.takeWhile { it.second == latest }.size
        val trend = when {
            big > small -> "BIG अधिक: $big बनाम SMALL $small"
            small > big -> "SMALL अधिक: $small बनाम BIG $big"
            else -> "दोनों बराबर: BIG $big / SMALL $small"
        }
        // Transparent heuristic: majority of the currently visible sample; ties follow the latest result.
        val pick = when {
            big > small -> "BIG"
            small > big -> "SMALL"
            else -> latest
        }
        val confidence = when {
            rows.size < 5 -> "कम"
            kotlin.math.abs(big - small) >= 4 -> "मध्यम (सिर्फ पैटर्न-आधारित)"
            else -> "कम (पैटर्न कमजोर)"
        }
        predictionView.text = "अनुमान: $pick  •  भरोसा: $confidence"
        trendView.text = "ट्रेंड: $trend  •  आखिरी $latest की लगातार लड़ी: $streak"
        historyView.text = "हाल के ${rows.size}: " + rows.joinToString("  ›  ") { it.second }

        // Send one Telegram update for each newly observed latest period, never every poll.
        val predictionKey = "$mode|${rows.first().first}|$pick"
        if (predictionKey != lastTelegramPredictionKey) {
            lastTelegramPredictionKey = predictionKey
            val tokenConfigured = prefs.getString("telegram_bot_token", "").orEmpty().isNotBlank()
            val chatConfigured = prefs.getString("telegram_chat_id", "").orEmpty().isNotBlank()
            if (tokenConfigured && chatConfigured) {
                val message = "🎯 <b>ANNU / TEAM — LIVE UPDATE</b>\n" +
                    "━━━━━━━━━━━━━━━━━━\n" +
                    "🎮 <b>Game:</b> $mode\n" +
                    "🆔 <b>Period:</b> <code>${rows.first().first}</code>\n" +
                    "📌 <b>Latest Result:</b> <b>$latest</b>\n" +
                    "━━━━━━━━━━━━━━━━━━\n" +
                    "📊 <b>LAST 10 RESULTS</b>\n" +
                    "🟢 BIG: <b>$big</b>     🔴 SMALL: <b>$small</b>\n" +
                    "📈 <b>Trend:</b> $trend\n" +
                    "🔥 <b>Current Streak:</b> $latest × $streak\n" +
                    "━━━━━━━━━━━━━━━━━━\n" +
                    "💡 <b>PATTERN ESTIMATE</b>\n" +
                    "⭐ <b>$pick</b>\n" +
                    "📶 <b>Strength:</b> $confidence\n" +
                    "━━━━━━━━━━━━━━━━━━\n" +
                    "⚠️ <i>Pattern-based estimate only. Not guaranteed; play responsibly.</i>"
                sendTelegram(message)
            }
        }
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
        content.addView(save, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(18)
        })
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
                    prefs.edit().putBoolean("bg_stopped", false).apply()
                    BackgroundMonitorService.start(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Background monitoring started", Toast.LENGTH_LONG).show()
                }
            }
        }
        content.addView(startMonitor, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        val stopMonitor = Button(this).apply {
            text = "STOP BACKGROUND MONITORING"
            isAllCaps = false
            setOnClickListener { stopService(Intent(this@MainActivity, BackgroundMonitorService::class.java)); Toast.makeText(this@MainActivity, "Monitoring stopped", Toast.LENGTH_SHORT).show() }
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


    // Read only the currently selected Bingo duration tab.
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
        val token = prefs.getString("telegram_bot_token", "").orEmpty()
        val chatId = prefs.getString("telegram_chat_id", "").orEmpty()
        if (token.isBlank() || chatId.isBlank()) {
            status.text = "Telegram not sent • Settings में Bot Token और Chat ID भरें"
            return
        }
        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.telegram.org/bot" + token + "/sendMessage").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") + "&parse_mode=HTML&text=" + URLEncoder.encode(message, "UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val ok = code in 200..299 && JSONObject(response).optBoolean("ok", false)
                runOnUiThread { status.text = if (ok) "Telegram message sent successfully" else "Telegram failed (HTTP " + code + ") • Token/Chat ID जाँचें" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Telegram error • " + (e.localizedMessage ?: "network error") }
            } finally {
                connection?.disconnect()
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
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
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
        private const val HISTORY_READER_JS = """(function () {
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

            // BDG may render tabs without ARIA panel links. Find a sibling content
            // region of the tab strip, but accept it only when it contains result rows.
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
            // A single self-contained record only: never borrow a result from an ancestor.
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
