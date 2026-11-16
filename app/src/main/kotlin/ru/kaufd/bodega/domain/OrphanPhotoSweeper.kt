package ru.kaufd.bodega.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подстраховочный порог возраста для файлов, которых не видит ни БД, ни [PhotoLease].
 *
 * Основная защита живых файлов — аренда, а не возраст: она знает и про черновик
 * редактора, и про pending-undo. Порог остаётся на случай держателя, о котором мы
 * не знаем, и стоит дёшево — сирота проживёт на неделю дольше.
 */
private const val ORPHAN_AGE_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Удаляет файлы фотографий, которых не держит ни запись в БД, ни живой экран.
 *
 * Единственный механизм самолечения хранилища: удаление файла может не удаться
 * ([PhotoStorage.delete] вернёт false), процесс может умереть между удалением
 * записи и удалением её снимка — в обоих случаях файл остаётся сиротой навсегда.
 *
 * Запускается один раз за процесс — после первой композиции, когда восстановленные
 * экраны уже успели взять аренду на свои файлы (см. MainActivity).
 */
@Singleton
class OrphanPhotoSweeper @Inject constructor(
    private val repository: EntryRepository,
    private val photoStorage: PhotoStorage,
    private val photoLease: PhotoLease,
    @AppScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /**
     * Запуск за процесс ровно один. MainActivity пересоздаётся при повороте и смене
     * темы, и без этого несколько уборок шли бы параллельно, соревнуясь за одни файлы.
     */
    fun sweep() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { sweepNow() }
    }

    /** @return сколько файлов удалено. */
    suspend fun sweepNow(now: Long = System.currentTimeMillis()): Int {
        // Первый проход только сужает круг: он берёт снимки БД и каталога без замка,
        // поэтому его выводам верить нельзя — решение принимается ниже, под замком.
        val candidates = try {
            val used = repository.usedPhotoPaths().toSet()
            photoStorage.list().filter {
                it.relativePath !in used && now - it.modifiedAt > ORPHAN_AGE_MS
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Список используемых путей неизвестен — не удаляем ничего: иначе
            // отказ БД стоил бы пользователю всех фотографий разом.
            return 0
        }

        var removed = 0
        for (photo in candidates) {
            // Проверка принадлежности и само удаление — под общим замком владения:
            // пока мы перебирали каталог, undo мог вернуть запись в БД, а редактор —
            // взять аренду. Порог возраста при этом отсекает файлы, только что
            // созданные импортом, — их аренду редактор берёт вне замка.
            val deleted = photoLease.withOwnership {
                val stillUsed = try {
                    repository.usedPhotoPaths().toSet()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@withOwnership false
                }
                if (photo.relativePath in stillUsed || photoLease.isHeld(photo.relativePath)) {
                    return@withOwnership false
                }
                try {
                    photoStorage.delete(photo.relativePath)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Не удалось — не беда: файл остаётся сиротой до следующего
                    // запуска, когда попытка повторится.
                    false
                }
            }
            if (deleted) removed++
        }
        return removed
    }
}
