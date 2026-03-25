package com.disbox.mobile

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import kotlin.math.min

class DiscordDataSource(
    private val api: DisboxApi,
    private val file: DisboxFile
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var bytesRemaining: Long = 0
    private var currentOffset: Long = 0
    private var isOpen = false

    private val estimatedChunkSize: Long
        get() {
            if (file.messageIds.isEmpty()) return 10 * 1024 * 1024L
            // Use Math.ceil logic like the desktop app
            val sizeDouble = file.size.toDouble() / file.messageIds.size
            return Math.ceil(sizeDouble).toLong()
        }

    private var cachedChunkIndex: Int = -1
    private var cachedChunkData: ByteArray? = null

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        currentOffset = dataSpec.position
        
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            file.size - currentOffset
        } else {
            dataSpec.length
        }
        
        isOpen = true
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesRead = try {
            val cSize = estimatedChunkSize
            val chunkIndex = (currentOffset / cSize).toInt()
            
            if (chunkIndex >= file.messageIds.size) {
                return C.RESULT_END_OF_INPUT
            }

            val chunkBytes = fetchChunk(chunkIndex)
            val offsetInChunk = (currentOffset % cSize).toInt()
            
            val availableInChunk = chunkBytes.size - offsetInChunk
            if (availableInChunk <= 0) {
                // Should not happen if chunk sizes are exactly as estimated
                // But if they vary slightly, we might need to adjust logic
                return C.RESULT_END_OF_INPUT 
            }

            val toCopy = min(length, availableInChunk)
            val toCopyLong = min(toCopy.toLong(), bytesRemaining).toInt()
            
            System.arraycopy(chunkBytes, offsetInChunk, buffer, offset, toCopyLong)
            toCopyLong
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }

        if (bytesRead > 0) {
            currentOffset += bytesRead
            bytesRemaining -= bytesRead
        }
        return bytesRead
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        isOpen = false
    }

    private fun fetchChunk(index: Int): ByteArray {
        if (index == cachedChunkIndex && cachedChunkData != null) {
            return cachedChunkData!!
        }
        val data = runBlocking { api.downloadSingleChunk(file.messageIds[index]) }
        cachedChunkIndex = index
        cachedChunkData = data
        return data
    }
}

class DiscordDataSourceFactory(
    private val api: DisboxApi,
    private val file: DisboxFile
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return DiscordDataSource(api, file)
    }
}
