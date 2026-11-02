package com.annukumar.team

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseException
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * BDG WIN companion with a local paper tracker.
 * This application does not submit wagers or automate betting controls.
 */
class MainActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val handler = Handler(Looper.getMainLooper())
    private var verificationId: String? = null
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var account: TextView
    private lateinit var periodView: TextView
    private lateinit var timerView: TextView
    private lateinit var predictionView: TextView
    private lateinit var historyView: TextView
    private lateinit var resultsInput: EditText
    private var sessionWins = 0
    private var level = 1
    private var consecutiveLosses = 0
    private var sessionEnded = false
    private lateinit var startButton: Button
    private lateinit var phone: EditText
    private lateinit var otp: EditText
    private lateinit var telegramToken: EditText
    private lateinit var telegramChatId: EditText
    private lateinit var telegramEnabled: CheckBox
    private val telegramExecutor = Executors.newSingleThreadExecutor()
    private var tracking = false
    private var lastPeriod = ""
    private var timerName = "Not detected"
    private var strategy = 0
    private var stake = 10
    private val history = mutableListOf<String>()
    private val prefs by lazy { getSharedPreferences("paper", MODE_PRIVATE) }

    private val poller = object : Runnable {
        override fun run() {
            readPage()
            if (tracking) handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        restoreTrackerState()
        setContentView(makeUi())
        renderHistory()
        predictionView.text = "Session WIN: $sessionWins/5 • Level: $level • Consecutive LOSS: $consecutiveLosses/8"
        predictionView.text = "Session WIN: $sessionWins/5 • Level: $level • Consecutive LOSS: $consecutiveLosses/8"
        SessionScheduler.scheduleAll(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 701)
        }
        web.loadUrl(prefs.getString("bdg_url", "https://bdgwin68.com/#/login")!!)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float = 14f) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(Color.rgb(35, 45, 60))
    }
    private fun btn(s: String, f: () -> Unit) = Button(this).apply {
        text = s
        setOnClickListener { f() }
    }

    private lateinit var loginPanel: LinearLayout
    private lateinit var dashboardPanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var appPhone: EditText

    private fun makeUi(): View {
        val navy = Color.rgb(10, 16, 30)
        val panel = Color.rgb(20, 29, 48)
        val accent = Color.rgb(38, 211, 174)
        val muted = Color.rgb(158, 174, 196)
        fun heading(s: String, size: Float = 18f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(5))
        }
        fun note(s: String) = TextView(this).apply {
            text = s; textSize = 13f; setTextColor(muted)
            setPadding(0, dp(3), 0, dp(8))
        }
        fun card() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(panel)
        }
        fun action(s: String, f: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; textSize = 14f
            setTextColor(navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent))
            setOnClickListener { f() }
        }
        fun field(hintText: String, single: Boolean = true) = EditText(this).apply {
            hint = hintText; setTextColor(Color.WHITE); setHintTextColor(muted)
            setSingleLine(single); textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(navy)
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        val brand = TextView(this).apply {
            text = "ANNu  /  TEAM"; textSize = 25f; setTextColor(accent)
            setPadding(0, dp(8), 0, dp(2))
        }
        body.addView(brand)
        body.addView(note("WinGo companion  •  Paper dashboard"))

        loginPanel = card()
        loginPanel.addView(heading("आपका मोबाइल नंबर", 20f))
        loginPanel.addView(note("यह ऐप आपके फोन पर लोकल प्रोफाइल बनाएगा। पासवर्ड या अलग अकाउंट की जरूरत नहीं।"))
        appPhone = field("मोबाइल नंबर, जैसे +91XXXXXXXXXX")
        appPhone.inputType = 3
        loginPanel.addView(appPhone)
        loginPanel.addView(action("आगे बढ़ें") {
            val number = appPhone.text.toString().trim()
            if (number.replace(Regex("\\D"), "").length < 10) {
                status.text = "कृपया सही मोबाइल नंबर डालें।"
            } else {
                prefs.edit().putString("app_phone", number).apply()
                showDashboard()
            }
        })
        body.addView(loginPanel)

        dashboardPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val welcome = card()
        welcome.addView(heading("आपका डैशबोर्ड", 21f))
        account = note("मोबाइल: " + (prefs.getString("app_phone", "") ?: ""))
        welcome.addView(account)
        status = note("तैयार है। नीचे से BDG इतिहास पढ़ें या सेटिंग खोलें।")
        welcome.addView(status)
        welcome.addView(action("⚙  BOT और ऐप सेटिंग्स") {
            settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        dashboardPanel.addView(welcome)

        settingsPanel = card().apply { visibility = View.GONE }
        settingsPanel.addView(heading("Telegram Bot सेटिंग्स"))
        settingsPanel.addView(note("Bot Token और Chat ID केवल इसी फोन में सेव होंगे। इन्हें किसी से साझा न करें।"))
        telegramToken = field("Telegram Bot Token")
        telegramToken.inputType = 129
        telegramToken.setText(prefs.getString("telegram_bot_token", ""))
        telegramChatId = field("Telegram Chat ID")
        telegramChatId.setText(prefs.getString("telegram_chat_id", ""))
        telegramEnabled = CheckBox(this).apply {
            text = "Telegram alerts चालू करें"; setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("telegram_enabled", false)
        }
        settingsPanel.addView(telegramToken)
        settingsPanel.addView(telegramChatId)
        settingsPanel.addView(telegramEnabled)
        settingsPanel.addView(action("सेटिंग सेव करें") { saveTelegramSettings() })
        settingsPanel.addView(action("टेस्ट मैसेज भेजें") {
            saveTelegramSettings()
            sendTelegramMessage("Annu Kumar Team: Telegram test message.")
        })
        settingsPanel.addView(action("मोबाइल नंबर बदलें") {
            prefs.edit().remove("app_phone").apply()
            dashboardPanel.visibility = View.GONE
            loginPanel.visibility = View.VISIBLE
            appPhone.setText("")
        })
        dashboardPanel.addView(settingsPanel)

        val siteCard = card()
        siteCard.addView(heading("BDG WIN"))
        siteCard.addView(note("वेबसाइट में अपना सामान्य लॉगिन स्वयं करें। यह ऐप कोई wager submit या auto-click नहीं करता।"))
        siteCard.addView(action("वेबसाइट रीफ्रेश करें") {
            web.loadUrl(prefs.getString("bdg_url", "https://bdgwin68.com/#/login")!!)
        })
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    status.text = "BDG वेबसाइट खुल गई। WinGo History खोलकर नीचे Read History दबाएँ।"
                    readPage()
                }
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(330))
            setBackgroundColor(panel)
        }
        siteCard.addView(web)
        dashboardPanel.addView(siteCard)

        val historyCard = card()
        historyCard.addView(heading("HISTORY READER"))
        timerView = note("Website timer: Not detected")
        periodView = note("Period: Not detected")
        historyCard.addView(timerView)
        historyCard.addView(periodView)
        historyCard.addView(action("READ HISTORY") { readPage() })
        dashboardPanel.addView(historyCard)

        val analyzerCard = card()
        analyzerCard.addView(heading("PAPER ANALYSIS"))
        analyzerCard.addView(note("पुराने से नए क्रम में अधिकतम 10 BIG / SMALL परिणाम डालें। यह केवल अवलोकन है, भविष्यवाणी की गारंटी नहीं।"))
        resultsInput = field("BIG SMALL BIG SMALL ...", false)
        resultsInput.minLines = 2
        analyzerCard.addView(resultsInput)
        analyzerCard.addView(action("ANALYZE") { analyzePaperPattern() })
        predictionView = note("अभी कोई पेपर विश्लेषण नहीं।")
        analyzerCard.addView(predictionView)
        dashboardPanel.addView(analyzerCard)

        val trackerCard = card()
        trackerCard.addView(heading("DEMO TRACKER"))
        trackerCard.addView(note("केवल रिकॉर्ड रखने के लिए — वास्तविक पैसे या wager नहीं।"))
        trackerCard.addView(action("DEMO WIN दर्ज करें") { recordDemoResult(true) })
        trackerCard.addView(action("DEMO LOSS दर्ज करें") { recordDemoResult(false) })
        startButton = action("पेपर ट्रैकर शुरू करें") { toggleTracker() }
        trackerCard.addView(startButton)
        trackerCard.addView(action("डेमो सेशन रीसेट करें") {
            history.clear(); lastPeriod = ""; sessionWins = 0; level = 1
            consecutiveLosses = 0; sessionEnded = false
            persistTrackerState(); renderHistory()
            predictionView.text = "Demo session reset."
        })
        historyView = note("अभी कोई रिकॉर्ड नहीं।")
        trackerCard.addView(heading("हाल की गतिविधि", 16f))
        trackerCard.addView(historyView)
        dashboardPanel.addView(trackerCard)
        body.addView(dashboardPanel)

        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val savedPhone = prefs.getString("app_phone", "") ?: ""
        if (savedPhone.isNotBlank()) showDashboard()
        return outer
    }

    private fun showDashboard() {
        loginPanel.visibility = View.GONE
        dashboardPanel.visibility = View.VISIBLE
        account.text = "मोबाइल: " + (prefs.getString("app_phone", "") ?: "")
        if (!::web.isInitialized || web.url == null) {
            if (::web.isInitialized) web.loadUrl(prefs.getString("bdg_url", "https://bdgwin68.com/#/login")!!)
        }
    }

    private fun updateAccount() {
        val user = auth.currentUser
        account.text = if (user == null) "Firebase: signed out / guest available"
            else "Firebase phone: " + (user.phoneNumber ?: "verified account")
    }

    private fun sendOtp() {
        val number = phone.text.toString().trim()
        if (!number.startsWith("+") || number.length < 10) {
            status.text = "Enter full phone number with country code."
            return
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    auth.signInWithCredential(credential).addOnCompleteListener {
                        updateAccount()
                        status.text = if (it.isSuccessful) "Phone verification successful." else "Login failed."
                    }
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    status.text = "OTP failed: " + (e.localizedMessage ?: "Check Firebase Phone Auth setup.")
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    otp.visibility = View.VISIBLE
                    status.text = "SMS OTP sent."
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyOtp() {
        val id = verificationId
        if (id == null) { status.text = "Send OTP first."; return }
        val code = otp.text.toString().trim()
        if (code.length < 4) { status.text = "Enter the SMS OTP."; return }
        auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code)).addOnCompleteListener {
            if (it.isSuccessful) { updateAccount(); status.text = "Phone login successful." }
            else status.text = "Login failed: " + (it.exception?.localizedMessage ?: "Unknown error")
        }
    }

    /**
     * Extract each history item from its own rendered DOM container.
     * This avoids joining a period from one row to a result in another row.
     */
    private fun readPage() {
        if (!::web.isInitialized) return
        val script = """(function(){
          const visible = e => !!(e && e.getClientRects().length);
          const periodRe = /\b\d{15,24}\b/;
          const seen = new Set(), rows = [];
          const leaves = Array.from(document.querySelectorAll('body *'))
            .filter(e => visible(e) && e.children.length === 0);
          for (const leaf of leaves) {
            const pm = (leaf.innerText || leaf.textContent || '').trim().match(periodRe);
            if (!pm || seen.has(pm[0])) continue;
            let p = leaf;
            for (let depth = 0; p && depth < 9; depth++, p = p.parentElement) {
              if (!visible(p)) continue;
              const t = (p.innerText || p.textContent || '').replace(/\s+/g, ' ').trim();
              if (!periodRe.test(t)) continue;
              const rest = t.replace(pm[0], ' ');
              const om = /\b(BIG|SMALL)\b/i.exec(rest);
              if (!om) continue;
              const nm = /(?:^|\D)([0-9])(?:\D|$)/.exec(rest.slice(0, om.index));
              if (!nm) continue;
              const number = Number(nm[1]), outcome = om[1].toUpperCase();
              if ((number <= 4 ? 'SMALL' : 'BIG') !== outcome) break;
              rows.push({period: pm[0], number: number, outcome: outcome});
              seen.add(pm[0]);
              break;
            }
          }
          return JSON.stringify({rows: rows.slice(0,10)});
        })()"""
        web.evaluateJavascript(script) { raw ->
            try {
                val decoded = JSONArray("[" + (raw ?: "{}") + "]").getJSONObject(0)
                inspectRows(decoded.optJSONArray("rows") ?: JSONArray())
            } catch (_: Exception) {
                status.text = "History data could not be decoded. Reload BDG and open WinGo history."
            }
        }
    }

    private fun inspectRows(rows: JSONArray) {
        val validRows = mutableListOf<Triple<String, Int, String>>()
        val seen = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val period = row.optString("period")
            val number = row.optInt("number", -1)
            val outcome = row.optString("outcome").uppercase()
            if (!period.matches(Regex("\\d{15,24}")) || number !in 0..9) continue
            if (outcome != (if (number <= 4) "SMALL" else "BIG")) continue
            if (seen.add(period)) validRows.add(Triple(period, number, outcome))
            if (validRows.size == 10) break
        }
        if (validRows.isEmpty()) {
            periodView.text = "Period: Not detected"
            status.text = "No complete history rows detected. Open WinGo game history and tap Read website now."
            return
        }
        val newest = validRows.first()
        periodView.text = "Period: " + newest.first
        if (tracking && newest.first != lastPeriod) {
            lastPeriod = newest.first
            persistTrackerState()
            addPaperPrediction(newest.first)
        }
        resultsInput.setText(validRows.asReversed().joinToString(" ") { it.third })
        status.text = "Complete history rows detected: ${validRows.size}/10. Results are ordered oldest to newest. Scroll/load history if fewer than 10 appear."
    }

    private fun addPaperPrediction(period: String) {
        // Do not manufacture a prediction from the period number. The current page
        // reader has not yet been validated against BDG WIN's actual result history.
        // Keep the period visible locally, but wait for verified BIG/SMALL outcomes
        // before generating a signal or publishing anything to Telegram.
        val entry = "Period " + period + " • " + timerName + " • Awaiting verified result history"
        history.add(0, entry)
        while (history.size > 100) history.removeAt(history.lastIndex)
        persistTrackerState()
        predictionView.text = "Prediction: WAITING FOR VERIFIED RESULTS\\nPeriod: " + period
        renderHistory()
        status.text = "Period detected. Prediction and Telegram signal are paused until actual BIG/SMALL result history can be verified."
    }

    private fun analyzePaperPattern() {
        if (sessionEnded) {
            status.text = "Session ended. Start a new session after reviewing the results."
            return
        }
        val summary = PatternAnalyzer.analyze(resultsInput.text.toString())
        val period = if (lastPeriod.isNotBlank()) lastPeriod else "Not detected"
        val signal = summary.signal
        val message = if (signal == null) {
            "Game: WinGo\nTimer: $timerName\nPeriod: $period\nBET SKIP"
        } else {
            "Game: WinGo\nTimer: $timerName\nPeriod: $period\n$signal"
        }
        predictionView.text = if (signal == null) "BET SKIP\n${summary.reason}" else
            "DEMO OBSERVATION: $signal\nLevel: $level • Period: $period\n${summary.reason}"
        history.add(0, "BIG: ${summary.big} | SMALL: ${summary.small} | ${message.replace("\n", " • ")}")
        while (history.size > 100) history.removeAt(history.lastIndex)
        persistTrackerState()
        renderHistory()
        status.text = "Paper analysis only. Recent results do not predict or guarantee the next outcome."
        if (telegramEnabled.isChecked) sendTelegramMessage(message)
    }

    private fun recordDemoResult(won: Boolean) {
        if (sessionEnded) {
            status.text = "Session already ended. Clear history to reset the local demo tracker."
            return
        }
        if (won) {
            sessionWins++
            consecutiveLosses = 0
            level = 1
            history.add(0, "DEMO RESULT: WIN • Session wins $sessionWins/5 • Level reset to 1")
            if (sessionWins >= 5) {
                sessionEnded = true
                status.text = "Demo session complete: 5 recorded wins."
                if (telegramEnabled.isChecked) {
                    sendTelegramMessage("WinGo demo session ended: 5 recorded WINs. Thank you.\nJoin our team: https://bdgwin98.com/#/register?invitationCode=5152785394")
                }
            } else {
                status.text = "Demo WIN recorded. Total: $sessionWins/5. Level reset to 1."
                if (telegramEnabled.isChecked) sendTelegramMessage("WIN")
            }
        } else {
            consecutiveLosses++
            level++
            history.add(0, "DEMO RESULT: LOSS • Level $level • Consecutive losses $consecutiveLosses/8")
            if (consecutiveLosses >= 8 || level > 8) {
                sessionEnded = true
                status.text = "Demo session cancelled after 8 consecutive losses."
                if (telegramEnabled.isChecked) sendTelegramMessage("आज का demo session यहीं रोक रहे हैं। लगातार 8 परिणाम अनुकूल नहीं रहे। कृपया नुकसान की भरपाई के लिए जल्दबाज़ी न करें।")
            } else {
                status.text = "Demo LOSS recorded. Next level: $level."
                if (telegramEnabled.isChecked) sendTelegramMessage("LOSS")
            }
        }
        predictionView.text = "Session WIN: $sessionWins/5 • Level: $level • Consecutive LOSS: $consecutiveLosses/8"
        persistTrackerState()
        renderHistory()
    }

    private fun restoreTrackerState() {
        sessionWins = prefs.getInt("session_wins", 0)
        level = prefs.getInt("session_level", 1).coerceIn(1, 9)
        consecutiveLosses = prefs.getInt("consecutive_losses", 0).coerceIn(0, 8)
        sessionEnded = prefs.getBoolean("session_ended", false)
        lastPeriod = prefs.getString("last_period", "") ?: ""
        val savedHistory = prefs.getString("paper_history", null)
        if (!savedHistory.isNullOrBlank()) {
            try {
                val array = JSONArray(savedHistory)
                for (i in 0 until array.length()) history.add(array.getString(i))
            } catch (_: Exception) { history.clear() }
        }
    }

    private fun persistTrackerState() {
        prefs.edit()
            .putInt("session_wins", sessionWins)
            .putInt("session_level", level)
            .putInt("consecutive_losses", consecutiveLosses)
            .putBoolean("session_ended", sessionEnded)
            .putString("last_period", lastPeriod)
            .putString("paper_history", JSONArray(history.take(100)).toString())
            .apply()
    }

    private fun saveTelegramSettings() {
        prefs.edit()
            .putString("telegram_bot_token", telegramToken.text.toString().trim())
            .putString("telegram_chat_id", telegramChatId.text.toString().trim())
            .putBoolean("telegram_enabled", telegramEnabled.isChecked)
            .apply()
        status.text = "Telegram settings saved on this device."
    }

    private fun sendTelegramMessage(message: String) {
        val token = telegramToken.text.toString().trim()
        val chatId = telegramChatId.text.toString().trim()
        if (token.isBlank() || chatId.isBlank()) {
            status.text = "Telegram Bot Token और Chat ID भरें।"
            return
        }
        telegramExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = URL("https://api.telegram.org/bot" + token + "/sendMessage")
                connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                val payload = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                    "&text=" + URLEncoder.encode(message, "UTF-8")
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                val ok = code in 200..299 && JSONObject(response).optBoolean("ok", false)
                runOnUiThread {
                    status.text = if (ok) "Telegram message sent." else "Telegram send failed (HTTP $code). Check bot token, Chat ID and start the bot."
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Telegram error: " + (e.localizedMessage ?: "network error") }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun toggleTracker() {
        tracking = !tracking
        if (tracking) {
            startButton.text = "STOP PAPER TRACKER"
            handler.removeCallbacks(poller)
            handler.post(poller)
            readPage()
            status.text = "Paper tracker started. New readable periods create local paper entries."
        } else {
            startButton.text = "START PAPER TRACKER"
            handler.removeCallbacks(poller)
            status.text = "Paper tracker stopped."
        }
    }

    private fun renderHistory() {
        historyView.text = if (history.isEmpty()) "No paper entries yet." else history.joinToString("\n")
    }

    override fun onPause() { super.onPause(); if (::web.isInitialized) web.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        telegramExecutor.shutdown()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
