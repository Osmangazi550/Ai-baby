package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.AssistantState
import com.example.data.model.VoiceLanguage
import com.example.ui.components.ChatHistoryList
import com.example.ui.components.MicActionButton
import com.example.ui.components.QuickActionsRow
import com.example.ui.components.SystemTelemetryCard
import com.example.ui.components.VisualizerOrb
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanGlow
import com.example.ui.theme.StateError
import com.example.ui.theme.StateListening
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AssistantViewModel

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val state by viewModel.assistantState.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val currentLanguage by viewModel.selectedLanguage.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val liveTranscription by viewModel.liveTranscription.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    var typedInputText by remember { mutableStateOf("") }
    var showChatDrawer by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Runtime Permission for RECORD_AUDIO
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice recognition. You can also type queries.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun handleMicTap() {
        if (state is AssistantState.Listening) {
            viewModel.stopListening()
            return
        }
        if (state is AssistantState.Speaking) {
            viewModel.stopSpeaking()
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = CyberBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Futuristic Header Bar
                TopActionBar(
                    currentLanguage = currentLanguage,
                    isSpeaking = isSpeaking,
                    onLanguageSelected = { viewModel.setLanguage(it) },
                    onToggleVoice = {
                        if (isSpeaking) viewModel.stopSpeaking()
                    },
                    onOpenHistory = { showChatDrawer = !showChatDrawer },
                    onOpenHelp = { showHelpDialog = true }
                )

                // 2. Clear Active Listening Indicator or Error Banner
                AnimatedVisibility(
                    visible = state is AssistantState.Listening,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    ActiveListeningBanner(
                        language = currentLanguage,
                        liveTranscription = liveTranscription,
                        amplitude = amplitude,
                        onStopListening = { viewModel.stopListening() }
                    )
                }

                AnimatedVisibility(
                    visible = state is AssistantState.Error,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    val errorState = state as? AssistantState.Error
                    val isVoiceRelated = errorState?.message?.let { msg ->
                        msg.contains("Voice", ignoreCase = true) ||
                                msg.contains("Microphone", ignoreCase = true) ||
                                msg.contains("Speech", ignoreCase = true) ||
                                msg.contains("recognition", ignoreCase = true) ||
                                msg.contains("audio", ignoreCase = true) ||
                                msg.contains("language", ignoreCase = true)
                    } ?: true

                    ErrorBanner(
                        errorMessage = errorState?.message ?: "Voice recognition interrupted.",
                        onRetry = {
                            viewModel.dismissError()
                            if (isVoiceRelated) {
                                handleMicTap()
                            } else {
                                viewModel.retryLastQuery()
                            }
                        },
                        onDismiss = { viewModel.dismissError() }
                    )
                }

                // 3. AI Voice Visualizer Zone
                VisualizerSection(
                    state = state,
                    amplitude = amplitude,
                    liveTranscription = liveTranscription,
                    onOrbClick = { handleMicTap() }
                )

                // 4. System Telemetry & Language
                SystemTelemetryCard(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { lang -> viewModel.setLanguage(lang) }
                )

                // 5. Quick Actions / Test Prompts
                QuickActionsRow(
                    onActionSelected = { prompt ->
                        viewModel.processUserQuery(prompt)
                    }
                )

                // 6. Hero Mic Trigger
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    MicActionButton(
                        state = state,
                        onClick = { handleMicTap() }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (state) {
                            is AssistantState.Listening -> "Microphone Active • Tap to Stop"
                            is AssistantState.Speaking -> "AI BABY Speaking • Tap to Mute"
                            is AssistantState.Thinking -> "AI BABY Thinking (Gemini 3.8 Flash)..."
                            else -> "Tap Mic to Talk with AI BABY"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (state) {
                            is AssistantState.Listening -> StateListening
                            is AssistantState.Speaking -> ElectricBlue
                            else -> TextSecondary
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 7. Text Query Fallback Input Field (ensures testing in all conditions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = typedInputText,
                        onValueChange = { typedInputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_input_field"),
                        placeholder = {
                            Text(
                                text = "Ask Babu anything or type in English / বাংলা...",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedContainerColor = CyberSurface,
                            unfocusedContainerColor = CyberSurfaceCard,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (typedInputText.isNotBlank()) {
                                    viewModel.processUserQuery(typedInputText)
                                    typedInputText = ""
                                    keyboardController?.hide()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (typedInputText.isNotBlank()) {
                                viewModel.processUserQuery(typedInputText)
                                typedInputText = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(NeonCyan, ElectricBlue))
                            )
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = Color(0xFF00222B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 8. Embedded Conversation Stream (displays recognized speech and AI responses)
                ChatHistoryList(
                    messages = messages,
                    onReplayAudio = { msg -> viewModel.replayMessage(msg) },
                    onClearHistory = { viewModel.clearChat() }
                )
            }

            // Slide-over Full Chat History Drawer
            AnimatedVisibility(
                visible = showChatDrawer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CyberBackground.copy(alpha = 0.98f))
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONVERSATION HISTORY",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showChatDrawer = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        ChatHistoryList(
                            messages = messages,
                            onReplayAudio = { msg -> viewModel.replayMessage(msg) },
                            onClearHistory = { viewModel.clearChat() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Info & Testing Guide Dialog
            if (showHelpDialog) {
                AlertDialog(
                    onDismissRequest = { showHelpDialog = false },
                    title = {
                        Text(
                            text = "AI BABY Voice Assistant Guide",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Welcome Babu! Here is how voice recognition works:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• Continuous Microphone: When you tap the mic, the microphone stays active until speech is detected or you tap to stop.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "• Bengali & English: Speak in English or বাংলা. AI BABY automatically detects your language and speaks back addressing you as \"Babu\" or \"বাবু\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "• System Commands: Try saying \"Check battery\", \"Toggle flashlight\", \"Open Wi-Fi\", or \"এখন কয়টা বাজে?\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "• Text Input: You can also type any question in the bottom box to test Gemini 3.8 Flash and natural female speech synthesis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showHelpDialog = false }) {
                            Text("Got it, Babu!", color = NeonCyan)
                        }
                    },
                    containerColor = CyberSurfaceCard,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveListeningBanner(
    language: VoiceLanguage,
    liveTranscription: String,
    amplitude: Float,
    onStopListening: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "ActiveListeningPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF042F2E).copy(alpha = 0.85f),
                        Color(0xFF064E3B).copy(alpha = 0.85f)
                    )
                )
            )
            .border(1.5.dp, StateListening.copy(alpha = pulseAlpha), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("active_listening_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(StateListening.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening",
                        tint = StateListening,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "AI BABY IS LISTENING...",
                        style = MaterialTheme.typography.labelSmall,
                        color = StateListening,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (liveTranscription.isNotBlank()) {
                        Text(
                            text = "\"$liveTranscription\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = when (language) {
                                VoiceLanguage.BANGLA -> "বাবু, বাংলায় বলুন... শুনছি"
                                VoiceLanguage.ENGLISH -> "Speak now in English, Babu..."
                                VoiceLanguage.AUTO -> "Speak now in English or বাংলায় কথা বলুন"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            IconButton(
                onClick = onStopListening,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF065F46))
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Done Listening",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val isVoiceError = errorMessage.contains("Microphone", ignoreCase = true) ||
            errorMessage.contains("audio", ignoreCase = true) ||
            errorMessage.contains("permission", ignoreCase = true) ||
            errorMessage.contains("speech", ignoreCase = true)

    val bannerTitle = if (isVoiceError) "VOICE INPUT NOTICE" else "ASSISTANT NOTICE"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3B1219).copy(alpha = 0.9f))
            .border(1.dp, StateError.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("error_banner")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = StateError,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bannerTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = StateError,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonCyan
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Try Again", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TopActionBar(
    currentLanguage: VoiceLanguage,
    isSpeaking: Boolean,
    onLanguageSelected: (VoiceLanguage) -> Unit,
    onToggleVoice: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenHelp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface.copy(alpha = 0.9f))
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(NeonCyan, ElectricBlue))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AB",
                    color = Color(0xFF030712),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "AI BABY",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Voice Assistant for Babu",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Quick Language Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberSurfaceCard)
                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        val next = when (currentLanguage) {
                            VoiceLanguage.AUTO -> VoiceLanguage.BANGLA
                            VoiceLanguage.BANGLA -> VoiceLanguage.ENGLISH
                            VoiceLanguage.ENGLISH -> VoiceLanguage.AUTO
                        }
                        onLanguageSelected(next)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentLanguage.shortCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            if (isSpeaking) {
                IconButton(
                    onClick = onToggleVoice,
                    modifier = Modifier.size(32.dp).testTag("stop_voice_playback")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = "Stop Speech",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier.size(32.dp).testTag("chat_history_icon")
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Chat History",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOpenHelp,
                modifier = Modifier.size(32.dp).testTag("help_icon")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Help Guide",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VisualizerSection(
    state: AssistantState,
    amplitude: Float,
    liveTranscription: String,
    onOrbClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CyberSurfaceCard,
                        CyberBackground
                    )
                )
            )
            .border(1.dp, CyberBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Voice Visualizer Orb
        VisualizerOrb(
            state = state,
            amplitude = amplitude,
            size = 200.dp,
            onClick = onOrbClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        // State indicator badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(state.glowColor.copy(alpha = 0.15f))
                .border(1.dp, state.glowColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = state.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = state.glowColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        // Live transcription subtitle when speaking or listening
        if (liveTranscription.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "\"$liveTranscription\"",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
