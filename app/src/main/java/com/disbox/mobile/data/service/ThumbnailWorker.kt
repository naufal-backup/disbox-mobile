package com.disbox.mobile.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.disbox.mobile.data.repository.DisboxRepository
import com.disbox.mobile.utils.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class ThumbnailWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DisboxRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fileId = inputData.getString("file_id") ?: return@withContext Result.failure()
        val filePath = inputData.getString("file_path") ?: return@withContext Result.failure()
        val messageIdsJson = inputData.getString("message_ids") ?: return@withContext Result.failure()
        
        val cacheKey = "thumb_${fileId}"
        val targetFile = File(context.cacheDir, cacheKey)
        
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext Result.success()
        }

        try {
            val fileName = filePath.split("/").last()
            val isVideo = isVideoFile(fileName)
            val isImage = isImageFile(fileName)
            val isAudio = isAudioFile(fileName)
            
            if (!isVideo && !isImage && !isAudio) return@withContext Result.success()

            // For large files, we only need the first chunk for thumbnail usually
            // but for videos we might need more? Let's start with first chunk.
            val firstChunk = repository.downloadFileChunkFromMsg(messageIdsJson, 0) ?: return@withContext Result.failure()
            
            var bitmap: Bitmap? = null
            
            if (isImage) {
                bitmap = BitmapFactory.decodeByteArray(firstChunk, 0, firstChunk.size)
            } else if (isVideo || isAudio) {
                // For video/audio, we might need to save to a temp file first for Retriever
                val tempFile = File(context.cacheDir, "temp_thumb_${fileId}")
                FileOutputStream(tempFile).use { it.write(firstChunk) }
                
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(tempFile.absolutePath)
                    if (isVideo) {
                        bitmap = retriever.getFrameAtTime(1000000) // 1 second
                    } else if (isAudio) {
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ThumbnailWorker", "Failed to extract metadata", e)
                } finally {
                    retriever.release()
                    tempFile.delete()
                }
            }

            if (bitmap != null) {
                // Scale down for thumbnail
                val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
                FileOutputStream(targetFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.WEBP, 80, out)
                }
                bitmap.recycle()
                scaled.recycle()
                return@withContext Result.success()
            }
            
            Result.failure()
        } catch (e: Exception) {
            Log.e("ThumbnailWorker", "Thumbnail generation failed", e)
            Result.failure()
        }
    }
}
