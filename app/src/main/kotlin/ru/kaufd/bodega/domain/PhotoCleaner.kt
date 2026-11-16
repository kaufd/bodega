package ru.kaufd.bodega.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Область корутин уровня приложения: переживает закрытие любого экрана. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/**
 * Удаляет осиротевшие файлы фотографий вне жизненного цикла экрана.
 *
 * Редактор закрывается сразу после отмены, его `viewModelScope` к этому моменту
 * уже отменён — а файл импортированного, но не сохранённого снимка удалить всё
 * равно нужно. Ошибки удаления проглатываются осознанно: это фоновая уборка,
 * показывать её пользователю нечего.
 */
@Singleton
class PhotoCleaner @Inject constructor(
    private val photoStorage: PhotoStorage,
    @AppScope private val scope: CoroutineScope,
) {
    fun purge(relativePath: String?) {
        if (relativePath == null) return
        scope.launch { runCatching { photoStorage.delete(relativePath) } }
    }

    fun purgeAll(relativePaths: Collection<String>) = relativePaths.forEach(::purge)
}
