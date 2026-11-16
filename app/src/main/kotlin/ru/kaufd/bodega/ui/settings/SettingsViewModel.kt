package ru.kaufd.bodega.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.di.IoDispatcher
import ru.kaufd.bodega.domain.BackupFormatException
import ru.kaufd.bodega.domain.DocumentStorage
import ru.kaufd.bodega.domain.ExportBackupUseCase
import ru.kaufd.bodega.domain.ImportBackupUseCase
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.RecognitionConfig
import javax.inject.Inject

/** Чем закончилась последняя операция с архивом. Показывается снекбаром и сбрасывается. */
sealed interface BackupResult {
    data object Exported : BackupResult
    data class Imported(val added: Int, val skipped: Int) : BackupResult

    /** Файл открылся, но это не архив дневника (или он повреждён). */
    data object WrongFormat : BackupResult

    /** Отказ ввода-вывода: нет места, файл недоступен, поток оборвался. */
    data object Failed : BackupResult
}

/** Что именно сейчас выполняется — чтобы индикатор крутился у своего пункта. */
enum class BackupOperation { EXPORT, IMPORT }

data class SettingsState(
    /** Операция в полёте: кнопки заблокированы, чтобы не запустить вторую поверх первой. */
    val running: BackupOperation? = null,
    val result: BackupResult? = null,
    val recognition: RecognitionConfig = RecognitionConfig(),
) {
    val busy: Boolean get() = running != null
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase,
    private val documents: DocumentStorage,
    private val settings: SettingsStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /**
     * Пользователь уже правил настройки распознавания на этом экране. Дальше
     * источник правды — состояние: прочитанное с диска его не затирает.
     */
    private var recognitionTouched = false

    init {
        viewModelScope.launch {
            // Только начальное значение, без подписки: DataStore эмитит наши же
            // записи, и эмиссия предыдущего символа откатывала бы поле назад.
            // Отказ чтения — дефолты, экран из-за настроек падать не должен.
            val saved = runCatching { settings.recognition.first() }.getOrNull() ?: return@launch
            if (recognitionTouched) return@launch
            _state.update { it.copy(recognition = saved) }
        }
    }

    /**
     * Правки применяются сразу: отдельной кнопки «Сохранить» на экране нет, а
     * потерять введённый ключ при выходе — худшее из возможных поведений.
     *
     * Правка приходит функцией, а не готовым конфигом, и состояние обновляется
     * синхронно, до записи на диск. Иначе поле ввода ждало бы круга через DataStore:
     * два события клавиатуры в одном кадре собирали бы конфиг от одного и того же
     * устаревшего значения, и первый символ терялся бы — на замаскированном ключе
     * незаметно, до отказа «ключ не подошёл».
     */
    fun onRecognitionChange(update: (RecognitionConfig) -> RecognitionConfig) {
        recognitionTouched = true
        val config = update(_state.value.recognition)
        _state.update { it.copy(recognition = config) }
        viewModelScope.launch {
            // Отказ DataStore не должен ронять экран: состояние уже показано,
            // при следующем открытии просто вернётся сохранённое ранее.
            runCatching { settings.saveRecognition(config) }
        }
    }

    /** [locator] — документ, выбранный пользователем в системном диалоге. */
    fun export(locator: String) = vm(BackupOperation.EXPORT) {
        documents.write(locator).use { exportBackup(it) }
        BackupResult.Exported
    }

    fun import(locator: String) = vm(BackupOperation.IMPORT) {
        val result = documents.read(locator).use { importBackup(it) }
        BackupResult.Imported(added = result.added, skipped = result.skipped)
    }

    fun onResultShown() = _state.update { it.copy(result = null) }

    private fun vm(operation: BackupOperation, block: suspend () -> BackupResult) {
        if (_state.value.busy) return
        _state.update { it.copy(running = operation, result = null) }
        viewModelScope.launch {
            val result = try {
                withContext(io) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupFormatException) {
                BackupResult.WrongFormat
            } catch (_: Exception) {
                BackupResult.Failed
            }
            _state.update { it.copy(running = null, result = result) }
        }
    }
}
