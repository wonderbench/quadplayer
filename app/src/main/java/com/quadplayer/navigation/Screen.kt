package com.quadplayer.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Folders : Screen("folders")
    data object Quad : Screen("quad")
    data object Settings : Screen("settings")
    data object Player : Screen("player/{videoId}?playlistId={playlistId}") {
        fun create(videoId: Long, playlistId: Long = -1L): String = "player/$videoId?playlistId=$playlistId"
    }
    data object Playlist : Screen("playlist/{playlistId}") {
        fun create(playlistId: Long): String = "playlist/$playlistId"
    }
}
