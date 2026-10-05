package com.uacastplayer.data.update

import java.io.InputStream
import java.io.OutputStream

/** Checks ownership between bounded writes; callers abandon an uncommitted session on cancellation. */
internal fun copyStagedApk(input: InputStream, output: OutputStream, checkActive: () -> Unit) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        checkActive()
        val count = input.read(buffer)
        if (count < 0) return
        checkActive()
        output.write(buffer, 0, count)
    }
}
