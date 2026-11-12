package com.quadplayer.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quadplayer.R
import com.quadplayer.feature.filebrowser.FileBrowserScreen
import com.quadplayer.feature.filebrowser.FileBrowserViewModel
import com.quadplayer.feature.home.HomeScreen
import com.quadplayer.feature.home.HomeViewModel
import com.quadplayer.feature.player.PlayerScreen
import com.quadplayer.feature.player.PlayerViewModel
import com.quadplayer.feature.playlist.PlaylistScreen
import com.quadplayer.feature.playlist.PlaylistViewModel
import com.quadplayer.feature.quadplayer.QuadPlayerScreen
import com.quadplayer.feature.quadplayer.QuadPlayerViewModel
import com.quadplayer.feature.settings.SettingsScreen
import com.quadplayer.feature.settings.SettingsViewModel

private data class BottomDestination(
    val screen: Screen,
    val labelRes: Int,
    val icon: @Composable () -> Unit,
)

@Composable
fun QuadPlayerApp() {
    val navController = rememberNavController()
    val bottomDestinations = listOf(
        BottomDestination(Screen.Home, R.string.nav_home) { Icon(Icons.Rounded.Home, contentDescription = null) },
        BottomDestination(Screen.Folders, R.string.nav_folders) { Icon(Icons.Rounded.Folder, contentDescription = null) },
        BottomDestination(Screen.Quad, R.string.nav_quad) { Icon(Icons.Rounded.GridView, contentDescription = null) },
        BottomDestination(Screen.Settings, R.string.nav_settings) { Icon(Icons.Rounded.Settings, contentDescription = null) },
    )

    Scaffold(bottomBar = { QuadPlayerBottomBar(navController, bottomDestinations) }) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    onOpenVideo = { navController.navigate(Screen.Player.create(it)) },
                    onOpenPlaylist = { navController.navigate(Screen.Playlist.create(it)) },
                )
            }
            composable(Screen.Folders.route) {
                val viewModel: FileBrowserViewModel = hiltViewModel()
                FileBrowserScreen(
                    viewModel = viewModel,
                    onOpenVideo = { navController.navigate(Screen.Player.create(it)) },
                    onOpenPlaylist = { navController.navigate(Screen.Playlist.create(it)) },
                )
            }
            composable(Screen.Quad.route) {
                val viewModel: QuadPlayerViewModel = hiltViewModel()
                QuadPlayerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(viewModel = viewModel)
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(navArgument("videoId") { defaultValue = -1L }),
            ) {
                val viewModel: PlayerViewModel = hiltViewModel()
                PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Playlist.route,
                arguments = listOf(navArgument("playlistId") { defaultValue = -1L }),
            ) {
                val viewModel: PlaylistViewModel = hiltViewModel()
                PlaylistScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { navController.navigate(Screen.Player.create(it)) },
                )
            }
        }
    }
}

@Composable
private fun QuadPlayerBottomBar(
    navController: NavHostController,
    destinations: List<BottomDestination>,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isQuadScreen = currentDestination?.hierarchy?.any { it.route == Screen.Quad.route } == true
    val showBottomBar = !isQuadScreen && destinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.screen.route } == true
    }
    if (!showBottomBar) return

    NavigationBar {
        destinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.screen.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = destination.icon,
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
