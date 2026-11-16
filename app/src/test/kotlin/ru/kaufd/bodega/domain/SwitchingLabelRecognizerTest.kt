package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class StubRecognizer(
    private val result: LabelScan = LabelScan(),
    private val failure: Throwable? = null,
) : LabelRecognizer {
    var calls = 0
    override suspend fun recognize(source: PhotoSource): LabelScan {
        calls++
        failure?.let { throw it }
        return result
    }
}

private class StubSettings(config: RecognitionConfig) : SettingsStore {
    override val recognition = MutableStateFlow(config)
    override suspend fun saveRecognition(config: RecognitionConfig) { recognition.value = config }

    override val settings: Flow<Settings> = MutableStateFlow(Settings())
    override suspend fun save(settings: Settings) = Unit
    override val compactList: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun saveCompactList(compact: Boolean) = Unit
}

class SwitchingLabelRecognizerTest {

    private val source = PhotoSource("content://photo")
    private val onDevice = StubRecognizer(LabelScan(rawText = "on-device"))

    private val notices = RecognitionNotices()

    private fun switching(
        config: RecognitionConfig,
        cloud: LabelRecognizer,
    ) = SwitchingLabelRecognizer(onDevice, cloud, StubSettings(config), notices)

    private val usable =
        RecognitionConfig(enabled = true, apiKey = "sk-or-v1", model = "vendor/model")

    @Test
    fun `распознавание выключено - работает on-device`() = runTest {
        val cloud = StubRecognizer(LabelScan(name = "Бароло"))

        val scan = switching(RecognitionConfig(enabled = false), cloud).recognize(source)

        assertEquals("on-device", scan.rawText)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun `включено с ключом и моделью - работает облако`() = runTest {
        val cloud = StubRecognizer(LabelScan(name = "Бароло"))
        val config = RecognitionConfig(enabled = true, apiKey = "sk-or-v1", model = "vendor/model")

        val scan = switching(config, cloud).recognize(source)

        assertEquals("Бароло", scan.name)
        assertEquals(1, cloud.calls)
    }

    @Test
    fun `включено, но ключ пуст - работает on-device`() = runTest {
        val cloud = StubRecognizer(LabelScan(name = "Бароло"))
        val config = RecognitionConfig(enabled = true, apiKey = "", model = "vendor/model")

        val scan = switching(config, cloud).recognize(source)

        assertEquals("on-device", scan.rawText)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun `пустой ответ облака - результат берётся с устройства`() = runTest {
        // content = null у reasoning-модели и неразобранный JSON выглядят так же:
        // без фолбэка латинская этикетка перестала бы читаться вовсе.
        val cloud = StubRecognizer(LabelScan(name = ""))

        val scan = switching(usable, cloud).recognize(source)

        assertEquals("on-device", scan.rawText)
        assertEquals(1, cloud.calls)
    }

    @Test
    fun `мёртвый ключ - о нём сообщают, хотя офлайн выручил`() = runTest {
        val cloud = StubRecognizer(failure = RecognitionException(RecognitionError.AUTH))

        val reported = mutableListOf<RecognitionError>()
        val watching = launch { notices.notices.toList(reported) }
        runCurrent()
        val scan = switching(usable, cloud).recognize(source)
        runCurrent()
        watching.cancel()

        // Результат есть — но ключ не починится сам, а на кириллице офлайн уже не спасёт.
        assertEquals("on-device", scan.rawText)
        assertEquals(listOf(RecognitionError.AUTH), reported)
    }

    @Test
    fun `облако упало и на устройстве пусто - причина доходит до вызывающего`() = runTest {
        val blind = SwitchingLabelRecognizer(
            onDevice = StubRecognizer(LabelScan()),
            cloud = StubRecognizer(failure = RecognitionException(RecognitionError.CREDITS)),
            settings = StubSettings(usable),
            notices = notices,
        )

        // Показать нечего, и «не прочиталось» здесь ввело бы в заблуждение:
        // дело не в этикетке, а в кончившихся кредитах.
        val thrown = runCatching { blind.recognize(source) }.exceptionOrNull()

        assertEquals(RecognitionError.CREDITS, (thrown as RecognitionException).error)
    }

    @Test
    fun `облако упало - результат берётся с устройства, а не исключение`() = runTest {
        val cloud = StubRecognizer(failure = java.io.IOException("нет сети"))
        val config = RecognitionConfig(enabled = true, apiKey = "sk-or-v1", model = "vendor/model")

        val scan = switching(config, cloud).recognize(source)

        assertEquals("on-device", scan.rawText)
        assertEquals(1, cloud.calls)
    }
}
