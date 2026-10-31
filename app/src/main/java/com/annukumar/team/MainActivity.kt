package com.annukumar.team

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply {
            text = "Annu Kumar Team"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Firebase कनेक्शन जाँचा जा रहा है…"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        }
        val check = android.widget.Button(this).apply {
            text = "Firebase फिर से जाँचें"
            setOnClickListener { checkFirebase() }
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(check)
        setContentView(layout)
        checkFirebase()
    }

    private fun checkFirebase() {
        status.text = "Firebase से संपर्क हो रहा है…"
        val payload = mapOf(
            "app" to "Annu Kumar Team",
            "package" to "com.annukumar.team",
            "lastCheck" to System.currentTimeMillis(),
            "connectionTest" to "ok"
        )
        db.collection("app_health").document("android")
            .set(payload, SetOptions.merge())
            .addOnSuccessListener {
                status.text = "Firebase कनेक्शन सफल।\nFirestore में app_health/android अपडेट हुआ।\nBDG WIN Demo अभी कनेक्ट नहीं है।"
            }
            .addOnFailureListener { e ->
                status.text = "Firebase कनेक्शन असफल।\n${e.localizedMessage ?: "अनजान त्रुटि"}\nFirestore Rules और google-services.json जाँचें।"
            }
    }
}
