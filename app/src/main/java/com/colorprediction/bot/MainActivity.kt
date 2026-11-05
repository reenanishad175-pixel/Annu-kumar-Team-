package com.colorprediction.bot

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
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
        status=text("READY • BDG page not connected",14f,Color.YELLOW,true)
        statusCard.addView(status)
        body.addView(statusCard)
        val pred=panel()
        pred.addView(text("CURRENT PREDICTION",14f,Color.LTGRAY,true))
        pred.addView(text("Waiting for verified BDG result data",20f,Color.CYAN,true))
        pred.addView(text("LEVEL 1 / 8     •     STRATEGY: AUTO",14f,Color.WHITE,true))
        body.addView(pred)
        body.addView(button(if(expanded) "MINIMIZE DASHBOARD" else "EXPAND DASHBOARD") {
            expanded=!expanded; showDashboard()
        })
        if(expanded) {
            body.addView(text("RECENT ACTIVITY",16f,Color.WHITE,true))
            body.addView(text("No confirmed betting period yet",13f,Color.LTGRAY))
            body.addView(button("BDG LOGIN PAGE") { showBdg() })
            body.addView(button("BOT SETTINGS") { showSettings() })
            body.addView(text("Rules configured: 4 strategies • 8 patterns • 8 levels",13f,Color.LTGRAY))
            body.addView(text("Level 8 LOSS: pause predictions for 1 hour",13f,Color.YELLOW))
            body.addView(text("Prediction rules are not a guarantee of game outcomes.",11f,Color.LTGRAY))
        } else body.addView(text("LEVEL 1  •  SKIP: 0",15f,Color.CYAN,true))
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
            webViewClient=WebViewClient()
            loadUrl(prefs.getString("bdg_url","https://bdgwin98.com") ?: "https://bdgwin98.com")
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(button("BACK TO DASHBOARD") { showDashboard() })
        setContentView(root)
    }

    private fun showSettings() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(16),dp(12),dp(16),dp(12)) }
        root.addView(text("BOT SETTINGS",22f,Color.WHITE,true))
        root.addView(text("BDG Login URL",14f,Color.CYAN,true))
        val url=field("https://...").apply { setText(prefs.getString("bdg_url","https://bdgwin98.com") ?: "") }
        val token=field("Telegram Bot Token",true).apply { setText(prefs.getString("tg_token","") ?: "") }
        val chat=field("Telegram Chat ID").apply { setText(prefs.getString("tg_chat","") ?: "") }
        root.addView(url); root.addView(text("Telegram Bot Token",14f,Color.CYAN,true)); root.addView(token)
        root.addView(text("Telegram Chat ID",14f,Color.CYAN,true)); root.addView(chat)
        root.addView(button("SAVE SETTINGS") {
            prefs.edit().putString("bdg_url",url.text.toString().trim())
                .putString("tg_token",token.text.toString()).putString("tg_chat",chat.text.toString().trim()).apply()
            Toast.makeText(this,"Settings saved on this device",Toast.LENGTH_SHORT).show()
        })
        root.addView(button("BACK TO DASHBOARD") { showDashboard() })
        setContentView(root)
    }
}
