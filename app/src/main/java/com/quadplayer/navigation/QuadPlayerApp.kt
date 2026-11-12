package com.quadplayer.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quadplayer.ui.theme.RoundNavigationIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
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
import com.quadplayer.MainActivityViewModel
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
fun QuadPlayerApp(activityViewModel: MainActivityViewModel) {
    val navController = rememberNavController()
    val externalVideo by activityViewModel.externalVideo.collectAsStateWithLifecycle()
    var externalOpenFailed by remember { mutableStateOf(false) }
    LaunchedEffect(externalVideo) {
        externalVideo?.let { result ->
            activityViewModel.consumeExternalVideo(result)
            if (result.videoId == null) externalOpenFailed = true
            else {
                externalOpenFailed = false
                navController.navigate(Screen.Player.create(result.videoId))
            }
        }
    }
    val bottomDestinations = listOf(
        BottomDestination(Screen.Home, R.string.nav_home) { Icon(Icons.Rounded.Home, contentDescription = null) },
        BottomDestination(Screen.Folders, R.string.nav_folders) { Icon(Icons.Rounded.Folder, contentDescription = null) },
        BottomDestination(Screen.Quad, R.string.nav_quad) { Icon(Icons.Rounded.GridView, contentDescription = null) },
        BottomDestination(Screen.Settings, R.string.nav_settings) { Icon(Icons.Rounded.Settings, contentDescription = null) },
    )

    val entry by navController.currentBackStackEntryAsState()
    val playbackRoute = entry?.destination?.route in setOf(Screen.Player.route, Screen.Quad.route)
    val showRail = !playbackRoute && LocalConfiguration.current.screenWidthDp >= 600 && bottomDestinations.any { it.screen.route == entry?.destination?.route }
    Scaffold(bottomBar = { if (!showRail) QuadPlayerBottomBar(navController, bottomDestinations) }) { innerPadding ->
        Row(Modifier.fillMaxSize()) {
        if (showRail) QuadPlayerNavigationRail(navController, bottomDestinations)
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = if (playbackRoute) Modifier.weight(1f) else Modifier.weight(1f).padding(innerPadding),
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
                arguments = listOf(navArgument("videoId") { defaultValue = -1L }, navArgument("playlistId") { defaultValue = -1L }),
            ) {
                val viewModel: PlayerViewModel = hiltViewModel()
                PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Playlist.route,
                arguments = listOf(navArgument("playlistId") { defaultValue = -1L }),
            ) { entry ->
                val viewModel: PlaylistViewModel = hiltViewModel()
                val playlistId = entry.arguments?.getLong("playlistId") ?: -1L
                PlaylistScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { navController.navigate(Screen.Player.create(it, playlistId)) },
                )
            }
        }
        }
    }
    if (externalOpenFailed) {
        AlertDialog(
            onDismissRequest = { externalOpenFailed = false },
            text = { Text(stringResource(R.string.external_video_open_failed)) },
            confirmButton = { TextButton(onClick = { externalOpenFailed = false }) { Text(stringResource(R.string.common_close)) } },
        )
    }
}

@Composable
private fun QuadPlayerNavigationRail(navController: NavHostController, destinations: List<BottomDestination>) {
    val entry by navController.currentBackStackEntryAsState()
    NavigationRail(modifier = Modifier.fillMaxHeight(), containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
        destinations.forEach { destination ->
            val selected = entry?.destination?.hierarchy?.any { it.route == destination.screen.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { RoundNavigationIcon(selected) { destination.icon() } },
                label = { Text(stringResource(destination.labelRes), maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(indicatorColor = Color.Transparent),
            )
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

    NavigationBar(
        modifier = Modifier.shadow(8.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
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
                icon = { RoundNavigationIcon(selected) { destination.icon() } },
                label = { Text(stringResource(destination.labelRes), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
