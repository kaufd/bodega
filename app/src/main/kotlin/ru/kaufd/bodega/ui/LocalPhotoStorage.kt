package ru.kaufd.bodega.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import ru.kaufd.bodega.domain.PhotoStorage

/** Доступ к [PhotoStorage] из Compose без знания о раскладке файлов. Провайдится в MainActivity. */
val LocalPhotoStorage = staticCompositionLocalOf<PhotoStorage> {
    error("LocalPhotoStorage не предоставлен")
}

/**
 * Единственная связь UI с [PhotoStorage]: относительный путь записи → абсолютный
 * для загрузчика картинок. Экраны получают функцию и о хранилище больше не знают.
 */
@Composable
fun rememberPhotoPathResolver(): (String) -> String {
    val storage = LocalPhotoStorage.current
    return remember(storage) { { path: String -> storage.absolutePath(path) } }
}
