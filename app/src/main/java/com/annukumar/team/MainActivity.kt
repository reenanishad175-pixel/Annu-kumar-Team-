package com.annukumar.team

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.widget.*
import android.text.InputType
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var panel: LinearLayout

    private val navy = Color.rgb(12, 24, 52)
    private val gold = Color.rgb(238, 190, 91)
    private val white = Color.WHITE

    private val prefs by lazy {
        getSharedPreferences("AKT_DEMO_DATA", MODE_PRIVATE)
    }

    private data class GameResult(
        val size: String,
        val color: String
    )

    private val history = mutableListOf<GameResult>()

    private var selectedSize = ""
    private var selectedColor = ""
    private var hourlyWins = 0
    private var savedHour = -1
    private var patternPaused = false

    private lateinit var strategySpinner: Spinner
    private lateinit var positionSpinner: Spinner
    private lateinit var patternSpinner: Spinner
    private lateinit var capSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var historyText: TextView
    private lateinit var screenText: TextView
    private lateinit var resultText: TextView

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        loadHistory()

        if (auth.currentUser != null) {
            showHome()
        } else {
            showLogin()
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun makeLayout() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(25), dp(18), dp(25))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(8, 17, 40),
                    Color.rgb(25, 46, 83),
                    Color.rgb(9, 20, 45)
                )
            )
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(25), dp(20), dp(25))
            background = rounded(Color.rgb(20, 37, 69), 22)
        }

        outer.addView(
            panel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        scroll.addView(outer)
        setContentView(scroll)
    }

    private fun addBrand() {
        addLabel("AKT", 32f, gold)
        addLabel("ANNU KUMAR TEAM", 22f, white)
        addLabel("MEMBER ACCESS", 11f, gold)
    }

    private fun addLabel(
        text: String,
        size: Float = 15f,
        color: Int = white
    ): TextView {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }

        panel.addView(view)
        return view
    }

    private fun heading(text: String) {
        val view = addLabel(text, 21f, gold)
        view.typeface = android.graphics.Typeface.DEFAULT_BOLD
        view.setPadding(0, dp(15), 0, dp(12))
    }

    private fun addInput(
        hintText: String,
        password: Boolean = false
    ): EditText {
        val field = EditText(this).apply {
            hint = hintText
            textSize = 16f
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)

            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_PHONE
            }

            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.WHITE, 12)
        }

        panel.addView(
            field,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        return field
    }

    private fun addButton(
        text: String,
        secondary: Boolean = false,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            this.text = text
            textSize = 14f
            isAllCaps = false
            setTextColor(if (secondary) gold else navy)

            background = if (secondary) {
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), gold)
                }
            } else {
                rounded(gold, 12)
            }

            setOnClickListener { action() }
        }

        panel.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun message(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun loginId(number: String): String? {
        var digits = number.filter { it.isDigit() }

        if (digits.startsWith("91") && digits.length == 12) {
            digits = digits.substring(2)
        }

        if (digits.length != 10 || digits[0] !in '6'..'9') {
            return null
        }

        return "$digits@users.annukumar.team"
    }

    // ---------------- LOGIN ----------------

    private fun showLogin() {
        makeLayout()
        addBrand()
        heading("लॉगिन करें")

        val phone = addInput("मोबाइल नंबर")
        val password = addInput("पासवर्ड", true)

        addButton("LOGIN • लॉगिन करें") {
            val id = loginId(phone.text.toString())
            val pass = password.text.toString()

            when {
                id == null ->
                    message("सही मोबाइल नंबर डालें")

                pass.isBlank() ->
                    message("पासवर्ड डालें")

                else -> auth.signInWithEmailAndPassword(id, pass)
                    .addOnSuccessListener {
                        message("लॉगिन सफल!")
                        showHome()
                    }
                    .addOnFailureListener {
                        message("लॉगिन नहीं हुआ: ${it.localizedMessage}")
                    }
            }
        }

        addButton("नई आईडी बनाएँ • REGISTER", true) {
            showRegister()
        }
    }

    // ---------------- REGISTER ----------------

    private fun showRegister() {
        makeLayout()
        addBrand()
        heading("नई आईडी बनाएँ")

        val phone = addInput("मोबाइल नंबर")
        val password = addInput("नया पासवर्ड", true)
        val confirm = addInput("पासवर्ड दोबारा डालें", true)

        addButton("REGISTER") {
            val id = loginId(phone.text.toString())
            val pass = password.text.toString()

            when {
                id == null ->
                    message("सही मोबाइल नंबर डालें")

                pass.length < 6 ->
                    message("पासवर्ड कम से कम 6 अक्षर का रखें")

                pass != confirm.text.toString() ->
                    message("दोनों पासवर्ड एक जैसे नहीं हैं")

                else -> auth.createUserWithEmailAndPassword(id, pass)
                    .addOnSuccessListener {
                        message("रजिस्ट्रेशन सफल!")
                        showHome()
                    }
                    .addOnFailureListener {
                        message("रजिस्ट्रेशन नहीं हुआ: ${it.localizedMessage}")
                    }
            }
        }

        addButton("लॉगिन पर वापस जाएँ", true) {
            showLogin()
        }
    }

    // ---------------- SPINNER ----------------

    private fun addSpinner(
        title: String,
        items: List<String>
    ): Spinner {
        addLabel(title)

        val spinner = Spinner(this)

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            items
        )

        panel.addView(
            spinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        return spinner
    }

    // ---------------- HISTORY STORAGE ----------------

    private fun loadHistory() {
        history.clear()

        try {
            val json = JSONArray(
                prefs.getString("history", "[]")
            )

            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)

                history.add(
                    GameResult(
                        item.optString("size"),
                        item.optString("color")
                    )
                )
            }
        } catch (_: Exception) {
            history.clear()
        }

        hourlyWins = prefs.getInt("hourlyWins", 0)
        savedHour = prefs.getInt("savedHour", -1)
        patternPaused = prefs.getBoolean("patternPaused", false)
    }

    private fun saveHistory() {
        val json = JSONArray()

        history.forEach {
            json.put(
                JSONObject().apply {
                    put("size", it.size)
                    put("color", it.color)
                }
            )
        }

        prefs.edit()
            .putString("history", json.toString())
            .putInt("hourlyWins", hourlyWins)
            .putInt("savedHour", savedHour)
            .putBoolean("patternPaused", patternPaused)
            .apply()
    }

    private fun updateHistory() {
        historyText.text = if (history.isEmpty()) {
            "अभी कोई रिजल्ट दर्ज नहीं है।"
        } else {
            history.mapIndexed { index, item ->
                "${index + 1}. ${item.size} / ${item.color}"
            }.joinToString("\n")
        }
    }

    // ---------------- RESULT ENTRY ----------------

    private fun saveResult() {
        if (selectedSize.isBlank() || selectedColor.isBlank()) {
            message("Big/Small और Red/Green दोनों चुनें")
            return
        }

        history.add(
            0,
            GameResult(selectedSize, selectedColor)
        )

        while (history.size > 10) {
            history.removeAt(history.lastIndex)
        }

        selectedSize = ""
        selectedColor = ""

        saveHistory()
        updateHistory()

        message("रिजल्ट History में सेव हो गया")
    }

    // ---------------- TIMER ----------------

    private fun sessionActive(): Boolean {
        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)

        return hour >= 9 && hour < 23
    }

    private fun resetHourlyCounter() {
        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)

        if (savedHour != hour) {
            savedHour = hour
            hourlyWins = 0
            saveHistory()
        }
    }

    // ---------------- STRATEGY ----------------

    private fun evaluateStrategy() {
        resetHourlyCounter()

        if (!sessionActive()) {
            resultText.text =
                "निर्धारित समय 9 AM से 11 PM है। अभी समय के बाहर है।"
            return
        }

        val cap = capSpinner.selectedItem.toString().toInt()

        if (hourlyWins >= cap) {
            resultText.text =
                "प्रति घंटे की जीत सीमा पूरी हो गई। NO BET"
            return
        }

        if (history.isEmpty()) {
            resultText.text =
                "पहले Game History में रिजल्ट दर्ज करें।"
            return
        }

        when (strategySpinner.selectedItemPosition) {

            // POSITION STRATEGY
            0 -> {
                val position =
                    positionSpinner.selectedItemPosition

                val item = history.getOrNull(position)

                resultText.text = if (item == null) {
                    "इस Position का रिजल्ट उपलब्ध नहीं है।"
                } else {
                    "Position ${position + 1}\n" +
                        "Size: ${item.size}\n" +
                        "Color: ${item.color}\n" +
                        "यह पिछला रिकॉर्ड है, अगला परिणाम नहीं।"
                }
            }

            // PATTERN STRATEGY
            1 -> {
                if (patternPaused) {
                    resultText.text =
                        "LOSS के बाद Pattern Strategy रुकी हुई है।"
                    return
                }

                val required =
                    patternSpinner.selectedItem.toString().toInt()

                val latest = history.first().size

                val streak = history
                    .takeWhile { it.size == latest }
                    .size

                resultText.text = if (streak >= required) {
                    "$streak लगातार $latest रिजल्ट मिले।\n" +
                        "Pattern मिला। अगला रिजल्ट निश्चित नहीं है।"
                } else {
                    "अभी $required लगातार समान रिजल्ट नहीं मिले।\nWAIT"
                }
            }

            // MAJORITY STRATEGY
            2 -> {
                val recent = history.take(10)

                val big = recent.count {
                    it.size == "BIG"
                }

                val small = recent.count {
                    it.size == "SMALL"
                }

                resultText.text = when {
                    big > small ->
                        "BIG बहुमत: $big बनाम $small"

                    small > big ->
                        "SMALL बहुमत: $small बनाम $big"

                    else ->
                        "BIG और SMALL बराबर हैं। NO BET"
                }
            }
        }
    }

    // ---------------- DEMO DASHBOARD ----------------

    private fun showHome() {
        makeLayout()
        addBrand()

        heading("BDG WIN • DEMO CONTROL")

        addLabel("लॉगिन सफल", 16f, gold)

        addLabel(
            auth.currentUser?.email ?: "",
            12f
        )

        heading("BDG DEMO CONNECTION")

        statusText = addLabel(
            "Accessibility Service की स्थिति जाँचें।"
        )

        addButton("ACCESSIBILITY SETTINGS खोलें") {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }

        addButton("BDG WIN खोलें") {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://bdgwin68.com/#/login")
                    )
                )
            } catch (_: Exception) {
                message("BDG वेबसाइट नहीं खुल सकी")
            }
        }

        addButton("स्क्रीन टेक्स्ट अपडेट करें", true) {
            refreshScreenText()
        }

        heading("स्क्रीन से पढ़ी गई जानकारी")

        screenText = addLabel(
            "अभी कोई स्क्रीन जानकारी उपलब्ध नहीं है।",
            13f
        )

        heading("STRATEGY CONTROL")

        strategySpinner = addSpinner(
            "Strategy चुनें",
            listOf(
                "Position Strategy",
                "Pattern Strategy",
                "Majority Strategy"
            )
        )

        positionSpinner = addSpinner(
            "Position चुनें",
            (1..10).map { "Position $it" }
        )

        positionSpinner.setSelection(4)

        patternSpinner = addSpinner(
            "लगातार कितने समान रिजल्ट?",
            listOf("2", "3", "4", "5")
        )

        patternSpinner.setSelection(1)

        capSpinner = addSpinner(
            "प्रति घंटे जीत की सीमा",
            listOf("3", "5")
        )

        heading("GAME HISTORY")

        historyText = addLabel(
            "अभी कोई रिजल्ट दर्ज नहीं है।",
            14f
        )

        updateHistory()

        addLabel("पहले Size चुनें")

        addButton("BIG") {
            selectedSize = "BIG"
            message("BIG चुना गया")
        }

        addButton("SMALL", true) {
            selectedSize = "SMALL"
            message("SMALL चुना गया")
        }

        addLabel("अब Color चुनें")

        addButton("RED") {
            selectedColor = "RED"
            message("RED चुना गया")
        }

        addButton("GREEN", true) {
            selectedColor = "GREEN"
            message("GREEN चुना गया")
        }

        addButton("रिजल्ट History में सेव करें") {
            saveResult()
        }

        heading("STRATEGY RESULT")

        resultText = addLabel(
            "Strategy Check दबाएँ।",
            15f
        )

        addButton("STRATEGY CHECK करें") {
            evaluateStrategy()
        }

        addButton("पिछली बाज़ी WIN हुई") {
            resetHourlyCounter()
            hourlyWins++
            saveHistory()

            message("जीत दर्ज हुई: $hourlyWins")
        }

        addButton("पिछली बाज़ी LOSS हुई") {
            if (strategySpinner.selectedItemPosition == 1) {
                patternPaused = true
            }

            saveHistory()

            resultText.text =
                "LOSS दर्ज हुई। Pattern Strategy रोक दी गई।"
        }

        addButton("Pattern फिर शुरू करें", true) {
            patternPaused = false
            saveHistory()
            message("Pattern Strategy फिर से चालू की गई")
        }

        addButton("History साफ करें", true) {
            history.clear()
            saveHistory()
            updateHistory()
            message("History साफ हो गई")
        }

        heading("TIME CONTROL")

        addLabel("सक्रिय समय: सुबह 9 बजे से रात 11 बजे तक")

        addLabel(
            if (sessionActive()) {
                "अभी निर्धारित समय के अंदर है।"
            } else {
                "अभी निर्धारित समय के बाहर है।"
            }
        )

        addLabel(
            "इस संस्करण में समय की जाँच होती है; " +
                "अपने-आप बैकग्राउंड Restart नहीं होता।",
            12f
        )

        heading("ACCOUNT SETTINGS")

        addButton("पासवर्ड बदलें", true) {
            showChangePassword()
        }

        addButton("LOGOUT", true) {
            auth.signOut()
            showLogin()
        }

        refreshScreenText()
    }

    // ---------------- SCREEN TEXT ----------------

    private fun refreshScreenText() {
        val connected =
            BdgAccessibilityService.serviceConnected

        statusText.text = if (connected) {
            "Accessibility Service: CONNECTED"
        } else {
            "Accessibility Service: NOT CONNECTED"
        }

        val text =
            BdgAccessibilityService.latestScreenText

        screenText.text = if (text.isBlank()) {
            "स्क्रीन से अभी टेक्स्ट नहीं मिला।"
        } else {
            text.take(3000)
        }
    }

    // ---------------- CHANGE PASSWORD ----------------

    private fun showChangePassword() {
        makeLayout()
        addBrand()
        heading("पासवर्ड बदलें")

        val oldPassword
