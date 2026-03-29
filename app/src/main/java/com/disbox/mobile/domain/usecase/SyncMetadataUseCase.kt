package com.disbox.mobile.domain.usecase

import com.disbox.mobile.data.repository.DisboxRepository

class SyncMetadataUseCase(private val repository: DisboxRepository) {
    suspend operator fun invoke(forceId: String? = null, forceSync: Boolean = false): Boolean {
        return repository.syncMetadata(forceId, forceSync)
    }
}
