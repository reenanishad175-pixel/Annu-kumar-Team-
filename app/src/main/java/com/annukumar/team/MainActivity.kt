package com.annukumar.team

import android.app.Activity
import android.os.Bundle
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
        setContentView(makeUi())
        updateAccount()
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

    private fun makeUi(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            setBackgroundColor(Color.rgb(245, 247, 251))
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        body.addView(text("ANNU KUMAR TEAM", 24f))
        body.addView(text("WinGo companion • Dashboard + demo tracker"))
        body.addView(text("Dashboard, Telegram alerts और demo tracking. कोई वास्तविक wager submit नहीं होता।", 12f))
        account = text("Firebase account: checking…")
        body.addView(account)
        phone = EditText(this).apply { hint = "Phone number with country code, e.g. +91"; inputType = 3 }
        otp = EditText(this).apply { hint = "SMS OTP"; inputType = 2; visibility = View.GONE }
        body.addView(phone)
        body.addView(otp)
        body.addView(btn("Send SMS OTP") { sendOtp() })
        body.addView(btn("Verify OTP / Login") { verifyOtp() })
        body.addView(btn("Continue as Guest") { status.text = "Guest mode active." })
        body.addView(text("BDG WIN", 18f))
        status = text("Loading BDG WIN…")
        body.addView(status)
        body.addView(btn("Reload BDG WIN") { web.loadUrl(prefs.getString("bdg_url", "https://bdgwin68.com/#/login")!!) })
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    status.text = "Website loaded. Select the game and period on BDG WIN."
                    readPage()
                }
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(360))
        }
        body.addView(web)
        body.addView(text("PAGE INFORMATION", 18f))
        timerView = text("Website timer: Not detected")
        periodView = text("Period: Not detected")
        body.addView(timerView)
        body.addView(periodView)
        body.addView(text("10-PERIOD PAPER PATTERN CHECK", 18f))
        body.addView(text("पिछले परिणाम पुराने से नए क्रम में लिखें; उदाहरण: BIG SMALL BIG SMALL. अधिकतम आखिरी 10 परिणाम लिए जाएँगे।", 12f))
        resultsInput = EditText(this).apply {
            hint = "BIG, SMALL, BIG, SMALL..."
            setSingleLine(false)
            minLines = 2
        }
        body.addView(resultsInput)
        body.addView(btn("ANALYZE PAPER PATTERN") { analyzePaperPattern() })
        body.addView(text("SESSION TRACKER", 18f))
        body.addView(text("WIN मिलने पर Level 1 से शुरू होगा। 8 लगातार LOSS या 5 कुल WIN पर session रुकता है।"))
        body.addView(btn("Mark demo result: WIN") { recordDemoResult(true) })
        body.addView(btn("Mark demo result: LOSS") { recordDemoResult(false) })
        predictionView = text("Demo observation: waiting for verified result history", 16f)
        body.addView(predictionView)
        body.addView(text("TELEGRAM PREDICTION ALERTS", 18f))
        body.addView(text("Bot token और Chat ID डालें। ये केवल इस फोन की private app preferences में सेव होंगे.", 12f))
        telegramToken = EditText(this).apply {
            hint = "Telegram Bot Token"
            setSingleLine(true)
            setText(prefs.getString("telegram_bot_token", ""))
        }
        telegramChatId = EditText(this).apply {
            hint = "Telegram Chat ID"
            setSingleLine(true)
            setText(prefs.getString("telegram_chat_id", ""))
        }
        telegramEnabled = CheckBox(this).apply {
            text = "हर नई Prediction Telegram पर भेजें"
            isChecked = prefs.getBoolean("telegram_enabled", false)
            setTextColor(Color.rgb(35, 45, 60))
        }
        body.addView(telegramToken)
        body.addView(telegramChatId)
        body.addView(telegramEnabled)
        body.addView(btn("Save Telegram Settings") { saveTelegramSettings() })
        body.addView(btn("Send Telegram Test Message") { saveTelegramSettings(); sendTelegramMessage("Annu Kumar Team: Telegram test message. Alerts are connected.") })
        startButton = btn("START PAPER TRACKER") { toggleTracker() }
        body.addView(startButton)
        body.addView(btn("Read website now") { readPage() })
        body.addView(btn("Clear / reset demo session") { history.clear(); lastPeriod = ""; sessionWins = 0; level = 1; consecutiveLosses = 0; sessionEnded = false; renderHistory(); predictionView.text = "Demo session reset." })
        body.addView(text("PAPER HISTORY", 18f))
        historyView = text("No paper entries yet.")
        body.addView(historyView)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return outer
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

    private fun readPage() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("(function(){return JSON.stringify({text:(document.body?document.body.innerText:'').slice(0,15000)});})()") { raw ->
            try {
                val decoded = JSONObject(raw?.trim('"')?.replace("\\\\\"", "\"") ?: "{}")
                inspectText(decoded.optString("text"))
            } catch (_: Exception) {
                status.text = "Could not read visible website text."
            }
        }
    }

    private fun inspectText(rawText: String) {
        val content = rawText.replace(Regex("\\s+"), " ")
        val period = Regex("\\b\\d{8,20}\\b").findAll(content).lastOrNull()?.value ?: ""
        timerName = when {
            Regex("30\\s*(s|sec|seconds)", RegexOption.IGNORE_CASE).containsMatchIn(content) -> "30S"
            Regex("1\\s*(m|min|minute)", RegexOption.IGNORE_CASE).containsMatchIn(content) -> "1M"
            Regex("3\\s*(m|min|minute)", RegexOption.IGNORE_CASE).containsMatchIn(content) -> "3M"
            else -> timerName
        }
        timerView.text = "Website timer: " + timerName
        if (period.isNotEmpty()) {
            periodView.text = "Period: " + period
            if (tracking && period != lastPeriod) {
                lastPeriod = period
                addPaperPrediction(period)
            }
        } else {
            status.text = "Website is open. Period label was not detected in readable page text."
        }
    }

    private fun addPaperPrediction(period: String) {
        // Do not manufacture a prediction from the period number. The current page
        // reader has not yet been validated against BDG WIN's actual result history.
        // Keep the period visible locally, but wait for verified BIG/SMALL outcomes
        // before generating a signal or publishing anything to Telegram.
        val entry = "Period " + period + " • " + timerName + " • Awaiting verified result history"
        history.add(0, entry)
        while (history.size > 100) history.removeAt(history.lastIndex)
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
            "Game: WinGo\\nTimer: $timerName\\nPeriod: $period\\nBET SKIP"
        } else {
            "Game: WinGo\\nTimer: $timerName\\nPeriod: $period\\nLevel: $level\\nPrediction: $signal"
        }
        predictionView.text = if (signal == null) "BET SKIP\\n${summary.reason}" else
            "DEMO OBSERVATION: $signal\\nLevel: $level • Period: $period\\n${summary.reason}"
        history.add(0, "BIG: ${summary.big} | SMALL: ${summary.small} | ${message.replace("\\n", " • ")}")
        while (history.size > 100) history.removeAt(history.lastIndex)
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
                if (telegramEnabled.isChecked) sendTelegramMessage("WinGo demo session ended: 5 recorded WINs. Thank you.")
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
        renderHistory()
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
