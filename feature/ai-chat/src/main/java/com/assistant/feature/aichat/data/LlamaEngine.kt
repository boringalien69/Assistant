package com.assistant.feature.aichat.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LlamaEngine"

/**
 * JNI callback interface — C++ calls these during generation.
 */
interface GenerationCallback {
    fun onToken(token: String)
    fun onDone()
}

@Singleton
class LlamaEngine @Inject constructor() {

    private var nativeHandle: Long = 0L
    private var isLoaded = false

    init {
        System.loadLibrary("assistant_llm")
        nativeHandle = nativeCreate()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    suspend fun loadModel(
        modelPath: String,
        nCtx: Int = 4096,
        nThreads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Loading model: $modelPath  ctx=$nCtx  threads=$nThreads")
        val ok = nativeLoadModel(nativeHandle, modelPath, nCtx, nThreads)
        isLoaded = ok
        if (!ok) Log.e(TAG, "Model load failed")
        ok
    }

    fun setSystemPrompt(prompt: String) {
        if (isLoaded) nativeSetSystemPrompt(nativeHandle, prompt)
    }

    fun clearHistory() {
        if (isLoaded) nativeClearHistory(nativeHandle)
    }

    /**
     * Returns a Flow that emits token strings as they are generated.
     * Flow completes when generation finishes or is aborted.
     */
    fun generate(userMessage: String, maxNewTokens: Int = 2048): Flow<String> =
        callbackFlow {
            if (!isLoaded) {
                close(IllegalStateException("Model not loaded"))
                return@callbackFlow
            }

            val callback = object : GenerationCallback {
                override fun onToken(token: String) {
                    trySend(token)
                }
                override fun onDone() {
                    close()
                }
            }

            withContext(Dispatchers.IO) {
                nativeGenerate(nativeHandle, userMessage, callback, maxNewTokens)
            }

            awaitClose { nativeAbort(nativeHandle) }
        }

    fun abort() {
        nativeAbort(nativeHandle)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
            isLoaded = false
        }
    }

    // ── JNI declarations ─────────────────────────────────────────────────────

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeLoadModel(handle: Long, modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeSetSystemPrompt(handle: Long, prompt: String)
    private external fun nativeClearHistory(handle: Long)
    private external fun nativeGenerate(handle: Long, userMessage: String, callback: GenerationCallback, maxNewTokens: Int)
    private external fun nativeAbort(handle: Long)
}
