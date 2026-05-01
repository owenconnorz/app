package com.aioweb.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.aioweb.app.ui.AioWebApp
import com.aioweb.app.ui.theme.AioWebTheme
import com.lagradost.cloudstream3.installPrefs

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore non-splash theme before super
        setTheme(R.style.Theme_AioWeb)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize CloudStream plugin system — must happen early so plugins can access
        // shared prefs and HTTP client when they're loaded
        installPrefs(this)
        
        // Android 13+ requires explicit POST_NOTIFICATIONS permission to surface the
        // media-session notification when music is playing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PermissionChecker.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AioWebTheme {
                AioWebApp()
            }
        }
    }
}
