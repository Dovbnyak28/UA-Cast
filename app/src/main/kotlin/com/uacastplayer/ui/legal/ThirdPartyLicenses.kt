package com.uacastplayer.ui.legal

import android.content.res.AssetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.uacastplayer.R
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.tvReadingFocus
import java.io.IOException
import kotlinx.coroutines.withContext

private const val LICENSE_LINES_PER_ITEM = 24

internal val thirdPartyLicenseFiles = linkedMapOf(
    "FFmpeg — LGPL 2.1+" to "ffmpeg-lgpl-2.1.txt",
    "FFmpeg / AndroidX — notices" to "ffmpeg-decoder-notice.txt",
    "AndroidX — Apache 2.0" to "ffmpeg-wrapper-apache-2.0.txt",
    "libyuv — BSD 3-Clause" to "libyuv-bsd.txt",
    "Roboto — Apache 2.0" to "tv-wordmark-roboto.txt",
    "Android NDK — notices" to "android-ndk-runtime-notices.txt",
)

/** Only reviewed APK assets are readable; provider files/URLs cannot enter the licence viewer. */
internal fun readLicenseParagraphs(assets: AssetManager, name: String): List<String> {
    require(name in thirdPartyLicenseFiles.values)
    return assets.open("licenses/$name").bufferedReader().use { reader ->
        reader.lineSequence().chunked(LICENSE_LINES_PER_ITEM).map { it.joinToString("\n") }.toList()
    }
}

@Composable
internal fun ThirdPartyLicensesButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    SecondaryButton(stringResource(R.string.legal_licenses_title), { open = true }, Modifier.fillMaxWidth())
    if (open) ThirdPartyLicensesDialog { open = false }
}

@Composable
private fun ThirdPartyLicensesDialog(onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val assets = context.applicationContext.assets
    val paragraphs by produceState<List<String>?>(initialValue = null, key1 = selected) {
        value = null
        val name = selected ?: return@produceState
        value = withContext(AppDispatchers.io) {
            try { readLicenseParagraphs(assets, name) } catch (_: IOException) { emptyList() }
        }
    }
    Dialog(onDismissRequest = { if (selected != null) selected = null else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(UaTheme.palette.void).safeDrawingPadding().padding(20.dp)) {
            SecondaryButton(stringResource(R.string.common_back),
                { if (selected != null) selected = null else onDismiss() })
            Text(thirdPartyLicenseFiles.entries.find { it.value == selected }?.key
                ?: stringResource(R.string.legal_licenses_title), color = UaTheme.palette.labelPrimary,
                style = BodyRegular, modifier = Modifier.padding(vertical = 12.dp))
            // Each document opens at its beginning, including after reading a long previous licence.
            key(selected) {
                if (selected == null) LicenseIndex(Modifier.weight(1f)) { selected = it }
                else LicenseText(paragraphs, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LicenseIndex(modifier: Modifier, onSelect: (String) -> Unit) {
    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Text(stringResource(R.string.legal_ffmpeg_notice), style = Caption,
                color = UaTheme.palette.labelPrimary, modifier = Modifier.tvReadingFocus())
        }
        items(thirdPartyLicenseFiles.keys.toList(), key = { it }) { title ->
            SecondaryButton(title, { onSelect(thirdPartyLicenseFiles.getValue(title)) },
                Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        item { LicenseSourcesButton() }
    }
}

@Composable
private fun LicenseText(paragraphs: List<String>?, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        item {
            if (paragraphs == null) Text(stringResource(R.string.legal_loading),
                color = UaTheme.palette.labelPrimary)
            else if (paragraphs.isEmpty()) Text(stringResource(R.string.legal_load_failed),
                color = UaTheme.palette.labelPrimary)
        }
        items(paragraphs.orEmpty()) { text ->
            SelectionContainer {
                Text(text, style = Caption, color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.padding(vertical = 4.dp).tvReadingFocus())
            }
        }
    }
}

@Composable
private fun LicenseSourcesButton() {
    val context = LocalContext.current
    SecondaryButton(stringResource(R.string.legal_sources), {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                "https://github.com/Dovbnyak28/UA-Cast/releases".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.legal_open_failed, Toast.LENGTH_LONG).show()
        }
    }, Modifier.fillMaxWidth().padding(top = 8.dp))
}
