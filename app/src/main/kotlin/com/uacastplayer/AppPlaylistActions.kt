package com.uacastplayer

import android.net.Uri
import com.uacastplayer.playlist.PlaylistSource

internal fun AppViewModel.setPlaylistDisplayName(name: String) =
    playlistController.setPlaylistDisplayName(name)

internal fun AppViewModel.loadPlaylistFromUrl(url: String) = playlistController.loadPlaylistFromUrl(url)

internal fun AppViewModel.loadXtreamPlaylist(server: String, username: String, password: String) =
    playlistController.loadXtreamPlaylist(server, username, password)

internal fun AppViewModel.loadPlaylistFromFile(uri: Uri) = playlistController.loadPlaylistFromFile(uri)

internal fun AppViewModel.cancelPendingPlaylistAdd() = playlistController.cancelPendingSourceAdd()

internal fun AppViewModel.refreshPlaylist() = playlistController.refreshPlaylist()

internal fun AppViewModel.switchPlaylistSource(source: PlaylistSource) =
    playlistController.switchPlaylistSource(source)

internal fun AppViewModel.removePlaylistSource(id: String) = playlistController.removePlaylistSource(id)

internal fun AppViewModel.pinGroup(groupKey: String) = groupVisibilityController.pinGroup(groupKey)

internal fun AppViewModel.hideGroup(groupKey: String) = groupVisibilityController.hideGroup(groupKey)

/** Returns a group to the active source's default order and visibility. */
internal fun AppViewModel.clearGroupOverride(groupKey: String) =
    groupVisibilityController.clearOverride(groupKey)
