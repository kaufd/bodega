package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отказ облака, который иначе никто не увидел бы: on-device распознаватель выручил,
 * и экран показал результат.
 *
 * Мёртвый ключ и пустой счёт сами не починятся, а без сигнала обнаружились бы только
 * на первой кириллической этикетке — той, где офлайн уже не поможет.
 */
@Singleton
class RecognitionNotices @Inject constructor() {

    private val _notices = MutableSharedFlow<RecognitionError>(extraBufferCapacity = 1)
    val notices: SharedFlow<RecognitionError> = _notices.asSharedFlow()

    fun report(error: RecognitionError) {
        _notices.tryEmit(error)
    }
}
