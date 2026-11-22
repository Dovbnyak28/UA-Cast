package app.zorik.player.app

import app.zorik.player.data.icons.IconRepository

/** Custom icon-source persistence, separated from icon resolution and background prefetching. */
class IconSourceController(
    private val iconRepository: IconRepository,
    private val onChanged: () -> Unit = {},
) {
    fun urls(): List<String> = iconRepository.customIconSources()

    fun add(url: String) {
        val previous = urls()
        iconRepository.addCustomIconSource(url)
        if (previous != urls()) onChanged()
    }

    fun remove(url: String) {
        val previous = urls()
        iconRepository.removeCustomIconSource(url)
        if (previous != urls()) onChanged()
    }
}
