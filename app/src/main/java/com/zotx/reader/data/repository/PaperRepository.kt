package com.zotx.reader.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.zotx.reader.data.model.Paper
import com.zotx.reader.data.parser.BibTeXParser
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class PaperRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val parser = BibTeXParser()
    private val _papers = mutableListOf<Paper>()
    private val papersLock = Any()
    private val _papersFlow = MutableStateFlow<List<Paper>>(emptyList())
    
    // DataStore for persisting read status
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "paper_status")
    private val readStatusKey = booleanPreferencesKey("paper_read_")
    
    // In-memory cache of read status
    private val readStatusCache = mutableMapOf<String, Boolean>()

    init {
        // Start loading read statuses when the repository is created
        coroutineScope.launch {
            try {
                loadReadStatus()
                updatePapersFlow()
            } catch (e: Exception) {
                Log.e("PaperRepository", "Error initializing repository", e)
            }
        }
    }

    private fun updatePapersFlow() {
        synchronized(papersLock) {
            // Update papers with their read status from cache
            _papersFlow.value = _papers.map { paper ->
                paper.markAsRead(readStatusCache[paper.id] ?: false)
            }
        }
    }

    fun getPapers(): StateFlow<List<Paper>> = _papersFlow.asStateFlow()
    
    private suspend fun loadReadStatus() {
        try {
            // Get the current preferences once
            val preferences = context.dataStore.data.first()
            
            // Clear the cache first to avoid stale data
            readStatusCache.clear()
            
            // Load all read statuses into the cache
            preferences.asMap().forEach { (key, value) ->
                if (key.name.startsWith("paper_read_")) {
                    val paperId = key.name.substringAfter("paper_read_")
                    @Suppress("UNCHECKED_CAST")
                    readStatusCache[paperId] = (value as? Boolean) ?: false
                }
            }
            
            // Update the papers flow with the loaded statuses
            updatePapersFlow()
        } catch (e: Exception) {
            Log.e("PaperRepository", "Error loading read statuses", e)
            // Re-throw to be handled by the ViewModel
            throw e
        }
    }
    
    suspend fun togglePaperReadStatus(paperId: String, isRead: Boolean) {
        try {
            // Update cache
            readStatusCache[paperId] = isRead
            
            // Persist to DataStore
            context.dataStore.edit { preferences ->
                preferences[booleanPreferencesKey("paper_read_$paperId")] = isRead
            }
            
            // Update the papers with the new read status
            synchronized(papersLock) {
                _papers.replaceAll { paper ->
                    if (paper.id == paperId) paper.markAsRead(isRead) else paper
                }
                // Update the flow with the modified papers
                _papersFlow.value = _papers.toList()
            }
        } catch (e: Exception) {
            // Handle error
            e.printStackTrace()
            throw e // Re-throw to handle in the ViewModel
        }
    }
    
    /**
     * Clears all read statuses for all papers
     */
    suspend fun clearAllReadStatuses() {
        try {
            // Clear the in-memory cache
            readStatusCache.clear()
            
            // Get all keys from DataStore and filter for read status keys
            val allPreferences = context.dataStore.data.first()
            val readStatusKeys = allPreferences.asMap().keys
                .filter { it.name.startsWith("paper_read_") }
                .toSet()
            
            // Remove all read status keys
            if (readStatusKeys.isNotEmpty()) {
                context.dataStore.edit { preferences ->
                    readStatusKeys.forEach { key ->
                        preferences.remove(key)
                    }
                }
            }
            
            // Update all papers to be marked as unread and update the flow
            synchronized(papersLock) {
                _papers.replaceAll { paper ->
                    paper.markAsRead(false)
                }
                // Force update the flow with the modified papers
                _papersFlow.value = _papers.toList()
            }
            
            // Ensure the UI is updated by emitting a new value
            updatePapersFlow()
            
        } catch (e: Exception) {
            Log.e("PaperRepository", "Error clearing read statuses", e)
            throw e
        }
    }

    suspend fun countPapersInBibFile(bibFileInputStream: InputStream, pdfFolderUri: Uri): Int {
        return parser.parseBibFile(bibFileInputStream, pdfFolderUri).size
    }

    private fun showToast(message: String, duration: Int = android.widget.Toast.LENGTH_SHORT) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, duration).show()
        }
    }

    suspend fun updatePapersWithProgress(bibFileInputStream: InputStream, pdfFolderUri: Uri, progressCallback: (processed: Int, total: Int) -> Unit) {
        // Show initial parsing message on main thread
        showToast("Starting to parse BibTeX file...")


        withContext(Dispatchers.IO) {
            val parsedPapers = try {
                // Pass the pdfFolderUri to the parser
                parser.parseBibFile(bibFileInputStream, pdfFolderUri)
            } catch (e: Exception) {
                showToast("Error parsing BibTeX file: ${e.message}", android.widget.Toast.LENGTH_LONG)
                throw e
            }

            val totalPapers = parsedPapers.size
            showToast("Found $totalPapers papers to process")

            val contentResolver = context.contentResolver
            val updatedPapers = mutableListOf<Paper>()

            // Scan for all PDF files in the folder once
            val pdfFileUriMap = getAllPdfFilesInFolder(contentResolver, pdfFolderUri)
            showToast("Found ${pdfFileUriMap.size} PDF files in the selected folder.")

            parsedPapers.forEachIndexed { index, paper ->
                try {
                    if (paper.filePath.isEmpty()) {
                        // Skip entries without a file field
                        showToast("Skipping paper: ${paper.title.take(20)}... (no file path)")
                        progressCallback(index + 1, totalPapers)
                        return@forEachIndexed
                    }


                    val fileName = paper.filePath // We already have just the filename
                    val pdfUri = pdfFileUriMap[fileName] // More efficient lookup

                    if (pdfUri != null) {
                        updatedPapers.add(paper.copy(filePath = pdfUri.toString()))
                    } else {
                        showToast("PDF not found: ${fileName.take(30)}${if (fileName.length > 30) "..." else ""}")
                        updatedPapers.add(paper.copy(filePath = "")) // File not found
                    }
                    progressCallback(index + 1, totalPapers) // Report progress (1-based index)
                } catch (e: Exception) {
                    val errorMsg = "Error processing paper ${paper.id}: ${e.message}"
                    showToast(errorMsg, android.widget.Toast.LENGTH_LONG)
                    throw RuntimeException(errorMsg, e)
                }
            }

            synchronized(papersLock) {
                _papers.clear()
                _papers.addAll(updatedPapers)
            }
            updatePapersFlow() // This updates a MutableStateFlow, which should be fine from IO dispatcher
        }
    }

    suspend fun updatePapers(bibFileInputStream: InputStream, pdfFolderUri: Uri) {
        // Initial Toast can be outside withContext
        android.widget.Toast.makeText(context, "Parsing BibTeX file...", android.widget.Toast.LENGTH_SHORT).show()

        try {
            withContext(Dispatchers.IO) {
                // Pass the pdfFolderUri to the parser
                val parsedPapers = parser.parseBibFile(bibFileInputStream, pdfFolderUri)

                // Toast can be called from background thread
                android.widget.Toast.makeText(
                    context,
                    "Found ${parsedPapers.size} papers to process",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                val contentResolver = context.contentResolver
                val pdfFileUriMap = getAllPdfFilesInFolder(contentResolver, pdfFolderUri)
                android.widget.Toast.makeText(
                    context,
                    "Found ${pdfFileUriMap.size} PDF files in the selected folder.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                val updatedPapers = parsedPapers.mapIndexed { _, paper ->
                    try {
                        // Ensure filePath is just the filename, not a path
                        val fileName = paper.filePath.substringAfterLast("/").substringAfterLast("\\")

                        val pdfUri = pdfFileUriMap[fileName] // Use the pre-scanned map
                        val result = paper.copy(filePath = pdfUri?.toString() ?: "")

                        if (pdfUri == null) {
                            android.widget.Toast.makeText(
                                context,
                                "PDF not found for ${paper.title}: $fileName", // Combined message
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        // else: No toast for found PDF, to reduce noise. Success is implicit.
                        result
                    } catch (e: Exception) {
                        val errorMsg = "Error processing ${paper.title}: ${e.message}" // More specific error
                        android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        throw RuntimeException(errorMsg, e) // Consider if individual paper errors should halt everything
                    }
                }

                synchronized(papersLock) {
                    _papers.clear()
                    _papers.addAll(updatedPapers)
                    updatePapersFlow()
                }

                // Toast can be called from background thread
                android.widget.Toast.makeText(
                    context,
                    "Paper processing complete. Found ${updatedPapers.filter { it.filePath.isNotEmpty() }.size} PDFs.",
                    android.widget.Toast.LENGTH_LONG // Changed to LONG for better visibility
                ).show()
            }
        } catch (e: Exception) {
            // This catch block handles exceptions from withContext or other parts of the try block
            val errorMsg = "Failed to update papers: ${e.message}"
            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
            throw e // Rethrowing the original exception or a custom one
        }
    }

    private fun getAllPdfFilesInFolder(
        contentResolver: ContentResolver,
        folderUri: Uri
    ): Map<String, Uri> {
        val filesMap = mutableMapOf<String, Uri>()
        val treeDocumentId = DocumentsContract.getTreeDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocumentId)

        // Define columns to retrieve
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        contentResolver.query(
            childrenUri,
            projection,
            null, // No selection (gets all files/subfolders)
            null, // No selection arguments
            null  // Default sort order
        )?.use { cursor ->
            val docIdColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(docIdColumn)
                val name = cursor.getString(nameColumn)
                val mimeType = cursor.getString(mimeTypeColumn)

                // Check if it's a PDF file by MIME type or extension
                if ("application/pdf" == mimeType || name.endsWith(".pdf", ignoreCase = true)) {
                    if (name != null && docId != null) { // Ensure name and docId are not null
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        filesMap[name] = documentUri
                    }
                }
            }
        }
        return filesMap
    }

    // The findPdfInFolder function has been removed as it's no longer used.
}
