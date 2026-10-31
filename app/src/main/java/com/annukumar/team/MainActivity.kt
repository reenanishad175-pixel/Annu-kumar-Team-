package com.annukumar.team

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var panel: LinearLayout

    private val navy = Color.rgb(12, 24, 52)
    private val gold = Color.rgb(238, 190, 91)
    private val white = Color.WHITE

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) showHome()
        else showLogin()
    }

    private fun rounded(
        color: Int,
        radius: Int,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun makeLayout() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(30), dp(22), dp(30))
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
            setPadding(dp(24), dp(28), dp(24), dp(26))
            background = rounded(Color.rgb(20, 37, 69), 24, gold)
            elevation = dp(8).toFloat()
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
        panel.addView(TextView(this).apply {
            text = "AKT"
            textSize = 34f
            setTextColor(gold)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })

        panel.addView(TextView(this).apply {
            text = "ANNU KUMAR TEAM"
            textSize = 22f
            setTextColor(white)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, dp(5), 0, dp(5))
        })

        panel.addView(TextView(this).apply {
            text = "WELCOME TO YOUR TEAM"
            textSize = 11f
            setTextColor(gold)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            setPadding(0, 0, 0, dp(22))
        })
    }

    private fun addHeading(value: String) {
        panel.addView(TextView(this).apply {
            text = value
            textSize = 21f
            setTextColor(white)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(18))
        })
    }

    private fun addInput(
        hintText: String,
        password: Boolean = false
    ): EditText {
        val field = EditText(this).apply {
            hint = hintText
            textSize = 16f
            setTextColor(Color.rgb(25, 35, 55))
            setHintTextColor(Color.rgb(110, 120, 140))
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_PHONE
            }
            setSingleLine(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.rgb(247, 248, 251), 13)
        }

        panel.addView(
            field,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                bottomMargin = dp(14)
            }
        )
        return field
    }

    private fun addButton(
        label: String,
        secondary: Boolean = false,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (secondary) gold else navy)
            background = if (secondary) {
                rounded(Color.TRANSPARENT, 14, gold)
            } else {
                rounded(gold, 14)
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { action() }
        }

        panel.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(12)
            }
        )
    }

    private fun addFooter() {
        panel.addView(TextView(this).apply {
            text = "ANNU KUMAR TEAM  •  MEMBER ACCESS"
            textSize = 10f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun message(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
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

    private fun showLogin() {
        makeLayout()
        addBrand()
        addHeading("लॉगिन करें")

        val phone = addInput("मोबाइल नंबर")
        val password = addInput("पासवर्ड", true)

        addButton("LOGIN  •  लॉगिन करें") {
            val id = loginId(phone.text.toString())
            val pass = password.text.toString()

            if (id == null) {
                message("सही 10 अंकों का मोबाइल नंबर डालें")
            } else if (pass.isBlank()) {
                message("पासवर्ड डालें")
            } else {
                auth.signInWithEmailAndPassword(id, pass)
                    .addOnSuccessListener {
                        message("लॉगिन सफल!")
                        showHome()
                    }
                    .addOnFailureListener {
                        message("लॉगिन नहीं हुआ: ${it.localizedMessage}")
                    }
            }
        }

        addButton("नई आईडी बनाएँ  •  REGISTER", true) {
            showRegister()
        }

        addFooter()
    }

    private fun showRegister() {
        makeLayout()
        addBrand()
        addHeading("नई आईडी बनाएँ")

        val phone = addInput("मोबाइल नंबर")
        val password = addInput("नया पासवर्ड", true)
        val confirm = addInput("पासवर्ड दोबारा डालें", true)

        addButton("REGISTER  •  रजिस्टर करें") {
            val id = loginId(phone.text.toString())
            val pass = password.text.toString()
            val confirmPass = confirm.text.toString()

            when {
                id == null ->
                    message("सही 10 अंकों का मोबाइल नंबर डालें")

                pass.length < 6 ->
                    message("पासवर्ड कम से कम 6 अक्षर का रखें")

                pass != confirmPass ->
                    message("दोनों पासवर्ड एक जैसे नहीं हैं")

                else -> {
                    auth.createUserWithEmailAndPassword(id, pass)
                        .addOnSuccessListener {
                            message("आपकी आईडी बन गई!")
                            showHome()
                        }
                        .addOnFailureListener {
                            message("रजिस्ट्रेशन नहीं हुआ: ${it.localizedMessage}")
                        }
                }
            }
        }

        addButton("लॉगिन पर वापस जाएँ", true) {
            showLogin()
        }

        addFooter()
    }

    private fun showHome() {
        makeLayout()
        addBrand()
        addHeading("आपका स्वागत है!")

        panel.addView(TextView(this).apply {
            text = "आप सफलतापूर्वक लॉगिन हैं।"
            textSize = 16f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        })

        addHeading("पासवर्ड बदलें")

        val oldPassword = addInput("पुराना पासवर्ड", true)
        val newPassword = addInput("नया पासवर्ड", true)

        addButton("पासवर्ड बदलें") {
            val user = auth.currentUser
            val old = oldPassword.text.toString()
            val new = newPassword.text.toString()

            when {
                user == null ->
                    message("पहले लॉगिन करें")

                old.isBlank() ->
                    message("पुराना पासवर्ड डालें")

                new.length < 6 ->
                    message("नया पासवर्ड कम से कम 6 अक्षर का रखें")

                else -> {
                    val credential = EmailAuthProvider.getCredential(
                        user.email ?: "",
                        old
                    )

                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(new)
                                .addOnSuccessListener {
                                    message("पासवर्ड सफलतापूर्वक बदल गया")
                                    oldPassword.text.clear()
                                    newPassword.text.clear()
                                }
                                .addOnFailureListener {
                                    message("पासवर्ड नहीं बदला: ${it.localizedMessage}")
                                }
                        }
                        .addOnFailureListener {
                            message("पुराना पासवर्ड सही नहीं है")
                        }
                }
            }
        }

        addButton("LOGOUT  •  लॉगआउट करें", true) {
            auth.signOut()
            showLogin()
        }

        addFooter()
    }
}
