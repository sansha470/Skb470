package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import com.example.ui.theme.SantBrowserTheme

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by browserViewModel.themeMode.collectAsStateWithLifecycle()
            val isIncognito by browserViewModel.isIncognitoMode.collectAsStateWithLifecycle()

            SantBrowserTheme(
                themeMode = themeMode,
                isIncognito = isIncognito
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen(viewModel = browserViewModel)
                }
            }
        }
    }
}
