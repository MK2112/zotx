package com.zotx.reader.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zotx.reader.ui.viewmodel.PaperListViewModel
import androidx.activity.compose.ManagedActivityResultLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePicker(
    viewModel: PaperListViewModel = viewModel()
) {
    val context = LocalContext.current
    var bibFileUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFolderUri by remember { mutableStateOf<Uri?>(null) }

    val bibFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                bibFileUri = it
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val pdfFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { 
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            pdfFolderUri = it 
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // BibTeX File Picker
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                bibFilePicker.launch(
                    arrayOf(
                        "text/plain",
                        "application/x-bibtex",
                        "application/x-bibtex-text-file",
                        "text/x-bibtex"
                    )
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "BibTeX File"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bibFileUri?.lastPathSegment ?: "Select BibTeX File",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = if (bibFileUri != null) "Selected" else "Select",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PDF Folder Picker
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { pdfFolderPicker.launch(null) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "PDF Folder"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pdfFolderUri?.lastPathSegment ?: "Select PDF Folder",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = if (pdfFolderUri != null) "Selected" else "Select",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Update Button
        Button(
            onClick = {
                bibFileUri?.let { bib ->
                    pdfFolderUri?.let { pdf ->
                        viewModel.updatePapers(bib, pdf)
                    }
                }
            },
            enabled = bibFileUri != null && pdfFolderUri != null
        ) {
            Text("Update Papers")
        }
    }
}
