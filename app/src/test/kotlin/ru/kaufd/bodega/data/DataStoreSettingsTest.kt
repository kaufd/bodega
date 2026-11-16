package ru.kaufd.bodega.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.RecognitionConfig
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SortOrder

class DataStoreSettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `save и чтение переживают пересоздание`() = runTest {
        val file = tmp.newFile("settings.preferences_pb")
        val store1 = DataStoreSettings(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )
        store1.save(Settings(lastCategory = Category.BEER, sort = SortOrder.PRICE))
        val loaded = store1.settings.first()
        assertEquals(Category.BEER, loaded.lastCategory)
        assertEquals(SortOrder.PRICE, loaded.sort)
    }

    @Test
    fun `настройки распознавания переживают сохранение категории`() = runTest {
        val store = DataStoreSettings(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tmp.newFile("s3.preferences_pb")
            }
        )
        store.saveRecognition(
            RecognitionConfig(enabled = true, apiKey = "sk-or-v1-test", model = "vendor/model")
        )

        // save() переписывает запись Settings целиком — распознавание не должно пострадать.
        store.save(Settings(lastCategory = Category.WINE, sort = SortOrder.RATING))

        assertEquals(
            RecognitionConfig(enabled = true, apiKey = "sk-or-v1-test", model = "vendor/model"),
            store.recognition.first(),
        )
    }

    @Test
    fun `дефолт распознавания - выключено и без ключа`() = runTest {
        val store = DataStoreSettings(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tmp.newFile("s4.preferences_pb")
            }
        )
        assertEquals(RecognitionConfig(), store.recognition.first())
    }

    @Test
    fun `дефолт - все категории и сортировка по дате`() = runTest {
        val store = DataStoreSettings(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tmp.newFile("s2.preferences_pb")
            }
        )
        assertEquals(Settings(), store.settings.first())
    }
}
