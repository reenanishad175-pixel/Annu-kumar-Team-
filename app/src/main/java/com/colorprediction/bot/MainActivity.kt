package com.colorprediction.bot

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
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
    private lateinit var web: WebView
    private var expanded = true
    private var currentLevel = 1
    private var skipLeft = 0
    private var pausedUntil = 0L
    private val bg = Color.rgb(8, 13, 25)
    private val blue = Color.rgb(18, 112, 255)
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionScheduler.scheduleAll(this)
        showDashboard()
    }

    private fun text(s:String,size:Float=16f,color:Int=Color.WHITE,bold:Boolean=false):TextView =
        TextView(this).apply { text=s; textSize=size; setTextColor(color); gravity=Gravity.CENTER; if(bold) setTypeface(null,1); setPadding(dp(8),dp(8),dp(8),dp(8)) }

    private fun button(s:String, action:()->Unit)=Button(this).apply {
        text=s; setTextColor(Color.WHITE); setBackgroundColor(blue); setOnClickListener { action() }
    }

    private fun field(hint:String, secret:Boolean=false):EditText = EditText(this).apply {
        this.hint=hint; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE)
        inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setPadding(dp(12),dp(4),dp(12),dp(4))
    }

    private fun panel():LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10))
        setBackgroundColor(Color.rgb(20,29,48))
    }

    private fun showDashboard() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(12),dp(8),dp(12),dp(8)) }
        val scroll=ScrollView(this); val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        body.addView(text("🦁  COLOR PREDICTION BOT",22f,Color.WHITE,true))
        body.addView(text("Lion Edition • Strategy Dashboard",13f,Color.LTGRAY))
        val statusCard=panel()
        status=text(prefs.getString("bdg_read_status","READY • BDG page not connected") ?: "READY • BDG page not connected",14f,Color.YELLOW,true)
        statusCard.addView(status)
        body.addView(statusCard)
        val pred=panel()
        pred.addView(text("CURRENT PREDICTION",14f,Color.LTGRAY,true))
        pred.addView(text(prefs.getString("current_prediction","WAITING") ?: "WAITING",20f,Color.CYAN,true))
        pred.addView(text("LEVEL ${prefs.getInt("current_level",1)} / 8     •     ${prefs.getString("current_strategy","Waiting for BDG data") ?: "Waiting for BDG data"}",14f,Color.WHITE,true))
        pred.addView(text("PERIOD: ${prefs.getString("current_period","—") ?: "—"}",13f,Color.LTGRAY))
        pred.addView(text("SKIP: ${prefs.getInt("engine_skip",0)}/${prefs.getInt("engine_skip_total",0)}",13f,Color.LTGRAY))
        body.addView(pred)
        body.addView(button(if(expanded) "MINIMIZE DASHBOARD" else "EXPAND DASHBOARD") {
            expanded=!expanded; showDashboard()
        })
        if(expanded) {
            body.addView(text("RECENT ACTIVITY",16f,Color.WHITE,true))
            body.addView(text(prefs.getString("recent_activity","No confirmed betting period yet") ?: "No confirmed betting period yet",13f,Color.LTGRAY))
            body.addView(button("BDG LOGIN PAGE") { showBdg() })
            body.addView(button("BDG LOGIN LINK SETTINGS") { showLoginLinkSettings() })
            body.addView(button("BOT SETTINGS") { showSettings() })
            body.addView(text("BDG text reader • waits for 3 explicit period/result rows",13f,Color.YELLOW))
            body.addView(text("Live BDG page format still requires on-device verification before release.",13f,Color.YELLOW))
            body.addView(text("Prediction rules are not a guarantee of game outcomes.",11f,Color.LTGRAY))
        } else body.addView(text("LEVEL ${prefs.getInt("current_level",1)}  •  ${prefs.getString("current_prediction","WAITING") ?: "WAITING"}  •  SKIP: ${prefs.getInt("engine_skip",0)}/${prefs.getInt("engine_skip_total",0)}",15f,Color.CYAN,true))
        scroll.addView(body); root.addView(scroll); setContentView(root)
    }

    private fun showBdg() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(8),dp(8),dp(8),dp(8)) }
        root.addView(text("BDG LOGIN",20f,Color.WHITE,true))
        val urlField=field("BDG login URL").apply { setText(prefs.getString("bdg_url","https://bdgwin98.com") ?: "") }
        root.addView(urlField)
        root.addView(button("OPEN / SAVE BDG PAGE") {
            val u=urlField.text.toString().trim()
            if(u.startsWith("https://")) { prefs.edit().putString("bdg_url",u).apply(); web.loadUrl(u) }
            else Toast.makeText(this,"HTTPS URL दर्ज करें",Toast.LENGTH_SHORT).show()
        })
        web=WebView(this).apply {
            settings.javaScriptEnabled=true; settings.domStorageEnabled=true
            addJavascriptInterface(BdgJavascriptBridge(), "AndroidBdg")
            webViewClient=object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(
                        "(function(){if(window.__colorBotReader)return;window.__colorBotReader=true;setInterval(function(){try{if(window.AndroidBdg&&document.body){window.AndroidBdg.onPageText(document.body.innerText||'');}}catch(e){}},2000);})();",
                        null
                    )
                }
            }
            loadUrl(prefs.getString("bdg_url","https://bdgwin98.com") ?: "https://bdgwin98.com")
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(button("BACK TO DASHBOARD") { showDashboard() })
        setContentView(root)
    }

    private inner class BdgJavascriptBridge {
        @JavascriptInterface
        fun onPageText(pageText: String) {
            val snapshot = BdgPageParser.parse(pageText)
            val message = if (snapshot == null) {
                "BDG PAGE OPEN • Waiting for readable period + 3 results"
            } else {
                BdgAutomationController.process(this@MainActivity, snapshot)
            }
            prefs.edit().putString("bdg_read_status", message).apply()
            runOnUiThread {
                if (::status.isInitialized) status.text = message
            }
        }
    }

    private fun showLoginLinkSettings() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(16),dp(12),dp(16),dp(12)) }
        root.addView(text("BDG LOGIN LINK SETTINGS",21f,Color.WHITE,true))
        root.addView(text("यहाँ अपना BDG login / game URL डालें। Save करने के बाद BDG LOGIN PAGE इसी लिंक को खोलेगा।",14f,Color.LTGRAY))
        val url=field("https://your-bdg-login-link.com").apply {
            setSingleLine(true)
            setText(prefs.getString("bdg_url","https://bdgwin98.com") ?: "https://bdgwin98.com")
        }
        root.addView(text("BDG LOGIN URL",14f,Color.CYAN,true))
        root.addView(url)
        root.addView(button("SAVE LOGIN LINK") {
            val value=url.text.toString().trim()
            if (value.startsWith("https://")) {
                prefs.edit().putString("bdg_url",value).apply()
                Toast.makeText(this,"BDG login link saved",Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this,"Valid HTTPS link दर्ज करें",Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(button("OPEN BDG LOGIN PAGE") { showBdg() })
        root.addView(button("BACK TO DASHBOARD") { showDashboard() })
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun showSettings() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(16),dp(12),dp(16),dp(12)) }
        root.addView(text("BOT SETTINGS",22f,Color.WHITE,true))
        root.addView(text("BDG Login URL",14f,Color.CYAN,true))
        val url=field("https://...").apply { setText(prefs.getString("bdg_url","https://bdgwin98.com") ?: "") }
        val token=field("Telegram Bot Token",true).apply { setText(prefs.getString("tg_token","") ?: "") }
        val chat=field("Telegram Chat ID").apply { setText(prefs.getString("tg_chat","") ?: "") }
        val referral=field("BDG Referral URL").apply {
            setText(prefs.getString("referral_url","https://bdgwin98.com/#/register?invitationCode=5152785394") ?: "")
            setSingleLine(true)
        }
        val durations = listOf("30 Seconds", "1 Minute", "3 Minutes")
        val durationSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, durations)
            setSelection(durations.indexOf(prefs.getString("game_duration", "30 Seconds")).coerceAtLeast(0))
        }
        root.addView(url); root.addView(text("Telegram Bot Token",14f,Color.CYAN,true)); root.addView(token)
        root.addView(text("Telegram Chat ID",14f,Color.CYAN,true)); root.addView(chat)
        root.addView(text("BDG REFERRAL URL",14f,Color.CYAN,true)); root.addView(referral)
        root.addView(text("GAME DURATION",14f,Color.CYAN,true)); root.addView(durationSpinner)
        root.addView(button("SAVE SETTINGS") {
            val bdgValue=url.text.toString().trim()
            val referralValue=referral.text.toString().trim()
            if (!bdgValue.startsWith("https://") || !referralValue.startsWith("https://")) {
                Toast.makeText(this,"BDG और Referral URL में valid HTTPS link डालें",Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putString("bdg_url",bdgValue)
                    .putString("tg_token",token.text.toString()).putString("tg_chat",chat.text.toString().trim())
                    .putString("referral_url",referralValue)
                    .putString("game_duration",durationSpinner.selectedItem.toString()).apply()
                Toast.makeText(this,"Settings saved on this device",Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(button("SEND TELEGRAM TEST") {
            val t=token.text.toString().trim()
            val ch=chat.text.toString().trim()
            if (t.isBlank() || ch.isBlank()) {
                Toast.makeText(this,"Bot Token और Chat ID भरें",Toast.LENGTH_SHORT).show()
            } else {
                TelegramNotifier.send(t,ch,"Color Prediction Bot: Telegram connection test") { ok ->
                    runOnUiThread {
                        Toast.makeText(this,if(ok) "Telegram test sent" else "Telegram test failed • details check करें",Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
        root.addView(button("BACK TO DASHBOARD") { showDashboard() })
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }
}
