package com.uacastplayer.data.backup

import android.content.Context
import android.net.Uri
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.core.security.BackupCipher
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** Binary envelope + legacy JSON, bounded before allocation/parsing. Caller owns IO dispatch. */
object BackupDocumentReader {
    @OptIn(InternalCoroutinesApi::class)
    fun read(context: Context, uri: Uri, owner: Job): ByteArray? = runCatchingNonFatal {
        owner.ensureActive()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val closeOnCancel = owner.invokeOnCompletion(onCancelling = true) { cause ->
                if (cause != null) runCatchingNonFatal { stream.close() }
            }
            try {
                val result = BoundedByteReader.readBytes(stream, BackupCipher.maxFileBytes)
                owner.ensureActive()
                (result as? BoundedBytesResult.Success)?.bytes
            } finally { closeOnCancel.dispose() }
        }
    }.getOrNull()
}
