package com.uacastplayer.data.cache

import java.io.File

object CacheSizeUtils {

    fun sizeOf(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** The combined size of [files], skipping any that has since disappeared. */
    fun sizeOf(files: List<File>): Long = files.sumOf { sizeOf(it) }

    fun clear(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { it.deleteRecursively() }
        } else {
            file.delete()
        }
    }

    fun clear(files: List<File>) {
        files.forEach { clear(it) }
    }
}
