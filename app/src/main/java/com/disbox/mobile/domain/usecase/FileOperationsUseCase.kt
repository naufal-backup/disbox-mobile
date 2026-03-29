package com.disbox.mobile.domain.usecase

import com.disbox.mobile.data.repository.DisboxRepository

class FileOperationsUseCase(private val repository: DisboxRepository) {
    suspend fun createFolder(name: String, parentPath: String) = repository.createFolder(name, parentPath)
    suspend fun deletePaths(idsOrPaths: List<String>) = repository.deletePaths(idsOrPaths)
    suspend fun renamePath(oldPath: String, newName: String, id: String? = null) = repository.renamePath(oldPath, newName, id)
    suspend fun toggleStatus(idsOrPaths: Set<String>, isLocked: Boolean? = null, isStarred: Boolean? = null) = repository.bulkSetStatus(idsOrPaths, isLocked, isStarred)
}
