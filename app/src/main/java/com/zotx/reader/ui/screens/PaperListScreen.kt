package com.zotx.reader.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.zotx.reader.data.model.Paper

// Helper function to highlight text
private fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isEmpty() || query.length < 2) {
        return AnnotatedString(text)
    }
    
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        
        while (currentIndex < text.length) {
            val startIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (startIndex == -1) {
                append(text.substring(currentIndex))
                break
            }
            
            // Add text before the match
            if (startIndex > currentIndex) {
                append(text.substring(currentIndex, startIndex))
            }
            
            // Add highlighted match
            withStyle(style = SpanStyle(
                background = Color.Yellow,
                color = Color.Black
            )) {
                append(text.substring(startIndex, startIndex + query.length))
            }
            
            currentIndex = startIndex + query.length
        }
    }
    
    return annotatedString
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PaperListScreen(
    papers: List<Paper>,
    onPaperClick: (Paper) -> Unit,
    onToggleReadStatus: (String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Filter and sort papers based on search query
    val filteredAndSortedPapers = remember(papers, searchQuery) {
        val query = searchQuery.lowercase().trim()
        papers
            .filter { paper ->
                query.isEmpty() ||
                paper.title.lowercase().contains(query, ignoreCase = true) ||
                paper.authors.any { it.lowercase().contains(query, ignoreCase = true) } ||
                paper.year.toString().contains(query, ignoreCase = true)
            }
            .sortedBy { it.title.lowercase() }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { keyboardController?.hide() },
            onClear = {
                searchQuery = ""
                focusManager.clearFocus()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            focusRequester = focusRequester
        )

        // Results count
        if (searchQuery.isNotEmpty()) {
            Text(
                text = "${filteredAndSortedPapers.size} papers found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Papers list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(filteredAndSortedPapers) { paper ->
                PaperCard(
                    paper = paper,
                    onClick = { onPaperClick(paper) },
                    onToggleReadStatus = { isRead -> onToggleReadStatus(paper.id, isRead) },
                    highlightQuery = searchQuery
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                // Add some padding at the bottom of the list
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    
    // Request focus when the screen is first displayed
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp)
            ) {
                androidx.compose.material3.TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    placeholder = { 
                        Text(
                            "Search papers...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    )
                )
            }
            
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperCard(
    paper: Paper,
    onClick: () -> Unit,
    onToggleReadStatus: (Boolean) -> Unit,
    highlightQuery: String = ""
) {
    val alpha = if (paper.isRead) 0.6f else 1f
    val textDecoration = if (paper.isRead) TextDecoration.LineThrough else TextDecoration.None
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .animateContentSize()
            .alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Row containing the title and read status toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Read status toggle
                IconButton(
                    onClick = { onToggleReadStatus(!paper.isRead) },
                    modifier = Modifier.size(24.dp)
                ) {
                    if (paper.isRead) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Mark as unread"
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Circle,
                            contentDescription = "Mark as read"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Title with academic styling and search highlighting
                val title = paper.title
                val highlightedTitle = highlightText(title, highlightQuery)
                
                Text(
                    text = highlightedTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 32.sp,
                        textDecoration = textDecoration
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Authors with academic formatting and search highlighting
            val authorsText = paper.authors.joinToString("; ") { it.trim() }
            val highlightedAuthors = highlightText(authorsText, highlightQuery)
            
            Text(
                text = highlightedAuthors,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 24.sp,
                    textDecoration = textDecoration
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Year with proper typography and search highlighting
            if (paper.year > 0) {
                val yearText = paper.year.toString()
                val highlightedYear = highlightText(yearText, highlightQuery)
                
                Text(
                    text = highlightedYear,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                        textDecoration = textDecoration
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
