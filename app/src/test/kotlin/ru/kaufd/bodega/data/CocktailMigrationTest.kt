package ru.kaufd.bodega.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.kaufd.bodega.di.ProvidesModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CocktailMigrationTest {
    @Test fun `база версии 5 обновляется без потери записи`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cocktail-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("""CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, name TEXT NOT NULL, photos TEXT, type TEXT, priceRub INTEGER, rating INTEGER, abvPercent REAL, note TEXT, tastedAt INTEGER, createdAt INTEGER NOT NULL, grape TEXT, wineType TEXT, color TEXT, sweetness TEXT, country TEXT, vintageYear INTEGER, bottlingYear INTEGER, servingTemp TEXT, style TEXT, searchText TEXT NOT NULL)""")
            db.execSQL("INSERT INTO entries (category, name, createdAt, searchText) VALUES ('WINE', 'Бароло', 1, 'бароло')")
            db.version = 5
        }
        val db = Room.databaseBuilder<BodegaDatabase>(context, file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(ProvidesModule.migration5To6)
            .build()
        try {
            val entry = db.entryDao().getById(1)!!
            assertEquals("Бароло", entry.name)
            assertNull(entry.ingredients)
            assertNull(entry.preparation)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
