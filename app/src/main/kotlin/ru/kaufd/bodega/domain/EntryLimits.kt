package ru.kaufd.bodega.domain

/** Единый источник границ валидации записи. Используется и в data-валидации, и в UI. */
object EntryLimits {
    /** Допустимый рейтинг: 1..10. */
    val RATING: IntRange = 1..10

    /** Допустимая крепость, %: 0.0..96.0. */
    val ABV: ClosedFloatingPointRange<Double> = 0.0..96.0

    /** Максимум фотографий в записи. */
    const val PHOTOS_MAX: Int = 3
}
