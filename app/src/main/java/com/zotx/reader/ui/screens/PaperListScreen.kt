package com.zotx.reader.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.IconToggleButton
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
import com.zotx.reader.data.model.PaperStatus

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
private fun StatusIconButton(
    isActive: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val iconTint = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    IconButton(
        onClick = onClick,
        modifier = modifier.size(24.dp)
    ) {
        Icon(
            imageVector = if (isActive) activeIcon else inactiveIcon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun PaperListScreen(
    papers: List<Paper>,
    onPaperClick: (Paper) -> Unit,
    onToggleStatus: (String, String, Boolean) -> Unit // paperId, statusType, isActive
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
                    onToggleStatus = { statusType, isActive ->
                        // This will be called when a status icon is clicked in the PaperCard
                        // We'll forward the call to the parent with the paper ID
                        onToggleStatus(paper.id, statusType, isActive)
                    },
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
    onToggleStatus: (String, Boolean) -> Unit, // statusType, isActive
    highlightQuery: String = ""
) {
    // Helper function to handle status toggling
    val onStatusToggle = { statusType: String, isActive: Boolean ->
        // For favorite, just toggle its state without affecting other statuses
        if (statusType == "favorite") {
            onToggleStatus(statusType, isActive)
        } else {
            // For read/to-read, make them mutually exclusive
            if (isActive) {
                // If we're activating a status, deactivate the other one
                if (statusType == "read") {
                    onToggleStatus("toread", false)
                } else if (statusType == "toread") {
                    onToggleStatus("read", false)
                }
            }
            onToggleStatus(statusType, isActive)
        }
    }
    val paperStatus = paper.getDisplayStatus()
    val alpha = when (paperStatus) {
        PaperStatus.NONE -> 1f
        PaperStatus.READ -> 0.6f
        PaperStatus.TO_READ -> 0.8f
        PaperStatus.FAVORITE -> 1f
    }
    
    val textDecoration = if (paperStatus == PaperStatus.READ) TextDecoration.LineThrough else TextDecoration.None
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize()
            .alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = when (paperStatus) {
                PaperStatus.FAVORITE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when (paperStatus) {
                PaperStatus.FAVORITE -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (paperStatus == PaperStatus.FAVORITE) 4.dp else 2.dp,
            pressedElevation = if (paperStatus == PaperStatus.FAVORITE) 6.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title row with content and status buttons
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Content section (title, authors, year)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Title with academic styling and search highlighting
                    val title = paper.title
                    val highlightedTitle = highlightText(title, highlightQuery)
                    
                    Text(
                        text = highlightedTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (paperStatus == PaperStatus.FAVORITE) FontWeight.Bold else FontWeight.SemiBold,
                            lineHeight = 32.sp,
                            textDecoration = textDecoration
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Vertical stack of status buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    // To Read button
                    StatusIconButton(
                        isActive = paperStatus == PaperStatus.TO_READ,
                        activeIcon = Icons.Default.Bookmark,
                        inactiveIcon = Icons.Outlined.BookmarkBorder,
                        contentDescription = if (paperStatus == PaperStatus.TO_READ) "Mark as not to read" else "Mark as to read"
                    ) {
                        onStatusToggle("toread", !paper.toRead)
                    }
                    
                    // Read/Unread button
                    StatusIconButton(
                        isActive = paperStatus == PaperStatus.READ,
                        activeIcon = Icons.Default.CheckCircle,
                        inactiveIcon = Icons.Outlined.Circle,
                        contentDescription = if (paperStatus == PaperStatus.READ) "Mark as unread" else "Mark as read"
                    ) {
                        onStatusToggle("read", !paper.isRead)
                    }
                    
                    // Favorite button (independent of read/to-read)
                    StatusIconButton(
                        isActive = paper.isFavorite,
                        activeIcon = Icons.Default.Star,
                        inactiveIcon = Icons.Outlined.StarBorder,
                        contentDescription = if (paper.isFavorite) "Remove from favorites" else "Add to favorites"
                    ) {
                        onStatusToggle("favorite", !paper.isFavorite)
                    }
                }
            }
            
            // Content section with authors and year
            Column(
                modifier = Modifier.padding(top = 8.dp) // Add some space between title and authors
            ) {
                // Authors with academic formatting and search highlighting
                val authorsText = paper.authors.joinToString("; ") { it.trim() }
                val highlightedAuthors = highlightText(authorsText, highlightQuery)
                
                if (authorsText.isNotEmpty()) {
                    Text(
                        text = highlightedAuthors,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 24.sp,
                            textDecoration = textDecoration,
                            fontWeight = if (paperStatus == PaperStatus.FAVORITE) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                // Year with proper typography and search highlighting
                if (paper.year > 0) {
                    val yearText = paper.year.toString()
                    val highlightedYear = highlightText(yearText, highlightQuery)
                    
                    Text(
                        text = highlightedYear,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                            textDecoration = textDecoration,
                            color = when (paperStatus) {
                                PaperStatus.FAVORITE -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    )
                }
            }
        }
    }
}
