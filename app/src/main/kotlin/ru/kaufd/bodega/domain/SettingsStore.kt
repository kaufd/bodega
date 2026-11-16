package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.Flow

data class Settings(
    val lastCategory: Category? = null,
    val sort: SortOrder = SortOrder.DATE,
)

/**
 * Запоминает выбор коллекции — категорию и сортировку, их восстанавливает главный
 * экран. Раздел рецептов не запоминается: пара в настройках одна, и заход к рецептам
 * стёр бы вино с сортировкой по баллам, а приложение открывалось бы на рецептах.
 */
suspend fun SettingsStore.rememberCollection(filter: EntryFilter) {
    if (filter.category?.isRecipe == true) return
    save(Settings(lastCategory = filter.category, sort = filter.sort))
}

/**
 * Модель по умолчанию: читает кириллические этикетки, принимает изображения,
 * и скан обходится примерно в четыре сотых цента. Меняется в настройках.
 */
const val DEFAULT_MODEL = "z-ai/glm-5.3-flash"

/**
 * Распознавание этикеток облачной моделью. Пустой [apiKey] или [model] — всё равно
 * что выключено: распознаёт on-device.
 */
data class RecognitionConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    val usable: Boolean get() = enabled && apiKey.isNotBlank() && model.isNotBlank()
}

interface SettingsStore {
    val settings: Flow<Settings>
    suspend fun save(settings: Settings)

    /**
     * Вид списка коллекции: компактный (по умолчанию) или плитка. Отдельно от
     * [Settings] намеренно — [save] переписывает запись целиком, и сохранение
     * категории или сортировки затирало бы выбранный вид.
     */
    val compactList: Flow<Boolean>
    suspend fun saveCompactList(compact: Boolean)

    /** Отдельно от [Settings] по той же причине, что и [compactList]. */
    val recognition: Flow<RecognitionConfig>
    suspend fun saveRecognition(config: RecognitionConfig)
}
