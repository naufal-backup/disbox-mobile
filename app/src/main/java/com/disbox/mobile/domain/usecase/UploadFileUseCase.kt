package com.disbox.mobile.domain.usecase

import android.net.Uri
import com.disbox.mobile.data.repository.DisboxRepository

class UploadFileUseCase(private val repository: DisboxRepository) {
    suspend operator fun invoke(uri: Uri, path: String, chunkSize: Int, onProgress: (Float) -> Unit): List<String> {
        return repository.uploadFile(uri, path, chunkSize, onProgress)
    }
}
