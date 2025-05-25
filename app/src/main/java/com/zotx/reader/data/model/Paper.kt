package com.zotx.reader.data.model

data class Paper(
    val id: String,
    val title: String,
    val authors: List<String>,
    val year: Int,
    val filePath: String,
    val pdfFolderUri: String, // New property for the folder URI
    val isRead: Boolean = false
) {
    fun markAsRead(read: Boolean): Paper = copy(isRead = read)
}
