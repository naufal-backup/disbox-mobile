package com.disbox.mobile.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.disbox.mobile.data.repository.DisboxRepository
import com.disbox.mobile.model.DisboxFile
import kotlinx.coroutines.runBlocking
import kotlin.math.min

class DiscordDataSource(
    private val repository: DisboxRepository,
    private val file: DisboxFile
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var bytesRemaining: Long = 0
    private var currentOffset: Long = 0
    private var isOpen = false

    private var cachedChunkIndex: Int = -1
    private var cachedChunkData: ByteArray? = null
    private var actualChunkSize: Long = -1L

    private fun getChunkSize(): Long {
        if (actualChunkSize > 0) return actualChunkSize
        if (file.messageIds.isEmpty()) return 10 * 1024 * 1024L
        
        val chunk0 = fetchChunk(0)
        actualChunkSize = if (file.messageIds.size == 1) file.size else chunk0.size.toLong()
        return actualChunkSize
    }

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        currentOffset = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) file.size - currentOffset else dataSpec.length
        isOpen = true
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesRead = try {
            val cSize = getChunkSize()
            val chunkIndex = (currentOffset / cSize).toInt()
            if (chunkIndex >= file.messageIds.size) return C.RESULT_END_OF_INPUT

            val chunkBytes = fetchChunk(chunkIndex)
            val offsetInChunk = (currentOffset % cSize).toInt()
            val availableInChunk = chunkBytes.size - offsetInChunk
            if (availableInChunk <= 0) return C.RESULT_END_OF_INPUT 

            val toCopyLong = min(min(length, availableInChunk).toLong(), bytesRemaining).toInt()
            System.arraycopy(chunkBytes, offsetInChunk, buffer, offset, toCopyLong)
            toCopyLong
        } catch (e: Exception) {
            e.printStackTrace()
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRead > 0) {
            currentOffset += bytesRead
            bytesRemaining -= bytesRead
        }
        return bytesRead
    }

    override fun getUri(): Uri? = dataSpec?.uri
    override fun close() { isOpen = false }

    private fun fetchChunk(index: Int): ByteArray {
        if (index == cachedChunkIndex && cachedChunkData != null) return cachedChunkData!!
        
        var attempts = 0
        var lastError: Exception? = null
        
        while (attempts < 3) {
            try {
                val data = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { 
                    repository.downloadFileChunk(file.messageIds[index].msgId, file.messageIds[index].index) 
                }
                if (data != null) {
                    cachedChunkIndex = index
                    cachedChunkData = data
                    return data
                }
            } catch (e: Exception) {
                lastError = e
                e.printStackTrace()
            }
            attempts++
            if (attempts < 3) Thread.sleep(500L * attempts)
        }
        
        throw lastError ?: Exception("Chunk download failed after 3 attempts")
    }
}

class DiscordDataSourceFactory(
    private val repository: DisboxRepository,
    private val file: DisboxFile
) : DataSource.Factory {
    override fun createDataSource(): DataSource = DiscordDataSource(repository, file)
}
