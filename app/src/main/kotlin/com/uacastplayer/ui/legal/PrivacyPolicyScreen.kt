package com.uacastplayer.ui.legal

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.uacastplayer.R
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme

/**
 * Shows the same privacy document that is published from [legal/privacy-policy.html].
 *
 * The document is local and static. JavaScript, DOM storage, file access and content access are
 * disabled, and all links are ignored so a compromised policy asset cannot turn this screen into
 * a general-purpose browser.
 */
@Composable
fun PrivacyPolicyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // This view is an offline document. Block HTTP(S) subresources as well as top-level
            // navigation so a future edit to the asset cannot turn it into a network client.
            settings.blockNetworkLoads = true
            // The policy is a local, static document. Keep every file-origin escape hatch closed
            // even on older WebView implementations where disabling file access alone is not
            // sufficient to prevent a future asset change from reaching another local resource.
            disableLegacyFileOriginAccess()
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean = true
            }
            val html = context.assets.open("privacy-policy.html").bufferedReader().use { it.readText() }
            loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UaTheme.palette.void)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = UaTheme.palette.labelPrimary,
                )
            }
            Text(
                text = stringResource(R.string.privacy_policy_title),
                style = CardTitle,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().testTag(UiTestTags.PRIVACY_POLICY_BODY),
        )
    }
}

/** These two flags are deprecated in newer WebView SDKs but remain the only explicit guard on
 * older WebViews, which this app still supports. Keep the suppression limited to this bridge. */
@Suppress("DEPRECATION")
private fun WebView.disableLegacyFileOriginAccess() {
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
}
