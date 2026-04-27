package com.aioweb.app

import android.app.Application
import com.aioweb.app.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class AioWebApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        // CloudStream plugins call top-level `setKey/getKey` from MainActivityKt — wire
        // them to a SharedPreferences instance scoped to this process.
        com.lagradost.cloudstream3.installPrefs(this)

        // Mirror the persisted YT Music cookie into the NewPipe HTTP shim so authenticated
        // requests Just Work after process restart. The flow keeps the in-memory copy in
        // sync with future logins/logouts.
        scope.launch {
            ServiceLocator.get(this@AioWebApplication).settings.ytMusicCookie
                .collectLatest { cookie ->
                    com.aioweb.app.data.newpipe.NewPipeDownloader.instance.ytMusicCookie = cookie
                }
        }
    }
}
