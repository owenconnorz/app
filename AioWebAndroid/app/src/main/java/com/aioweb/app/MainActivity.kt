package com.aioweb.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aioweb.app.ui.AioWebApp
import com.aioweb.app.ui.theme.AioWebTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore non-splash theme before super
        setTheme(R.style.Theme_AioWeb)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AioWebTheme {
                AioWebApp()
            }
        }
    }
}
