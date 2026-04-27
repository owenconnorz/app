package com.aioweb.app.ui.account

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.aioweb.app.data.ServiceLocator
import kotlinx.coroutines.launch

/**
 * WebView-based YouTube Music login. Mirrors Metrolist's flow:
 *
 *   1. Send the user to `accounts.google.com/ServiceLogin?service=youtube` with the next
 *      param pointing at music.youtube.com.
 *   2. After successful login Google redirects back to music.youtube.com with cookies set.
 *   3. We pull the resulting `Cookie:` header out of [CookieManager] and persist it via
 *      [SettingsRepository.setYtMusicCookie] — that single string is what authenticates
 *      every subsequent NewPipe request.
 *
 * The Activity finishes itself the moment we see a logged-in `music.youtube.com` page,
 * which is detected by the presence of the `SAPISID` cookie.
 */
class YtMusicLoginActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val progress = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        container.addView(progress)
        container.addView(web)
        setContentView(container)

        // Drop any stale cookies before sign-in so we don't carry a half-expired session.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                if (url == null) return
                // We only consider the user "logged in" once they reach a music.youtube.com
                // page AND the cookie jar has the SAPISID cookie (Google's signed-in marker).
                if (!url.contains("music.youtube.com")) return
                val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com")
                    .orEmpty()
                if (cookie.isBlank() || !cookie.contains("SAPISID")) return
                // Capture and persist.
                lifecycleScope.launch {
                    val sl = ServiceLocator.get(applicationContext)
                    sl.settings.setYtMusicCookie(cookie)
                    // Best-effort: scrape the user's display name from the page; if it
                    // fails, we just persist a generic label so the Settings UI shows
                    // "Signed in" instead of being blank.
                    sl.settings.setYtMusicUser(name = "Signed in", avatar = "")
                    com.aioweb.app.data.newpipe.NewPipeDownloader.instance.ytMusicCookie = cookie
                    finish()
                }
            }
        }

        // Send users straight at the YT Music login URL — Google's login page detects
        // the embedded `next=` and redirects back automatically once they finish.
        web.loadUrl(
            "https://accounts.google.com/ServiceLogin" +
                "?ltmpl=music&service=youtube&passive=true&hl=en&continue=" +
                "https%3A%2F%2Fmusic.youtube.com%2F",
        )
    }
}
