package ru.kaufd.bodega.domain

/**
 * Распознаёт этикетку из изображения.
 * Реализации — в platform-слое (ML Kit on-device и облачная модель). Мокается на JVM.
 *
 * On-device распознаватель заполняет только [LabelScan.rawText]; облачный — и остальные
 * поля. Пустой [LabelScan] — распознать не удалось.
 */
interface LabelRecognizer {
    suspend fun recognize(source: PhotoSource): LabelScan
}
