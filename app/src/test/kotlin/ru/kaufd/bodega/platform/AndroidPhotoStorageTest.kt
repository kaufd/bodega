package ru.kaufd.bodega.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.kaufd.bodega.domain.PhotoSource
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPhotoStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `absolutePath собирается из filesDir`() {
        val storage = AndroidPhotoStorage(tmp.root, context.contentResolver)
        assertEquals(
            File(tmp.root, "photos/a.jpg").absolutePath,
            storage.absolutePath("photos/a.jpg"),
        )
    }

    @Test
    fun `delete удаляет файл и не падает на отсутствующем`() = runTest {
        val dir = File(tmp.root, "photos").apply { mkdirs() }
        val f = File(dir, "x.jpg").apply { writeText("data") }
        val storage = AndroidPhotoStorage(tmp.root, context.contentResolver)
        storage.delete("photos/x.jpg")
        assertFalse(f.exists())
        storage.delete("photos/nope.jpg") // не бросает
    }

    @Test
    fun `importImage читает locator потоково и ограничивает сторону 1600`() = runTest {
        val sourceFile = File(tmp.root, "source.png")
        Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            sourceFile.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val storage = AndroidPhotoStorage(tmp.root, context.contentResolver)

        val relative = storage.importImage(PhotoSource(Uri.fromFile(sourceFile).toString()))
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(tmp.root, relative).absolutePath, options)

        assertTrue(File(tmp.root, relative).isFile)
        assertTrue(maxOf(options.outWidth, options.outHeight) <= 1600)
    }

    @Test
    fun `importImage применяет EXIF-ориентацию и разворачивает фото`() = runTest {
        // Landscape-исходник, но EXIF помечает поворот на 90° — итог должен стать портретом.
        val sourceFile = File(tmp.root, "rotated.jpg")
        Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            sourceFile.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
        }
        androidx.exifinterface.media.ExifInterface(sourceFile.absolutePath).apply {
            setAttribute(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            saveAttributes()
        }
        val storage = AndroidPhotoStorage(tmp.root, context.contentResolver)

        val relative = storage.importImage(PhotoSource(Uri.fromFile(sourceFile).toString()))
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(tmp.root, relative).absolutePath, options)

        assertTrue("ожидался портрет после поворота", options.outHeight > options.outWidth)
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T =
        try {
            block(this)
        } finally {
            recycle()
        }
}
