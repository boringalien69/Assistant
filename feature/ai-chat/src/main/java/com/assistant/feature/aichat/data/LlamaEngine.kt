package com.assistant.feature.aichat.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LlamaEngine"

data class ChatMessage(
    val role: String,    // "system" | "user" | "assistant"
    val content: String,
)

data class ModelParams(
    val nCtx: Int         = 4096,
    val temperature: Float = 0.8f,
    val topP: Float        = 0.95f,
    val topK: Int          = 40,
    val maxNewTokens: Int  = 2048,
    val nThreads: Int      = 0,   // 0 = auto (half cores)
)

interface TokenCallback {
    fun onToken(token: String)
    fun onDone()
    fun onError(message: String)
}

@Singleton
class LlamaEngine @Inject constructor() {

    private var nativeHandle: Long = 0L

    val isLoaded: Boolean
        get() = nativeIsLoaded(nativeHandle)

    val loadedModelPath: String?
        get() = if (isLoaded) nativeGetModelPath(nativeHandle) else null

    init {
        System.loadLibrary("assistant_llm")
        nativeHandle = nativeCreate()
    }

    suspend fun loadModel(
        modelPath: String,
        params: ModelParams = ModelParams(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "loadModel: $modelPath")
        val threads = if (params.nThreads <= 0)
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        else params.nThreads
        val ok = nativeLoadModel(nativeHandle, modelPath, params.nCtx, threads)
        if (ok) Result.success(Unit)
        else Result.failure(RuntimeException("Failed to load model: $modelPath"))
    }

    fun setSystemPrompt(prompt: String) = nativeSetSystemPrompt(nativeHandle, prompt)

    fun clearHistory() = nativeClearHistory(nativeHandle)

    /**
     * Run inference over a full message list (system + history + user).
     * Returns a Flow that emits tokens as they are generated.
     */
    fun runInference(messages: List<ChatMessage>, maxNewTokens: Int = 2048): Flow<String> =
        callbackFlow {
            if (!isLoaded) {
                close(IllegalStateException("No model loaded"))
                return@callbackFlow
            }
            val roles    = messages.map { it.role }.toTypedArray()
            val contents = messages.map { it.content }.toTypedArray()
            val cb = object : TokenCallback {
                override fun onToken(token: String) { trySend(token) }
                override fun onDone()  { close() }
                override fun onError(message: String) { close(RuntimeException(message)) }
            }
            withContext(Dispatchers.IO) {
                nativeRunInference(nativeHandle, roles, contents, cb, maxNewTokens)
            }
            awaitClose { nativeAbort(nativeHandle) }
        }

    fun cancelInference() = nativeAbort(nativeHandle)

    fun releaseModel() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = nativeCreate()
        }
    }

    // ── JNI ──────────────────────────────────────────────────────────────────
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeLoadModel(handle: Long, modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeIsLoaded(handle: Long): Boolean
    private external fun nativeGetModelPath(handle: Long): String?
    private external fun nativeSetSystemPrompt(handle: Long, prompt: String)
    private external fun nativeClearHistory(handle: Long)
    private external fun nativeRunInference(handle: Long, roles: Array<String>, contents: Array<String>, callback: TokenCallback, maxNewTokens: Int)
    private external fun nativeAbort(handle: Long)
}
