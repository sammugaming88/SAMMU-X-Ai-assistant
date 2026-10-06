package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

data class FastCommandResult(
    val isHandled: Boolean,
    val success: Boolean,
    val responseText: String
)

class FastCommandRouter(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine
) {

    suspend fun route(rawText: String): FastCommandResult {
        val text = rawText.trim()
        if (text.isEmpty()) return FastCommandResult(isHandled = false, success = false, responseText = "")

        val lower = text.lowercase().replace("?", "").replace("!", "").trim()

        // 1. WhatsApp message commands (must check before plain WhatsApp open)
        val waMsg = matchWhatsAppMessage(text)
        if (waMsg != null) {
            val (contact, message) = waMsg
            Log.d("FastCommandRouter", "Detected WhatsApp message command: contact='$contact', message='$message'")
            val resultMsg = toolEngine.sendWhatsApp(contact, message)
            val success = !resultMsg.contains("could not", ignoreCase = true) &&
                    !resultMsg.contains("failed", ignoreCase = true) &&
                    !resultMsg.contains("disabled", ignoreCase = true) &&
                    !resultMsg.contains("not installed", ignoreCase = true)
            return FastCommandResult(isHandled = true, success = success, responseText = resultMsg)
        }

        // 2. Instagram open command
        if (isInstagramCommand(lower)) {
            Log.d("FastCommandRouter", "Detected fast Instagram open command: $text")
            val res = openInstagram()
            return FastCommandResult(isHandled = true, success = res.first, responseText = res.second)
        }

        // 3. WhatsApp open command
        if (isWhatsAppOpenCommand(lower)) {
            Log.d("FastCommandRouter", "Detected fast WhatsApp open command: $text")
            val res = openWhatsApp()
            return FastCommandResult(isHandled = true, success = res.first, responseText = res.second)
        }

        // 4. YouTube open command
        if (isYouTubeCommand(lower)) {
            Log.d("FastCommandRouter", "Detected fast YouTube open command: $text")
            val res = openYouTube()
            return FastCommandResult(isHandled = true, success = res.first, responseText = res.second)
        }

        // 5. Camera open command
        if (isCameraCommand(lower)) {
            Log.d("FastCommandRouter", "Detected fast Camera open command: $text")
            val res = openCamera()
            return FastCommandResult(isHandled = true, success = res.first, responseText = res.second)
        }

        // 6. Settings open command
        if (isSettingsCommand(lower)) {
            Log.d("FastCommandRouter", "Detected fast Settings open command: $text")
            val res = openSettings()
            return FastCommandResult(isHandled = true, success = res.first, responseText = res.second)
        }

        return FastCommandResult(isHandled = false, success = false, responseText = "")
    }

    fun openInstagram(): Pair<Boolean, String> {
        val profileUrl = "https://www.instagram.com/hyy_sammu.x?stkn=MXZjb2hydTNsc3p3NQ=="
        val pm = context.packageManager
        return try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)).apply {
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (appIntent.resolveActivity(pm) != null) {
                context.startActivity(appIntent)
                Pair(true, "Instagram open ho gaya! Please Sammu ko follow kar lijiye: @hyy_sammu.x ❤️")
            } else {
                val launchIntent = pm.getLaunchIntentForPackage("com.instagram.android")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Pair(true, "Instagram open ho gaya! Please Sammu ko follow kar lijiye: @hyy_sammu.x ❤️")
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    Pair(true, "Opening Sammu's Instagram profile (@hyy_sammu.x)")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Could not open Instagram: ${e.message}")
        }
    }

    fun openWhatsApp(): Pair<Boolean, String> {
        val pm = context.packageManager
        var intent = pm.getLaunchIntentForPackage("com.whatsapp")
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage("com.whatsapp.w4b")
        }
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Pair(true, "WhatsApp opened")
        } else {
            Pair(false, "WhatsApp is not installed.")
        }
    }

    fun openYouTube(): Pair<Boolean, String> {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Pair(true, "YouTube opened")
        } else {
            Pair(false, "YouTube is not installed.")
        }
    }

    fun openCamera(): Pair<Boolean, String> {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Camera opened")
        } catch (e: Exception) {
            Pair(false, "Could not open camera: ${e.message}")
        }
    }

    fun openSettings(): Pair<Boolean, String> {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Settings opened")
        } catch (e: Exception) {
            Pair(false, "Could not open settings: ${e.message}")
        }
    }

    private fun isInstagramCommand(lower: String): Boolean {
        val isInsta = lower.contains("instagram") || lower.contains("insta")
        if (!isInsta) return false
        val actionKeywords = listOf("खोलो", "kholo", "open", "चला दो", "chala do", "chalao", "start", "शुरू")
        if (actionKeywords.any { lower.contains(it) }) return true
        val words = lower.split("\\s+".toRegex())
        return words.size <= 2 && (words.contains("instagram") || words.contains("insta"))
    }

    private fun isWhatsAppOpenCommand(lower: String): Boolean {
        val isWa = lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("वाट्सएप")
        if (!isWa) return false
        val msgKeywords = listOf("message", "msg", "sms", "text", "भेजो", "bhejo", "send", "chat", "चैट", "likho", "लिखो")
        if (msgKeywords.any { lower.contains(it) }) return false
        val actionKeywords = listOf("खोलो", "kholo", "open", "चला दो", "chala do", "chalao", "start", "शुरू")
        if (actionKeywords.any { lower.contains(it) }) return true
        val words = lower.split("\\s+".toRegex())
        return words.size <= 2 && words.any { it.contains("whatsapp") || it.contains("व्हाट्सएप") }
    }

    private fun isYouTubeCommand(lower: String): Boolean {
        val isYt = lower.contains("youtube") || lower.contains("yt") || lower.contains("यूट्यूब")
        if (!isYt) return false
        val actionKeywords = listOf("खोलो", "kholo", "open", "चला दो", "chala do", "chalao", "start")
        if (actionKeywords.any { lower.contains(it) }) return true
        val words = lower.split("\\s+".toRegex())
        return words.size <= 2 && words.any { it.contains("youtube") || it.contains("यूट्यूब") }
    }

    private fun isCameraCommand(lower: String): Boolean {
        val isCam = lower.contains("camera") || lower.contains("kamera") || lower.contains("कैमरा")
        if (!isCam) return false
        val actionKeywords = listOf("खोलो", "kholo", "open", "चला दो", "chala do", "chalao", "start", "on karo")
        if (actionKeywords.any { lower.contains(it) }) return true
        val words = lower.split("\\s+".toRegex())
        return words.size <= 2 && words.any { it.contains("camera") || it.contains("कैमरा") }
    }

    private fun isSettingsCommand(lower: String): Boolean {
        val isSet = lower.contains("setting") || lower.contains("सेटिंग") || lower.contains("सेटिंग्स")
        if (!isSet) return false
        val actionKeywords = listOf("खोलो", "kholo", "open", "चला दो", "chala do", "chalao")
        if (actionKeywords.any { lower.contains(it) }) return true
        val words = lower.split("\\s+".toRegex())
        return words.size <= 2 && words.any { it.contains("setting") || it.contains("सेटिंग") }
    }

    private fun matchWhatsAppMessage(text: String): Pair<String, String>? {
        val clean = text.trim()

        val regex1 = Regex("""(?i)(?:whatsapp|व्हाट्सएप)\s*(?:पर|pe|par|me|mein)?\s*(?:jao\s*aur\s*|जाओ\s*और\s*)?(.+?)\s*(?:को|ko)\s+(.+?)\s*(?:message|msg|मैसेज)?\s*(?:करो|karo|भेजो|bhejo|send\s*karo|send\s*kar\s*do)""")
        val m1 = regex1.find(clean)
        if (m1 != null) {
            val contact = cleanContactName(m1.groupValues[1])
            val msg = m1.groupValues[2].trim()
            if (contact.isNotBlank() && msg.isNotBlank()) return Pair(contact, msg)
        }

        val regex2 = Regex("""(?i)(?:whatsapp|व्हाट्सएप)\s*(?:par|pe|me)?\s*(.+?)\s*(?:ko|को)\s*(.+?)\s*(?:bhejo|भेजो|send\s*karo)""")
        val m2 = regex2.find(clean)
        if (m2 != null) {
            val contact = cleanContactName(m2.groupValues[1])
            val msg = m2.groupValues[2].trim()
            if (contact.isNotBlank() && msg.isNotBlank()) return Pair(contact, msg)
        }

        val regex3 = Regex("""(?i)send\s+(?:a\s+)?whatsapp\s+message\s+to\s+(.+?)(?:\s*:|\s+saying|\s+with\s+text)?\s+(.+)""")
        val m3 = regex3.find(clean)
        if (m3 != null) {
            val contact = cleanContactName(m3.groupValues[1])
            val msg = m3.groupValues[2].trim()
            if (contact.isNotBlank() && msg.isNotBlank()) return Pair(contact, msg)
        }

        val regex4 = Regex("""(?i)message\s+(.+?)\s+on\s+whatsapp\s+(.+)""")
        val m4 = regex4.find(clean)
        if (m4 != null) {
            val contact = cleanContactName(m4.groupValues[1])
            val msg = m4.groupValues[2].trim()
            if (contact.isNotBlank() && msg.isNotBlank()) return Pair(contact, msg)
        }

        return null
    }

    private fun cleanContactName(raw: String): String {
        return raw.trim()
            .replace(Regex("""^(?:par|pe|me|jao|aur|पर|जाओ|और)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
