package com.uacastplayer.app

import android.net.Uri
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupPreview
import com.uacastplayer.backup.BackupPreviewPolicy
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.security.BackupCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BackupRestoreState {
    data object Idle : BackupRestoreState
    data object Reading : BackupRestoreState
    data class PasswordRequired(val failed: Boolean = false) : BackupRestoreState
    data class Preview(val summary: BackupPreview) : BackupRestoreState
    data object Applying : BackupRestoreState
    data object Failed : BackupRestoreState
}

/** One pending import in ViewModel memory. No writes until explicit confirmation; process death
 * discards pending data/password. Confirm applies these exact validated bytes, never rereads URI. */
internal class BackupRestoreWorkflow(
    private val scope: CoroutineScope,
    private val read: suspend (Uri) -> ByteArray?,
    private val prepare: suspend (BackupData) -> BackupData?,
    private val current: () -> BackupData,
    private val apply: (BackupData) -> Job,
    private val cpuDispatcher: CoroutineDispatcher = AppDispatchers.cpu,
) {
    private val mutableState = MutableStateFlow<BackupRestoreState>(BackupRestoreState.Idle)
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var encrypted: ByteArray? = null
    private var pending: BackupData? = null

    fun open(uri: Uri) {
        if (mutableState.value == BackupRestoreState.Applying) return
        cancel()
        mutableState.value = BackupRestoreState.Reading
        work = scope.launch {
            val bytes = read(uri)
            currentCoroutineContext().ensureActive()
            if (bytes == null) mutableState.value = BackupRestoreState.Failed
            else if (BackupCipher.isEncrypted(bytes)) {
                encrypted = bytes
                mutableState.value = BackupRestoreState.PasswordRequired()
            } else publishPreview(bytes)
        }
    }

    fun unlock(password: CharArray) {
        val bytes = encrypted ?: return
        if (mutableState.value !is BackupRestoreState.PasswordRequired) return
        val owned = password.copyOf()
        mutableState.value = BackupRestoreState.Reading
        work = scope.launch {
            val plain = withContext(cpuDispatcher) {
                val owner = currentCoroutineContext().job
                runCatchingNonFatal { BackupCipher.decrypt(bytes, owned, owner::ensureActive) }.getOrNull()
            }
            if (plain == null) mutableState.value = BackupRestoreState.PasswordRequired(failed = true)
            else try { publishPreview(plain) } finally { plain.fill(0) }
        }.also { it.invokeOnCompletion { owned.fill('\u0000') } }
    }

    private suspend fun publishPreview(bytes: ByteArray) {
        val data = withContext(AppDispatchers.io) {
            if (bytes.size > BackupCodec.MAX_BACKUP_BYTES) null
            else BackupCodec.decode(bytes.toString(Charsets.UTF_8))?.let { prepare(it) }
        }
        currentCoroutineContext().ensureActive()
        if (data == null) mutableState.value = BackupRestoreState.Failed
        else {
            pending = data
            encrypted = null
            val snapshot = current()
            val summary = withContext(cpuDispatcher) { BackupPreviewPolicy.create(data, snapshot) }
            mutableState.value = BackupRestoreState.Preview(summary)
        }
    }

    fun confirm() {
        val data = pending ?: return
        val preview = mutableState.value as? BackupRestoreState.Preview ?: return
        mutableState.value = BackupRestoreState.Reading
        work = scope.launch {
            val snapshot = current()
            val refreshed = withContext(cpuDispatcher) { BackupPreviewPolicy.create(data, snapshot) }
            if (refreshed != preview.summary) mutableState.value = BackupRestoreState.Preview(refreshed)
            else {
                mutableState.value = BackupRestoreState.Applying
                pending = null
                apply(data).join()
                currentCoroutineContext().ensureActive()
                mutableState.value = BackupRestoreState.Idle
            }
        }
    }

    fun cancel() {
        if (mutableState.value == BackupRestoreState.Applying) return
        work?.cancel()
        work = null
        encrypted = null
        pending = null
        mutableState.value = BackupRestoreState.Idle
    }
}
