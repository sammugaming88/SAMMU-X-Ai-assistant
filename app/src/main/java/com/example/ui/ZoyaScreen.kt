package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.abs
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ZoyaScreen() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToChat = { navController.navigate("chat") }
            )
        }
        composable("chat") {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToChat: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }
    val defaultKey = remember {
        val buildKey = com.example.BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
    }
    var apiKey by remember {
        val saved = prefs.getString("api_key", "") ?: ""
        mutableStateOf(if (saved.isNotEmpty()) saved else defaultKey)
    }
    var showApiKeyDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    var zoyaState by remember { mutableStateOf(ZoyaForegroundService.currentState) }
    var serviceStarted by remember { mutableStateOf(ZoyaForegroundService.activeService != null) }
    var showMenu by remember { mutableStateOf(false) }

    val audioAmplitude by ZoyaForegroundService.audioAmplitude.collectAsState(initial = 0f)
    val waveformBars by ZoyaForegroundService.waveformBars.collectAsState(initial = List(28) { 0.08f })

    val smoothAmplitude by animateFloatAsState(
        targetValue = audioAmplitude,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "smoothAmplitude"
    )

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.RECORD_AUDIO] == true) {
            val intent = Intent(context, ZoyaForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
            serviceStarted = true
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        ZoyaForegroundService.onStateChange = { state ->
            zoyaState = state
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SAMMU X AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        Text("⚙", color = Color.White, fontSize = 20.sp)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF1E1E2E).copy(alpha = 0.95f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Logs / Chat", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onNavigateToChat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("API Key Settings", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showApiKeyDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Accessibility Settings (Auto-Click)", color = Color.White) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A)),
                        radius = 1500f
                    )
                )
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(20.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(24.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                ZoyaOrb(state = zoyaState, amplitude = smoothAmplitude)

                Spacer(modifier = Modifier.height(14.dp))

                AudioWaveformVisualizer(
                    waveformBars = waveformBars,
                    amplitude = smoothAmplitude,
                    state = zoyaState
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (!serviceStarted) {
                    if (apiKey.isEmpty()) {
                        Button(
                            modifier = Modifier.testTag("setup_api_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            onClick = { showApiKeyDialog = true }
                        ) {
                            Text("Setup API Key", fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                        }
                    } else {
                        Button(
                            modifier = Modifier.testTag("start_zoya_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasContacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasPhone = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (hasMic && hasContacts && hasPhone) {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    ContextCompat.startForegroundService(context, intent)
                                    serviceStarted = true
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.RECORD_AUDIO,
                                            android.Manifest.permission.READ_CONTACTS,
                                            android.Manifest.permission.CALL_PHONE
                                        )
                                    )
                                }
                            }
                        ) {
                            Text("Initialize SAMMU X", fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                        }
                    }
                } else if (zoyaState == ZoyaState.IDLE) {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        onClick = {
                            val service = ZoyaForegroundService.activeService
                            if (service != null) {
                                service.reconnectSession()
                            } else {
                                val intent = Intent(context, ZoyaForegroundService::class.java)
                                ContextCompat.startForegroundService(context, intent)
                            }
                        }
                    ) {
                        Text("Reconnect Uplink", fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val intent = Intent(context, ZoyaForegroundService::class.java)
                            context.stopService(intent)
                            serviceStarted = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                            contentColor = Color(0xFFEF9A9A)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Terminate Session", fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    }
                } else {
                    Text(
                        text = when (zoyaState) {
                            ZoyaState.LISTENING -> "Awaiting Input..."
                            ZoyaState.THINKING -> "Processing Data..."
                            ZoyaState.SPEAKING -> "Transmitting..."
                            else -> ""
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val intent = Intent(context, ZoyaForegroundService::class.java)
                            context.stopService(intent)
                            serviceStarted = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                            contentColor = Color(0xFFEF9A9A)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Disconnect", fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sammu X Instagram Follow Card
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/hyy_sammu.x?stkn=MXZjb2hydTNsc3p3NQ==")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF833AB4).copy(alpha = 0.25f),
                                        Color(0xFFFD1D1D).copy(alpha = 0.25f),
                                        Color(0xFFFCB045).copy(alpha = 0.25f)
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📸", fontSize = 24.sp)
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Column {
                                    Text(
                                        text = "Follow Sammu on Instagram ❤️",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "@hyy_sammu.x • Pyaar se follow karein ❤️",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/hyy_sammu.x?stkn=MXZjb2hydTNsc3p3NQ==")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Follow ✨", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fast Command Action Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    FastChip("📸 Insta") {
                        val service = ZoyaForegroundService.activeService
                        if (service != null) service.executeCommand("Instagram खोलो")
                        else com.example.tools.FastCommandRouter(context, com.example.tools.ToolExecutionEngine(context)).openInstagram()
                    }
                    FastChip("💬 WhatsApp") {
                        val service = ZoyaForegroundService.activeService
                        if (service != null) service.executeCommand("WhatsApp खोलो")
                        else com.example.tools.FastCommandRouter(context, com.example.tools.ToolExecutionEngine(context)).openWhatsApp()
                    }
                    FastChip("▶️ YouTube") {
                        val service = ZoyaForegroundService.activeService
                        if (service != null) service.executeCommand("YouTube खोलो")
                        else com.example.tools.FastCommandRouter(context, com.example.tools.ToolExecutionEngine(context)).openYouTube()
                    }
                    FastChip("📷 Cam") {
                        val service = ZoyaForegroundService.activeService
                        if (service != null) service.executeCommand("Camera खोलो")
                        else com.example.tools.FastCommandRouter(context, com.example.tools.ToolExecutionEngine(context)).openCamera()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Command Text Input
                var textCommand by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = textCommand,
                        onValueChange = { textCommand = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { innerTextField ->
                            if (textCommand.isEmpty()) {
                                Text("Command: e.g. Instagram खोलो...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    TextButton(
                        onClick = {
                            if (textCommand.isNotBlank()) {
                                val cmd = textCommand
                                textCommand = ""
                                val service = ZoyaForegroundService.activeService
                                if (service != null) {
                                    service.executeCommand(cmd)
                                } else {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        val router = com.example.tools.FastCommandRouter(context, com.example.tools.ToolExecutionEngine(context))
                                        val res = router.route(cmd)
                                        if (res.isHandled) {
                                            android.widget.Toast.makeText(context, res.responseText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Send", color = Color(0xFF80D8FF), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text("Enter your Gemini API key to use Sammu X AI.")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        placeholder = { Text("AIza...") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            if (tempKey.isNotEmpty()) {
                                IconButton(onClick = { tempKey = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear text"
                                    )
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Get your API key here",
                        color = Color(0xFF00B0FF),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit().putString("api_key", tempKey).apply()
                        apiKey = tempKey
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ZoyaOrb(state: ZoyaState, amplitude: Float = 0f) {
    val radiusScale = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0.5f) }

    val ring1Angle = remember { Animatable(0f) }
    val ring2Angle = remember { Animatable(120f) }
    val ring3Angle = remember { Animatable(240f) }
    val ring4Angle = remember { Animatable(45f) }

    LaunchedEffect(state) {
        when (state) {
            ZoyaState.IDLE -> {
                radiusScale.animateTo(1f, animationSpec = tween(1000))
                glowAlpha.animateTo(
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.LISTENING -> {
                radiusScale.animateTo(1.1f, animationSpec = tween(500))
                glowAlpha.animateTo(
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.THINKING -> {
                radiusScale.animateTo(1.05f, animationSpec = tween(400))
                glowAlpha.animateTo(
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.SPEAKING -> {
                radiusScale.animateTo(1.2f, animationSpec = tween(200))
                glowAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(300, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        launch {
            ring1Angle.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(6000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring2Angle.animateTo(
                targetValue = 360f + 120f,
                animationSpec = infiniteRepeatable(
                    animation = tween(7000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring3Angle.animateTo(
                targetValue = 360f + 240f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5500, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring4Angle.animateTo(
                targetValue = -360f + 45f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 4f
            val pulseBoost = if (state != ZoyaState.IDLE) amplitude * 0.4f else 0f
            val currentRadius = baseRadius * (radiusScale.value + pulseBoost)

            val coreInnerColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF80D8FF)
                ZoyaState.LISTENING -> Color(0xFFB388FF)
                ZoyaState.THINKING -> Color(0xFFFFD180)
                ZoyaState.SPEAKING -> Color(0xFF69F0AE)
            }

            val coreOuterColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF00B0FF)
                ZoyaState.LISTENING -> Color(0xFF651FFF)
                ZoyaState.THINKING -> Color(0xFFFF9100)
                ZoyaState.SPEAKING -> Color(0xFF00E676)
            }

            val dynamicGlowRadius = currentRadius * (2.5f + if (state != ZoyaState.IDLE) amplitude * 1.5f else 0f)
            val dynamicGlowAlpha = (glowAlpha.value + if (state != ZoyaState.IDLE) amplitude * 0.45f else 0f).coerceIn(0f, 1f)

            // 1. Ambient Background Glow (reacts dynamically to mic amplitude)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreOuterColor.copy(alpha = dynamicGlowAlpha * 0.55f), Color.Transparent),
                    center = center,
                    radius = dynamicGlowRadius
                ),
                radius = dynamicGlowRadius
            )

            // 2. The Glass Sphere (Core)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        coreInnerColor.copy(alpha = 0.8f),
                        coreOuterColor.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.5f)
                    ),
                    center = androidx.compose.ui.geometry.Offset(center.x - currentRadius * 0.3f, center.y - currentRadius * 0.3f),
                    radius = currentRadius * 1.2f
                ),
                radius = currentRadius
            )

            // Inner Core Highlight for 3D effect
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                center = androidx.compose.ui.geometry.Offset(center.x - currentRadius * 0.4f, center.y - currentRadius * 0.4f),
                radius = currentRadius * 0.3f
            )

            // 3. Neon Orbital Rings
            val ringRadiusX = currentRadius * 1.8f
            val ringRadiusY = currentRadius * 0.6f
            val ringStrokeWidth = 4f + if (state != ZoyaState.IDLE) amplitude * 5f else 0f

            fun drawNeonRing(angle: Float, startColor: Color, endColor: Color, strokeWidth: Float) {
                rotate(angle, center) {
                    drawOval(
                        brush = Brush.sweepGradient(
                            colors = listOf(startColor, endColor, startColor, Color.Transparent, startColor),
                            center = center
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = androidx.compose.ui.geometry.Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth)
                    )
                    drawOval(
                        color = startColor.copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = androidx.compose.ui.geometry.Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth * 3)
                    )
                }
            }

            val speedMultiplier = if (state == ZoyaState.THINKING || state == ZoyaState.SPEAKING) 2f else if (state == ZoyaState.LISTENING) 1f + amplitude * 2f else 1f

            drawNeonRing(ring1Angle.value * speedMultiplier, Color(0xFFFF1744), Color(0xFFD50000), ringStrokeWidth)
            drawNeonRing(ring2Angle.value * speedMultiplier, Color(0xFF00E676), Color(0xFF76FF03), ringStrokeWidth)
            drawNeonRing(ring3Angle.value * speedMultiplier, Color(0xFF00E5FF), Color(0xFF2979FF), ringStrokeWidth)
            drawNeonRing(ring4Angle.value * speedMultiplier, Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f), 2f)

            // 4. Outer Glass Dome Reflection
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.3f)),
                    center = center,
                    radius = currentRadius * 2.2f
                ),
                radius = currentRadius * 2.2f,
                style = Stroke(width = 2f)
            )

            // 5. Dynamic expanding pulse waves when user speaks into microphone
            if (state != ZoyaState.IDLE && amplitude > 0.04f) {
                val ripple1 = currentRadius * (1.25f + amplitude * 1.5f)
                drawCircle(
                    color = coreOuterColor.copy(alpha = (amplitude * 0.45f).coerceIn(0f, 0.45f)),
                    center = center,
                    radius = ripple1,
                    style = Stroke(width = (2f + amplitude * 4f))
                )
                val ripple2 = currentRadius * (1.6f + amplitude * 2.2f)
                drawCircle(
                    color = coreInnerColor.copy(alpha = (amplitude * 0.25f).coerceIn(0f, 0.25f)),
                    center = center,
                    radius = ripple2,
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    waveformBars: List<Float>,
    amplitude: Float,
    state: ZoyaState,
    modifier: Modifier = Modifier
) {
    val barColor1 = when (state) {
        ZoyaState.LISTENING -> Color(0xFF00E5FF)
        ZoyaState.SPEAKING -> Color(0xFF00E676)
        ZoyaState.THINKING -> Color(0xFFFF9100)
        ZoyaState.IDLE -> Color(0xFF80D8FF).copy(alpha = 0.5f)
    }

    val barColor2 = when (state) {
        ZoyaState.LISTENING -> Color(0xFFD500F9)
        ZoyaState.SPEAKING -> Color(0xFF76FF03)
        ZoyaState.THINKING -> Color(0xFFFFD180)
        ZoyaState.IDLE -> Color(0xFF00B0FF).copy(alpha = 0.4f)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val isActive = state != ZoyaState.IDLE
    val isVoiceActive = isActive && amplitude > 0.04f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = waveformBars.size.coerceAtLeast(1)
                val totalWidth = size.width * 0.92f
                val startX = (size.width - totalWidth) / 2f
                val slotWidth = totalWidth / count
                val barWidth = (slotWidth * 0.6f).coerceIn(2.5.dp.toPx(), 7.dp.toPx())
                val centerY = size.height / 2f
                val maxHeight = size.height * 0.92f

                // 1. Concentric Radial Pulse Glow around waveform when voice detected
                if (isVoiceActive) {
                    val pulseRadius = (size.width * 0.28f * (0.8f + amplitude * 1.2f)).coerceAtMost(size.width * 0.48f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                barColor1.copy(alpha = (amplitude * 0.35f).coerceIn(0f, 0.35f)),
                                barColor2.copy(alpha = (amplitude * 0.15f).coerceIn(0f, 0.15f)),
                                Color.Transparent
                            ),
                            center = center,
                            radius = pulseRadius
                        ),
                        radius = pulseRadius
                    )
                }

                // 2. Continuous Fluid Sine Wave Ribbon (animated flowing curve)
                if (isActive) {
                    val path = androidx.compose.ui.graphics.Path()
                    val waveHeight = (maxHeight * 0.35f * (0.2f + amplitude * 1.5f)).coerceAtMost(maxHeight * 0.48f)
                    val points = 60
                    val step = totalWidth / points

                    for (j in 0..points) {
                        val px = startX + j * step
                        val normalizedX = j.toFloat() / points
                        val rad = Math.toRadians((normalizedX * 720.0 + wavePhase).toDouble())
                        val py = centerY + (kotlin.math.sin(rad).toFloat() * waveHeight)
                        if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(listOf(barColor1, barColor2, barColor1)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = (2.dp.toPx() + amplitude * 2.dp.toPx()),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }

                // 3. Symmetric High-Density Spectrum Bars
                for (i in 0 until count) {
                    val rawVal = waveformBars.getOrElse(i) { 0.08f }
                    val distanceFromCenter = abs(i - (count - 1) / 2f) / ((count - 1) / 2f)
                    val envelope = 1f - 0.25f * distanceFromCenter
                    val scaledHeight = if (isActive) {
                        val dynamicVal = (rawVal * 0.65f + amplitude * 0.65f) * envelope
                        maxHeight * dynamicVal.coerceIn(0.12f, 1f)
                    } else {
                        maxHeight * 0.12f
                    }

                    val x = startX + i * slotWidth + slotWidth / 2f
                    val topY = centerY - scaledHeight / 2f
                    val bottomY = centerY + scaledHeight / 2f

                    // Bar glow halo behind active bar
                    if (isVoiceActive && amplitude > 0.08f) {
                        drawLine(
                            color = barColor1.copy(alpha = (amplitude * 0.32f).coerceIn(0f, 0.32f)),
                            start = androidx.compose.ui.geometry.Offset(x, topY),
                            end = androidx.compose.ui.geometry.Offset(x, bottomY),
                            strokeWidth = barWidth * 2.4f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    // Main gradient bar
                    val brush = Brush.verticalGradient(
                        colors = listOf(barColor1, barColor2),
                        startY = topY,
                        endY = bottomY
                    )

                    drawLine(
                        brush = brush,
                        start = androidx.compose.ui.geometry.Offset(x, topY),
                        end = androidx.compose.ui.geometry.Offset(x, bottomY),
                        strokeWidth = barWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Real-Time Voice Activity & Decibel Indicator Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(
                    if (isVoiceActive) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isVoiceActive) Color(0xFF00E5FF).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            // Pulsing live dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isVoiceActive) Color(0xFF00E676) else if (isActive) Color(0xFF00E5FF) else Color(0xFF80D8FF).copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                text = when {
                    isVoiceActive -> "🎙️ Live Voice (${(amplitude * 100).toInt()}%)"
                    state == ZoyaState.LISTENING -> "🎙️ Listening... Speak now"
                    state == ZoyaState.SPEAKING -> "🔊 Speaking..."
                    state == ZoyaState.THINKING -> "⚡ Thinking..."
                    else -> "🎙️ Voice Input Ready"
                },
                color = if (isVoiceActive) Color(0xFF80D8FF) else Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Dynamic mini visual level bars (5 bars)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (lvl in 1..5) {
                    val isLit = (amplitude * 5f) >= (lvl - 0.5f)
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = (5 + lvl * 2).dp)
                            .background(
                                if (isLit) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(onNavigateBack: () -> Unit) {
    val liveSessionManager = ZoyaForegroundService.activeService?.liveSessionManager
    val messages = liveSessionManager?.messages?.collectAsState(initial = emptyList())?.value ?: emptyList()

    val audioAmplitude by ZoyaForegroundService.audioAmplitude.collectAsState(initial = 0f)
    val waveformBars by ZoyaForegroundService.waveformBars.collectAsState(initial = List(28) { 0.08f })
    val smoothAmplitude by animateFloatAsState(
        targetValue = audioAmplitude,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "chatSmoothAmp"
    )

    Scaffold(
        containerColor = Color(0xFF1E1E2E),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF80D8FF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Back", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Status: ${ZoyaForegroundService.currentState.name}",
                color = Color(0xFF00E5FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Text(
                        text = message,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp
                    )
                }
            }

            AudioWaveformVisualizer(
                waveformBars = waveformBars,
                amplitude = smoothAmplitude,
                state = ZoyaForegroundService.currentState,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            var chatCommand by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = chatCommand,
                    onValueChange = { chatCommand = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    decorationBox = { innerTextField ->
                        if (chatCommand.isEmpty()) {
                            Text("Type command: e.g. Open Instagram...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )
                TextButton(
                    onClick = {
                        if (chatCommand.isNotBlank()) {
                            val cmd = chatCommand
                            chatCommand = ""
                            ZoyaForegroundService.activeService?.executeCommand(cmd)
                        }
                    }
                ) {
                    Text("Send", color = Color(0xFF80D8FF), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { ZoyaForegroundService.activeService?.reconnectSession() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF80D8FF)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Reconnect Uplink", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FastChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
