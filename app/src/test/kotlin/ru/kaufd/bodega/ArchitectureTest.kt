package ru.kaufd.bodega

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureTest {

    @Test
    fun `data и domain не импортируют android_*`() {
        Konsist.scopeFromProduction()
            .files
            .filter {
                it.packagee?.name?.startsWith("ru.kaufd.bodega.domain") == true ||
                    it.packagee?.name?.startsWith("ru.kaufd.bodega.data") == true
            }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("android.") }
            }
    }

    @Test
    fun `ui не открывает и не буферизует photo streams`() {
        Konsist.scopeFromProduction()
            .files
            .filter { it.packagee?.name?.startsWith("ru.kaufd.bodega.ui") == true }
            .assertFalse { file ->
                file.text.contains("contentResolver.openInputStream") ||
                    file.text.contains(".readBytes()")
            }
    }

    @Test
    fun `LocalPhotoStorage читает ровно одна функция`() {
        // Экраны берут путь через rememberPhotoPathResolver и о хранилище больше не
        // знают. Правило про одну функцию, а не список экранов: список приходилось
        // обновлять руками и он не отличал «взял путь» от «позвал importImage».
        Konsist.scopeFromProduction()
            .functions()
            .filter { it.packagee?.name?.startsWith("ru.kaufd.bodega.ui") == true }
            .filter { it.text.contains("LocalPhotoStorage") }
            .assertTrue { it.name == "rememberPhotoPathResolver" }
    }

    @Test
    fun `направление зависимостей слоёв`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("Domain", "ru.kaufd.bodega.domain..")
            val data = Layer("Data", "ru.kaufd.bodega.data..")
            val platform = Layer("Platform", "ru.kaufd.bodega.platform..")
            val di = Layer("Di", "ru.kaufd.bodega.di..")
            val ui = Layer("Ui", "ru.kaufd.bodega.ui..")

            domain.dependsOnNothing()
            data.dependsOn(domain)
            platform.dependsOn(domain)
            di.dependsOn(domain, data, platform)
            ui.dependsOn(domain)
        }
    }
}
