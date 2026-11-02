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

    private val navy = Color.rgb(9, 15, 29)
    private val panel = Color.rgb(20, 30, 49)
    private val accent = Color.rgb(39, 211, 175)
    private val muted = Color.rgb(174, 188, 207)
    private val white = Color.WHITE
    private val bdgUrl = "https://bdgwin68.com/#/login"

    private val pageMonitor = object : Runnable {
        override fun run() {
            if (::web.isInitialized && pageReady) {
                web.evaluateJavascript(
                    "(function(){return JSON.stringify({title:document.title,ready:document.readyState,url:location.href});})()"
                ) { raw ->
                    if (!isFinishing && raw != null && raw != "null") {
                        status.text = if (pageReady) "BDG पेज खुला है • वेबसाइट की स्थिति अपने-आप जाँची जा रही है" else "BDG पेज लोड हो रहा है…"
                    }
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
}
