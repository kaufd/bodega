package ru.kaufd.bodega.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import ru.kaufd.bodega.data.BodegaDatabase
import ru.kaufd.bodega.domain.AppScope
import ru.kaufd.bodega.data.DataStoreSettings
import ru.kaufd.bodega.data.EntryRepositoryImpl
import ru.kaufd.bodega.domain.DocumentStorage
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.LabelRecognizer
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.platform.AndroidDocumentStorage
import ru.kaufd.bodega.platform.AndroidLabelRecognizer
import ru.kaufd.bodega.platform.OpenRouterLabelRecognizer
import ru.kaufd.bodega.platform.OpenRouterDraftWriter
import ru.kaufd.bodega.domain.RecognitionNotices
import ru.kaufd.bodega.domain.SwitchingLabelRecognizer
import ru.kaufd.bodega.domain.DraftWriter
import ru.kaufd.bodega.platform.AndroidPhotoStorage
import java.io.File
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object ProvidesModule {

    val migration5To6 = object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE entries ADD COLUMN ingredients TEXT")
            connection.execSQL("ALTER TABLE entries ADD COLUMN preparation TEXT")
        }
    }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): BodegaDatabase =
        Room.databaseBuilder<BodegaDatabase>(
            context = context,
            name = context.getDatabasePath("bodega.db").absolutePath,
        )
            // Destructive fallback снят: журнал переживает обновления приложения.
            // Теперь смена схемы без миграции роняет приложение при запуске — это
            // и есть смысл: пусть падает у разработчика, а не стирает записи
            // у пользователя. Каждое изменение схемы = +1 к version в
            // BodegaDatabase и своя Migration; экспорт схем в app/schemas
            // включён, разница между версиями видна по json.
            //
            // Важно: с BundledSQLiteDriver миграция обязана переопределять
            // migrate(SQLiteConnection). Вариант с SupportSQLiteDatabase
            // компилируется, но в рантайме бросает NotImplementedError.
            .addMigrations(migration5To6)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides
    fun entryDao(db: BodegaDatabase) = db.entryDao()

    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // Повреждённый settings.preferences_pb иначе роняет приложение при
            // каждом чтении. Настройки — кэш выбора пользователя, восстанавливать
            // из них нечего: начинаем с чистых.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) {
            File(context.filesDir, "settings.preferences_pb")
        }

    @Provides @Singleton
    fun photoStorage(@ApplicationContext context: Context): AndroidPhotoStorage =
        AndroidPhotoStorage(context.filesDir, context.contentResolver)

    @Provides @Singleton
    fun labelRecognizer(@ApplicationContext context: Context): AndroidLabelRecognizer =
        AndroidLabelRecognizer(context)

    /**
     * Распознаватель для экранов — делегат: он сам решает, звать облако или ML Kit.
     * Вызывающие о выборе не знают.
     */
    @Provides @Singleton
    fun switchingLabelRecognizer(
        onDevice: AndroidLabelRecognizer,
        cloud: OpenRouterLabelRecognizer,
        settings: SettingsStore,
        notices: RecognitionNotices,
    ): LabelRecognizer = SwitchingLabelRecognizer(onDevice, cloud, settings, notices)

    @Provides @Singleton
    fun documentStorage(@ApplicationContext context: Context): AndroidDocumentStorage =
        AndroidDocumentStorage(context.contentResolver)

    /** Не отменяется вместе с экраном — нужна для фоновой уборки файлов (см. PhotoCleaner). */
    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Блокирующий ввод-вывод. Отдельно от Dispatchers.IO, чтобы тесты подставляли свой. */
    @Provides @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds abstract fun repository(impl: EntryRepositoryImpl): EntryRepository
    @Binds abstract fun settings(impl: DataStoreSettings): SettingsStore
    @Binds abstract fun photos(impl: AndroidPhotoStorage): PhotoStorage
    @Binds abstract fun documents(impl: AndroidDocumentStorage): DocumentStorage

    // LabelRecognizer намеренно не здесь: его отдаёт switchingLabelRecognizer,
    // которому нужны обе реализации сразу.
    @Binds abstract fun draftWriter(impl: OpenRouterDraftWriter): DraftWriter
}
