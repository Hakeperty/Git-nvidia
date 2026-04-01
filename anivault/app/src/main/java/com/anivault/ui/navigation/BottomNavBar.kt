package com.anivault.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.anivault.ui.theme.PrimaryOrange
import com.anivault.ui.theme.SurfacePrimary
import com.anivault.ui.theme.SurfaceSecondary
import com.anivault.ui.theme.TextSecondary

@Composable
fun BottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom nav on detail and player screens
    val shouldShowBottomBar = when (currentRoute) {
        null,
        NavRoutes.AnimeDetail.route,
        NavRoutes.MangaDetail.route,
        NavRoutes.Player.route,
        NavRoutes.MangaReader.route -> false
        else -> true
    }

    if (!shouldShowBottomBar) return

    NavigationBar(
        modifier = modifier.height(80.dp),
        containerColor = SurfacePrimary,
        tonalElevation = 8.dp
    ) {
        BottomNavItem.entries.forEach { item ->
            val isSelected = currentRoute == item.route

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = getIconVector(item.iconRes),
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryOrange,
                    selectedTextColor = PrimaryOrange,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = SurfaceSecondary
                )
            )
        }
    }
}

@Composable
private fun getIconVector(iconName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (iconName) {
        "home" -> Icons.Filled.Home
        "library" -> Icons.Filled.Bookshelf
        "import" -> Icons.Filled.AddLink
        "download" -> Icons.Filled.Download
        "settings" -> Icons.Filled.Settings
        else -> Icons.Filled.Home
    }
}
