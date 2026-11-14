package com.clearmind.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/**
 * The shell. The whole interface is the local page in assets/; this class gives
 * it the things a web page cannot do: notifications, alarms, the widget,
 * WhatsApp, and the phone dialler.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { Notifier.showClock(this); Scheduler.syncAll(this) }
        else Toast.makeText(this, "Without notifications I can't check on you. You can turn them on in Settings.", Toast.LENGTH_LONG).show()
    }
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        Notifier.ensureChannels(this)

        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        web.setBackgroundColor(0xFF101015.toInt())
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, req: android.webkit.WebResourceRequest?): Boolean {
                val u = req?.url ?: return false
                if (u.scheme == "file") return false
                // Links out (WhatsApp, tel:) leave the app rather than loading inside it.
                return try { startActivity(Intent(Intent.ACTION_VIEW, u)); true } catch (e: Exception) { true }
            }
            override fun onPageFinished(v: WebView?, url: String?) { pushEvents() }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Only the microphone, and only for the user's own voice message.
                if (request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    else { askMic.launch(Manifest.permission.RECORD_AUDIO); request.deny() }
                } else request.deny()
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.onBack&&window.onBack())?1:0") { r ->
                    if (r != "1") { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
                }
            }
        })

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val dest = i?.getStringExtra("dest") ?: return
        Store.queue(this, "open", dest)
        pushEvents()
    }

    override fun onResume() {
        super.onResume()
        pushEvents()
    }

    /** Tell the page that something happened while it wasn't looking. */
    private fun pushEvents() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("window.onNative&&window.onNative()", null)
    }

    inner class Bridge {
        /** The page pushes its state here on every save. */
        @JavascriptInterface
        fun sync(json: String) {
            runOnUiThread {
                try {
                    Store.put(this@MainActivity, json)
                    Scheduler.syncAll(this@MainActivity)
                } catch (e: Exception) { /* a malformed push must never crash the app */ }
            }
        }

        @JavascriptInterface
        fun pull(): String = Store.drainEvents(this@MainActivity)

        @JavascriptInterface
        fun craveStart(endAt: String) {
            val at = endAt.toLongOrNull() ?: return
            runOnUiThread { Scheduler.craveStart(this@MainActivity, at) }
        }

        @JavascriptInterface
        fun craveCancel() { runOnUiThread { Scheduler.craveCancel(this@MainActivity) } }

        @JavascriptInterface
        fun whatsapp(phone: String, text: String) {
            runOnUiThread {
                val uri = Uri.parse("https://wa.me/$phone?text=" + Uri.encode(text))
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                catch (e: Exception) { Toast.makeText(this@MainActivity, "Couldn't open WhatsApp.", Toast.LENGTH_SHORT).show() }
            }
        }

        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                startActivity(Intent.createChooser(i, "Send"))
            }
        }

        @JavascriptInterface
        fun dial(number: String) {
            runOnUiThread {
                try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun askNotify() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { Notifier.showClock(this@MainActivity); Scheduler.syncAll(this@MainActivity) }
            }
        }

        @JavascriptInterface
        fun askMic() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
