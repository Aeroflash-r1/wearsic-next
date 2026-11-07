package com.wearsic.next.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text

/**
 * Settings — deliberately has NO server URL/API key configuration: Wearsic
 * Next is fully standalone (Rust engine in-process). Only user-facing
 * preferences and diagnostics live here.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    var shufflePref by remember { mutableStateOf(false) }
    var repeatPref by remember { mutableStateOf(false) }
    var hardwareAccelPref by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = Color(0xFFC4B0FF),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "SETTINGS",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF232027))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Back", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Playback preferences (local only — no server).
        PrefToggle("Shuffle by default", shufflePref) { shufflePref = it }
        PrefToggle("Repeat after track", repeatPref) { repeatPref = it }
        PrefToggle("Hardware audio decode", hardwareAccelPref) { hardwareAccelPref = it }

        Spacer(Modifier.height(18.dp))

        // About / privacy (no network config anywhere).
        InfoCard()

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Wearsic Next is fully standalone — no server, phone or API key required.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrefToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141318))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(36.dp, 22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) Color(0xFFC4B0FF) else Color(0xFF232027)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (checked) Color(0xFF1C1A22) else Color(0xFF4A464F)),
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141318))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = Color(0xFFC4B0FF),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "ABOUT",
                color = Color(0xFFC4B0FF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Wearsic Next", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Rust-powered standalone Wear OS music player.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Engine: embedded Rust (InnerTube search + stream resolution). Playback: Android Media3.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
        )
    }
}
