package com.uacastplayer.ui.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.remote.dispatchTvRemote
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.tv.LocalTvInputRegistry
import com.uacastplayer.ui.tv.LocalTvMode
import com.uacastplayer.ui.tv.TvInputRegistry
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconPackCheckDialogInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun changingPackReplacesOldPreviewAndDpadCanCloseDialog() {
        val image = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            finally { bitmap.recycle() }
        }
        val pool = Executors.newSingleThreadExecutor()
        ServerSocket(0).use { server ->
            pool.submit {
                repeat(2) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine().split(' ')[1]
                        while (!reader.readLine().isNullOrEmpty()) { /* Drain headers. */ }
                        val status = if (path.startsWith("/first/")) 200 else 404
                        val header = "HTTP/1.1 $status Result\r\nContent-Length: ${image.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        socket.getOutputStream().apply { write(header.toByteArray()); write(image) }
                    }
                }
            }
            try { exerciseDialog("http://127.0.0.1:${server.localPort}") }
            finally { pool.shutdownNow() }
        }
    }

    private fun exerciseDialog(base: String) {
        val source = mutableStateOf("$base/first")
        val showing = mutableStateOf(true)
        val registry = TvInputRegistry()
        val channels = listOf(M3uChannel("Sample", "https://unused.test", tvgId = "sample"))
        var dismissed = 0
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            CompositionLocalProvider(LocalTvMode provides true, LocalTvInputRegistry provides registry) {
                if (showing.value) IconPackCheckDialog(source.value, channels) {
                    dismissed++; showing.value = false
                }
            }
        } }
        waitForLabel(R.string.icon_pack_check_found)
        rule.runOnIdle { source.value = "$base/second" }
        waitForLabel(R.string.icon_pack_check_missing)
        rule.onNodeWithText(rule.activity.getString(R.string.icon_pack_check_found)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.common_cancel))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.runOnIdle { rule.activity.dispatchTvRemote(RemoteCommand.SELECT, registry::dispatchToDialog) }
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    private fun waitForLabel(label: Int) {
        val text = rule.activity.getString(label)
        rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
