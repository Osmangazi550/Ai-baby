package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VoiceLanguage
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurfaceCard
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanGlow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SystemTelemetryCard(
    currentLanguage: VoiceLanguage,
    onLanguageSelected: (VoiceLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        CyberSurfaceCard,
                        CyberSurfaceVariant.copy(alpha = 0.85f)
                    )
                )
            )
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("system_telemetry_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Owner & AI Identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NeonCyanGlow)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI BABY SYSTEM CORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Owner Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF003745))
                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Owner: Jihad (Babu)",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyanGlow,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Specs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryItem(
                    icon = Icons.Default.RecordVoiceOver,
                    label = "VOICE ENGINE",
                    value = "Natural Female TTS"
                )
                TelemetryItem(
                    icon = Icons.Default.AutoAwesome,
                    label = "AI MODEL",
                    value = "Gemini 3.8 Flash"
                )
                TelemetryItem(
                    icon = Icons.Default.Security,
                    label = "APP CHECK",
                    value = "Active"
                )
            }

            // Language Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Language",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Speech Mode:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                VoiceLanguage.entries.forEach { lang ->
                    val isSelected = currentLanguage == lang
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) NeonCyan.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NeonCyan else CyberBorder,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onLanguageSelected(lang) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("lang_chip_${lang.shortCode}")
                    ) {
                        Text(
                            text = lang.shortCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) NeonCyan else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

data class QuickPrompt(
    val title: String,
    val prompt: String,
    val icon: ImageVector,
    val isBengali: Boolean = false
)

@Composable
fun QuickActionsRow(
    onActionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prompts = listOf(
        QuickPrompt("What's the weather?", "What's the weather?", Icons.Default.AutoAwesome),
        QuickPrompt("Set 5m Timer", "Set a timer for 5 minutes", Icons.Default.AutoAwesome),
        QuickPrompt("Tell me a Joke", "Tell me a joke", Icons.Default.AutoAwesome),
        QuickPrompt("Battery Level", "What is my battery level?", Icons.Default.BatteryChargingFull),
        QuickPrompt("Toggle Torch", "Turn on the flashlight", Icons.Default.FlashlightOn),
        QuickPrompt("আজকের আবহাওয়া", "বাবু, আজকের আবহাওয়া কেমন?", Icons.Default.RecordVoiceOver, isBengali = true),
        QuickPrompt("কৌতুক বলো", "বাবু, একটা কৌতুক বলো", Icons.Default.RecordVoiceOver, isBengali = true),
        QuickPrompt("Wi-Fi Settings", "Open Wi-Fi settings", Icons.Default.Settings),
        QuickPrompt("কয়টা বাজে?", "বাবু, এখন সময় কত?", Icons.Default.RecordVoiceOver, isBengali = true)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QUICK VOICE & SYSTEM COMMANDS",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEach { item ->
                QuickActionChip(
                    item = item,
                    onClick = { onActionSelected(item.prompt) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    item: QuickPrompt,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CyberSurfaceCard)
            .border(1.dp, CyberBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("quick_action_${item.title}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (item.isBengali) NeonCyan else ElectricBlue,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
