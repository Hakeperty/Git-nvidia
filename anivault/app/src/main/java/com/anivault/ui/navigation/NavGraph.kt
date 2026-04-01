package com.anivault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.anivault.ui.screens.downloads.DownloadsScreen
import com.anivault.ui.screens.home.HomeScreen
import com.anivault.ui.screens.`import`.ImportScreen
import com.anivault.ui.screens.library.LibraryScreen
import com.anivault.ui.screens.player.AnimeDetailScreen
import com.anivault.ui.screens.player.MangaDetailScreen
import com.anivault.ui.screens.player.EpisodePlayerScreen
import com.anivault.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Home.route) {
            HomeScreen(
                onAnimeClick = { animeId ->
                    navController.navigate(NavRoutes.AnimeDetail.createRoute(animeId))
                },
                onMangaClick = { mangaId ->
                    navController.navigate(NavRoutes.MangaDetail.createRoute(mangaId))
                }
            )
        }

        composable(NavRoutes.Library.route) {
            LibraryScreen(
                onAnimeClick = { animeId ->
                    navController.navigate(NavRoutes.AnimeDetail.createRoute(animeId))
                },
                onMangaClick = { mangaId ->
                    navController.navigate(NavRoutes.MangaDetail.createRoute(mangaId))
                }
            )
        }

        composable(NavRoutes.Import.route) {
            ImportScreen(
                onImportSuccess = { animeId, isManga ->
                    val route = if (isManga) {
                        NavRoutes.MangaDetail.createRoute(animeId)
                    } else {
                        NavRoutes.AnimeDetail.createRoute(animeId)
                    }
                    navController.navigate(route)
                }
            )
        }

        composable(NavRoutes.Downloads.route) {
            DownloadsScreen(
                onItemClick = { download ->
                    if (download.itemType == com.anivault.data.model.DownloadItemType.EPISODE) {
                        navController.navigate(NavRoutes.Player.createRoute(download.itemId))
                    }
                }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen()
        }

        composable(
            route = NavRoutes.AnimeDetail.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId") ?: return@composable
            AnimeDetailScreen(
                animeId = animeId,
                onBackClick = { navController.popBackStack() },
                onEpisodeClick = { episodeId ->
                    navController.navigate(NavRoutes.Player.createRoute(episodeId))
                }
            )
        }

        composable(
            route = NavRoutes.MangaDetail.route,
            arguments = listOf(
                navArgument("mangaId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: return@composable
            MangaDetailScreen(
                mangaId = mangaId,
                onBackClick = { navController.popBackStack() },
                onChapterClick = { mangaId, chapterId ->
                    navController.navigate(NavRoutes.MangaReader.createRoute(mangaId, chapterId))
                }
            )
        }

        composable(
            route = NavRoutes.Player.route,
            arguments = listOf(
                navArgument("episodeId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
            EpisodePlayerScreen(
                episodeId = episodeId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.MangaReader.route,
            arguments = listOf(
                navArgument("mangaId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: return@composable
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: return@composable
            // MangaReader screen to be implemented
            navController.popBackStack()
        }
    }
}
