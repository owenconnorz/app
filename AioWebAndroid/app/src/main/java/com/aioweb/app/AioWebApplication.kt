package com.aioweb.app

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe

class AioWebApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize NewPipe Extractor with our OkHttp-backed downloader
        NewPipe.init(com.aioweb.app.data.newpipe.NewPipeDownloader.instance)
    }
}
