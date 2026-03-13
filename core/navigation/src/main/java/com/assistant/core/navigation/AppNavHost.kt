package com.assistant.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

// Route constants — shared across all modules via core:navigation
object Routes {
    const val CHAT_LIST         = "chat_list"
    const val CHAT              = "chat/{chatId}"
    const val MODEL_PICKER      = "model_picker"
    const val MODEL_LIBRARY     = "model_library"
    const val PERSONA_SETTINGS  = "persona_settings"

    fun chatRoute(chatId: String) = "chat/$chatId"
}

// AppNavHost takes the nav graph builder as a lambda —
// the app shell passes in feature registrations without core knowing about features.
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = "ai_chat_graph",
    builder: androidx.navigation.NavGraphBuilder.(NavHostController) -> Unit,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
    ) {
        builder(navController)
    }
}
