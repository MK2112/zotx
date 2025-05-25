package com.zotx.reader

import android.app.Application
import com.zotx.reader.di.ViewModelFactory

class BibPDFReaderApp : Application() {
    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(this)
    }
}
