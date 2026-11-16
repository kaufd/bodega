package ru.kaufd.bodega.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kaufd.bodega.domain.DeleteEntryUseCase
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryRepository
import javax.inject.Inject

/**
 * Содержимое карточки. Отдельное состояние загрузки нужно, чтобы «ещё не прочитали»
 * не выглядело как «записи нет»: раньше и то и другое было `null`, и экран оставался
 * полностью пустым — вместе с кнопкой «назад».
 */
sealed interface DetailContent {
    data object Loading : DetailContent
    data object NotFound : DetailContent

    /** Поток Room оборвался ошибкой — «не прочитали» не то же самое, что «записи нет». */
    data object Failed : DetailContent
    data class Ready(val entry: Entry) : DetailContent
}

data class DetailState(
    val content: DetailContent = DetailContent.Loading,
    val deleteFailed: Boolean = false,
    /** Удаление в полёте: экран заморожен, пока владение записью не перешло. */
    val deleting: Boolean = false,
    /**
     * Запись удалена, экран пора закрывать. Состояние, а не колбэк: ViewModel
     * переживает пересоздание Activity, и переданная навигация осталась бы
     * привязанной к уничтоженному NavController — экран замер бы с deleting = true
     * и заблокированной кнопкой «назад».
     */
    val deleted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: EntryRepository,
    private val deleteUseCase: DeleteEntryUseCase,
) : ViewModel() {

    private val id: Long = checkNotNull(savedState["id"])

    private val deleteFailed = MutableStateFlow(false)
    private val deleting = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)

    /** Счётчик попыток перечитать запись после отказа Room. */
    private val retry = MutableStateFlow(0)

    /** Реактивное наблюдение записи — автоматически обновляется после возврата из редактора. */
    val state: StateFlow<DetailState> = combine(
        // Без catch отказ Room вылетал бы из корутины stateIn и ронял приложение,
        // а экран застывал бы на Loading. Повтор пересоздаёт подписку — Flow после
        // ошибки сам не оживает.
        retry.flatMapLatest {
            repository.observeById(id)
                .map { entry ->
                    if (entry == null) DetailContent.NotFound else DetailContent.Ready(entry)
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(DetailContent.Failed)
                }
        },
        deleteFailed,
        deleting,
        deleted,
    ) { content, failed, busy, done -> DetailState(content, failed, busy, done) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    /** Повторная подписка на запись после отказа Room. */
    fun onRetryLoad() { retry.value++ }

    /** Удаляет запись через единый use case (чистит фото прежней pending), регистрирует undo. */
    fun delete() {
        if (deleting.value) return
        val e = (state.value.content as? DetailContent.Ready)?.entry ?: return
        deleting.value = true
        viewModelScope.launch {
            try {
                // Сам переход владения неотменяем (см. DeleteEntryUseCase.transfer),
                // поэтому уход с экрана не может оставить его наполовину сделанным.
                deleteUseCase.delete(e)
            } catch (t: CancellationException) {
                throw t
            } catch (_: Exception) {
                // Отказ Room: остаёмся на карточке и сообщаем — повтор возможен.
                deleting.value = false
                deleteFailed.value = true
                return@launch
            }
            deleted.value = true
        }
    }
}
