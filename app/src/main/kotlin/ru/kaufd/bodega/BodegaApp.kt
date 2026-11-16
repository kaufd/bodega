package ru.kaufd.bodega

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kaufd.bodega.domain.AppScope
import ru.kaufd.bodega.ui.util.purgeStaleCaptures
import javax.inject.Inject

@HiltAndroidApp
class BodegaApp : Application() {

    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Временные снимки камеры ничьи по определению — их можно чистить сразу.
        // Уборка фотографий записей запускается позже, из MainActivity: ей нужно
        // дождаться, пока восстановленные экраны возьмут аренду на свои файлы.
        appScope.launch { purgeStaleCaptures(this@BodegaApp) }
    }
}
