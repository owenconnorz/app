package com.aioweb.app

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class AioWebApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // NewPipe Extractor needs a Downloader, a Localization and a ContentCountry.
        // Without all three, music search/stream extraction silently produces empty results
        // on some YouTube responses (PoToken / visitor_data flows).
        NewPipe.init(
            com.aioweb.app.data.newpipe.NewPipeDownloader.instance,
            Localization.DEFAULT,           // en/US
            ContentCountry.DEFAULT,         // US
        )
    }
}
