package com.assistant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.assistant.core.navigation.AppNavHost
import com.assistant.core.theme.AssistantTheme
import com.assistant.feature.aichat.ui.aiChatNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AssistantTheme.colors.background
                ) {
                    val navController = rememberNavController()
                    // App shell wires feature nav graphs — core:navigation never touches features
                    AppNavHost(navController = navController) { nav ->
                        aiChatNavGraph(nav)
                    }
                }
            }
        }
    }
}
