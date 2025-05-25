package com.zotx.reader.di

import android.content.Context
import com.zotx.reader.data.datastore.AppPreferences
import com.zotx.reader.data.repository.PaperRepository
import com.zotx.reader.ui.viewmodel.PaperListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppModule {
    private var paperRepository: PaperRepository? = null
    private var appPreferences: AppPreferences? = null
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    fun providePaperRepository(context: Context): PaperRepository {
        return paperRepository ?: PaperRepository(
            context = context,
            coroutineScope = applicationScope
        ).also { paperRepository = it }
    }
    
    fun provideAppPreferences(context: Context): AppPreferences {
        return appPreferences ?: AppPreferences(context).also { appPreferences = it }
    }
}
