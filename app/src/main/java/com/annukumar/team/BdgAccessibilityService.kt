package com.annukumar.team

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class BdgAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var latestScreenText: String = ""

        @Volatile
        var serviceConnected: Boolean = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnected = true
        Log.d("AKT_DEMO", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val collectedText = StringBuilder()

        fun readNodes(node: AccessibilityNodeInfo?) {
            if (node == null) return

            node.text?.toString()?.let {
                if (it.isNotBlank()) {
                    collectedText.append(it).append("\n")
                }
            }

            node.contentDescription?.toString()?.let {
                if (it.isNotBlank()) {
                    collectedText.append(it).append("\n")
                }
            }

            for (i in 0 until node.childCount) {
                readNodes(node.getChild(i))
            }
        }

        readNodes(rootInActiveWindow)

        val screenText = collectedText.toString().trim()

        if (screenText.isNotEmpty()) {
            latestScreenText = screenText
            Log.d("AKT_DEMO", "Screen information updated")
        }
    }

    override fun onInterrupt() {
        Log.d("AKT_DEMO", "Accessibility interrupted")
    }

    override fun onDestroy() {
        serviceConnected = false
        super.onDestroy()
    }
}
