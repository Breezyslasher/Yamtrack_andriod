package com.yamtrack.app.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Lays the screen out between the status and navigation bars.
 *
 * From Android 15 (targetSdk 35) apps are always drawn edge-to-edge, under
 * the system bars, and the theme's bar colours are ignored. This app's
 * layouts aren't built for that, so pad the content by the bar (and
 * keyboard) insets instead; the padding shows the window background where
 * the bars are. Done on every Android version so all of them look the same.
 * Call after setContentView.
 */
fun Activity.fitContentToSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        // With the window no longer fitting the bars, the keyboard doesn't
        // resize it either: keep focused fields above the keyboard.
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
        // Consumed, so views like the bottom navigation don't pad again.
        WindowInsetsCompat.CONSUMED
    }
}
