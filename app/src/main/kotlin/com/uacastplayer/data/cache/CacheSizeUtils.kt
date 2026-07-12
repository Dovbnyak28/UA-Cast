package com.uacastplayer.data.cache

import java.io.File

object CacheSizeUtils {

    fun sizeOf(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clear(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { it.deleteRecursively() }
        } else {
            file.delete()
        }
    }
}
