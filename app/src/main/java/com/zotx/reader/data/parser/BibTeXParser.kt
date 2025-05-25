package com.zotx.reader.data.parser

import android.net.Uri
import com.zotx.reader.data.model.Paper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

// Helper function to clean up LaTeX formatting
private fun String.cleanLatexFormatting(): String {
    // Remove LaTeX commands like \textbackslash, \{\textendash\}, etc.
    var result = this
        // Remove surrounding { and }
        .removeSurrounding("{", "}")
        // Replace \"u with ü
        .replace("\\\"u", "ü")
        // Replace \"a with ä
        .replace("\\\"a", "ä")
        // Replace \"o with ö
        .replace("\\\"o", "ö")
        // Replace \"U with Ü
        .replace("\\\"U", "Ü")
        // Replace \"A with Ä
        .replace("\\\"A", "Ä")
        // Replace \"O with Ö
        .replace("\\\"O", "Ö")
        // Replace LaTeX commands with spaces
        .replace(Regex("\\\\[a-zA-Z]+"), " ")
        // Replace math mode delimiters
        .replace(Regex("\\$[^$]*\\$"), "")
        // Remove remaining LaTeX commands
        .replace(Regex("\\[a-zA-Z]+"), "")
        // Replace multiple spaces with single space
        .replace(Regex("\\s+"), " ")
        .trim()

    result = result.replace(Regex("[{}&%$#_^~]+"), "")
    return result
}

class BibTeXParser {

