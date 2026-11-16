package ru.kaufd.bodega.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Выбирает распознаватель по настройкам: облачная модель, если она настроена,
 * иначе on-device. Вызывающие о выборе не знают.
 */
class SwitchingLabelRecognizer(
    private val onDevice: LabelRecognizer,
    private val cloud: LabelRecognizer,
    private val settings: SettingsStore,
    private val notices: RecognitionNotices,
) : LabelRecognizer {

    override suspend fun recognize(source: PhotoSource): LabelScan {
        if (!settings.recognition.first().usable) return onDevice.recognize(source)
        val fromCloud = try {
            cloud.recognize(source)
        } catch (e: CancellationException) {
            throw e
        } catch (cloudFailure: Exception) {
            // Сеть, ключ или квота подвели — распознать офлайн лучше, чем ничего.
            // Но если и офлайн пусто, молчать нельзя: «не прочиталось» списало бы
            // отказ на этикетку, а причина другая и действия у неё разные.
            val offline = onDevice.recognize(source).takeIf { !it.isEmpty } ?: throw cloudFailure
            // Результат есть, но о причине отказа облака всё равно сообщаем — см. [RecognitionNotices].
            (cloudFailure as? RecognitionException)?.error
                ?.takeIf { it in CONFIG_FAILURES }
                ?.let(notices::report)
            return offline
        }
        // Пустой ответ облака успехом не считаем: `content = null` у reasoning-модели
        // и неразобранный JSON выглядят точно так же, а латиницу ML Kit ещё прочтёт.
        return fromCloud.takeIf { !it.isEmpty } ?: onDevice.recognize(source)
    }

    private companion object {
        /** Отказы, которые лечатся только правкой настроек, — о них стоит сказать. */
        val CONFIG_FAILURES = setOf(RecognitionError.AUTH, RecognitionError.CREDITS)
    }
}
