package com.wearsic.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wearsic.next.engine.RustEngine

/** UI state for the engine status card. */
sealed interface EngineUiState {
    data object Loading : EngineUiState
    data class Ready(val version: String, val status: String) : EngineUiState
    data class Failed(val error: String) : EngineUiState
}

@Composable
fun WearsicNextApp() {
    var engineState by remember { mutableStateOf<EngineUiState>(EngineUiState.Loading) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        engineState = EngineUiState.Loading
        engineState = try {
            val engine = RustEngine.create()
            EngineUiState.Ready(engine.version, engine.status)
        } catch (t: Throwable) {
            EngineUiState.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        // Round face: keep everything inside the inscribed square.
        val safe = minOf(width, height)

        Box(
            modifier = Modifier
                .size(safe)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color(0xFF0A0A0A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Branding
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFC4B0FF),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "WEARSIC NEXT",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Rust-powered standalone engine",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                // Engine status card
                when (val s = engineState) {
                    is EngineUiState.Loading -> EngineCard {
                        Text("Initializing engine…", color = Color.White, fontSize = 12.sp)
                    }

                    is EngineUiState.Ready -> {
                        EngineCard {
                            StatusRow(ok = true, label = "Rust engine loaded")
                            Spacer(Modifier.height(8.dp))
                            StatusRow(ok = true, label = "Version ${s.version}")
                        }
                        Spacer(Modifier.height(16.dp))
                        DiagnosticsCard(s.status)
                    }

                    is EngineUiState.Failed -> EngineCard {
                        StatusRow(ok = false, label = "Engine failed")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = s.error,
                            color = Color(0xFFF2B8C6),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { attempt++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC4B0FF),
                                contentColor = Color(0xFF1C1A22),
                            ),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Phase 2 foundation — engine load check",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EngineCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141318))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun DiagnosticsCard(statusJson: String) {
    val summary = remember(statusJson) { parseStatusSummary(statusJson) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141318))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.Start,
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
                text = "DIAGNOSTICS",
                color = Color(0xFFC4B0FF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        summary.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$k:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = v,
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End,
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) Color(0xFF9DB89A) else Color(0xFFF2B8C6),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = if (ok) FontWeight.Medium else FontWeight.Bold,
        )
    }
}

/** Tiny tolerant extractor for the engine status JSON (no dependency). */
private fun parseStatusSummary(json: String): List<Pair<String, String>> {
    if (json.isBlank()) return listOf("status" to "unknown")
    val out = mutableListOf<Pair<String, String>>()
    for (key in listOf("name", "version", "phase", "ops", "uptime_s")) {
        val m = Regex("\"$key\"\\s*:\\s*\"?([^\",}\\s]+)")
            .find(json)
        if (m != null) out += key to m.groupValues[1]
    }
    if (out.isEmpty()) out += "status" to json.take(40)
    return out
}
