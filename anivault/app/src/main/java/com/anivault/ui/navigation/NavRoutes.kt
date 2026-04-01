package com.anivault.ui.navigation

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object Library : NavRoutes("library")
    object Import : NavRoutes("import")
    object Downloads : NavRoutes("downloads")
    object Settings : NavRoutes("settings")
    object AnimeDetail : NavRoutes("anime_detail/{animeId}") {
        fun createRoute(animeId: String) = "anime_detail/$animeId"
    }
    object MangaDetail : NavRoutes("manga_detail/{mangaId}") {
        fun createRoute(mangaId: String) = "manga_detail/$mangaId"
    }
    object Player : NavRoutes("player/{episodeId}") {
        fun createRoute(episodeId: String) = "player/$episodeId"
    }
    object MangaReader : NavRoutes("reader/{mangaId}/{chapterId}") {
        fun createRoute(mangaId: String, chapterId: String) = "reader/$mangaId/$chapterId"
    }
}

enum class BottomNavItem(
    val route: String,
    val title: String,
    val iconRes: String
) {
    Home("home", "Home", "home"),
    Library("library", "Library", "library"),
    Import("import", "Import", "import"),
    Downloads("downloads", "Downloads", "download"),
    Settings("settings", "Settings", "settings")
}