    fun parseBibFile(inputStream: InputStream, pdfFolderUri: Uri): List<Paper> { // Added pdfFolderUri parameter
        val papers = mutableListOf<Paper>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var currentEntryString = StringBuilder()
        var braceLevel = 0
        var inEntry = false

        reader.use { bufferedReader ->
            bufferedReader.forEachLine { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("%")) { // Skip comment lines
                    return@forEachLine
                }

                if (trimmedLine.startsWith("@")) {
                    // If we encounter a new entry marker, and we were already processing one (and it's valid)
                    // This check helps to handle concatenated bib files without empty lines in between
                    if (inEntry && braceLevel == 0 && currentEntryString.isNotEmpty()) {
                        parseEntry(currentEntryString.toString(), papers, pdfFolderUri) // Pass pdfFolderUri
                        currentEntryString.clear()
                        // braceLevel is already 0
                    } else if (inEntry && braceLevel > 0) {
                        // This means a new @ started before the previous entry was properly closed.
                        // This is likely an error in the BibTeX file or our previous logic.
                        // For now, we'll log it (if we had a logger) and reset.
                        // System.err.println("Warning: New entry started before previous one closed. Discarding: " + currentEntryString)
                    }
                    currentEntryString.clear()
                    inEntry = true
                    currentEntryString.append(trimmedLine)
                    braceLevel = 0 // Reset brace level for the new entry
                    for (char in trimmedLine) {
                        if (char == '{') braceLevel++
                        // No need to count closing braces here for the first line,
                        // as the entry must at least have one opening brace for the citation key.
                    }
                } else if (inEntry) {
                    currentEntryString.append("\n").append(trimmedLine) // Preserve line breaks for easier debugging if needed, or use space
                    for (char in trimmedLine) {
                        if (char == '{') braceLevel++
                        else if (char == '}') braceLevel--
                    }
                    // Check if the entry is complete
                    // An entry is considered complete if braceLevel is 0 AND we've seen at least one opening brace.
                    // The check for currentEntryString.contains("{") ensures we don't prematurely close on just "}"
                    if (braceLevel == 0 && currentEntryString.contains("{")) {
                        parseEntry(currentEntryString.toString(), papers, pdfFolderUri) // Pass pdfFolderUri
                        currentEntryString.clear()
                        inEntry = false
                        // braceLevel is already 0
                    }
                }
            }
            // After the loop, if there's still an entry being processed (e.g., file ends mid-entry)
            // and it seems valid (braceLevel 0), parse it.
            if (inEntry && braceLevel == 0 && currentEntryString.isNotEmpty() && currentEntryString.contains("{")) {
                parseEntry(currentEntryString.toString(), papers, pdfFolderUri) // Pass pdfFolderUri
            }
        }
        return papers
    }

    private fun parseEntry(entryString: String, papers: MutableList<Paper>, pdfFolderUri: Uri) { // Added pdfFolderUri parameter
        val entryTypeRegex = Regex("@(\\w+)\\s*\\{\\s*([^,]+)\\s*,", RegexOption.IGNORE_CASE)
        val typeMatch = entryTypeRegex.find(entryString)

        if (typeMatch == null) {
            // System.err.println("Warning: Could not parse entry type from: ${entryString.take(50)}...")
            return
        }

        // We don't use the entry type right now, but we'll keep it for future use
        // and suppress the unused variable warning
        @Suppress("UNUSED_VARIABLE")
        val entryType = typeMatch.groupValues[1] // e.g. "article"
        
        @Suppress("UNUSED_VARIABLE")
        val citationKey = typeMatch.groupValues[2].trim()

        // Content is between the first comma and the last '}'
        val fieldsStartIndex = typeMatch.range.last + 1
        val fieldsEndIndex = entryString.lastIndexOf('}')
        if (fieldsStartIndex >= fieldsEndIndex) {
            // System.err.println("Warning: No fields found for entry $citationKey")
            return
        }
        
        val fieldsString = entryString.substring(fieldsStartIndex, fieldsEndIndex)
        val fields = mutableMapOf<String, String>()
        var currentIndex = 0
        val keyPattern = Regex("\\s*([a-zA-Z0-9_-]+)\\s*=\\s*") // Key consists of alphanumeric, _, -
        
        while (currentIndex < fieldsString.length) {
            val keyMatch = keyPattern.find(fieldsString, currentIndex)
            if (keyMatch == null) {
                // No more valid field structures found
                break
            }

            val key = keyMatch.groupValues[1].lowercase() // Normalize key to lowercase
            var valueStartIndex = keyMatch.range.last + 1
            
            if (valueStartIndex >= fieldsString.length) break // Should not happen with valid bibtex

            // Skip whitespace after equals sign
            while (valueStartIndex < fieldsString.length && fieldsString[valueStartIndex].isWhitespace()) {
                valueStartIndex++
            }
            if (valueStartIndex >= fieldsString.length) {
                break // End of string reached prematurely
            }
            // Get the character after the equals sign (after skipping whitespace)
            val charAfterEquals = fieldsString[valueStartIndex]
            var rawValue: String? = null
            var valueEndIndex = valueStartIndex

            when (charAfterEquals) {
                '{' -> {
                    var braceDepth = 0
                    valueStartIndex++ // Move past the initial '{'
                    var currentPos = valueStartIndex
                    while (currentPos < fieldsString.length) {
                        if (fieldsString[currentPos] == '{' && (currentPos == 0 || fieldsString[currentPos -1] != '\\')) {
                            braceDepth++
                        } else if (fieldsString[currentPos] == '}' && (currentPos == 0 || fieldsString[currentPos -1] != '\\')) {
                            if (braceDepth == 0) {
                                rawValue = fieldsString.substring(valueStartIndex, currentPos)
                                valueEndIndex = currentPos + 1
                                break
                            }
                            braceDepth--
                        }
                        currentPos++
                    }
                }
                '"' -> {
                    valueStartIndex++ // Move past the initial '"'
                    var currentPos = valueStartIndex
                    var escaped = false
                    while (currentPos < fieldsString.length) {
                        if (fieldsString[currentPos] == '"' && !escaped) {
                            rawValue = fieldsString.substring(valueStartIndex, currentPos)
                            valueEndIndex = currentPos + 1
                            break
                        }
                        escaped = fieldsString[currentPos] == '\\' && !escaped
                        currentPos++
                    }
                }
                else -> { // Unquoted value (number or string constant)
                    var currentPos = valueStartIndex
                    while (currentPos < fieldsString.length) {
                        if (fieldsString[currentPos] == ',') {
                            rawValue = fieldsString.substring(valueStartIndex, currentPos).trim()
                            valueEndIndex = currentPos
                            break
                        }
                        currentPos++
                    }
                    if (rawValue == null) { // Last field, no trailing comma
                        rawValue = fieldsString.substring(valueStartIndex).trim()
                        valueEndIndex = fieldsString.length
                    }
                }
            }


            if (rawValue != null) {
                // Basic cleaning: remove outer delimiters if they were part of capture, unescape quotes
                val cleanedValue = when (charAfterEquals) {
                    '{' -> rawValue // Outermost braces already excluded by substring
                    '"' -> rawValue.replace("\\\"", "\"") // Unescape quotes
                    else -> rawValue // No delimiters to remove for numbers/constants
                }
                fields[key] = cleanedValue

                // Advance currentIndex past the parsed field (key, equals, value, and potential comma)
                currentIndex = valueEndIndex
                while (currentIndex < fieldsString.length && (fieldsString[currentIndex] == ',' || fieldsString[currentIndex].isWhitespace())) {
                    currentIndex++
                }
            } else {
                // Could not parse value, something is wrong, break to avoid infinite loop
                // System.err.println("Warning: Could not parse value for key '$key' in entry '$citationKey'")
                break
            }
        }

        // Get and clean title
        val title = (fields["title"] ?: fields["booktitle"] ?: "No Title").cleanLatexFormatting()
        
        // Get and clean authors
        val authorString = fields["author"] ?: ""
        val authors = authorString
            .split(Regex("\\s+and\\s+", RegexOption.IGNORE_CASE))
            .filter { it.isNotBlank() }
            .map { it.cleanLatexFormatting() }
            
        // Get year or fallback to urldate
        val year = (fields["year"] ?: fields["urldate"]?.takeWhile { it.isDigit() }?.take(4) ?: "0").toIntOrNull() ?: 0
        
        val filePath = fields["file"] ?: ""
        val fileName = if (filePath.isNotEmpty()) {
            filePath.substringAfterLast(':').substringAfterLast('/').substringAfterLast('\\')
        } else {
            ""
        }
        
        if (title != "No Title" || typeMatch.groupValues[2].trim().isNotEmpty()) { // Add if we have a title or at least a key
            papers.add(Paper(
                id = typeMatch.groupValues[2].trim(),
                title = title.replace(Regex("\\.\\s*"), " ").trim(),  // Remove LaTeX newlines and extra spaces
                authors = authors,
                year = year,
                filePath = fileName,
                pdfFolderUri = pdfFolderUri.toString()
            ))
        } else {
            // System.err.println("Skipping entry due to missing title and key. Entry string fragment: ${entryString.take(100)}")
        }
    }
}
