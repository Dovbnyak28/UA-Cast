package com.uacastplayer.data.epg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpgSnapshotSourceValidationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "epg_snapshot.bin").delete()
    }

    @Test
    fun `snapshot from previously configured source is rejected and removed`() = runTest {
        val oldUrl = "https://old.example/guide.xml"
        EpgSnapshotStore(context).save(
            sourceFingerprint = Fingerprint.of(oldUrl),
            savedAtEpochMillis = 1L,
            data = EpgData(EpgIndex(emptyList()), emptyMap()),
        )

        val restored = EpgRepository(context).restoreSnapshot("https://new.example/guide.xml")

        assertNull(restored)
        assertFalse(File(context.filesDir, "epg_snapshot.bin").exists())
    }
}
