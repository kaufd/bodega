package ru.kaufd.bodega

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import dagger.hilt.android.AndroidEntryPoint
import ru.kaufd.bodega.domain.OrphanPhotoSweeper
import ru.kaufd.bodega.domain.PhotoStorage
import kotlinx.coroutines.CoroutineScope
import ru.kaufd.bodega.domain.AppScope
import ru.kaufd.bodega.ui.BodegaNavHost
import ru.kaufd.bodega.ui.LocalAppScope
import ru.kaufd.bodega.ui.LocalPhotoStorage
import ru.kaufd.bodega.ui.theme.BodegaTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var photoStorage: PhotoStorage

    @Inject lateinit var orphanPhotoSweeper: OrphanPhotoSweeper

    @Inject @AppScope lateinit var appScope: CoroutineScope

    // Приложение русскоязычное и переводов не имеет. На устройстве с другой
    // локалью Android брал бы её правила множественного числа и склонял русские
    // строки по-английски: «Показать 6 записи» вместо «записей».
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("ru"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Обложка главного экрана уходит под статус-бар; экраны сами отступают
        // через statusBarsPadding/navigationBarsPadding. На Android 15+ система
        // включает edge-to-edge сама, ниже — только этим вызовом.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(
                LocalPhotoStorage provides photoStorage,
                LocalAppScope provides appScope,
            ) {
                BodegaTheme {
                    BodegaNavHost()
                }
            }
            // После первой композиции: восстановленный редактор к этому моменту уже
            // создан и держит аренду на фото своего черновика, так что уборка его
            // не тронет. Один раз за процесс.
            LaunchedEffect(Unit) { orphanPhotoSweeper.sweep() }
        }
    }
}
