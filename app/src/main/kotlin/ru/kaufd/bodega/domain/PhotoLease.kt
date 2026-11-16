package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Файлы фотографий, которые кто-то держит прямо сейчас, хотя ссылки из БД на них нет.
 *
 * Таких две категории, и обе невидимы для `SELECT photos FROM entries`:
 * снимок открытого черновика редактора (импортирован, но ещё не сохранён) и снимок
 * записи, удалённой с возможностью undo (строки уже нет, а фото должно пережить откат).
 * Без этого списка [OrphanPhotoSweeper] считал бы их осиротевшими.
 *
 * Счётчик, а не множество: один и тот же путь может держать и редактор, и pending-undo,
 * и освобождение одним из них не должно снимать защиту.
 */
@Singleton
class PhotoLease @Inject constructor() {

    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val mutex = Mutex()

    fun acquire(path: String?) {
        if (path == null) return
        counts.update { it + (path to (it[path] ?: 0) + 1) }
    }

    fun release(path: String?) {
        if (path == null) return
        counts.update {
            val left = (it[path] ?: 0) - 1
            if (left > 0) it + (path to left) else it - path
        }
    }

    fun acquireAll(paths: Collection<String>) = paths.forEach(::acquire)

    fun releaseAll(paths: Collection<String>) = paths.forEach(::release)

    fun isHeld(path: String): Boolean = counts.value.containsKey(path)

    /**
     * Критическая секция смены владельца файла: удаление строки из БД, взятие и
     * снятие аренды, физическое удаление снимка.
     *
     * Общий замок нужен, потому что «есть ссылка в БД» и «есть аренда» — два
     * отдельных наблюдения. Без него уборка успевает вклиниться между ними:
     * строки уже нет, аренду ещё не взяли — и файл удаляется прямо перед undo.
     */
    suspend fun <T> withOwnership(block: suspend () -> T): T = mutex.withLock { block() }
}
