
package com.annukumar.team

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var layout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            showHome()
        } else {
            showLogin()
        }
    }

    private fun makeLayout() {
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply {
            addView(layout)
        })
    }

    private fun addTitle(value: String) {
        layout.addView(TextView(this).apply {
            text = value
            textSize = 25f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        })
    }

    private fun addInput(hintText: String, password: Boolean = false): EditText {
        val field = EditText(this).apply {
            hint = hintText
            textSize = 16f
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_PHONE
            }
            setPadding(20, 12, 20, 12)
        }
        layout.addView(field)
        return field
    }

    private fun addButton(label: String, action: () -> Unit) {
        layout.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
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
        addTitle("Annu Kumar Team")

        val phone = addInput("मोबाइल नंबर")
        val password = addInput("पासवर्ड", true)

        addButton("लॉगिन करें") {
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

        addButton("नई आईडी रजिस्टर करें") {
            showRegister()
        }
    }

    private fun showRegister() {
        makeLayout()
        addTitle("नई आईडी बनाएँ")

        val phone = addInput("मोबाइल नंबर")
        val password = addInput("नया पासवर्ड", true)
        val confirm = addInput("पासवर्ड दोबारा डालें", true)

        addButton("रजिस्टर करें") {
            val id = loginId(phone.text.toString())
            val pass = password.text.toString()
            val confirmPass = confirm.text.toString()

            when {
                id == null -> message("सही 10 अंकों का मोबाइल नंबर डालें")
                pass.length < 6 -> message("पासवर्ड कम से कम 6 अक्षर का रखें")
                pass != confirmPass -> message("दोनों पासवर्ड एक जैसे नहीं हैं")
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

        addButton("लॉगिन पर वापस जाएँ") {
            showLogin()
        }
    }

    private fun showHome() {
        makeLayout()
        addTitle("Annu Kumar Team")

        layout.addView(TextView(this).apply {
            text = "आप सफलतापूर्वक लॉगिन हैं।"
            textSize = 18f
            gravity = Gravity.CENTER
        })

        addTitle("पासवर्ड बदलें")

        val oldPassword = addInput("पुराना पासवर्ड", true)
        val newPassword = addInput("नया पासवर्ड", true)

        addButton("पासवर्ड बदलें") {
            val user = auth.currentUser
            val old = oldPassword.text.toString()
            val new = newPassword.text.toString()

            when {
                user == null -> message("पहले लॉगिन करें")
                old.isBlank() -> message("पुराना पासवर्ड डालें")
                new.length < 6 -> message("नया पासवर्ड कम से कम 6 अक्षर का रखें")
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

        addButton("लॉगआउट करें") {
            auth.signOut()
            showLogin()
        }
    }
}
