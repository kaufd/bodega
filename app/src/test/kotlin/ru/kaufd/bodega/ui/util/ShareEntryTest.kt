package ru.kaufd.bodega.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.buildPostText
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareEntryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val resolvePhotoPath = { path: String -> File(context.filesDir, path).path }

    private val entry = Entry(category = Category.WINE, name = "Cape Art Pinotage", rating = 8)

    @Test
    fun `без фотографий уходит один текст`() {
        val intent = buildShareIntent(context, entry, resolvePhotoPath)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(buildPostText(entry), intent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java))
    }

    @Test
    fun `фотографии уходят вместе с текстом и правом на чтение`() {
        val withPhotos = entry.copy(photos = listOf("photos/a.jpg", "photos/b.jpg"))

        val intent = buildShareIntent(context, withPhotos, resolvePhotoPath)

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("image/*", intent.type)
        assertEquals(buildPostText(withPhotos), intent.getStringExtra(Intent.EXTRA_TEXT))
        val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals(2, uris.size)
        assertTrue(uris.all { it.toString().startsWith("content://ru.kaufd.bodega.fileprovider/photos/") })
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
