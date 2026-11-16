package ru.kaufd.bodega.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

/**
 * Область корутин уровня приложения (см. `AppScope`) для работ, которые обязаны
 * пережить уход с экрана: уборка временного снимка камеры после импорта или OCR.
 *
 * `rememberCoroutineScope()` для этого не годится — он отменяется вместе с
 * композицией, и файл остался бы в кэше, если пользователь ушёл во время импорта.
 */
val LocalAppScope = staticCompositionLocalOf<CoroutineScope> {
    error("LocalAppScope не предоставлен")
}
