package com.zotx.reader.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    private object PreferencesKeys {
        val BIB_FILE_URI = stringPreferencesKey("bib_file_uri")
        val PDF_FOLDER_URI = stringPreferencesKey("pdf_folder_uri")
    }

    // Bib file URI
    val bibFileUri: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.BIB_FILE_URI]
        }

    // PDF folder URI
    val pdfFolderUri: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.PDF_FOLDER_URI]
        }

    suspend fun saveBibFileUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BIB_FILE_URI] = uri
        }
    }

    suspend fun savePdfFolderUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PDF_FOLDER_URI] = uri
        }
    }

    suspend fun clearPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
