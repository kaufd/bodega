package ru.kaufd.bodega.ui

import androidx.annotation.StringRes
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.RecognitionError

/**
 * Текст отказа облачного распознавания: у каждой причины своё действие пользователя.
 *
 * Одна таблица на все экраны: скан этикетки и черновик заметки ходят к одной модели
 * и отказывают одинаково — с двумя копиями новая причина молча теряла бы текст.
 */
@StringRes
fun recognitionMessage(error: RecognitionError): Int = when (error) {
    RecognitionError.DISABLED -> R.string.recognition_disabled
    RecognitionError.AUTH -> R.string.recognition_auth_failed
    RecognitionError.CREDITS -> R.string.recognition_no_credits
    RecognitionError.MODEL -> R.string.recognition_model_failed
    RecognitionError.BUSY -> R.string.recognition_busy
    RecognitionError.NETWORK -> R.string.recognition_offline
    RecognitionError.NO_DATA -> R.string.recognition_no_data
    RecognitionError.OTHER -> R.string.recognition_failed
}
