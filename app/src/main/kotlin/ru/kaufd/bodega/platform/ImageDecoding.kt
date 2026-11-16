package ru.kaufd.bodega.platform

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/** Предел стороны изображения: и для хранения, и для отправки в модель. */
internal const val MAX_SIDE = 1600

/** Качество JPEG — вторая половина параметров кодирования, рядом с [MAX_SIDE]. */
internal const val JPEG_QUALITY = 85

private fun ContentResolver.openOrFail(uri: Uri) =
    requireNotNull(openInputStream(uri)) { "Не удалось открыть изображение" }

/**
 * Декодирует изображение, разворачивает по EXIF и уменьшает до [MAX_SIDE] —
 * одной матрицей за один проход.
 *
 * Камера/галерея хранят ориентацию в EXIF; перекодированный JPEG её теряет,
 * поэтому поворот применяется к пикселям, иначе фото уедет боком. Масштаб
 * складывается в ту же матрицу: раздельные повороты и scale держали в памяти
 * две полноразмерные копии сразу — на слабом устройстве это и был OOM.
 */
internal fun ContentResolver.decodeNormalized(uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openOrFail(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось прочитать изображение" }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
    }
    val decoded = openOrFail(uri).use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Не удалось прочитать изображение")

    val orientation = openOrFail(uri).use { stream ->
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> Unit
    }
    // Поворот не меняет max(ширина, высота), поэтому коэффициент считается до него.
    val maxSide = max(decoded.width, decoded.height)
    if (maxSide > MAX_SIDE) {
        val ratio = MAX_SIDE.toFloat() / maxSide
        matrix.postScale(ratio, ratio)
    }
    if (matrix.isIdentity) return decoded
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        .also { if (it != decoded) decoded.recycle() }
}

private fun calculateInSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= MAX_SIDE) sample *= 2
    return sample
}
