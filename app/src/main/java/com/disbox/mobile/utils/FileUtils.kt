package com.disbox.mobile.utils

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun getFileIcon(name: String): String {
    val ext = name.split(".").last().lowercase()
    return when (ext) {
        "pdf" -> "📄"
        "mp4", "mov", "avi", "mkv" -> "🎬"
        "mp3", "wav", "flac", "ogg" -> "🎵"
        "jpg", "jpeg", "png", "gif", "webp", "svg" -> "🖼"
        "zip", "rar", "tar", "gz", "7z" -> "📦"
        "js", "ts", "jsx", "tsx", "py", "rs" -> "⚙️"
        "html" -> "🌐"
        "css" -> "🎨"
        "json" -> "📋"
        "doc", "docx", "txt", "md" -> "📝"
        "xls", "xlsx", "csv" -> "📊"
        else -> "📄"
    }
}

fun isImageFile(name: String): Boolean {
    val ext = name.split(".").last().lowercase()
    return ext in listOf("jpg", "jpeg", "png", "gif", "webp")
}

fun isPdfFile(name: String): Boolean {
    return name.split(".").last().lowercase() == "pdf"
}

fun isVideoFile(name: String): Boolean {
    val ext = name.split(".").last().lowercase()
    return listOf("mp4", "mkv", "mov", "avi", "webm").contains(ext)
}

fun isAudioFile(name: String): Boolean {
    val ext = name.split(".").last().lowercase()
    return listOf("mp3", "wav", "flac", "ogg", "m4a").contains(ext)
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatSize(size: Long): String {
    return if (size >= 1024 * 1024) {
        "%.2f MB".format(size.toFloat() / (1024 * 1024))
    } else {
        "${size / 1024} KB"
    }
}
