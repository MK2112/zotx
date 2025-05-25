package com.zotx.reader.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.zotx.reader.R
import com.zotx.reader.ui.theme.BibPDFTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "BibPDFMainActivity"

class BibPDFMainActivity : ComponentActivity() {
    private var isInitialized = false
    private var selectedFolderUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val context = LocalContext.current
            var showApp by remember { mutableStateOf(false) }
            var showFolderPicker by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            
            // Launcher for folder picker
            val folderPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { treeUri ->
                        try {
                            // Take persistent URI permission
                            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                            
                            // Save the selected folder URI
                            selectedFolderUri = treeUri
                            showApp = true
                            errorMessage = null
                            
                            // Now you can work with the selected folder
                            listFilesInFolder(context, treeUri)
                        } catch (e: Exception) {
                            errorMessage = "Failed to access folder: ${e.message}"
                            Log.e(TAG, "Error accessing folder", e)
                        }
                    } ?: run {
                        errorMessage = "No folder selected"
                    }
                } else {
                    if (showApp) {
                        // User pressed back but we already have access
                        showApp = true
                    } else {
                        // User denied folder access
                        errorMessage = "Folder access is required to use this app"
                    }
                }
            }
            
            // Show folder picker on first launch
            LaunchedEffect(Unit) {
                if (!isInitialized) {
                    isInitialized = true
                    showFolderPicker = true
                }
            }
            
            // Show folder picker dialog if needed
            if (showFolderPicker) {
                AlertDialog(
                    onDismissRequest = { /* Don't allow dismissing by clicking outside */ },
                    title = { Text("Select PDF Folder") },
                    text = { 
                        Text("Please select the folder containing your PDFs and .bib file")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    
                                    // Optional: Start in the Downloads directory
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, 
                                            android.os.Environment.getExternalStoragePublicDirectory(
                                                android.os.Environment.DIRECTORY_DOWNLOADS).toURI())
                                    }
                                }
                                folderPickerLauncher.launch(intent)
                                showFolderPicker = false
                            }
                        ) {
                            Text("Select Folder")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { finish() }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            BibPDFTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showApp) {
                        // Pass the selected folder URI to your app
                        var currentFolderUri by remember { mutableStateOf(selectedFolderUri) }
                        LaunchedEffect(selectedFolderUri) {
                            currentFolderUri = selectedFolderUri
                        }
                        BibPDFApp(selectedFolderUri = currentFolderUri)
                    } else if (errorMessage != null) {
                        // Show error message if any
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        // Show loading indicator
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
    
    private fun listFilesInFolder(context: Context, treeUri: Uri) {
        try {
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            if (docFile != null && docFile.isDirectory) {
                // List all PDF files
                val pdfFiles = docFile.listFiles().filter { 
                    it.name?.endsWith(".pdf", ignoreCase = true) == true 
                }
                
                // Find .bib file
                val bibFile = docFile.listFiles().find { 
                    it.name?.endsWith(".bib", ignoreCase = true) == true 
                }
                
                Log.d(TAG, "Found ${pdfFiles.size} PDF files and ${if (bibFile != null) 1 else 0} .bib file")
                
                // TODO: Process the files as needed
                // You can pass these to your ViewModel or process them directly
                
            } else {
                Toast.makeText(context, "Selected item is not a directory", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files", e)
            Toast.makeText(context, "Error accessing folder: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // No need to check permissions as we're using SAF
        setContent {
            var showApp by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                showApp = true
            }
            
            if (showApp) {
                BibPDFApp()
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
