package com.assistant.feature.aichat.ui

import androidx.navigation.*
import androidx.navigation.compose.composable
import com.assistant.core.navigation.Routes
import com.assistant.feature.aichat.ui.chat.ChatScreen
import com.assistant.feature.aichat.ui.chatlist.ChatListScreen
import com.assistant.feature.aichat.ui.model.ModelPickerScreen

// This extension function is imported by core:navigation's AppNavHost
// via the app shell — it registers all ai-chat screens into the nav graph.
fun NavGraphBuilder.aiChatNavGraph(navController: NavHostController) {
    navigation(
        startDestination = Routes.CHAT_LIST,
        route            = "ai_chat_graph",
    ) {
        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onChatClick = { chatId ->
                    navController.navigate(Routes.chatRoute(chatId))
                },
                onNewChat = {
                    // Create a new conversation — for now navigate to chat with a new UUID
                    // Real implementation creates chat in VM then navigates
                    navController.navigate("new_chat")
                },
                onOpenModelLibrary = {
                    navController.navigate(Routes.MODEL_LIBRARY)
                },
            )
        }

        composable(
            route     = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) {
            ChatScreen(
                onBack            = { navController.popBackStack() },
                onOpenModelLibrary = { navController.navigate(Routes.MODEL_LIBRARY) },
            )
        }

        composable(Routes.MODEL_PICKER) {
            ModelPickerScreen(
                onModelReady = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.MODEL_PICKER) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MODEL_LIBRARY) {
            // ModelLibraryScreen — placeholder routed correctly
            ModelPickerScreen(
                onModelReady = { navController.popBackStack() },
            )
        }

        // New chat — creates conversation then navigates into it
        composable("new_chat") {
            NewChatEntryPoint(
                onCreated = { chatId ->
                    navController.navigate(Routes.chatRoute(chatId)) {
                        popUpTo("new_chat") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
