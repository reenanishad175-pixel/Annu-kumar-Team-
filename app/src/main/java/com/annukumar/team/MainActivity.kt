package com.annukumar.team

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var settingsScreen: ScrollView
    private lateinit var tokenField: EditText
    private lateinit var chatField: EditText
    private lateinit var loginTab: Button
    private lateinit var settingsTab: Button
    private var pageReady = false
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
            if (::web.isInitialized && pageReady) {
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

        settingsScreen = buildSettings()
        dashboard.addView(settingsScreen, FrameLayout.LayoutParams(-1, -1))
        return root
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

    private fun saveSettings() {
        prefs.edit()
            .putString("telegram_bot_token", tokenField.text.toString().trim())
            .putString("telegram_chat_id", chatField.text.toString().trim())
            .apply()
        status.text = "Telegram सेटिंग्स इसी डिवाइस में सेव हो गईं"
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }


    // Read history only after the visible page has reached the Bingo screen.
    private fun consumeHistory(raw: String) {
        try {
            val decoded = org.json.JSONTokener(raw).nextValue() as? String ?: return
            if (decoded == "WAIT_BINGO") {
                status.text = "पहले BDG में Lottery पर जाएँ, फिर Bingo खोलें • वहीं History पढ़ी जाएगी"
                return
            }
            val payload = JSONObject(decoded)
            if (!payload.optBoolean("bingo", false)) {
                status.text = "Bingo स्क्रीन खुलने का इंतज़ार…"
                return
            }
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
                status.text = "Bingo खुला है • पहले पेज की History दिख रही है, लेकिन Period/Result पढ़ा नहीं जा सका"
                return
            }
            observedRows.clear()
            observedRows.addAll(pageRows.take(10))
            val latest = observedRows.first()
            status.text = "Bingo History: " + observedRows.size + "/10 • Period: " + latest.first + " • " + latest.second
        } catch (_: Exception) {
            status.text = "Bingo History: स्क्रीन का डेटा अभी पढ़ा नहीं जा सका"
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
                val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") + "&text=" + URLEncoder.encode(message, "UTF-8")
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
          var found = [], seen = Object.create(null), docs = [];
          var visibleText = (document.body && (document.body.innerText || document.body.textContent) || '').toLowerCase();
          var route = (location.href || '').toLowerCase();
          var bingoVisible = /bingo/.test(route) || /bingo/.test(visibleText);
          if (!bingoVisible) return JSON.stringify('WAIT_BINGO');
          function visible(el) {
            if (!el || !el.getClientRects || !el.getClientRects().length) return false;
            var s = window.getComputedStyle(el);
            return s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0';
          }
          function textOf(el) {
            return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
          }
          function add(period, size) {
            if (!seen[period] && (size === 'BIG' || size === 'SMALL')) {
              seen[period] = true;
              found.push({period: period, size: size});
            }
          }
          function parseContainer(el, period) {
            var txt = textOf(el);
            if (!txt || txt.length > 420) return;
            var sizeMatch = txt.match(/\b(BIG|SMALL)\b/i);
            if (sizeMatch) {
              add(period, sizeMatch[1].toUpperCase());
              return;
            }
            var digitMatch = txt.match(/(?:^|\s)([0-9])(?:\s|$)/);
            if (digitMatch) add(period, Number(digitMatch[1]) >= 5 ? 'BIG' : 'SMALL');
          }
          function scanRoot(root) {
            if (!root || docs.indexOf(root) >= 0) return;
            docs.push(root);
            root.querySelectorAll('tr,[role="row"],[role="gridcell"]').forEach(function (row) {
              if (!visible(row)) return;
              var txt = textOf(row);
              var p = txt.match(/\b[0-9]{8,24}\b/);
              if (p) parseContainer(row, p[0]);
            });
            root.querySelectorAll('body *').forEach(function (el) {
              if (!visible(el)) return;
              var own = textOf(el);
              if (!own || own.length > 420) return;
              var pm = own.match(/\b[0-9]{8,24}\b/);
              if (!pm) return;
              var node = el;
              for (var depth = 0; node && depth < 5; depth++, node = node.parentElement) {
                var block = textOf(node);
                if (block.length > 420) break;
                if (new RegExp('\\b' + pm[0] + '\\b').test(block) && /\b(BIG|SMALL)\b/i.test(block)) {
                  parseContainer(node, pm[0]);
                  break;
                }
              }
            });
            root.querySelectorAll('*').forEach(function (el) {
              if (el.shadowRoot) scanRoot(el.shadowRoot);
            });
            root.querySelectorAll('iframe').forEach(function (frame) {
              try { if (frame.contentDocument) scanRoot(frame.contentDocument); } catch (_) {}
            });
          }
          try { scanRoot(document); } catch (_) {}
          return JSON.stringify({bingo: true, rows: found.slice(0, 10)});
        })()"""
    }
}
