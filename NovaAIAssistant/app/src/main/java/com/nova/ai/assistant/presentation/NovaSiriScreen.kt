package com.nova.ai.assistant.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nova.ai.assistant.domain.model.*
import com.nova.ai.assistant.presentation.components.AudioReactiveVisualizer

@Composable
fun NovaSiriScreen(viewModel: NovaViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showTypeDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onOrbClicked()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF03060D),
                            Color(0xFF080D19),
                            Color(0xFF020408)
                        )
                    )
                )
        ) {
            // Main Siri Stage
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Status Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "NOVA AI",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Status Indicator
                    val statusLabel = when (uiState.voiceState) {
                        is VoiceState.Listening -> "يستمع..."
                        is VoiceState.Processing -> "يفكر..."
                        is VoiceState.Speaking -> "يتحدث..."
                        is VoiceState.ExecutingAction -> "ينفذ..."
                        is VoiceState.Error -> "خطأ"
                        VoiceState.Idle -> "جاهز"
                    }
                    Text(
                        text = statusLabel,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                // Center: Glowing Voice Orb & Live Subtitles
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    AudioReactiveVisualizer(
                        voiceState = uiState.voiceState,
                        modifier = Modifier
                            .size(260.dp)
                            .clickable { viewModel.onOrbClicked() }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // User Spoken Subtitle (Siri Style)
                    if (uiState.spokenUserText.isNotBlank()) {
                        Text(
                            text = "«${uiState.spokenUserText}»",
                            fontSize = 20.sp,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Assistant Voice Output Subtitle
                    Text(
                        text = uiState.assistantReplyText,
                        fontSize = 16.sp,
                        color = Color(0xFF00E5FF).copy(alpha = 0.9f),
                        lineHeight = 22.sp
                    )

                    // Floating Siri Action Card (Call confirmation or Alarm)
                    AnimatedVisibility(visible = uiState.isPendingCallConfirmation) {
                        Box(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF10192A))
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "تأكيد الاتصال بـ ${uiState.pendingContactName}؟",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { viewModel.confirmCall() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                                    ) {
                                        Text("اتصال الآن", color = Color.Black)
                                    }
                                    OutlinedButton(onClick = { viewModel.dismissCall() }) {
                                        Text("إلغاء", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Siri Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type to Siri Button
                    IconButton(
                        onClick = { showTypeDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "الكتابة",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Main Microphone Trigger Orb
                    FloatingActionButton(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.onOrbClicked()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        containerColor = if (uiState.voiceState is VoiceState.Listening) Color(0xFFFF5252) else Color(0xFF00E5FF),
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.voiceState is VoiceState.Listening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "تحدث",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Stop Speaking Button
                    IconButton(
                        onClick = { viewModel.stopSpeaking() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "كتم",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Type To Siri Bottom Sheet / Dialog
            if (showTypeDialog) {
                AlertDialog(
                    onDismissRequest = { showTypeDialog = false },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    viewModel.processVoiceQuery(textInput)
                                    textInput = ""
                                    showTypeDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("تنفيذ", color = Color.Black)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTypeDialog = false }) {
                            Text("إلغاء", color = Color.White)
                        }
                    },
                    title = { Text("الكتابة إلى المساعد (Type to Siri)") },
                    text = {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("اكتب طلبك هنا...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    },
                    containerColor = Color(0xFF10192A)
                )
            }
        }
    }
}
