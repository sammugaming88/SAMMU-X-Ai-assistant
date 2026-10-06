package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.ZoyaScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
           Log.i("ZoyaDiagnostic", "All permissions granted.")
        } else {
           Log.e("ZoyaDiagnostic", "Some permissions denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i("ZoyaDiagnostic", "MainActivity onCreate started")
        
        checkPermissions()
        startDiagnosticLogging()
        initTextToSpeech()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZoyaScreen()
                }
            }
        }
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(this, this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing TTS: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("hi-IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.ENGLISH
            }
            speakInstagramFollowRequest()
        }
    }

    private fun speakInstagramFollowRequest() {
        val welcomeMessage = "Aapka bahut bahut swagat hai! Main hoon Sammu X AI assistant. Mere pyare creator Sammu ko Instagram par please pyaar se follow kar lijiye na! Profile ID hai hyy underscore sammu dot x. Shukriya!"
        tts?.speak(welcomeMessage, TextToSpeech.QUEUE_FLUSH, null, "instagram_follow_welcome")
    }
    
    private var diagnosticJob: kotlinx.coroutines.Job? = null

    private fun startDiagnosticLogging() {
        diagnosticJob?.cancel()
        diagnosticJob = CoroutineScope(Dispatchers.Main).launch {
            while (kotlinx.coroutines.isActive) {
                val service = ZoyaForegroundService.activeService
                if (service != null) {
                    Log.d("ZoyaDiagnostic", "STATUS REPORT: Service Running=true, State=${com.example.ZoyaForegroundService.currentState.name}")
                } else {
                    Log.d("ZoyaDiagnostic", "STATUS REPORT: Service Not Running")
                }
                delay(3000)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        diagnosticJob?.cancel()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error shutting down TTS: ${e.message}")
        }
    }
}
