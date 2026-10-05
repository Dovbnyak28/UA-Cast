package com.uacastplayer.app

import android.net.Uri
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.core.security.BackupCipher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRestoreWorkflowTest {
    private val data = BackupData(emptyList(), emptyList(), BackupSettings(bufferSize = "LARGE"))
    private val uri = Uri.parse("content://test/backup")

    private suspend fun await(workflow: BackupRestoreWorkflow, accepts: (BackupRestoreState) -> Boolean) =
        withTimeout(10_000) { while (!accepts(workflow.state.value)) delay(5) }

    @Test fun previewAndCancelNeverWriteThenConfirmationWritesOnlyOnce() = runBlocking {
        var applied = 0
        val workflow = BackupRestoreWorkflow(
            this, { BackupCodec.encode(data).toByteArray() }, { it },
            { data.copy(settings = BackupSettings()) }, { launch { applied++ } },
        )
        workflow.open(uri)
        await(workflow) { it is BackupRestoreState.Preview }
        assertEquals(0, applied)
        workflow.cancel()
        workflow.confirm()
        assertEquals(0, applied)
        workflow.open(uri)
        await(workflow) { it is BackupRestoreState.Preview }
        workflow.confirm()
        workflow.confirm()
        await(workflow) { it == BackupRestoreState.Idle }
        assertEquals(1, applied)
    }

    @Test fun encryptedInputCannotMutateUntilPasswordAndConfirmation() = runBlocking {
        val password = "a sufficiently long password".toCharArray()
        val encrypted = BackupCipher.encrypt(BackupCodec.encode(data).toByteArray(), password)
        var applied = 0
        val workflow = BackupRestoreWorkflow(this, { encrypted }, { it }, { data }, { launch { applied++ } })
        workflow.open(uri)
        await(workflow) { it is BackupRestoreState.PasswordRequired }
        workflow.unlock("a different long password".toCharArray())
        await(workflow) { it == BackupRestoreState.PasswordRequired(failed = true) }
        assertEquals(0, applied)
        workflow.unlock(password)
        await(workflow) { it is BackupRestoreState.Preview }
        assertEquals(0, applied)
        workflow.confirm()
        await(workflow) { it == BackupRestoreState.Idle }
        assertEquals(1, applied)
    }

    @Test fun changedDeviceStateRequiresRefreshedPreviewBeforeApply() = runBlocking {
        var current = data.copy(settings = BackupSettings())
        var applied = 0
        val workflow = BackupRestoreWorkflow(
            this, { BackupCodec.encode(data).toByteArray() }, { it }, { current }, { launch { applied++ } },
        )
        workflow.open(uri)
        await(workflow) { it is BackupRestoreState.Preview }
        current = data
        workflow.confirm()
        await(workflow) { it is BackupRestoreState.Preview }
        assertEquals(0, applied)
        workflow.confirm()
        await(workflow) { it == BackupRestoreState.Idle }
        assertEquals(1, applied)
    }

    @Test fun replacedAndCancelledReadersCannotPublishAnOldPreview() = runBlocking {
        val gate = CompletableDeferred<ByteArray?>()
        var reads = 0
        val workflow = BackupRestoreWorkflow(
            this, { if (++reads == 1) gate.await() else BackupCodec.encode(data).toByteArray() },
            { it }, { data }, { launch {} },
        )
        workflow.open(uri)
        delay(10)
        workflow.open(uri)
        await(workflow) { it is BackupRestoreState.Preview }
        gate.complete("invalid".toByteArray())
        delay(10)
        assertTrue(workflow.state.value is BackupRestoreState.Preview)
        workflow.cancel()
    }

    @Test fun invalidPortablePayloadFailsWithoutApplying() = runBlocking {
        var applied = 0
        val workflow = BackupRestoreWorkflow(
            this, { BackupCodec.encode(data).toByteArray() }, { null }, { data }, { launch { applied++ } },
        )
        workflow.open(uri)
        await(workflow) { it == BackupRestoreState.Failed }
        workflow.confirm()
        assertEquals(0, applied)
    }
}
