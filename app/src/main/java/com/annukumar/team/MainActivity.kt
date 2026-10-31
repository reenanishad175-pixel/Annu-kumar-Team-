package com.annukumar.team

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var panel: LinearLayout

    private val navy = Color.rgb(12, 24, 52)
    private val gold = Color.rgb(238, 190, 91)
    private val white = Color.WHITE

    private val history = mutableListOf<String>()
    private var strategyPaused = false
    private var hourlyWins = 0
    private var winHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private lateinit var strategySpinner: Spinner
    private lateinit var patternSpinner: Spinner
    private lateinit var positionSpinner: Spinner
    private lateinit var capSpinner: Spinner
    private lateinit var resultText: TextView
    private lateinit var historyText: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) showHome() else showLogin()
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun makeLayout() {
        val scroll = ScrollView(this)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(24), dp(18), dp(24))
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
            setPadding(dp(20), dp(24), dp(20), dp(24))
            background = rounded(Color.rgb(20, 37, 69), 22)
        }

        outer.addView(panel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        scroll.addView(outer)
        setContentView(scroll)
    }

    private fun label(text: String, size: Float = 15f) {
        panel.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    private fun heading(text: String) {
        panel.addView(TextView(this).apply {
            this.text = text
            textSize = 21f
            setTextColor(gold)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, dp(14))
        })
    }

    private fun input(hintText: String, password: Boolean = false): EditText {
        val field = EditText(this).apply {
            hint = hintText
            textSize = 16f
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            inputType = if (password) 129 else 3
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.WHITE, 12)
        }
        panel.addView(field, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { bottomMargin = dp(12) })
        return field
    }

    private fun button(
        text: String,
        secondary: Boolean = false,
        action: () -> Unit
    ) {
        val b = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(if (secondary) gold else navy)
            background = if (secondary)
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), gold)
                }
            else rounded(gold, 12)
            setOnClickListener { action() }
        }

        panel.addView(b, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { bottomMargin = dp(9) })
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun brand() {
        label("AKT", 32f)
        label("ANNU KUMAR TEAM", 22f)
        label("MEMBER ACCESS", 11f)
    }

    private fun loginId(number: String): String? {
        var digits = number.filter { it.isDigit() }
        if (digits.startsWith("91") && digits.length == 12)
            digits = digits.substring(2)

        if (digits.length != 10 || digits[0] !in '6'..'9')
            return null

        return "$digits@users.annukumar.team"
    }

    private fun showLogin() {
        makeLayout()
        brand()
        heading("लॉगिन करें")

        val phone = input("मोबाइल नंबर")
        val pass = input("पासवर्ड", true)

        button("LOGIN • लॉगिन करें") {
            val id = loginId(phone.text.toString())
            if (id == null) toast("सही मोबाइल नंबर डालें")
            else auth.signInWithEmailAndPassword(id, pass.text.toString())
                .addOnSuccessListener { showHome() }
                .addOnFailureListener {
                    toast("लॉगिन नहीं हुआ: ${it.localizedMessage}")
                }
        }

        button("नई आईडी बनाएँ • REGISTER", true) {
            showRegister()
        }
    }

    private fun showRegister() {
        makeLayout()
        brand()
        heading("नई आईडी बनाएँ")

        val phone = input("मोबाइल नंबर")
        val pass = input("नया पासवर्ड", true)
        val confirm = input("पासवर्ड दोबारा डालें", true)

        button("REGISTER") {
            val id = loginId(phone.text.toString())
            when {
                id == null -> toast("सही मोबाइल नंबर डालें")
                pass.text.length < 6 -> toast("पासवर्ड कम से कम 6 अक्षर का रखें")
                pass.text.toString() != confirm.text.toString() ->
                    toast("दोनों पासवर्ड एक जैसे नहीं हैं")
                else -> auth.createUserWithEmailAndPassword(
                    id, pass.text.toString()
                ).addOnSuccessListener {
                    showHome()
                }.addOnFailureListener {
                    toast("रजिस्ट्रेशन नहीं हुआ: ${it.localizedMessage}")
                }
            }
        }

        button("लॉगिन पर वापस जाएँ", true) { showLogin() }
    }

    private fun spinner(
        title: String,
        values: List<String>
    ): Spinner {
        label(title)
        val s = Spinner(this)
        s.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            values
        )
        panel.addView(s, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ))
        return s
    }

    private fun isSessionActive(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 9 && hour < 23
    }

    private fun resetHourlyCounterIfNeeded() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour != winHour) {
            winHour = hour
            hourlyWins = 0
        }
    }

    private fun showHome() {
        makeLayout()
        brand()
        heading("BDG WIN • DEMO CONTROL")

        label("लॉगिन सफल: ${auth.currentUser?.email ?: ""}")
        label("केवल Demo अकाउंट इस्तेमाल करें।")

        button("BDG WIN खोलें") {
            try {
                startActivity(Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://bdgwin68.com/#/login")
                ))
            } catch (e: Exception) {
                toast("वेबसाइट नहीं खुल सकी")
            }
        }

        heading("स्ट्रेटेजी सेटिंग")

        strategySpinner = spinner(
            "स्ट्रेटेजी चुनें",
            listOf("Position Strategy", "Pattern Strategy", "Majority Strategy")
        )

        positionSpinner = spinner(
            "Position चुनें (पिछले 10 रिजल्ट में)",
            (1..10).map { "Position $it" }
        )
        positionSpinner.setSelection(4)

        patternSpinner = spinner(
            "Pattern में लगातार कितने रिजल्ट?",
            listOf("2", "3", "4", "5")
        )
        patternSpinner.setSelection(1)

        capSpinner = spinner(
            "प्रति घंटे अधिकतम जीत",
            listOf("3", "5")
        )

        heading("Game History")
        historyText = TextView(this).apply {
            text = "अभी कोई रिजल्ट दर्ज नहीं है।"
            textSize = 14f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(12))
        }
        panel.addView(historyText)

        label("नया रिजल्ट दर्ज करें")
        button("BIG") { addResult("BIG") }
        button("SMALL", true) { addResult("SMALL") }
        button("RED") { addResult("RED") }
        button("GREEN", true) { addResult("GREEN") }

        heading("Strategy Result")
        resultText = TextView(this).apply {
            text = "स्ट्रेटेजी चलाने के लिए नीचे दबाएँ।"
            textSize = 16f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        }
        panel.addView(resultText)

        button("STRATEGY CHECK करें") {
            evaluateStrategy()
        }

        button("पिछली बाज़ी WIN हुई") {
            resetHourlyCounterIfNeeded()
            hourlyWins++
            if (hourlyWins >= capSpinner.selectedItem.toString().toInt()) {
                resultText.text = "घंटे की जीत सीमा पूरी। अगली बाज़ी रोकें।"
            } else {
                toast("जीत दर्ज हुई: $hourlyWins")
            }
        }

        button("पिछली बाज़ी LOSS हुई") {
            if (strategySpinner.selectedItemPosition == 1) {
                strategyPaused = true
                resultText.text = "LOSS के बाद Pattern Strategy रुकी हुई है। नया Pattern बनने तक इंतज़ार करें।"
            } else {
                resultText.text = "LOSS दर्ज हुई।"
            }
        }

        button("History साफ करें", true) {
            history.clear()
            strategyPaused = false
            updateHistory()
            resultText.text = "History साफ कर दी गई।"
        }

        heading("समय नियंत्रण")
        label("सक्रिय समय: सुबह 9:00 से रात 11:00")
        label(
            if (isSessionActive())
                "अभी निर्धारित समय के अंदर है।"
            else
                "अभी निर्धारित समय के बाहर है।"
        )

        button("पासवर्ड बदलें", true) { showChangePassword() }

        button("LOGOUT • लॉगआउट करें", true) {
            auth.signOut()
            showLogin()
        }
    }

    private fun addResult(value: String) {
        history.add(0, value)
        while (history.size > 10) history.removeAt(history.lastIndex)
        updateHistory()

        if (strategyPaused) {
            val needed = patternSpinner.selectedItem.toString().toInt()
            val latest = history.firstOrNull()
            val streak = history.takeWhile { it == latest }.size
            if (streak >= needed) {
                strategyPaused = false
                toast("नया Pattern मिला। Strategy फिर से जाँच सकते हैं।")
            }
        }
    }

    private fun updateHistory() {
        historyText.text = if (history.isEmpty()) {
            "अभी कोई रिजल्ट दर्ज नहीं है।"
        } else {
            history.mapIndexed { i, v -> "${i + 1}. $v" }
                .joinToString("\n")
        }
    }

    private fun evaluateStrategy() {
        resetHourlyCounterIfNeeded()

        if (!isSessionActive()) {
            resultText.text = "अभी समय 9 AM–11 PM के बाहर है।"
            return
        }

        if (hourlyWins >= capSpinner.selectedItem.toString().toInt()) {
            resultText.text = "प्रति घंटे की जीत सीमा पूरी हो चुकी है।"
            return
        }

        if (history.isEmpty()) {
            resultText.text = "पहले Game History में रिजल्ट दर्ज करें।"
            return
        }

        when (strategySpinner.selectedItemPosition) {
            0 -> {
                val pos = positionSpinner.selectedItemPosition + 1
                val value = history.getOrNull(pos - 1)
                resultText.text = if (value == null) {
                    "Position $pos का रिजल्ट उपलब्ध नहीं है।"
                } else {
                    "Position $pos का दर्ज रिजल्ट: $value\nयह केवल रिकॉर्ड पर आधारित संकेत है।"
                }
            }

            1 -> {
                if (strategyPaused) {
                    resultText.text = "Pattern Strategy LOSS के बाद रुकी हुई है।"
                    return
                }

                val needed = patternSpinner.selectedItem.toString().toInt()
                val latest = history.first()
                val streak = history.takeWhile { it == latest }.size

                resultText.text = if (streak >= needed) {
                    "Pattern मिला: $streak लगातार $latest\nअगला निर्णय लेने से पहले गेम का नया रिजल्ट जाँचें।"
                } else {
                    "अभी $needed लगातार एक जैसे रिजल्ट नहीं मिले। WAIT"
                }
            }

            2 -> {
                val big = history.count { it == "BIG" }
                val small = history.count { it == "SMALL" }

                resultText.text = when {
                    big > small -> "पिछले रिजल्ट में BIG बहुमत: $big बनाम $small"
                    small > big -> "पिछले रिजल्ट में SMALL बहुमत: $small बनाम $big"
                    else -> "BIG और SMALL बराबर हैं। NO BET"
                }
            }
        }
    }

    private fun showChangePassword() {
        makeLayout()
        brand()
        heading("पासवर्ड बदलें")

        val old = input("पुराना पासवर्ड", true)
        val new = input("नया पासवर्ड", true)

        button("पासवर्ड बदलें") {
            val user = auth.currentUser
            when {
                user == null -> toast("पहले लॉगिन करें")
                old.text.isBlank() -> toast("पुराना पासवर्ड डालें")
                new.text.length < 6 -> toast("नया पासवर्ड कम से कम 6 अक्षर का रखें")
                else -> {
                    val credential = EmailAuthProvider.getCredential(
                        user.email ?: "",
                        old.text.toString()
                    )
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(new.text.toString())
                                .addOnSuccessListener {
                                    toast("पासवर्ड बदल गया")
                                    showHome()
                                }
                                .addOnFailureListener {
                                    toast("पासवर्ड नहीं बदला: ${it.localizedMessage}")
                                }
                        }
                        .addOnFailureListener {
                            toast("पुराना पासवर्ड सही नहीं है")
                        }
                }
            }
        }

        button("वापस जाएँ", true) { showHome() }
    }
}
