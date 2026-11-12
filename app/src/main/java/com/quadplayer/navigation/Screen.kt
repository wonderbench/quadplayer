package com.quadplayer.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Folders : Screen("folders")
    data object Quad : Screen("quad")
    data object Settings : Screen("settings")
    data object Player : Screen("player/{videoId}") {
        fun create(videoId: Long): String = "player/$videoId"
    }
    data object Playlist : Screen("playlist/{playlistId}") {
        fun create(playlistId: Long): String = "playlist/$playlistId"
    }
}
