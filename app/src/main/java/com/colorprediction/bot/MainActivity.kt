package com.colorprediction.bot

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.*
import android.text.InputType
import android.content.Context

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bot_settings", Context.MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var predictionView: TextView
    private lateinit var periodView: TextView
    private lateinit var activityView: TextView
    private lateinit var web: WebView
    private val bg = Color.rgb(8, 13, 25)
    private val blue = Color.rgb(18, 112, 255)
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionScheduler.scheduleAll(this)
        showLoginLinkSettings()
    }

    private fun text(s:String,size:Float=16f,color:Int=Color.WHITE,bold:Boolean=false):TextView =
        TextView(this).apply { text=s; textSize=size; setTextColor(color); gravity=Gravity.CENTER; if(bold) setTypeface(null,1); setPadding(dp(6),dp(4),dp(6),dp(4)) }

    private fun button(s:String, action:()->Unit)=Button(this).apply {
        text=s; setTextColor(Color.WHITE); setBackgroundColor(blue); setOnClickListener { action() }
    }

    private fun field(hint:String, secret:Boolean=false):EditText = EditText(this).apply {
        this.hint=hint; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE)
        inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setPadding(dp(12),dp(4),dp(12),dp(4))
    }

    private fun panel():LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(5),dp(8),dp(5))
        setBackgroundColor(Color.rgb(20,29,48))
    }

    private fun showBdg() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(6),dp(4),dp(6),dp(4)) }
        val dashboard=panel()
        dashboard.addView(text("🦁 COLOR PREDICTION BOT",16f,Color.WHITE,true))
        status=text(prefs.getString("bdg_read_status","BDG PAGE OPEN • Waiting for readable game data") ?: "BDG PAGE OPEN • Waiting for readable game data",11f,Color.YELLOW,true)
        predictionView=text("",13f,Color.CYAN,true)
        periodView=text("",11f,Color.WHITE)
        activityView=text("",10f,Color.LTGRAY)
        dashboard.addView(status)
        dashboard.addView(predictionView)
        dashboard.addView(periodView)
        dashboard.addView(activityView)
        refreshDashboard()
        // Wrap content prevents the dashboard card from being clipped on short screens.
        root.addView(dashboard,LinearLayout.LayoutParams(-1,-2))
        web=WebView(this).apply {
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.loadsImagesAutomatically=true
            addJavascriptInterface(BdgJavascriptBridge(), "AndroidBdg")
            webViewClient=object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // Read the rendered page text repeatedly, including SPA route/content changes.
                    view.evaluateJavascript("(function(){if(window.__colorBotReaderTimer)clearInterval(window.__colorBotReaderTimer);window.__colorBotReaderTimer=setInterval(function(){try{if(window.AndroidBdg&&document.body){var t=document.body.innerText||'';window.AndroidBdg.onPageText(t);}}catch(e){}},2500);})();", null)
                }
            }
            loadUrl(prefs.getString("bdg_url","https://bdgwin98.com") ?: "https://bdgwin98.com")
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private inner class BdgJavascriptBridge {
        @JavascriptInterface
        fun onPageText(pageText: String) {
            val snapshot = BdgPageParser.parse(pageText)
            val message = if (snapshot == null) {
                "BDG PAGE OPEN • Waiting for live period + 3 history results"
            } else {
                BdgAutomationController.process(this@MainActivity, snapshot)
            }
            prefs.edit().putString("bdg_read_status", message).apply()
            runOnUiThread { refreshDashboard(message) }
        }
    }

    private fun refreshDashboard(statusMessage: String? = null) {
        if (::status.isInitialized) status.text = statusMessage ?: (prefs.getString("bdg_read_status","Waiting for BDG data") ?: "Waiting for BDG data")
        if (::predictionView.isInitialized) predictionView.text =
            "PREDICTION: ${prefs.getString("current_prediction","WAITING") ?: "WAITING"}  •  LEVEL ${prefs.getInt("current_level",1)}/8"
        if (::periodView.isInitialized) periodView.text =
            "PERIOD: ${prefs.getString("current_period","—") ?: "—"}  •  SKIP: ${prefs.getInt("engine_skip",0)}/${prefs.getInt("engine_skip_total",0)}"
        if (::activityView.isInitialized) activityView.text =
            "ACTIVITY: ${prefs.getString("recent_activity","Waiting for first result") ?: "Waiting for first result"}"
    }

    private fun showLoginLinkSettings() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setBackgroundColor(bg); setPadding(dp(20),dp(16),dp(20),dp(16)) }
        root.addView(text("🦁 COLOR PREDICTION BOT",22f,Color.WHITE,true))
        root.addView(text("BDG LOGIN LINK SETUP",17f,Color.CYAN,true))
        root.addView(text("अपना BDG गेम लॉगिन लिंक डालें। OK दबाते ही गेम लॉगिन पेज और डैशबोर्ड साथ खुलेंगे।",14f,Color.LTGRAY))
        val url=field("https://your-bdg-login-link.com").apply {
            setSingleLine(true)
            setText(prefs.getString("bdg_url","https://bdgwin98.com") ?: "https://bdgwin98.com")
        }
        root.addView(url)
        root.addView(button("OK • OPEN GAME + DASHBOARD") {
            val value=url.text.toString().trim()
            if (value.startsWith("https://")) {
                prefs.edit().putString("bdg_url",value).apply()
                showBdg()
            } else Toast.makeText(this,"Valid HTTPS link दर्ज करें",Toast.LENGTH_SHORT).show()
        })
        setContentView(root)
    }
}
