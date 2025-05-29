package com.zotx.reader.data.model

data class Paper(
    val id: String,
    val title: String,
    val authors: List<String>,
    val year: Int,
    val filePath: String,
    val pdfFolderUri: String, // New property for the folder URI
    val isRead: Boolean = false,
    val toRead: Boolean = false,
    val isFavorite: Boolean = false
) {
    fun markAsRead(read: Boolean): Paper = copy(isRead = read)
    fun markAsToRead(toRead: Boolean): Paper = copy(toRead = toRead)
    fun markAsFavorite(favorite: Boolean): Paper = copy(isFavorite = favorite)
    
    // Helper function to get the status that should be displayed (for backward compatibility)
    fun getDisplayStatus(): PaperStatus {
        return when {
            isFavorite -> PaperStatus.FAVORITE
            isRead -> PaperStatus.READ
            toRead -> PaperStatus.TO_READ
            else -> PaperStatus.NONE
        }
    }
}

enum class PaperStatus {
    NONE, READ, TO_READ, FAVORITE
}
