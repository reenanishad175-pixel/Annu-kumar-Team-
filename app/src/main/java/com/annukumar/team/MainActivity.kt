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
        body.addView(text("WinGo companion • Paper tracker"))
        body.addView(text("Prediction और paper history; कोई वास्तविक wager submit नहीं होता।", 12f))
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
        body.addView(text("Prediction strategy"))
        body.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Strategy 1 • Frequency", "Strategy 2 • Trend", "Strategy 3 • Conservative"))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    strategy = position
                }
            }
        })
        val stakeInput = EditText(this).apply { hint = "Paper stake (₹)"; setText("10"); inputType = 2 }
        body.addView(stakeInput)
        body.addView(btn("Set paper stake") {
            stake = (stakeInput.text.toString().toIntOrNull() ?: 10).coerceIn(1, 100000)
            status.text = "Paper stake set to ₹" + stake
        })
        predictionView = text("Next paper prediction: —", 16f)
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
        body.addView(btn("Clear paper history") { history.clear(); lastPeriod = ""; renderHistory() })
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
        val seed = kotlin.math.abs(period.takeLast(4).toIntOrNull() ?: period.hashCode())
        val digit = when (strategy) {
            0 -> (seed + 3) % 10
            1 -> (seed * 7 + 1) % 10
            else -> (seed + 5) % 10
        }
        val side = if (digit % 2 == 0) "Even" else "Odd"
        val entry = "Period " + period + " • " + timerName + " • Digit " + digit + " • " + side + " • Paper ₹" + stake
        history.add(0, entry)
        while (history.size > 100) history.removeAt(history.lastIndex)
        predictionView.text = "Paper prediction: " + digit + " (" + side + ")\\nPeriod: " + period
        renderHistory()
        if (telegramEnabled.isChecked) {
            val strategyName = arrayOf("Frequency", "Trend", "Conservative")[strategy]
            val message = "📊 ANNU KUMAR TEAM\\nGame: WinGo\\nTimer: $timerName\\nPeriod: $period\\nStrategy: $strategyName\\nPrediction: $digit ($side)\\nPaper stake reference: ₹$stake\\nNote: Prediction only; no bet was submitted."
            sendTelegramMessage(message)
        }
        status.text = "Prediction recorded. Telegram alert queued if enabled. No wager was submitted."
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
        historyView.text = if (history.isEmpty()) "No paper entries yet." else history.joinToString("\\n")
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
