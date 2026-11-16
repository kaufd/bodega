package ru.kaufd.bodega.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.RecognitionException
import ru.kaufd.bodega.domain.RecognitionError
import ru.kaufd.bodega.domain.DraftWriter
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.EntryViolation
import ru.kaufd.bodega.domain.PhotoCleaner
import ru.kaufd.bodega.domain.PhotoLease
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.violations
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Фаза экрана: пока запись не прочитана, форму показывать нельзя.
 * FAILED и NOT_FOUND различаются намеренно — «не смогли прочитать» лечится
 * повтором, «записи нет» не лечится ничем.
 */
enum class EditPhase { LOADING, READY, NOT_FOUND, FAILED }

data class EditState(
    val phase: EditPhase = EditPhase.READY,
    val entry: Entry = Entry(category = Category.WINE, name = ""),
    val isNew: Boolean = true,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** Идёт импорт фотографий: сохранять рано, иначе в запись уйдёт неполный список. */
    val photoImporting: Boolean = false,
    /** Последняя попытка сохранения упала (Room/диск) — экран предлагает повтор. */
    val saveFailed: Boolean = false,
    /**
     * Запись сохранена, экран пора закрывать. Состояние, а не колбэк: ViewModel
     * переживает пересоздание Activity, и переданная навигация осталась бы
     * привязанной к уничтоженному NavController — экран замер бы с saving = true.
     */
    val saved: Boolean = false,
    /**
     * Сырой текст поля крепости. Хранится отдельно от [Entry.abvPercent], потому
     * что «14,» и «.» — состояния ввода, а не числа: без них набранное превращалось
     * бы в «14.0» на лету, а нераспарсенное — молча уходило бы в БД как NULL.
     */
    val abvText: String = "",
    /** Идёт написание черновика заметки. */
    val drafting: Boolean = false,
) {
    val violations: Set<EntryViolation> get() = entry.violations()

    /** Набрано непустое, но не число: сохранять нельзя — иначе прежняя крепость сотрётся. */
    val abvUnparsed: Boolean get() = abvText.isNotBlank() && parseAbv(abvText) == null

    /**
     * Пока идёт сохранение, форма заморожена: правки после нажатия «Сохранить»
     * визуально принимались бы, но в БД уходил бы более ранний снимок.
     */
    val editable: Boolean get() = phase == EditPhase.READY && !saving

    /**
     * Пока модель пишет черновик, поле заметки заморожено: готовый ответ
     * перезаписывает его целиком, и набранное за эти секунды пропало бы молча.
     * Остальные поля правятся как обычно — их черновик не трогает.
     */
    val noteEditable: Boolean get() = editable && !drafting

    val canSave: Boolean
        get() = editable && violations.isEmpty() && !abvUnparsed && !photoImporting

    /** Сколько фотографий ещё влезет: экран выбирает по нему пикер, VM — обрезает пачку. */
    val freePhotoSlots: Int get() = EntryLimits.PHOTOS_MAX - entry.photos.size
}

