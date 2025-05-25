package com.zotx.reader.ui.viewmodel

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zotx.reader.data.datastore.AppPreferences
import com.zotx.reader.data.model.Paper
import com.zotx.reader.data.repository.PaperRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private const val TAG = "PaperListViewModel"

class PaperListViewModel(
    private val context: Context,
    private val repository: PaperRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {
    
    private var _savedBibFileUri: Uri? = null
    private var _savedPdfFolderUri: Uri? = null
    
    val savedBibFileUri: Uri?
        get() = _savedBibFileUri
    
    val savedPdfFolderUri: Uri?
        get() = _savedPdfFolderUri
        
    val paperRepository: PaperRepository
        get() = repository
        
    /**
     * Toggles the read status of a paper
     * @param paperId The ID of the paper to toggle
     * @param isRead The new read status
     */
    fun togglePaperReadStatus(paperId: String, isRead: Boolean) {
        viewModelScope.launch {
            try {
                repository.togglePaperReadStatus(paperId, isRead)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling read status for paper $paperId", e)
                _uiState.update { it.copy(
                    error = "Failed to update read status: ${e.message}"
                )}
            }
        }
    }

    private val _uiState = MutableStateFlow(PaperListUiState())
    val uiState: StateFlow<PaperListUiState> = _uiState

    // updateProgress should already be compatible with (processed, total)
    private fun updateProgress(current: Int, total: Int) {
        val progress = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        _uiState.update {
            it.copy(
                progress = progress,
                progressText = if (total > 0) "Updating papers... ${String.format("%.1f", progress * 100)}%" else "Processing..."
            )
        }
    }

    init {
        loadSavedUris()
        loadPapers()
    }
    
    private fun loadSavedUris() {
        viewModelScope.launch {
            try {
                // Collect both flows in parallel
                combine(
                    appPreferences.bibFileUri,
                    appPreferences.pdfFolderUri
                ) { bibUri, pdfUri ->
                    bibUri?.let { uri -> _savedBibFileUri = Uri.parse(uri) }
                    pdfUri?.let { uri -> _savedPdfFolderUri = Uri.parse(uri) }
                    
                    // If we have both URIs, try to load papers automatically
                    if (bibUri != null && pdfUri != null) {
                        _uiState.update { it.copy(
                            isLoading = true,
                            progressText = "Loading saved papers..."
                        )}
                        
                        try {
                            val parsedBibUri = Uri.parse(bibUri)
                            val parsedPdfUri = Uri.parse(pdfUri)
                            
                            // Verify URIs are still accessible
                            val bibFile = DocumentFile.fromSingleUri(context, parsedBibUri)
                            val pdfFolder = DocumentFile.fromTreeUri(context, parsedPdfUri)
                            
                            if (bibFile?.exists() == true && pdfFolder?.exists() == true) {
                                updatePapers(parsedBibUri, parsedPdfUri)
                            } else {
                                // Clear invalid URIs
                                if (bibFile?.exists() != true) {
                                    appPreferences.saveBibFileUri("")
                                    _savedBibFileUri = null
                                }
                                if (pdfFolder?.exists() != true) {
                                    appPreferences.savePdfFolderUri("")
                                    _savedPdfFolderUri = null
                                }
                                _uiState.update { it.copy(
                                    isLoading = false,
                                    error = "One or more saved locations are no longer accessible"
                                )}
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading saved URIs", e)
                            _uiState.update { it.copy(
                                isLoading = false,
                                error = "Error loading saved locations: ${e.message}"
                            )}
                        }
                    }
                }.collect()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved URIs", e)
            }
        }
    }

    private fun loadPapers() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                repository.getPapers().collect { papers ->
                    _uiState.update { 
                        it.copy(
                            papers = papers,
                            isLoading = false,
                            error = null
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage
                    ) 
                }
            }
        }
    }

    fun updatePapers(bibFileUri: Uri, pdfFolderUri: Uri) {
        // Save the URIs when they're updated
        viewModelScope.launch {
            try {
                appPreferences.saveBibFileUri(bibFileUri.toString())
                appPreferences.savePdfFolderUri(pdfFolderUri.toString())
                _savedBibFileUri = bibFileUri
                _savedPdfFolderUri = pdfFolderUri
            } catch (e: Exception) {
                Log.e(TAG, "Error saving URIs to preferences", e)
            }
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, progress = 0f, progressText = "Initializing...") }

                if (bibFileUri == Uri.EMPTY) {
                    throw Exception("No .bib file selected.")
                }

                context.contentResolver.openInputStream(bibFileUri)?.use { bibInputStream ->
                    repository.updatePapersWithProgress(bibInputStream, pdfFolderUri) { processed, total ->
                        updateProgress(processed, total)
                        // The progressText in updateProgress is generic, let's make this one more specific
                        _uiState.update { ui -> ui.copy(progressText = "Processing paper $processed/$total...") }
                    }
                } ?: run {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to open BibTeX file."
                        )
                    }
                    return@launch
                }

                // Refresh papers list after update
                // This assumes getPapers() will emit the latest list post-update.
                // The repository's updatePapersWithProgress already updates the internal list and flow.
                // So, the existing loadPapers or direct collection might be redundant if getPapers is a hot flow
                // that automatically reflects changes. Let's simplify to rely on the flow updated by the repository.
                // The init block already calls loadPapers which collects getPapers().
                // Forcing a refresh might not be needed if it's a StateFlow that's correctly updated.
                // However, to ensure the UI reflects the "Update complete" and clears loading state *after* processing:
                 _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = 0f,
                        progressText = "Update complete",
                        error = null // Clear previous errors
                        // Papers list will be updated by the flow collection in loadPapers
                    )
                }
                // Explicitly call loadPapers to refresh the list if needed, or rely on existing collection.
                // For now, let's assume the existing collection in loadPapers is sufficient.
                // If not, one might call loadPapers() here or directly collect:
                // repository.getPapers().collect { updatedPapers -> ... }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = 0f,
                        progressText = "Update failed",
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    fun openPdf(paper: Paper) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val folderUriString = paper.pdfFolderUri
                val fileName = paper.filePath

                if (folderUriString.isBlank()) {
                    throw Exception("PDF folder URI is missing for this paper.")
                }
                if (fileName.isBlank()) {
                    throw Exception("PDF file name is missing for this paper.")
                }

                // Parse the folder URI
                val folderUri = Uri.parse(folderUriString)
                
                // Check if the file path is already a content URI
                val pdfUri = try {
                    if (fileName.startsWith("content://")) {
                        Uri.parse(fileName)
                    } else {
                        // Build the document URI from the folder URI and file name
                        val treeDocumentId = DocumentsContract.getTreeDocumentId(folderUri)
                        val documentId = "$treeDocumentId:$fileName"
                        DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to build document URI: ${e.localizedMessage}")
                }

                // Try to open the document
                val documentFile = DocumentFile.fromSingleUri(context, pdfUri)
                    ?: throw Exception("Failed to access PDF file. The file might have been moved or deleted.")

                if (!documentFile.exists() || !documentFile.canRead()) {
                    throw Exception("Cannot read PDF file. The file might be corrupted or you don't have permission to access it.")
                }

                // Create a view intent with the PDF URI
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(documentFile.uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                // Create an open intent as fallback
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(documentFile.uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Check which intents can be handled
                val packageManager = context.packageManager
                val activities = packageManager.queryIntentActivities(viewIntent, 0)
                
                if (activities.isNotEmpty()) {
                    // Grant permissions for all activities that can handle the intent
                    for (info in activities) {
                        context.grantUriPermission(
                            info.activityInfo.packageName,
                            documentFile.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    // Start the activity with the PDF-specific intent
                    context.startActivity(viewIntent)
                    _uiState.update { it.copy(isLoading = false, error = null) }
                } else {
                    // If no PDF-specific handlers, try with generic file type
                    val genericActivities = packageManager.queryIntentActivities(openIntent, 0)
                    if (genericActivities.isNotEmpty()) {
                        for (info in genericActivities) {
                            context.grantUriPermission(
                                info.activityInfo.packageName,
                                documentFile.uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        context.startActivity(openIntent)
                        _uiState.update { it.copy(isLoading = false, error = null) }
                    } else {
                        // If no handlers at all, show a helpful message
                        throw Exception("No app found to open PDF. Please install a file manager or PDF reader.")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to open PDF: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // Dead code getRealPathFromUri and getDataColumn have been removed.
    
    suspend fun clearSavedUris() {
        appPreferences.clearPreferences()
        _savedBibFileUri = null
        _savedPdfFolderUri = null
        // Clear all read statuses when clearing saved URIs
        repository.clearAllReadStatuses()
        _uiState.update { it.copy(papers = emptyList()) }
    }
}

data class PaperListUiState(
    val papers: List<Paper> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val progress: Float = 0f,  // 0.0f to 1.0f
    val progressText: String = ""
)
