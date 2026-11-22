package app.zorik.player.core.nav

enum class BottomDestination {
    HOME,
    CHANNELS,
    FAVORITES,
    SETTINGS;

    companion object {
        val START: BottomDestination = HOME
    }
}
