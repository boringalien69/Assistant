package com.assistant.feature.aichat.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.assistant.feature.aichat.data.local.DownloadRepository
import com.assistant.feature.aichat.data.local.ModelRepository
import com.assistant.core.database.entity.ModelConfigEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadRepository: DownloadRepository,
    private val modelRepository: ModelRepository,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_TASK_ID     = "task_id"
        const val KEY_URL         = "url"
        const val KEY_DEST_PATH   = "dest_path"
        const val KEY_DISPLAY_NAME = "display_name"
        const val NOTIF_CHANNEL   = "model_download"
        const val NOTIF_ID        = 1001

        fun buildRequest(taskId: String, url: String, destPath: String, displayName: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TASK_ID      to taskId,
                        KEY_URL          to url,
                        KEY_DEST_PATH    to destPath,
                        KEY_DISPLAY_NAME to displayName,
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId      = inputData.getString(KEY_TASK_ID)      ?: return@withContext Result.failure()
        val url         = inputData.getString(KEY_URL)          ?: return@withContext Result.failure()
        val destPath    = inputData.getString(KEY_DEST_PATH)    ?: return@withContext Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: return@withContext Result.failure()

        createNotificationChannel()
        setForeground(buildForegroundInfo(displayName, 0))

        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()

        // Resumable: check existing bytes
        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        try {
            downloadRepository.updateProgress(taskId, existingBytes, "ACTIVE")

            val requestBuilder = Request.Builder().url(url)
            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful && response.code != 206) {
                downloadRepository.updateProgress(taskId, existingBytes, "FAILED")
                return@withContext Result.retry()
            }

            val totalBytes = if (existingBytes > 0) {
                // 206 Partial Content — Content-Range: bytes X-Y/total
                val contentRange = response.header("Content-Range") ?: ""
                val total = contentRange.substringAfterLast("/").toLongOrNull() ?: -1L
                total
            } else {
                response.header("Content-Length")?.toLongOrNull() ?: -1L
            }

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(destFile, existingBytes > 0).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesWritten = existingBytes
                    var lastCheckpoint = existingBytes
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesWritten += read

                        // Checkpoint every ~1MB
                        if (bytesWritten - lastCheckpoint >= 1_000_000) {
                            lastCheckpoint = bytesWritten
                            downloadRepository.updateProgress(taskId, bytesWritten, "ACTIVE")

                            val progress = if (totalBytes > 0) {
                                ((bytesWritten.toFloat() / totalBytes) * 100).toInt()
                            } else 0
                            setForeground(buildForegroundInfo(displayName, progress))
                        }
                    }

                    downloadRepository.updateProgress(taskId, bytesWritten, "COMPLETE")
                }
            }

            // Register model in DB
            modelRepository.saveModel(
                ModelConfigEntity(
                    modelPath   = destPath,
                    displayName = displayName,
                    isActive    = false,
                    lastUsedAt  = System.currentTimeMillis(),
                )
            )

            Result.success()

        } catch (e: Exception) {
            downloadRepository.updateProgress(taskId, existingBytes, "FAILED")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL,
                "MODEL DOWNLOAD",
                NotificationManager.IMPORTANCE_LOW,
            )
            applicationContext
                .getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundInfo(modelName: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL)
            .setContentTitle("ACQUIRING MODEL")
            .setContentText("$modelName — $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else 0
        )
    }
}
