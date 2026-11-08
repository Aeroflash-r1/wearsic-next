package com.wearsic.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wearsic.next.engine.RustEngine
import com.wearsic.next.ui.WearsicNextApp
import com.wearsic.next.ui.data.FavoritesStore
import com.wearsic.next.ui.theme.WearsicPrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load the native engine as early as possible; failure is surfaced
        // in the UI state (not a crash).
        runCatching { RustEngine.load() }
        // Preference + favorites state must be backed BEFORE composition.
        WearsicPrefs.init(this)
        FavoritesStore.init(this)
        setContent {
            WearsicNextApp()
        }
    }
}
