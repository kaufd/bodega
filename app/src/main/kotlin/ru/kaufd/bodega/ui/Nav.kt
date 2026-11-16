package ru.kaufd.bodega.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.ui.detail.DetailScreen
import ru.kaufd.bodega.ui.edit.EditScreen
import ru.kaufd.bodega.ui.filters.FiltersScreen
import ru.kaufd.bodega.ui.list.ListScreen
import ru.kaufd.bodega.ui.settings.SettingsScreen

object Routes {
    const val LIST = "list"
    const val FILTERS = "filters"
    const val SETTINGS = "settings"
    const val DETAIL_PATTERN = "detail/{id}"
    const val EDIT_PATTERN = "edit?id={id}&category={category}"
    fun detail(id: Long) = "detail/$id"

    /** Редактор: id для правки, category — стартовая категория новой записи. */
    fun edit(id: Long? = null, category: Category? = null): String {
        val params = buildList {
            if (id != null) add("id=$id")
            if (category != null) add("category=${category.name}")
        }
        return if (params.isEmpty()) "edit" else "edit?" + params.joinToString("&")
    }
}

@Composable
fun BodegaNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            ListScreen(
                onEntryClick = { nav.navigate(Routes.detail(it)) },
                onAddClick = { category -> nav.navigate(Routes.edit(category = category)) },
                onFiltersClick = { nav.navigate(Routes.FILTERS) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.DETAIL_PATTERN,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            DetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(Routes.edit(id)) },
            )
        }
        composable(
            Routes.EDIT_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("category") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) {
            EditScreen(onClose = { nav.popBackStack() })
        }
        composable(Routes.FILTERS) {
            FiltersScreen(onClose = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onClose = { nav.popBackStack() })
        }
    }
}
