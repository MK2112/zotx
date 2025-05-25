package com.zotx.reader.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zotx.reader.ui.viewmodel.PaperListViewModel

class ViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(PaperListViewModel::class.java) -> {
                val repository = AppModule.providePaperRepository(context)
                val preferences = AppModule.provideAppPreferences(context)
                PaperListViewModel(context, repository, preferences) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
