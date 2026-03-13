package com.assistant.feature.aichat.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessage(
    val role: String,    // "system" | "user" | "assistant"
    val content: String,
)

data class ModelParams(
    val nCtx: Int          = 2048,
    val temperature: Float = 0.7f,
    val topP: Float        = 0.9f,
    val topK: Int          = 40,
    val maxNewTokens: Int  = 512,
    val nThreads: Int      = 0,    // 0 = auto
)

@Singleton
class LlamaEngine @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("assistant_llm")
        }
    }

    // JNI declarations
    private external fun nativeCreate(): Long
    private external fun nativeLoadModel(
        ptr: Long,
        fd: Int, offset: Long, length: Long,
        nCtx: Int, temperature: Float, topP: Float, topK: Int,
        maxNewTokens: Int, nThreads: Int,
    ): Boolean
    private external fun nativeRunInference(
        ptr: Long,
        roles: Array<String>,
        contents: Array<String>,
        callback: TokenCallback,
    )
    private external fun nativeCancel(ptr: Long)
    private external fun nativeRelease(ptr: Long)
    private external fun nativeDestroy(ptr: Long)
    private external fun nativeIsLoaded(ptr: Long): Boolean

    interface TokenCallback {
        fun onToken(token: String)
    }

    private val mutex = Mutex()
    private var nativePtr: Long = 0L
    private var currentModelPath: String? = null

    val isLoaded: Boolean get() = nativePtr != 0L && nativeIsLoaded(nativePtr)
    val loadedModelPath: String? get() = currentModelPath

    suspend fun loadModel(
        context: Context,
        modelFile: File,
        params: ModelParams = ModelParams(),
    ): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (nativePtr != 0L) {
                    nativeRelease(nativePtr)
                    nativeDestroy(nativePtr)
                    nativePtr = 0L
                    currentModelPath = null
                }

                nativePtr = nativeCreate()

                val pfd = android.os.ParcelFileDescriptor.open(
                    modelFile,
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY,
                )

                val success = nativeLoadModel(
                    nativePtr,
                    pfd.fd,
                    0L,
                    modelFile.length(),
                    params.nCtx,
                    params.temperature,
                    params.topP,
                    params.topK,
                    params.maxNewTokens,
                    params.nThreads,
                )

                pfd.close()

                if (success) {
                    currentModelPath = modelFile.absolutePath
                    Result.success(Unit)
                } else {
                    nativeDestroy(nativePtr)
                    nativePtr = 0L
                    Result.failure(IllegalStateException("MODEL_LOAD_FAILED"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun runInference(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        val roles    = messages.map { it.role }.toTypedArray()
        val contents = messages.map { it.content }.toTypedArray()

        val callback = object : TokenCallback {
            override fun onToken(token: String) { trySend(token) }
        }

        withContext(Dispatchers.IO) {
            mutex.withLock {
                nativeRunInference(nativePtr, roles, contents, callback)
            }
        }

        close()
        awaitClose()
    }.flowOn(Dispatchers.IO)

    suspend fun cancelInference() = withContext(Dispatchers.IO) {
        if (nativePtr != 0L) nativeCancel(nativePtr)
    }

    suspend fun releaseModel() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (nativePtr != 0L) {
                nativeRelease(nativePtr)
                nativeDestroy(nativePtr)
                nativePtr = 0L
                currentModelPath = null
            }
        }
    }
}
