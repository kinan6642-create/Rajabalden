package com.erp.unified

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * تطبيق بسيط يعرض واجهة منصة ERP الموحدة داخل WebView، ويتصل بنفس الخادم
 * الذي يشغّل البرنامج (على اللابتوب عبر الشبكة المحلية، أو خادم سحابي).
 * لا يوجد أي بيانات محفوظة داخل التطبيق نفسه — كل البيانات تبقى في الخادم.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorView: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences

    companion object {
        private const val PREFS_NAME = "erp_prefs"
        private const val KEY_SERVER_URL = "server_url"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        errorView = findViewById(R.id.errorView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.setSupportZoom(false)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                showContent()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    swipeRefresh.isRefreshing = false
                    showError()
                }
            }
        }

        swipeRefresh.setOnRefreshListener { loadServerUrl() }
        findViewById<android.widget.Button>(R.id.retryButton).setOnClickListener { loadServerUrl() }

        val savedUrl = prefs.getString(KEY_SERVER_URL, null)
        if (savedUrl.isNullOrBlank()) {
            promptForServerUrl(isFirstRun = true)
        } else {
            loadServerUrl()
        }
    }

    private fun showContent() {
        webView.visibility = android.view.View.VISIBLE
        errorView.visibility = android.view.View.GONE
    }

    private fun showError() {
        webView.visibility = android.view.View.GONE
        errorView.visibility = android.view.View.VISIBLE
    }

    private fun loadServerUrl() {
        val url = prefs.getString(KEY_SERVER_URL, null) ?: return
        swipeRefresh.isRefreshing = true
        webView.loadUrl(url)
    }

    /** يعرض نافذة لإدخال/تعديل رابط خادم البرنامج، ويحفظه محليًا على الجهاز. */
    private fun promptForServerUrl(isFirstRun: Boolean) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.dialog_server_hint)
            setText(prefs.getString(KEY_SERVER_URL, "http://"))
            setSelection(text.length)
        }

        val container = FrameLayout(this).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(input)
        }

        val messageView = if (isFirstRun) TextView(this).apply {
            text = context.getString(R.string.first_run_message)
            setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
        } else null

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            messageView?.let { addView(it) }
            addView(container)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_server_title)
            .setView(wrapper)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                var value = input.text.toString().trim()
                if (value.isNotEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) {
                    value = "http://$value"
                }
                if (value.isNotEmpty()) {
                    prefs.edit().putString(KEY_SERVER_URL, value).apply()
                    loadServerUrl()
                }
            }
            .setCancelable(!isFirstRun)

        if (!isFirstRun) {
            dialog.setNegativeButton(R.string.dialog_cancel, null)
        }

        dialog.show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadServerUrl()
                true
            }
            R.id.action_change_server -> {
                promptForServerUrl(isFirstRun = false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
