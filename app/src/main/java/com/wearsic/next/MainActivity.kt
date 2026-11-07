package com.wearsic.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wearsic.next.engine.RustEngine
import com.wearsic.next.ui.WearsicNextApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load the native engine as early as possible; failure is surfaced
        // in the UI state (not a crash).
        runCatching { RustEngine.load() }
        setContent {
            WearsicNextApp()
        }
    }
}
