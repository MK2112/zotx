package com.zotx.reader.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zotx.reader.BibPDFReaderApp
import com.zotx.reader.ui.components.FilePicker
import com.zotx.reader.ui.screens.PaperListScreen
import com.zotx.reader.ui.viewmodel.PaperListViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibPDFApp(
    selectedFolderUri: Uri? = null,
    viewModel: PaperListViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as BibPDFReaderApp).viewModelFactory
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val papers by viewModel.paperRepository.getPapers().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // Function to handle clearing saved URIs
    fun onClearSavedUris() {
        coroutineScope.launch {
            try {
                viewModel.clearSavedUris()
            } catch (e: Exception) {
                // Handle any errors
                Log.e("BibPDFApp", "Error clearing saved URIs", e)
            } finally {
                showInfoDialog = false
            }
        }
    }

    // Check for saved URIs when the app starts
    LaunchedEffect(Unit) {
        viewModel.savedBibFileUri?.let { bibUri ->
            viewModel.savedPdfFolderUri?.let { pdfUri ->
                // Verify URIs are still accessible
                val bibFile = DocumentFile.fromSingleUri(context, bibUri)
                val pdfFolder = DocumentFile.fromTreeUri(context, pdfUri)
                
                if (bibFile?.exists() == true && pdfFolder?.exists() == true) {
                    // Auto-load papers if both URIs are valid
                    viewModel.updatePapers(bibUri, pdfUri)
                }
            }
        }
    }

    // Handle new folder selection
    LaunchedEffect(selectedFolderUri) {
        selectedFolderUri?.let { uri ->
            // Process the selected folder
            val docFile = DocumentFile.fromTreeUri(context, uri)
            if (docFile != null && docFile.isDirectory) {
                // List all files and find the .bib file
                val files = docFile.listFiles()
                val bibFile = files.find { 
                    it.name?.endsWith(".bib", ignoreCase = true) == true 
                }
                
                // If we have a .bib file, process it
                bibFile?.let { bib ->
                    viewModel.updatePapers(bib.uri, uri)
                } ?: run {
                    // Show error if no .bib file found
                    viewModel.updatePapers(Uri.EMPTY, uri) // This will trigger the error handling in the ViewModel
                }
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "zotx",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Info button to show saved paths
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "App Info",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
            
            // Info Dialog
            if (showInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text("App Information") },
                    text = {
                        Column {
                            Text("zotx - Zotero PDF Reader")
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Show saved paths if available
                            viewModel.savedBibFileUri?.let { bibUri ->
                                Text("Bib File:", fontWeight = FontWeight.Bold)
                                Text(bibUri.toString(), style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            viewModel.savedPdfFolderUri?.let { pdfUri ->
                                Text("PDF Folder:", fontWeight = FontWeight.Bold)
                                Text(pdfUri.toString(), style = MaterialTheme.typography.bodySmall)
                            }
                            
                            if (viewModel.savedBibFileUri == null && viewModel.savedPdfFolderUri == null) {
                                Text("No saved locations found.")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showInfoDialog = false }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { onClearSavedUris() }
                        ) {
                            Text("Clear Saved")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // File Picker Section - Always show
                FilePicker(viewModel = viewModel)

                // Error Message - Show if there's an error
                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Display the paper list if we have papers
                if (papers.isNotEmpty()) {
                    PaperListScreen(
                        papers = papers,
                        onPaperClick = { paper ->
                            viewModel.openPdf(paper)
                        },
                        onToggleReadStatus = { paperId, isRead ->
                            coroutineScope.launch {
                                viewModel.paperRepository.togglePaperReadStatus(paperId, isRead)
                            }
                        }
                    )
                }
            }


            // Loading Indicator - Show on top of content when loading
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.progressText.ifEmpty { "Loading..." },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
