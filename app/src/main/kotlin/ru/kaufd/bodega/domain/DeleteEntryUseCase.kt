package ru.kaufd.bodega.domain

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый владелец сценария «удаление с undo». Хранит одну ожидающую (pending)
 * запись; при вытеснении прежней pending её фотографии очищаются (иначе — сироты на диске).
 */
@Singleton
class DeleteEntryUseCase @Inject constructor(
    private val repository: EntryRepository,
    private val photoLease: PhotoLease,
) {
    private val _pending = MutableStateFlow<Entry?>(null)
    val pending: StateFlow<Entry?> = _pending.asStateFlow()

    /**
     * Переход владения выполняется целиком или не выполняется вовсе.
     *
     * NonCancellable: экран закрывается сразу после подтверждения, его
     * `viewModelScope` отменяется — без этого строка успевала исчезнуть из БД
     * раньше, чем появлялись pending и аренда, и undo терялся.
     * Замок — общий с уборкой ([PhotoLease.withOwnership]), чтобы она не видела
     * промежуточных состояний.
     */
    private suspend fun <T> transfer(block: suspend () -> T): T =
        withContext(NonCancellable) { photoLease.withOwnership(block) }

    /**
     * Удаляет строку; фотографии прежней pending-записи (если это другая запись) очищаются.
     *
     * Для undo запоминается строка, фактически удалённая из БД, а не переданный
     * UI-снимок: экран мог прочитать запись до её правки. Если строки уже не было,
     * прежний pending сохраняется — undo остаётся осмысленным.
     */
    suspend fun delete(entry: Entry) = transfer {
        val previous = _pending.value
        // Прежний pending вытесняется только после того, как новая строка
        // действительно удалена: иначе отказ ниже (строки уже не было) забирал бы
        // с собой возможность откатить предыдущее удаление.
        repository.delete(entry.id)?.let {
            if (previous != null && previous.id != entry.id) {
                repository.purgePhotos(previous.photos)
                clearPending(previous)
            }
            _pending.value = it
            // Строки в БД больше нет, а фотографии должны пережить откат — держим их
            // от фоновой уборки (см. PhotoLease).
            photoLease.acquireAll(it.photos)
        }
    }

    /** Undo: восстановить последнюю удалённую запись, снять pending. */
    suspend fun undo() = transfer {
        val entry = _pending.value ?: return@transfer
        repository.restore(entry)
        clearPending(entry)
    }

    /** Истечение undo: удалить осиротевшие файлы фотографий, снять pending. */
    suspend fun expire() = transfer {
        val entry = _pending.value ?: return@transfer
        repository.purgePhotos(entry.photos)
        clearPending(entry)
    }

    private fun clearPending(entry: Entry) {
        _pending.value = null
        photoLease.releaseAll(entry.photos)
    }
}
