package com.disbox.mobile.domain.usecase

import com.disbox.mobile.data.repository.DisboxRepository
import com.disbox.mobile.model.DisboxFile
import java.io.File
import java.io.FileOutputStream

class DownloadFileUseCase(private val repository: DisboxRepository) {
    suspend operator fun invoke(file: DisboxFile, destFile: File, onProgress: (Float) -> Unit) {
        val out = FileOutputStream(destFile)
        out.use { stream ->
            for (i in file.messageIds.indices) {
                val data = repository.downloadFileChunk(file.messageIds[i].msgId, file.messageIds[i].index) ?: throw Exception("Chunk failed")
                stream.write(data)
                onProgress((i + 1).toFloat() / file.messageIds.size)
            }
        }
    }
}
