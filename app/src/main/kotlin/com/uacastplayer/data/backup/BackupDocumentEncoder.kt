package com.uacastplayer.data.backup

import android.util.JsonWriter
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupJsonInputGuard
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.log.AppLog
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Encode one flat schema row at a time, stopping at the import budget before opening a SAF file.
 * Schema fields remain defined by BackupCodec; no whole JSON tree, String or unbounded byte array. */
internal object BackupDocumentEncoder {
    fun encode(data: BackupData, owner: Job, maxBytes: Int = BackupCodec.MAX_BACKUP_BYTES): ByteArray? {
        require(maxBytes in 0..BackupCodec.MAX_BACKUP_BYTES)
        owner.ensureActive()
        // Root and settings are objects too. All schema row values are primitive; depth is at most 3.
        if (data.sources.size.toLong() + data.favorites.size + ROOT_OBJECTS > BackupJsonInputGuard.MAX_OBJECTS) {
            return null
        }
        return runCatchingNonFatal {
            val output = LimitedBackupOutput(owner, maxBytes)
            // JsonWriter emits individual characters too. Buffer them before the UTF-8 encoder.
            JsonWriter(BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))).use { writer ->
                writer.setIndent("  ")
                DocumentJson(writer).write(data)
            }
            owner.ensureActive()
            output.bytes()
        }.onFailure { error ->
            AppLog.w("BackupDocumentEncoder") { "Backup encoding refused: ${error.javaClass.simpleName}" }
        }.getOrNull()
    }

    private const val ROOT_OBJECTS = 2
}

private class DocumentJson(private val writer: JsonWriter) {
    private var properties = ROOT_PROPERTIES

    fun write(data: BackupData) {
        writer.beginObject().name("version").value(BackupCodec.CURRENT_VERSION)
        writer.name("sources").beginArray()
        data.sources.forEach { writeObject(BackupCodec.sourceToJson(it)) }
        writer.endArray().name("favorites").beginArray()
        data.favorites.forEach { writeObject(BackupCodec.favoriteToJson(it)) }
        writer.endArray().name("settings")
        writeObject(BackupCodec.settingsToJson(data.settings))
        writer.endObject()
    }

    private fun writeObject(value: JSONObject) {
        writer.beginObject()
        val keys = value.keys()
        while (keys.hasNext()) {
            if (++properties > BackupJsonInputGuard.MAX_PROPERTIES) throw BackupEncodingLimitExceeded()
            val key = keys.next()
            writer.name(key)
            when (val field = value.get(key)) {
                is String -> writer.value(field)
                is Number -> writer.value(field)
                is Boolean -> writer.value(field)
                JSONObject.NULL -> writer.nullValue()
                else -> error("Unsupported backup field type")
            }
        }
        writer.endObject()
    }

    private companion object { const val ROOT_PROPERTIES = 4 }
}

private class LimitedBackupOutput(private val owner: Job, private val maxBytes: Int) : OutputStream() {
    private val buffer = ByteArrayOutputStream(minOf(BUFFER_BYTES, maxBytes))

    override fun write(value: Int) {
        admit(1)
        buffer.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        admit(length)
        buffer.write(bytes, offset, length)
    }

    private fun admit(length: Int) {
        owner.ensureActive()
        if (length > maxBytes - buffer.size()) throw BackupEncodingLimitExceeded()
    }

    fun bytes(): ByteArray = buffer.toByteArray()

    private companion object { const val BUFFER_BYTES = 8192 }
}

private class BackupEncodingLimitExceeded : IOException("Backup encoding budget exceeded")
