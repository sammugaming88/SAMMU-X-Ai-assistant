package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

sealed class WhatsAppSendResult {
    object Success : WhatsAppSendResult()
    data class Failure(val reason: String) : WhatsAppSendResult()
    object Timeout : WhatsAppSendResult()
}

class ZoyaAccessibilityService : AccessibilityService() {

    companion object {
        var shouldAutoClick = false
        var targetAppName = "whatsapp"
        var instance: ZoyaAccessibilityService? = null

        fun dispatchGestureClick(x: Float, y: Float): Boolean {
            val inst = instance ?: return false
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x, y)

            val builder = android.accessibilityservice.GestureDescription.Builder()
            builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
            val gesture = builder.build()

            return inst.dispatchGesture(gesture, null, null)
        }

        fun clickTextOnScreen(text: String): Boolean {
            val inst = instance ?: return false
            val root = inst.rootInActiveWindow ?: return false
            val node = findNodeByText(root, text, exact = false) ?: return false
            return clickNodeSafely(node)
        }

        // ==================== REUSABLE HELPER METHODS ====================

        fun findNodeByViewId(root: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
            if (root == null || viewId.isBlank()) return null
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (node != null) return node
                    }
                }
            } catch (e: Exception) {
                Log.w("ZoyaAccessibility", "Error in findAccessibilityNodeInfosByViewId: ${e.message}")
            }
            return recursiveFindByViewId(root, viewId)
        }

        private fun recursiveFindByViewId(node: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
            if (node == null) return null
            val resName = node.viewIdResourceName
            if (resName != null && resName.equals(viewId, ignoreCase = true)) {
                return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                val found = recursiveFindByViewId(child, viewId)
                if (found != null) return found
            }
            return null
        }

        fun findNodeByText(root: AccessibilityNodeInfo?, text: String, exact: Boolean = false): AccessibilityNodeInfo? {
            if (root == null || text.isBlank()) return null
            try {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (node == null) continue
                        val nText = node.text?.toString() ?: ""
                        val nDesc = node.contentDescription?.toString() ?: ""
                        if (exact) {
                            if (nText.equals(text, ignoreCase = true) || nDesc.equals(text, ignoreCase = true)) {
                                return node
                            }
                        } else {
                            return node
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ZoyaAccessibility", "Error in findAccessibilityNodeInfosByText: ${e.message}")
            }
            return recursiveFindByText(root, text.lowercase(), exact)
        }

        private fun recursiveFindByText(node: AccessibilityNodeInfo?, textLower: String, exact: Boolean): AccessibilityNodeInfo? {
            if (node == null) return null
            val nText = node.text?.toString()?.lowercase() ?: ""
            val nDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (exact) {
                if (nText == textLower || nDesc == textLower) return node
            } else {
                if (nText.contains(textLower) || nDesc.contains(textLower)) return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                val found = recursiveFindByText(child, textLower, exact)
                if (found != null) return found
            }
            return null
        }

        fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            var current = node?.parent
            while (current != null) {
                if (current.isClickable) return current
                current = current.parent
            }
            return null
        }

        fun clickNodeSafely(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false

            // 1. Direct action click if node itself is clickable
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.d("ZoyaAccessibility", "Clicked node directly via ACTION_CLICK")
                    return true
                }
            }

            // 2. Check clickable parent
            val parent = findClickableParent(node)
            if (parent != null) {
                val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.d("ZoyaAccessibility", "Clicked parent via ACTION_CLICK")
                    return true
                }
            }

            // 3. Fallback: visual coordinate click via gesture
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
                val x = bounds.centerX().toFloat()
                val y = bounds.centerY().toFloat()
                val gestureSuccess = dispatchGestureClick(x, y)
                if (gestureSuccess) {
                    Log.d("ZoyaAccessibility", "Clicked node via visual gesture ($x, $y)")
                    return true
                }
            }

            return false
        }

        suspend fun waitForNodeWithTimeout(
            timeoutMs: Long = 7000L,
            intervalMs: Long = 250L,
            finder: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?
        ): AccessibilityNodeInfo? {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val root = instance?.rootInActiveWindow
                if (root != null) {
                    val found = finder(root)
                    if (found != null) return found
                }
                delay(intervalMs)
            }
            return null
        }

        // ==================== WHATSAPP AUTOMATION ====================

        suspend fun automateWhatsAppSend(
            expectedMessage: String,
            timeoutMs: Long = 8000L
        ): WhatsAppSendResult {
            val service = instance ?: return WhatsAppSendResult.Failure("Accessibility Service is not running. Please enable Sammu X AI in Accessibility Settings.")

            val startTime = System.currentTimeMillis()
            val retryInterval = 250L
            var textEntered = expectedMessage.isEmpty()

            Log.d("ZoyaAccessibility", "Starting automateWhatsAppSend for message length=${expectedMessage.length}, timeout=${timeoutMs}ms")

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val root = service.rootInActiveWindow
                if (root == null) {
                    delay(retryInterval)
                    continue
                }

                val pkg = root.packageName?.toString() ?: ""
                if (!pkg.contains("whatsapp")) {
                    delay(retryInterval)
                    continue
                }

                // Check message input field: if message is not yet entered, type it
                val entryNode = findNodeByViewId(root, "com.whatsapp:id/entry")
                    ?: findNodeByViewId(root, "com.whatsapp.w4b:id/entry")
                    ?: findNodeByClass(root, "android.widget.EditText")

                if (entryNode != null && !textEntered && expectedMessage.isNotEmpty()) {
                    val currentText = entryNode.text?.toString() ?: ""
                    if (!currentText.contains(expectedMessage)) {
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, expectedMessage)
                        }
                        entryNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        textEntered = true
                        delay(200L)
                    } else {
                        textEntered = true
                    }
                }

                // Search for the actual Send button
                var sendButton: AccessibilityNodeInfo? = findNodeByViewId(root, "com.whatsapp:id/send")
                if (sendButton == null) {
                    sendButton = findNodeByViewId(root, "com.whatsapp.w4b:id/send")
                }
                if (sendButton == null) {
                    sendButton = findSendButtonByDescription(root)
                }

                if (sendButton != null) {
                    Log.d("ZoyaAccessibility", "Found Send button! Clicking...")
                    val clickResult = clickNodeSafely(sendButton)
                    if (clickResult) {
                        Log.d("ZoyaAccessibility", "Send button clicked. Verifying message dispatch...")
                        val isVerified = verifyMessageSent(expectedMessage, verifyTimeoutMs = 2500L)
                        if (isVerified) {
                            Log.d("ZoyaAccessibility", "Send successfully verified!")
                            return WhatsAppSendResult.Success
                        } else {
                            // Secondary retry if still on screen
                            val retryRoot = service.rootInActiveWindow
                            val retrySend = retryRoot?.let {
                                findNodeByViewId(it, "com.whatsapp:id/send")
                                    ?: findNodeByViewId(it, "com.whatsapp.w4b:id/send")
                                    ?: findSendButtonByDescription(it)
                            }
                            if (retrySend != null) {
                                clickNodeSafely(retrySend)
                                if (verifyMessageSent(expectedMessage, verifyTimeoutMs = 1500L)) {
                                    return WhatsAppSendResult.Success
                                }
                            }
                            return WhatsAppSendResult.Failure("Message was placed in chat but could not verify that Send was completed.")
                        }
                    }
                }

                delay(retryInterval)
            }

            Log.w("ZoyaAccessibility", "automateWhatsAppSend timed out after ${timeoutMs}ms")
            return WhatsAppSendResult.Timeout
        }

        private suspend fun verifyMessageSent(expectedMessage: String, verifyTimeoutMs: Long = 2500L): Boolean {
            val service = instance ?: return false
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < verifyTimeoutMs) {
                delay(200L)
                val root = service.rootInActiveWindow ?: continue

                // 1. Verify that the typing entry box is cleared
                val entryNode = findNodeByViewId(root, "com.whatsapp:id/entry")
                    ?: findNodeByViewId(root, "com.whatsapp.w4b:id/entry")
                    ?: findNodeByClass(root, "android.widget.EditText")

                val entryText = entryNode?.text?.toString() ?: ""
                val isInputCleared = entryNode != null && (entryText.isEmpty() || !entryText.contains(expectedMessage))

                // 2. Check if send button turned back into voice record button
                val sendBtn = findNodeByViewId(root, "com.whatsapp:id/send")
                    ?: findNodeByViewId(root, "com.whatsapp.w4b:id/send")
                val voiceBtn = findNodeByViewId(root, "com.whatsapp:id/voice_note_btn")
                    ?: findNodeByViewId(root, "com.whatsapp.w4b:id/voice_note_btn")
                    ?: findVoiceButtonByDescription(root)

                val isSendReplacedByVoice = (sendBtn == null) && (voiceBtn != null || isInputCleared)

                if (isInputCleared && isSendReplacedByVoice) {
                    return true
                }

                // 3. Sent message bubble presence in message list
                if (expectedMessage.isNotEmpty()) {
                    val bubble = findNodeByText(root, expectedMessage, exact = false)
                    if (isInputCleared || (bubble != null && sendBtn == null)) {
                        return true
                    }
                }
            }

            return false
        }

        suspend fun automateWhatsAppSearchAndSend(
            contactName: String,
            message: String,
            timeoutMs: Long = 10000L
        ): WhatsAppSendResult {
            val service = instance ?: return WhatsAppSendResult.Failure("Accessibility Service is not active. Enable it in Settings.")

            val startTime = System.currentTimeMillis()
            val retryInterval = 250L
            var searchOpened = false
            var contactSelected = false

            Log.d("ZoyaAccessibility", "Starting automateWhatsAppSearchAndSend for contact='$contactName'")

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val root = service.rootInActiveWindow
                if (root == null) {
                    delay(retryInterval)
                    continue
                }

                val pkg = root.packageName?.toString() ?: ""
                if (!pkg.contains("whatsapp")) {
                    delay(retryInterval)
                    continue
                }

                // 1. Click Search Icon on Home Screen
                if (!searchOpened) {
                    val searchBtn = findNodeByViewId(root, "com.whatsapp:id/menuitem_search")
                        ?: findNodeByViewId(root, "com.whatsapp.w4b:id/menuitem_search")
                        ?: findNodeByDescription(root, "search")
                        ?: findNodeByDescription(root, "खोजें")
                    if (searchBtn != null) {
                        clickNodeSafely(searchBtn)
                        searchOpened = true
                        delay(350L)
                        continue
                    }
                }

                // 2. Enter contact name in search field
                if (searchOpened && !contactSelected) {
                    val searchField = findNodeByViewId(root, "com.whatsapp:id/search_src_text")
                        ?: findNodeByClass(root, "android.widget.EditText")
                    if (searchField != null) {
                        val currentQuery = searchField.text?.toString() ?: ""
                        if (!currentQuery.equals(contactName, ignoreCase = true)) {
                            val args = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, contactName)
                            }
                            searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                            delay(500L)
                            continue
                        }
                    }

                    // Look for contact in result list
                    val contactNode = findNodeByText(root, contactName, exact = false)
                    if (contactNode != null && contactNode != searchField) {
                        clickNodeSafely(contactNode)
                        contactSelected = true
                        delay(400L)
                        continue
                    }
                }

                // 3. In conversation chat, type message and send
                if (contactSelected) {
                    return automateWhatsAppSend(expectedMessage = message, timeoutMs = 7000L)
                }

                delay(retryInterval)
            }

            return if (!searchOpened) {
                WhatsAppSendResult.Failure("Could not find WhatsApp search button.")
            } else if (!contactSelected) {
                WhatsAppSendResult.Failure("Could not find contact '$contactName' in WhatsApp.")
            } else {
                WhatsAppSendResult.Timeout
            }
        }

        private fun findSendButtonByDescription(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val sendKeywords = listOf("send", "bheje", "bhejen", "भेजें", "enviar", "envoyer", "send message")
            if (sendKeywords.any { desc == it || desc.startsWith("send ") || desc.startsWith("भेजें") }) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findSendButtonByDescription(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        private fun findVoiceButtonByDescription(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains("voice") || desc.contains("record") || desc.contains("वॉयस") || desc.contains("ऑडियो")) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findVoiceButtonByDescription(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        private fun findNodeByDescription(node: AccessibilityNodeInfo?, descSub: String): AccessibilityNodeInfo? {
            if (node == null) return null
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains(descSub.lowercase())) return node
            for (i in 0 until node.childCount) {
                val found = findNodeByDescription(node.getChild(i), descSub)
                if (found != null) return found
            }
            return null
        }

        private fun findNodeByClass(node: AccessibilityNodeInfo?, className: String): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.className?.toString()?.equals(className, ignoreCase = true) == true) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findNodeByClass(node.getChild(i), className)
                if (found != null) return found
            }
            return null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("ZoyaAccessibility", "Accessibility Service Connected successfully.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.i("ZoyaAccessibility", "Accessibility Service Unbound.")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shouldAutoClick) return

        val packageName = event.packageName?.toString() ?: ""
        if (packageName.contains("whatsapp")) {
            val rootNode = rootInActiveWindow ?: return
            val sendBtn = findNodeByViewId(rootNode, "com.whatsapp:id/send")
                ?: findNodeByViewId(rootNode, "com.whatsapp.w4b:id/send")
                ?: findSendButtonByDescription(rootNode)
            if (sendBtn != null) {
                val clicked = clickNodeSafely(sendBtn)
                if (clicked) {
                    Log.d("ZoyaAccessibility", "Successfully clicked send button via onAccessibilityEvent!")
                    shouldAutoClick = false
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("ZoyaAccessibility", "Accessibility Service Interrupted")
    }
}
