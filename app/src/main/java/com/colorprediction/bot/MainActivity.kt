package com.colorprediction.bot

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.*
import android.text.InputType
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bot_settings", Context.MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var predictionView: TextView
    private lateinit var periodView: TextView
    private lateinit var activityView: TextView
    private lateinit var resultFlashView: TextView
    private lateinit var web: WebView
    private val bg = Color.rgb(8, 13, 25)
    private val blue = Color.rgb(18, 112, 255)
    private var lastFlashId = 0L
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        SessionScheduler.scheduleAll(this)
        showLoginLinkSettings()
    }

    private fun text(s:String,size:Float=16f,color:Int=Color.WHITE,bold:Boolean=false):TextView =
        TextView(this).apply { text=s; textSize=size; setTextColor(color); gravity=Gravity.CENTER; if(bold) setTypeface(null,1); setPadding(dp(6),dp(7),dp(6),dp(7)) }

    private fun button(s:String, action:()->Unit)=Button(this).apply {
        text=s; setTextColor(Color.WHITE); setBackgroundColor(blue); setOnClickListener { action() }
    }

    private fun field(hint:String, secret:Boolean=false):EditText = EditText(this).apply {
        this.hint=hint; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE)
        inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setSingleLine(true)
        setPadding(dp(12),dp(8),dp(12),dp(8))
    }

    private fun panel():LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(14),dp(12),dp(14),dp(12))
        setBackgroundColor(Color.rgb(20,29,48))
    }

    private fun showBdg() {
        val screen = FrameLayout(this).apply { setBackgroundColor(bg) }

        // The game WebView always occupies the complete screen. The dashboard is
        // a separate overlay, so folding it never resizes or shrinks the game.
        web=WebView(this).apply {
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.loadsImagesAutomatically=true
            addJavascriptInterface(BdgJavascriptBridge(), "AndroidBdg")
            webViewClient=object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript("(function(){try{var id='color-bot-safe-bottom';var st=document.getElementById(id);if(!st){st=document.createElement('style');st.id=id;document.head.appendChild(st);}st.textContent='html{scroll-padding-bottom:100px!important}body{padding-bottom:100px!important;box-sizing:border-box!important}button,[role=button],.van-button,.bet-btn,.submit-btn{scroll-margin-bottom:100px!important}';var lift=function(){document.querySelectorAll('button,[role=button],.van-button,.bet-btn,.submit-btn').forEach(function(el){var r=el.getBoundingClientRect();var cs=getComputedStyle(el);if((cs.position==='fixed'||cs.position==='sticky')&&r.bottom>innerHeight-90&&r.width>90&&r.height>24&&r.height<110){if(!el.dataset.colorBotLifted){el.dataset.colorBotLifted='1';el.style.setProperty('transform','translateY(-32px)','important');}}});};lift();if(window.__colorBotLiftTimer)clearInterval(window.__colorBotLiftTimer);window.__colorBotLiftTimer=setInterval(lift,1500);}catch(e){}if(window.__colorBotReaderTimer)clearInterval(window.__colorBotReaderTimer);window.__colorBotReaderTimer=setInterval(function(){try{if(window.AndroidBdg&&document.body){var t=document.body.innerText||'';window.AndroidBdg.onPageText(t);}}catch(e){}},2500);})();", null)
                }
            }
            loadUrl(prefs.getString("bdg_url","https://bdgwin98.com") ?: "https://bdgwin98.com")
        }
        screen.addView(web, FrameLayout.LayoutParams(-1,-1))

        val dashboard=panel().apply {
            minimumHeight=dp(245)
            elevation=dp(12).toFloat()
        }
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val logo=ImageView(this).apply {
            setImageResource(R.drawable.ic_color_bot_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
            alpha=0.96f
        }
        header.addView(logo,LinearLayout.LayoutParams(dp(54),dp(54)))
        val title=text("COLOR PREDICTION BOT",18f,Color.WHITE,true).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        val foldButton=button("⌃ MINIMIZE") { }
        header.addView(foldButton,LinearLayout.LayoutParams(-2,dp(44)))
        dashboard.addView(header)

        val details=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
        }
        status=text(prefs.getString("bdg_read_status","BDG PAGE OPEN • Waiting for readable game data") ?: "BDG PAGE OPEN • Waiting for readable game data",13f,Color.YELLOW,true)
        predictionView=text("",22f,Color.RED,true).apply { setPadding(dp(8),dp(13),dp(8),dp(13)) }
        periodView=text("",14f,Color.WHITE,true)
        resultFlashView=text("",19f,Color.WHITE,true).apply {
            setPadding(dp(8),dp(8),dp(8),dp(8))
            visibility=View.GONE
        }
        activityView=text("",13f,Color.LTGRAY)
        details.addView(status)
        details.addView(predictionView)
        details.addView(periodView)
        details.addView(resultFlashView)
        details.addView(activityView)
        dashboard.addView(details)
        refreshDashboard()

        foldButton.setOnClickListener {
            val folded = details.visibility == View.VISIBLE
            details.visibility = if (folded) View.GONE else View.VISIBLE
            dashboard.minimumHeight = if (folded) dp(0) else dp(245)
            foldButton.text = if (folded) "⌄ EXPAND" else "⌃ MINIMIZE"
        }

        screen.addView(dashboard,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        setContentView(screen)
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
        val flashId=prefs.getLong("result_flash_id",0L)
        if (::resultFlashView.isInitialized && flashId>lastFlashId) {
            lastFlashId=flashId
            val outcome=prefs.getString("result_flash","").orEmpty()
            if (outcome=="WIN" || outcome=="LOSS") {
                resultFlashView.text=if(outcome=="WIN") "✓  YOUR WIN!" else "✕  YOUR LOSS"
                resultFlashView.setTextColor(Color.WHITE)
                resultFlashView.setBackgroundColor(if(outcome=="WIN") Color.rgb(20,150,75) else Color.rgb(190,45,45))
                resultFlashView.visibility=View.VISIBLE
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ if(::resultFlashView.isInitialized) resultFlashView.visibility=View.GONE },5000)
            }
        }
    }

    private fun showLoginLinkSettings() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setBackgroundColor(bg); setPadding(dp(20),dp(16),dp(20),dp(16)) }
        root.addView(text("🦁 COLOR PREDICTION BOT",22f,Color.WHITE,true))
        root.addView(text("BDG LOGIN LINK SETUP",17f,Color.CYAN,true))
        root.addView(text("अपना BDG गेम लॉगिन लिंक डालें। OK दबाते ही गेम लॉगिन पेज और डैशबोर्ड साथ खुलेंगे।",14f,Color.LTGRAY))
        val url=field("https://your-bdg-login-link.com").apply {
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
        root.addView(button("BOT SETTINGS • TELEGRAM / REFERRAL") { showBotSettings() })
        setContentView(root)
    }

    private fun showBotSettings() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setBackgroundColor(bg); setPadding(dp(18),dp(12),dp(18),dp(12)) }
        root.addView(text("BOT SETTINGS",22f,Color.CYAN,true))
        root.addView(text("Telegram और referral details यहाँ सेव करें।",14f,Color.LTGRAY))
        val token=field("Telegram Bot Token",true).apply { setText(prefs.getString("tg_token","") ?: "") }
        val chat=field("Telegram Channel / Chat ID").apply { setText(prefs.getString("tg_chat","") ?: "") }
        val referral=field("Referral Link").apply { setText(prefs.getString("referral_url","https://bdgwin98.com/#/register?invitationCode=5152785394") ?: "") }
        root.addView(token)
        root.addView(chat)
        root.addView(referral)
        root.addView(button("SAVE BOT SETTINGS") {
            val t=token.text.toString().trim()
            val c=chat.text.toString().trim()
            val ref=referral.text.toString().trim()
            if(ref.startsWith("https://")) {
                prefs.edit().putString("tg_token",t).putString("tg_chat",c).putString("referral_url",ref).apply()
                Toast.makeText(this,"Bot settings saved on this device",Toast.LENGTH_SHORT).show()
                showLoginLinkSettings()
            } else Toast.makeText(this,"Valid HTTPS referral link दर्ज करें",Toast.LENGTH_SHORT).show()
        })
        root.addView(button("BACK") { showLoginLinkSettings() })
        setContentView(root)
    }
}