/** Разбор поля крепости: запятая как разделитель — тоже валидный ввод. */
fun parseAbv(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

/**
 * Локальная полночь сегодняшнего дня — дата дегустации новой записи по умолчанию:
 * пишут о выпитом в тот же день, а выставлять её руками каждый раз незачем.
 * Хранится полночь, а не «сейчас»: в записи живёт день, не момент.
 */
private fun today(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Сколько прежних заметок показать модели как образец стиля. Три: медианная
 * заметка — около 450 символов, и этого хватает, чтобы стиль считался.
 */
private const val NOTE_EXAMPLES = 3

/** Пустая форма новой записи. */
private fun blankEntry(category: Category) = Entry(category = category, name = "", tastedAt = if (category.isRecipe) null else today())

/** Текст поля крепости для значения из записи; null — пустое поле. */
private fun abvTextOf(entry: Entry): String = entry.abvPercent?.toString() ?: ""

@HiltViewModel
class EditViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: EntryRepository,
    private val photoStorage: PhotoStorage,
    private val photoCleaner: PhotoCleaner,
    private val photoLease: PhotoLease,
    private val draftWriter: DraftWriter,
    settings: SettingsStore,
) : ViewModel() {

    /**
     * Черновик заметки доступен: облачная модель настроена. Без неё кнопка не
     * показывается — иначе на свежей установке она отвечала бы отказом.
     *
     * Отдельно от [state]: тот пересобирается целиком при загрузке записи, и
     * настройка затиралась бы пришедшим объектом.
     */
    val draftAvailable: StateFlow<Boolean> = settings.recognition
        .map { it.usable }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val editId: Long? = savedState.get<String?>("id")?.toLongOrNull()
    // стартовая категория новой записи приходит из вкладки списка; по умолчанию — вино
    private val initialCategory: Category =
        Category.parseOrNull(savedState.get<String?>("category")) ?: Category.WINE

    private val _state = MutableStateFlow(
        EditState(
            phase = if (editId == null) EditPhase.READY else EditPhase.LOADING,
            entry = blankEntry(initialCategory),
            isNew = editId == null,
        )
    )
    val state: StateFlow<EditState> = _state

    // Одноразовое событие «фото не удалось импортировать» — экран показывает Toast.
    private val _photoError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val photoError: SharedFlow<Unit> = _photoError.asSharedFlow()

    /** Чем закончился черновик заметки: у каждой причины своё действие пользователя. */
    private val _draftError = MutableSharedFlow<RecognitionError>(extraBufferCapacity = 1)
    val draftError: SharedFlow<RecognitionError> = _draftError.asSharedFlow()

    /**
     * Фотографии, принадлежащие сохранённой записи. Всё, чего здесь нет, — staged:
     * при отмене удаляется, при сохранении становится собственностью записи.
     */
    private var committedPhotos: Set<String> =
        savedState.get<Array<String>>(KEY_COMMITTED_PHOTOS)?.toSet() ?: emptySet()

    private var photoJob: Job? = null

    /**
     * Файлы, которые держит форма. Пока редактор жив, фоновая уборка их не тронет:
     * импортированные, но не сохранённые снимки ни одной записью не используются
     * (см. [PhotoLease]).
     */
    private var leasedPhotos: Set<String> = emptySet()

    init {
        val draft = savedState.get<String>(KEY_DRAFT)?.let(::decodeDraft)
        when {
            // Черновик пережил смерть процесса — он новее строки в БД.
            draft != null -> _state.value = EditState(
                phase = EditPhase.READY,
                entry = draft,
                isNew = editId == null,
                dirty = savedState[KEY_DIRTY] ?: true,
                // Нераспарсенный ввод в черновик записи не попадает — восстанавливаем его отдельно.
                abvText = savedState[KEY_ABV_TEXT] ?: abvTextOf(draft),
            ).also {
                // Черновик пережил смерть процесса: его фото не числится ни за одной
                // записью, и без аренды уборка сочла бы файл осиротевшим.
                leasePhotos(draft.photos)
            }

            editId != null -> viewModelScope.launch { load(editId) }
        }
    }

    private suspend fun load(id: Long) {
        val loaded = try {
            repository.getById(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Отказ БД — не «записи нет»: предлагаем повтор, а не сообщаем
            // пользователю, что его запись исчезла.
            _state.value = _state.value.copy(phase = EditPhase.FAILED)
            return
        }
        committedPhotos = loaded?.photos?.toSet() ?: emptySet()
        savedState[KEY_COMMITTED_PHOTOS] = committedPhotos.toTypedArray()
        _state.value = if (loaded == null) {
            _state.value.copy(phase = EditPhase.NOT_FOUND)
        } else {
            EditState(
                phase = EditPhase.READY,
                entry = loaded,
                isNew = false,
                abvText = abvTextOf(loaded),
            )
        }
    }

    /** Повторное чтение записи после отказа БД. */
    fun onRetryLoad() {
        if (editId == null || _state.value.phase != EditPhase.FAILED) return
        _state.value = _state.value.copy(phase = EditPhase.LOADING)
        viewModelScope.launch { load(editId) }
    }

    fun onField(update: (Entry) -> Entry) {
        if (!_state.value.editable) return
        updateEntry(update(_state.value.entry))
    }

    /**
     * Ввод крепости: сырой текст сохраняется как есть, число — только если текст
     * разбирается. Нераспарсенное значение блокирует «Сохранить» (см. [EditState.abvUnparsed]),
     * а не превращается в тихий NULL.
     */
    fun onAbvInput(text: String) {
        if (!_state.value.editable) return
        _state.value = _state.value.copy(abvText = text)
        savedState[KEY_ABV_TEXT] = text
        updateEntry(_state.value.entry.copy(abvPercent = parseAbv(text)))
    }

    /**
     * Черновик заметки о вкусе. Перезаписывает поле целиком, поэтому экран
     * спрашивает подтверждение, когда там уже есть текст.
     */
    fun onDraftNote() {
        if (!_state.value.editable || _state.value.drafting) return
        if (_state.value.entry.name.isBlank()) return
        _state.value = _state.value.copy(drafting = true)
        viewModelScope.launch {
            try {
                // Образцы — заметки той же категории: у пива и вина автор пишет
                // о разном, и мешать их в примерах смысла нет. Отказ чтения не
                // отменяет черновик: образцы — необязательный вход, а без них
                // модель просто напишет обобщённее.
                val examples = runCatching {
                    repository.recentNotes(_state.value.entry.category, NOTE_EXAMPLES)
                }.getOrDefault(emptyList())
                val draft = draftWriter.draftNote(_state.value.entry, examples)
                updateEntry(_state.value.entry.copy(note = draft))
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecognitionException) {
                _draftError.tryEmit(e.error)
            } catch (_: Exception) {
                _draftError.tryEmit(RecognitionError.OTHER)
            } finally {
                _state.value = _state.value.copy(drafting = false)
            }
        }
    }

    /** Смена категории: общие поля остаются, категорийные очищаются (Entry.withCategory). */
    fun onCategoryChange(c: Category) {
        if (c == _state.value.entry.category) return
        onField { it.withCategory(c) }
    }

    /**
     * [onFinished] вызывается, когда файл источника прочитан и больше не нужен, —
     * по нему вызывающая сторона удаляет временный снимок камеры.
     */
    fun onPhotosPicked(sources: List<PhotoSource>, onFinished: () -> Unit = {}) {
        // Пока идёт импорт, форма новых источников не принимает — экран в этот момент
        // и не даёт их выбрать (см. photoImporting). Без этого пришлось бы решать,
        // какая из пачек «побеждает», и удалять файлы проигравшей.
        if (!_state.value.editable || _state.value.photoImporting) {
            onFinished()
            return
        }
        val accepted = sources.take(_state.value.freePhotoSlots)
        if (accepted.isEmpty()) {
            onFinished()
            return
        }
        _state.value = _state.value.copy(photoImporting = true)
        photoJob = viewModelScope.launch {
            var failed = false
            try {
                for (source in accepted) {
                    val imported = try {
                        photoStorage.importImage(source)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Нечитаемое/неподдерживаемое изображение, отозванный доступ, OOM
                        // и т.п. — не роняем приложение и не бросаем остальную пачку.
                        failed = true
                        continue
                    }
                    if (!isActive) {
                        // Редактор закрылся — файл уже никому не нужен, и в запись
                        // он не попал, значит аренда его не защищает.
                        withContext(NonCancellable) { runCatching { photoStorage.delete(imported) } }
                        return@launch
                    }
                    // Добавляем сразу после успеха: updateEntry берёт аренду, и файл
                    // перестаёт быть беззащитным до конца обработки пачки.
                    updateEntry(_state.value.entry.copy(photos = _state.value.entry.photos + imported))
                }
                // Одно событие на пачку: экран показывает один тост, а не три подряд.
                if (failed) _photoError.tryEmit(Unit)
                _state.value = _state.value.copy(photoImporting = false)
            } finally {
                // Источники дочитаны в любом исходе — временный снимок камеры
                // больше не нужен.
                withContext(NonCancellable) { onFinished() }
            }
        }
    }

    /**
     * Убирает фотографию из формы. Файл удаляется, только если он staged: снимок
     * сохранённой записи должен пережить отмену редактирования.
     */
    fun onPhotoRemove(path: String) {
        if (!_state.value.editable) return
        val entry = _state.value.entry
        if (path !in entry.photos) return
        updateEntry(entry.copy(photos = entry.photos - path))
        if (path !in committedPhotos) photoCleaner.purge(path)
    }

    /** Делает фотографию обложкой — переносит её в начало списка. Файлы не трогает. */
    fun onPhotoMakeCover(path: String) {
        if (!_state.value.editable) return
        val entry = _state.value.entry
        if (path !in entry.photos) return
        updateEntry(entry.copy(photos = listOf(path) + (entry.photos - path)))
    }

    fun save() {
        if (!_state.value.canSave) return
        _state.value = _state.value.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            val e = _state.value.entry
            val stamped = if (e.createdAt == 0L) e.copy(createdAt = System.currentTimeMillis()) else e
            val replaced = (committedPhotos - stamped.photos.toSet()).toList()
            try {
                repository.upsert(stamped)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Отказ Room/диска: снимаем saving, иначе кнопка «Сохранить»
                // остаётся мёртвой навсегда.
                _state.value = _state.value.copy(saving = false, saveFailed = true)
                return@launch
            }
            // Запись сохранена — заменённое фото больше никому не принадлежит.
            // Фиксация не должна пропасть от отмены viewModelScope: иначе
            // committedPhotos осталось бы старым и discard() удалил бы файлы,
            // пути к которым уже лежат в БД.
            withContext(NonCancellable) {
                committedPhotos = stamped.photos.toSet()
                clearDraft()
                photoCleaner.purgeAll(replaced)
                _state.value = _state.value.copy(saved = true)
            }
        }
    }

    /**
     * Уход из редактора без сохранения. Импортированные, но не сохранённые снимки
     * удаляются — иначе файлы остаются на диске навсегда.
     *
     * Во время сохранения — no-op: фотографии уже принадлежат записи (или станут
     * принадлежать через мгновение), удалять их нельзя. Экран в этот момент
     * закрыться и не даёт, см. EditScreenContent.
     */
    fun discard() {
        if (_state.value.saving) return
        photoJob?.cancel()
        photoCleaner.purgeAll(_state.value.entry.photos - committedPhotos)
        clearDraft()
    }

    /**
     * Переносит аренду на [paths]; прежняя освобождается.
     *
     * Сначала acquire новых, потом release ушедших: при обратном порядке счётчик
     * пути, оставшегося в списке, успевал бы просесть до нуля — и уборка забрала бы
     * живой файл.
     */
    private fun leasePhotos(paths: List<String>) {
        val next = paths.toSet()
        if (next == leasedPhotos) return
        photoLease.acquireAll(next - leasedPhotos)
        photoLease.releaseAll(leasedPhotos - next)
        leasedPhotos = next
    }

    override fun onCleared() {
        photoLease.releaseAll(leasedPhotos)
        leasedPhotos = emptySet()
    }

    private fun updateEntry(entry: Entry) {
        leasePhotos(entry.photos)
        _state.value = _state.value.copy(entry = entry, dirty = true)
        // Черновик переживает смерть процесса: без этого набранный текст и
        // импортированное фото теряются при возврате в приложение.
        savedState[KEY_DRAFT] = json.encodeToString(entry)
        savedState[KEY_DIRTY] = true
        // committedPhotos здесь не пишем: набор меняется только в load() и save(),
        // а updateEntry зовётся на каждый символ в поле названия и заметки.
    }

    private fun clearDraft() {
        savedState.remove<String>(KEY_DRAFT)
        savedState.remove<Boolean>(KEY_DIRTY)
        savedState.remove<Array<String>>(KEY_COMMITTED_PHOTOS)
        savedState.remove<String>(KEY_ABV_TEXT)
    }

    private fun decodeDraft(raw: String): Entry? =
        runCatching { json.decodeFromString<Entry>(raw) }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val KEY_DRAFT = "edit_draft"
        const val KEY_DIRTY = "edit_draft_dirty"
        const val KEY_COMMITTED_PHOTOS = "edit_committed_photos"
        const val KEY_ABV_TEXT = "edit_abv_text"
    }
}
