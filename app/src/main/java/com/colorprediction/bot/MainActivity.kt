package com.colorprediction.bot

import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var telegramStatus: TextView
    private lateinit var liveReadMessage: TextView
    private lateinit var predictionView: TextView
    private lateinit var periodView: TextView
    private lateinit var activityView: TextView
    private lateinit var levelView: TextView
    private lateinit var resultFlashView: TextView
    private lateinit var web: WebView
    private val bg = Color.rgb(8, 13, 25)
    private val blue = Color.rgb(18, 112, 255)
    private var lastFlashId = 0L
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A fresh app launch starts a new Telegram win-count session.
        prefs.edit()
            .putInt("telegram_wins_completed", 0)
            .putBoolean("telegram_predictions_paused", false)
            .putBoolean("stop_after_win_requested", false)
            .putBoolean("predictions_stopped", false)
            .apply()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        showLoginLinkSettings()
    }

    override fun onDestroy() {
        if (isFinishing) stopService(android.content.Intent(this, BdgMonitoringService::class.java))
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) {
            web.onResume()
            web.resumeTimers()
            web.postDelayed({
                if (::web.isInitialized) {
                    web.evaluateJavascript("window.__colorBotReadNow && window.__colorBotReadNow()", null)
                    refreshDashboard()
                }
            }, 300)
        }
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
                    runCatching {
                        android.webkit.CookieManager.getInstance().flush()
                        val monitorIntent = android.content.Intent(this@MainActivity, BdgMonitoringService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(monitorIntent) else startService(monitorIntent)
                    }
                    view.evaluateJavascript("""(function(){try{var sid='color-bot-bet-spacer';function findSubmit(){var a=Array.from(document.querySelectorAll('button,[role=button],.van-button,.bet-btn,.submit-btn'));return a.find(function(x){return /submit|place bet|confirm bet|bet now/i.test((x.innerText||x.getAttribute('aria-label')||'').trim());});}function makeRoom(){var b=findSubmit();if(!b)return;var panel=b.closest('.van-popup,[role=dialog],.bet-panel,.betting-panel,.bet-dialog')||(b.parentElement&&b.parentElement.parentElement);if(!panel)return;var r=b.getBoundingClientRect();var s=document.getElementById(sid);if(!s){s=document.createElement('div');s.id=sid;s.setAttribute('aria-hidden','true');s.style.cssText='display:block;width:100%;height:88px;min-height:88px;pointer-events:none;';panel.insertAdjacentElement('afterend',s);}var bottom=innerHeight-90;if(r.bottom>bottom||r.top<0){b.scrollIntoView({block:'center',behavior:'smooth'});}}document.addEventListener('click',function(e){var t=e.target&&e.target.closest&&e.target.closest('button,[role=button],.van-button');if(t&&/big|small|submit|bet|confirm/i.test((t.innerText||t.getAttribute('aria-label')||'').trim())){setTimeout(makeRoom,180);setTimeout(makeRoom,550);setTimeout(makeRoom,1000);}},true);document.addEventListener('input',function(){setTimeout(makeRoom,250);},true);if(window.__colorBotBetObserver)window.__colorBotBetObserver.disconnect();window.__colorBotBetObserver=new MutationObserver(function(){setTimeout(makeRoom,150);});window.__colorBotBetObserver.observe(document.body,{childList:true,subtree:true});}catch(e){}window.__colorBotReadNow=function(){try{if(window.AndroidBdg&&document.body){var pageText=document.body.innerText||'';var visible=function(x){var r=x.getBoundingClientRect(),s=getComputedStyle(x);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity)!==0;};var candidates=Array.from(document.querySelectorAll('[role=tab],[aria-selected="true"],[aria-pressed="true"],.van-tab--active,[class*="active"],[data-active="true"]')).filter(visible);var activeMode=candidates.filter(function(x){var t=(x.innerText||x.textContent||'').trim(),cl=String(x.className||'');return (/true/i.test(x.getAttribute('aria-selected')||'')||/true/i.test(x.getAttribute('aria-pressed')||'')||/active/i.test(cl)||x.getAttribute('data-active')==='true')&&/(?:30\s*(?:sec|s)|1\s*(?:min|m)|3\s*(?:min|m)|5\s*(?:min|m))/i.test(t);}).map(function(x){return (x.innerText||x.textContent||'').trim();}).sort(function(a,b){return a.length-b.length;})[0];if(activeMode)pageText+='\nACTIVE_GAME_MODE: '+activeMode;var rows=Array.from(document.querySelectorAll('tr,li,[class*="record"],[class*="history"],[class*="item"],[class*="row"]')).filter(visible);var normalized=rows.map(function(x){var t=(x.innerText||x.textContent||'').replace(/\s+/g,' ').trim();var ids=t.match(/\b\d{12,20}\b/g)||[],sides=t.match(/\b(?:BIG|SMALL)\b/gi)||[];if(ids.length!==1||sides.length!==1||t.length>240)return '';var digit=t.match(/\b[0-9]\b/);return ids[0]+' '+(digit?digit[0]+' ':'')+sides[0].toUpperCase();}).filter(Boolean);if(normalized.length)pageText+='\n'+normalized.join('\n');window.AndroidBdg.onPageText(pageText);}}catch(e){}};window.__colorBotReadNow();if(window.__colorBotReaderTimer)clearInterval(window.__colorBotReaderTimer);window.__colorBotReaderTimer=setInterval(window.__colorBotReadNow,2000);})();""", null)
                }
            }
            loadUrl(prefs.getString("bdg_url","https://bdgwin98.com") ?: "https://bdgwin98.com")
        }
        screen.addView(web, FrameLayout.LayoutParams(-1,-1))

        val dashboard=panel().apply {
            minimumHeight=dp(310)
            elevation=dp(12).toFloat()
        }
        val telegramBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(6), dp(2))
            setBackgroundColor(Color.WHITE)
        }
        val messageColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        telegramStatus = text(
            prefs.getString("telegram_status", "TELEGRAM • NOT CHECKED") ?: "TELEGRAM • NOT CHECKED",
            12f, Color.rgb(20, 82, 160), true
        ).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; maxLines = 1; setPadding(dp(4), dp(2), dp(4), 0) }
        liveReadMessage = text(
            prefs.getString("bdg_read_status", "BDG • Waiting for page data") ?: "BDG • Waiting for page data",
            11f, Color.rgb(20, 82, 160), false
        ).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; maxLines = 1; setPadding(dp(4), 0, dp(4), dp(2)) }
        messageColumn.addView(telegramStatus, LinearLayout.LayoutParams(-1, 0, 1f))
        messageColumn.addView(liveReadMessage, LinearLayout.LayoutParams(-1, 0, 1f))
        telegramBar.addView(messageColumn, LinearLayout.LayoutParams(0, dp(54), 1f))
        val startButton = TextView(this).apply {
            text = "▶"
            textSize = 19f
            setTextColor(Color.rgb(20, 130, 70))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(220, 252, 231))
            contentDescription = "Start predictions"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                prefs.edit()
                    .putBoolean("predictions_stopped", false)
                    .putBoolean("stop_after_win_requested", false)
                    .putBoolean("telegram_predictions_paused", false)
                    .putInt("telegram_wins_completed", 0)
                    .putBoolean("pending_telegram_bin", false)
                    .putString("current_prediction", "WAITING")
                    .putString("bot_status", "START REQUESTED • waiting for next BDG period")
                    .putString("recent_activity", "Prediction monitoring restarted")
                    .apply()
                val monitorIntent = android.content.Intent(this@MainActivity, BdgMonitoringService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(monitorIntent) else startService(monitorIntent)
                refreshDashboard("START REQUESTED • waiting for next BDG period")
                Toast.makeText(this@MainActivity, "Predictions started • waiting for next period", Toast.LENGTH_LONG).show()
            }
        }
        val startParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            leftMargin = dp(6)
            rightMargin = dp(8)
        }
        telegramBar.addView(startButton, startParams)

        val referButton = TextView(this).apply {
            text = "↗"
            textSize = 23f
            setTextColor(Color.rgb(18, 91, 190))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(219, 234, 254))
            contentDescription = "Send referral promotion to Telegram"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                TelegramReports.referral(this@MainActivity)
                Toast.makeText(this@MainActivity, "Referral promotion queued for Telegram", Toast.LENGTH_SHORT).show()
            }
        }
        val referParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            leftMargin = dp(14)
            rightMargin = dp(8)
        }
        telegramBar.addView(referButton, referParams)

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
        val foldButton = TextView(this).apply {
            text = "−"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(blue)
            contentDescription = "Minimize or expand dashboard"
            isClickable = true
            isFocusable = true
        }
        header.addView(foldButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        dashboard.addView(header)

        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(4),dp(5),dp(4),dp(2)) }
        status=text(prefs.getString("bdg_read_status","BDG PAGE OPEN • Waiting for readable game data") ?: "BDG PAGE OPEN • Waiting for readable game data",13f,Color.rgb(125,190,255),true)
        val hero=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),dp(2),dp(4),dp(2)) }
        val predictionColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL }
        predictionView=text("WAITING",30f,Color.rgb(34,211,119),true).apply { gravity=Gravity.START; setPadding(dp(5),dp(1),dp(5),dp(1)) }
        predictionColumn.addView(text("PREDICTION",12f,Color.rgb(125,190,255),true).apply { gravity=Gravity.START })
        predictionColumn.addView(predictionView)
        hero.addView(predictionColumn,LinearLayout.LayoutParams(0,-2,1.35f))
        val levelColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.END or Gravity.CENTER_VERTICAL }
        levelView=text("",14f,Color.rgb(180,215,255),true).apply { gravity=Gravity.END }
        levelColumn.addView(levelView)
        hero.addView(levelColumn,LinearLayout.LayoutParams(0,-2,1f))
        periodView=text("",13f,Color.rgb(180,215,255),false).apply { gravity=Gravity.CENTER; setPadding(dp(2),dp(4),dp(2),dp(2)) }
        resultFlashView=text("",14f,Color.WHITE,true).apply { setPadding(dp(8),dp(6),dp(8),dp(6)); visibility=View.GONE }
        activityView=text("",12f,Color.rgb(180,215,255),false).apply { gravity=Gravity.CENTER; setPadding(dp(2),dp(2),dp(2),dp(2)) }
        details.addView(status); details.addView(hero); details.addView(periodView); details.addView(resultFlashView); details.addView(activityView)
        dashboard.addView(details)
        refreshDashboard()
        foldButton.setOnClickListener {
            val folded = details.visibility == View.VISIBLE
            details.visibility = if (folded) View.GONE else View.VISIBLE
            dashboard.minimumHeight = if (folded) dp(0) else dp(310)
            foldButton.text = if (folded) "+" else "−"
        }

        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(telegramBar, LinearLayout.LayoutParams(-1, dp(64)))
            addView(dashboard, LinearLayout.LayoutParams(-1, -2))
        }
        screen.addView(topPanel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(screen)
    }

    private inner class BdgJavascriptBridge {
        @JavascriptInterface
        fun onPageText(pageText: String) {
            val snapshot = BdgPageParser.parse(pageText)
            val message = if (snapshot == null) {
                "BDG PAGE OPEN • Waiting for live period + 3 history results"
            } else if (snapshot.gameDuration == null) {
                "GAME MODE NOT CONFIRMED • Select 30 Sec / 1 Min / 3 Min / 5 Min"
            } else {
                BdgAutomationController.process(this@MainActivity, snapshot)
            }
            prefs.edit().putString("bdg_read_status", message).apply()
            runOnUiThread { refreshDashboard(message) }
        }
    }

    private fun refreshDashboard(statusMessage: String? = null) {
        if (::telegramStatus.isInitialized) {
            val telegram = prefs.getString("telegram_status", "TELEGRAM • NOT CHECKED") ?: "TELEGRAM • NOT CHECKED"
            telegramStatus.text = telegram
            telegramStatus.setTextColor(Color.rgb(20, 82, 160))
            liveReadMessage.text = prefs.getString("bdg_read_status", "BDG • Waiting for page data") ?: "BDG • Waiting for page data"
            liveReadMessage.setTextColor(Color.rgb(20, 82, 160))
        }
        if (::status.isInitialized) status.text = statusMessage ?: (prefs.getString("bdg_read_status","Waiting for BDG data") ?: "Waiting for BDG data")
        if (::predictionView.isInitialized) {
            val prediction = prefs.getString("current_prediction", "WAITING") ?: "WAITING"
            predictionView.text = prediction
            predictionView.setTextColor(if (prediction == "BIG" || prediction == "SMALL") Color.rgb(34,211,119) else Color.rgb(255,70,70))
            levelView.text = "LABEL  " + prefs.getInt("current_level",1)
            // Dashboard shows progression label, not internal strategy name.
        }
        if (::periodView.isInitialized) periodView.text = "GAME: " + (prefs.getString("active_game_duration", prefs.getString("game_duration", "—")) ?: "—") + "   •   PERIOD: " + (prefs.getString("current_period","—") ?: "—") + "   •   SKIP: " + prefs.getInt("engine_skip",0) + "/" + prefs.getInt("engine_skip_total",0)
        if (::activityView.isInitialized) activityView.text = "ACTIVITY: " + (prefs.getString("recent_activity","Waiting for first result") ?: "Waiting for first result")
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

    private fun showTelegramSettingsDialog() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(2)) }
        val token = EditText(this).apply {
            hint = "Telegram Bot Token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(prefs.getString("tg_token", "").orEmpty())
        }
        val chat = EditText(this).apply {
            hint = "Telegram Channel / Chat ID"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(prefs.getString("tg_chat", "").orEmpty())
        }
        val referral = EditText(this).apply {
            hint = "Referral Link"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(prefs.getString("referral_url", "https://bdgwin98.com/#/register?invitationCode=5152785394").orEmpty())
        }
        content.addView(token); content.addView(chat); content.addView(referral)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Telegram Bot Settings")
            .setMessage("Token और Chat ID सेव करने के बाद ऐप Telegram पर test message भेजकर कनेक्शन जाँचेगा।")
            .setView(content).setNegativeButton("CANCEL", null).setPositiveButton("SAVE & TEST", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = token.text.toString().trim(); val c = chat.text.toString().trim(); val ref = referral.text.toString().trim()
                if (t.isBlank() || c.isBlank()) {
                    prefs.edit().putString("telegram_status", "TOKEN/CHAT ID MISSING • SETTINGS").apply()
                    refreshDashboard()
                    Toast.makeText(this, "Bot Token और Chat ID दोनों भरें", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!ref.startsWith("https://")) { Toast.makeText(this, "Valid HTTPS referral link दर्ज करें", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                prefs.edit().putString("tg_token", t).putString("tg_chat", c).putString("referral_url", ref)
                    .putString("telegram_status", "CHECKING • Telegram test message भेज रहे हैं…").apply()
                refreshDashboard()
                TelegramNotifier.testConnection(t, c) { success, detail ->
                    runOnUiThread {
                        val state = if (success) "CONNECTED • Telegram test message delivered" else "FAILED • $detail"
                        prefs.edit().putString("telegram_status", state).apply()
                        refreshDashboard()
                        Toast.makeText(this, if (success) "Telegram कनेक्ट हो गया" else "Telegram error: $detail", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
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