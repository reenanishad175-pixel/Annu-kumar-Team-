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

        @Volatile
        private var serviceInstance: BdgAccessibilityService? = null

        fun clickText(buttonText: String): Boolean {
            return serviceInstance?.clickButton(buttonText) ?: false
        }

        fun readScreen(): String {
            return latestScreenText
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInstance = this
        serviceConnected = true

        Log.d("AKT_DEMO", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val root = rootInActiveWindow ?: return
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

            readNodes(root)

            val screenText = collectedText.toString().trim()

            if (screenText.isNotEmpty()) {
                latestScreenText = screenText
                Log.d("AKT_DEMO", "Screen information updated")
            }

        } catch (e: Exception) {
            Log.e("AKT_DEMO", "Screen reading error", e)
        }
    }

    private fun clickButton(buttonText: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val target = buttonText.trim()

            fun search(node: AccessibilityNodeInfo?): Boolean {
                if (node == null) return false

                val text = node.text?.toString()?.trim() ?: ""
                val description =
                    node.contentDescription?.toString()?.trim() ?: ""

                val matched =
                    text.equals(target, ignoreCase = true) ||
                    description.equals(target, ignoreCase = true)

                if (matched) {
                    var current: AccessibilityNodeInfo? = node

                    while (current != null) {
                        if (current.isClickable) {
                            return current.performAction(
                                AccessibilityNodeInfo.ACTION_CLICK
                            )
                        }

                        current = current.parent
                    }
                }

                for (i in 0 until node.childCount) {
                    if (search(node.getChild(i))) {
                        return true
                    }
                }

                return false
            }

            search(root)

        } catch (e: Exception) {
            Log.e("AKT_DEMO", "Button click error", e)
            false
        }
    }

    override fun onInterrupt() {
        Log.d("AKT_DEMO", "Accessibility interrupted")
    }

    override fun onDestroy() {
        serviceConnected = false
        serviceInstance = null

        super.onDestroy()
    }
}
