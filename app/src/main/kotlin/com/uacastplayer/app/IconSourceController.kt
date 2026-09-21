package com.uacastplayer.app

import com.uacastplayer.data.icons.IconRepository

/** Custom icon-source persistence, separated from icon resolution and background prefetching. */
class IconSourceController(private val iconRepository: IconRepository) {
    fun urls(): List<String> = iconRepository.customIconSources()

    fun add(url: String) = iconRepository.addCustomIconSource(url)

    fun remove(url: String) = iconRepository.removeCustomIconSource(url)
}
