package ru.kaufd.bodega.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.DEFAULT_MODEL
import ru.kaufd.bodega.domain.RecognitionConfig
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.SortOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {

    private val keyCategory = stringPreferencesKey("last_category")
    private val keySort = stringPreferencesKey("sort")
    private val keyCompactList = booleanPreferencesKey("compact_list")
    private val keyRecognitionEnabled = booleanPreferencesKey("recognition_enabled")
    private val keyRecognitionApiKey = stringPreferencesKey("recognition_api_key")
    private val keyRecognitionModel = stringPreferencesKey("recognition_model")

    /**
     * Нечитаемый файл настроек — не повод падать: отдаём дефолты. Повреждение
     * файла разбирает ReplaceFileCorruptionHandler при создании DataStore,
     * здесь остаются транзиентные отказы ввода-вывода.
     */
    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val settings: Flow<Settings> = prefs
        .map { prefs ->
            Settings(
                lastCategory = Category.parseOrNull(prefs[keyCategory]),
                sort = prefs[keySort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                    ?: SortOrder.DATE,
            )
        }

    override suspend fun save(settings: Settings) {
        dataStore.edit { prefs ->
            if (settings.lastCategory != null) prefs[keyCategory] = settings.lastCategory.name
            else prefs.remove(keyCategory)
            prefs[keySort] = settings.sort.name
        }
    }

    override val compactList: Flow<Boolean> = prefs.map { it[keyCompactList] ?: true }

    override suspend fun saveCompactList(compact: Boolean) {
        dataStore.edit { prefs -> prefs[keyCompactList] = compact }
    }

    override val recognition: Flow<RecognitionConfig> = prefs.map { prefs ->
        RecognitionConfig(
            enabled = prefs[keyRecognitionEnabled] ?: false,
            apiKey = prefs[keyRecognitionApiKey].orEmpty(),
            // ?: а не orEmpty(): на свежей установке подставляется модель по
            // умолчанию, но осознанно очищенное поле остаётся пустым.
            model = prefs[keyRecognitionModel] ?: DEFAULT_MODEL,
        )
    }

    override suspend fun saveRecognition(config: RecognitionConfig) {
        dataStore.edit { prefs ->
            prefs[keyRecognitionEnabled] = config.enabled
            // trim: ключ и имя модели обычно вставляют из буфера, а хвостовой пробел
            // или перевод строки дают 401 и 400 — отказы, по которым не догадаться,
            // что дело в невидимом символе.
            prefs[keyRecognitionApiKey] = config.apiKey.trim()
            prefs[keyRecognitionModel] = config.model.trim()
        }
    }
}
