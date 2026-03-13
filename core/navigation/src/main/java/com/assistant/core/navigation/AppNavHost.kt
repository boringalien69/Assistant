package com.assistant.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

// Route constants — shared across all modules
object Routes {
    // feature:ai-chat routes
    const val CHAT_LIST         = "chat_list"
    const val CHAT              = "chat/{chatId}"
    const val MODEL_PICKER      = "model_picker"
    const val MODEL_LIBRARY     = "model_library"
    const val PERSONA_SETTINGS  = "persona_settings"

    fun chatRoute(chatId: String) = "chat/$chatId"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
) {
    // The start destination is determined at runtime by the feature module:
    // - If no model downloaded → MODEL_PICKER
    // - Otherwise → CHAT_LIST
    // This is handled inside the feature:ai-chat nav graph registration.
    NavHost(
        navController = navController,
        startDestination = "ai_chat_graph",
    ) {
        // Feature modules register their own nav graphs here via extension functions.
        // The app shell calls this — features never reference each other.
        aiChatNavGraph(navController)
    }
}
