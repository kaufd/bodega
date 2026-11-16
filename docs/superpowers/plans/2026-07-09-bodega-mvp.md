# Погребокъ MVP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android-приложение «Погребокъ» — локальный дневник алкогольных напитков: CRUD, поиск, фильтры, пустые состояния, undo-удаление.

**Architecture:** Один модуль `:app`, MVVM + Repository. Пакеты `ru.kaufd.bodega.{domain,data,platform,ui,di}`. Границы охраняются Konsist-тестами. Room через SQLiteDriver API — DAO-тесты гоняются на JVM без эмулятора.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2.8.x (`BundledSQLiteDriver`), Hilt, Navigation Compose, Coil 3, DataStore Preferences, kotlinx-serialization, Konsist.

## Global Constraints

- Спека: `docs/superpowers/specs/2026-07-09-bodega-mvp-design.md` — при сомнениях сверяться с ней; визуал — `docs/СПЕЦ-для-агента.md`.
- Namespace / applicationId: `ru.kaufd.bodega`.
- Пакеты `data` и `domain` **не импортируют `android.*`** (androidx.room / androidx.datastore / kotlinx — можно).
- Room — только driver API (`setDriver(BundledSQLiteDriver())`), никакого SupportSQLite.
- Рейтинг — целое 1–10. Цена — Int, рубли. Даты — epoch millis (Long).
- Тексты UI — русские, «ъ»-стилистика из СПЕЦ («Погребокъ», «журналъ дегустацій»). Эмодзи не использовать.
- Версии в каталоге ниже — целевые; если конкретная версия недоступна в Maven, брать ближайшую новее stable и зафиксировать в коммите.
- Каждая задача заканчивается зелёными тестами `./gradlew testDebugUnitTest` и коммитом.

---

### Task 1: Каркас проекта

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/BodegaApp.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `.gitignore` (дополнить существующий)

**Interfaces:**
- Produces: собирающийся пустой Hilt-проект; версионный каталог `libs.*`, на который ссылаются все задачи.

- [ ] **Step 1: Gradle wrapper и настройки**

```bash
cd /Users/kaufd/_Projects/bodega
# gradle установлен? если нет: brew install gradle
gradle wrapper --gradle-version 8.14
```

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "bodega"
include(":app")
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g
android.useAndroidX=true
kotlin.code.style=official
```

Дополнить `.gitignore`:
```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
```

- [ ] **Step 2: Каталог версий** — `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
ksp = "2.2.20-2.0.2"
composeBom = "2026.06.00"
activityCompose = "1.11.0"
lifecycle = "2.9.4"
navigation = "2.9.1"
room = "2.8.3"
sqlite = "2.6.1"
hilt = "2.57"
hiltNavigation = "1.3.0"
coil = "3.3.0"
datastore = "1.1.7"
serialization = "1.9.0"
coroutines = "1.10.2"
junit = "4.13.2"
konsist = "0.17.3"
turbine = "1.2.0"
googleFonts = "1.9.0"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
compose-google-fonts = { module = "androidx.compose.ui:ui-text-google-fonts", version.ref = "googleFonts" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigation" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
konsist = { module = "com.lemonappdev:konsist", version.ref = "konsist" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

Корневой `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
```

- [ ] **Step 3: `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "ru.kaufd.bodega"
    compileSdk = 36
    defaultConfig {
        applicationId = "ru.kaufd.bodega"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDir("src/androidTest/kotlin")
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.google.fonts)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test.junit4)
}
```

- [ ] **Step 4: Manifest, Application, Activity, ресурсы**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".BodegaApp"
        android:label="@string/app_name"
        android:theme="@style/Theme.Bodega"
        android:supportsRtl="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`BodegaApp.kt`:
```kotlin
package ru.kaufd.bodega

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BodegaApp : Application()
```

`MainActivity.kt`:
```kotlin
package ru.kaufd.bodega

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Погребокъ") }
    }
}
```

`res/values/strings.xml`:
```xml
<resources><string name="app_name">Погребокъ</string></resources>
```

`res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.Bodega" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 5: Проверка сборки**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Если версия из каталога не резолвится — поднять до ближайшей stable, зафиксировать в сообщении коммита.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "chore: каркас проекта (Compose, Hilt, Room driver API)"
```

---

### Task 2: Domain — модели, фильтр, справочники, интерфейсы

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/Category.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/Entry.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/EntryFilter.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/Suggestions.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/EntryRepository.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/PhotoStorage.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/SettingsStore.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/domain/EntryFilterTest.kt`

**Interfaces:**
- Produces (используют все последующие задачи):
  - `enum Category { WINE, BEER, SPIRIT }`
  - `data class Entry(...)` — поля см. код ниже
  - `data class EntryFilter(...)` + `val activeFilterCount: Int` + `enum SortOrder { RATING, PRICE, DATE }`
  - `interface EntryRepository`, `interface PhotoStorage`, `interface SettingsStore` + `data class Settings`

- [ ] **Step 1: Написать падающий тест** — `EntryFilterTest.kt`:

```kotlin
package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryFilterTest {
    @Test
    fun `пустой фильтр не активен`() {
        assertEquals(0, EntryFilter().activeFilterCount)
    }

    @Test
    fun `категория и поиск не считаются фильтрами`() {
        val f = EntryFilter(category = Category.WINE, query = "неббиоло")
        assertEquals(0, f.activeFilterCount)
    }

    @Test
    fun `каждый заданный фильтр считается один раз`() {
        val f = EntryFilter(
            types = setOf("IPA", "Стаут"),      // 1 (группа типов)
            countries = setOf("Италия"),          // 1
            minRating = 7, maxRating = 10,        // 1 (диапазон рейтинга)
            minPrice = 500,                        // 1 (диапазон цены)
        )
        assertEquals(4, f.activeFilterCount)
    }

    @Test
    fun `сортировка по умолчанию DATE не считается фильтром`() {
        assertEquals(0, EntryFilter(sort = SortOrder.DATE).activeFilterCount)
        assertEquals(1, EntryFilter(sort = SortOrder.RATING).activeFilterCount)
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.domain.EntryFilterTest"`
Expected: FAIL (классы не существуют / не компилируется).

- [ ] **Step 3: Реализация**

`Category.kt`:
```kotlin
package ru.kaufd.bodega.domain

enum class Category { WINE, BEER, SPIRIT }
```

`Entry.kt`:
```kotlin
package ru.kaufd.bodega.domain

import kotlinx.serialization.Serializable

@Serializable
data class Entry(
    val id: Long = 0,
    val category: Category,
    val name: String,
    val photoPath: String? = null,   // относительный путь внутри files/
    val type: String? = null,        // «Красное, сухое» / «Тёмный эль» / «Виски (скотч)»
    val priceRub: Int? = null,
    val rating: Int? = null,         // 1..10
    val abvPercent: Double? = null,
    val volumeMl: Int? = null,
    val note: String? = null,
    val tastedAt: Long? = null,      // epoch millis
    val createdAt: Long = 0,
    // категорийные
    val grape: String? = null,       // WINE
    val country: String? = null,     // WINE
    val vintageYear: Int? = null,    // WINE
    val style: String? = null,       // BEER
    val ageStatement: String? = null // SPIRIT
)
```

`EntryFilter.kt`:
```kotlin
package ru.kaufd.bodega.domain

enum class SortOrder { RATING, PRICE, DATE }

data class EntryFilter(
    val category: Category? = null,
    val query: String = "",
    val types: Set<String> = emptySet(),
    val countries: Set<String> = emptySet(),
    val minRating: Int? = null,
    val maxRating: Int? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val sort: SortOrder = SortOrder.DATE,
) {
    /** Число активных фильтров для бейджа. Категория (таб) и поиск не считаются. */
    val activeFilterCount: Int
        get() = listOf(
            types.isNotEmpty(),
            countries.isNotEmpty(),
            minRating != null || maxRating != null,
            minPrice != null || maxPrice != null,
            sort != SortOrder.DATE,
        ).count { it }

    fun resetFilters(): EntryFilter = EntryFilter(category = category, query = query)
}
```

`Suggestions.kt`:
```kotlin
package ru.kaufd.bodega.domain

/** Предзаполненные подсказки; свободный ввод всегда разрешён. */
object Suggestions {
    val types: Map<Category, List<String>> = mapOf(
        Category.WINE to listOf(
            "Красное, сухое", "Красное, полусухое", "Белое, сухое",
            "Белое, полусладкое", "Розовое", "Игристое", "Креплёное",
        ),
        Category.BEER to listOf(
            "Лагер", "Пилзнер", "IPA", "Пшеничное", "Тёмный эль",
            "Портер", "Стаут", "Квадрюпель", "Сауэр",
        ),
        Category.SPIRIT to listOf(
            "Виски (скотч)", "Виски (бурбон)", "Ром", "Текила",
            "Кальвадос", "Коньяк", "Джин", "Водка",
        ),
    )

    val grapes: List<String> = listOf(
        "Неббиоло", "Санджовезе", "Каберне Совиньон", "Мерло", "Пино Нуар",
        "Сира", "Темпранильо", "Шардоне", "Совиньон Блан", "Рислинг", "Саперави",
    )

    val countries: List<String> = listOf(
        "Италия", "Франция", "Испания", "Португалия", "Германия",
        "Грузия", "Россия", "Чили", "Аргентина", "ЮАР", "Австралия", "США",
    )
}
```

`EntryRepository.kt`:
```kotlin
package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.Flow

interface EntryRepository {
    fun observeEntries(filter: EntryFilter): Flow<List<Entry>>
    fun observeCount(category: Category?): Flow<Int>
    suspend fun getById(id: Long): Entry?
    /** @return id записи (новой или обновлённой) */
    suspend fun upsert(entry: Entry): Long
    /** Удаляет строку; файл фото НЕ трогает (undo). */
    suspend fun delete(entry: Entry)
    /** Возврат после undo: вставка с исходным id. */
    suspend fun restore(entry: Entry)
    /** Вызвать после истечения undo: удалить осиротевший файл фото. */
    suspend fun purgePhoto(photoPath: String?)
    fun observeUsedTypes(category: Category?): Flow<List<String>>
    fun observeUsedCountries(): Flow<List<String>>
}
```

`PhotoStorage.kt`:
```kotlin
package ru.kaufd.bodega.domain

interface PhotoStorage {
    suspend fun delete(relativePath: String)
    /** Абсолютный путь для загрузчика изображений. */
    fun absolutePath(relativePath: String): String
}
```

`SettingsStore.kt`:
```kotlin
package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.Flow

data class Settings(
    val lastCategory: Category? = null,
    val sort: SortOrder = SortOrder.DATE,
)

interface SettingsStore {
    val settings: Flow<Settings>
    suspend fun save(settings: Settings)
}
```

- [ ] **Step 4: Тесты зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.domain.EntryFilterTest"`
Expected: PASS (4 теста).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(domain): модели, фильтр, справочники, интерфейсы"
```

---

### Task 3: Konsist — архитектурные правила

**Files:**
- Test: `app/src/test/kotlin/ru/kaufd/bodega/ArchitectureTest.kt`

**Interfaces:**
- Consumes: пакеты `domain` (Task 2); `data`, `ui`, `platform` появятся позже — правила формулируются на весь проект сразу.
- Produces: постоянно действующий страж границ; все последующие задачи обязаны проходить этот тест.

- [ ] **Step 1: Написать тест** (API Konsist сверить с актуальной документацией; ниже — целевая форма для 0.17.x):

```kotlin
package ru.kaufd.bodega

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertFalse
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
    fun `направление зависимостей ui - domain - data`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("Domain", "ru.kaufd.bodega.domain..")
            val data = Layer("Data", "ru.kaufd.bodega.data..")
            val ui = Layer("Ui", "ru.kaufd.bodega.ui..")
            domain.dependsOnNothing()
            data.dependsOn(domain)
            ui.dependsOn(domain)
        }
    }
}
```

- [ ] **Step 2: Запустить**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ArchitectureTest"`
Expected: PASS (пока в проекте только чистый domain). Если API Konsist отличается — поправить вызовы по документации, суть правил не менять.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "test: Konsist-правила границ (no android.* в data/domain, слои)"
```

---

### Task 4: Room — entity, DAO, база (тесты на JVM)

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/data/EntryEntity.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/data/EntryDao.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/data/BodegaDatabase.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/data/EntryDaoTest.kt`

**Interfaces:**
- Consumes: `Category`, `SortOrder` (Task 2).
- Produces:
  - `EntryEntity` (таблица `entries`) + `fun EntryEntity.toDomain(): Entry` + `fun Entry.toEntity(): EntryEntity`
  - `EntryDao.observeFiltered(...): Flow<List<EntryEntity>>` — сигнатура ниже
  - `EntryDao`: `getById(id): EntryEntity?`, `upsert(e): Long`, `deleteById(id)`, `insert(e)`, `observeCount(category: String?): Flow<Int>`, `observeUsedTypes(category: String?): Flow<List<String>>`, `observeUsedCountries(): Flow<List<String>>`
  - `BodegaDatabase` (abstract, `entryDao()`)

- [ ] **Step 1: Падающий тест** — `EntryDaoTest.kt`. Благодаря `BundledSQLiteDriver` тесты Room работают в обычном JVM-юните, без эмулятора:

```kotlin
package ru.kaufd.bodega.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EntryDaoTest {
    private lateinit var db: BodegaDatabase
    private lateinit var dao: EntryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder<BodegaDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.entryDao()
    }

    @After
    fun teardown() = db.close()

    private fun entity(
        name: String,
        category: String = "WINE",
        type: String? = null,
        country: String? = null,
        rating: Int? = null,
        priceRub: Int? = null,
        createdAt: Long = 0,
    ) = EntryEntity(
        id = 0, category = category, name = name, photoPath = null, type = type,
        priceRub = priceRub, rating = rating, abvPercent = null, volumeMl = null,
        note = null, tastedAt = null, createdAt = createdAt,
        grape = null, country = country, vintageYear = null, style = null, ageStatement = null,
    )

    @Test
    fun `фильтр по категории`() = runTest {
        dao.upsert(entity("Бароло", category = "WINE"))
        dao.upsert(entity("Гиннесс", category = "BEER"))
        val wines = dao.observeFiltered(
            category = "WINE", query = "", typesEmpty = 1, types = emptyList(),
            countriesEmpty = 1, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "DATE",
        ).first()
        assertEquals(listOf("Бароло"), wines.map { it.name })
    }

    @Test
    fun `поиск по имени типу и стране без заметки`() = runTest {
        dao.upsert(entity("Бароло", type = "Красное, сухое", country = "Италия"))
        dao.upsert(entity("Рислинг", type = "Белое, сухое", country = "Германия"))
        val byCountry = dao.observeFiltered(
            category = null, query = "итал", typesEmpty = 1, types = emptyList(),
            countriesEmpty = 1, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "DATE",
        ).first()
        assertEquals(listOf("Бароло"), byCountry.map { it.name })
    }

    @Test
    fun `фильтр по списку типов и диапазону рейтинга`() = runTest {
        dao.upsert(entity("A", type = "IPA", rating = 8))
        dao.upsert(entity("B", type = "Стаут", rating = 5))
        dao.upsert(entity("C", type = "IPA", rating = 4))
        val result = dao.observeFiltered(
            category = null, query = "", typesEmpty = 0, types = listOf("IPA"),
            countriesEmpty = 1, countries = emptyList(),
            minRating = 5, maxRating = 10, minPrice = null, maxPrice = null,
            sort = "DATE",
        ).first()
        assertEquals(listOf("A"), result.map { it.name })
    }

    @Test
    fun `сортировка по рейтингу убывание`() = runTest {
        dao.upsert(entity("низкий", rating = 3, createdAt = 3))
        dao.upsert(entity("высокий", rating = 9, createdAt = 1))
        val sorted = dao.observeFiltered(
            category = null, query = "", typesEmpty = 1, types = emptyList(),
            countriesEmpty = 1, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "RATING",
        ).first()
        assertEquals(listOf("высокий", "низкий"), sorted.map { it.name })
    }

    @Test
    fun `использованные типы по категории`() = runTest {
        dao.upsert(entity("A", category = "BEER", type = "IPA"))
        dao.upsert(entity("B", category = "BEER", type = "IPA"))
        dao.upsert(entity("C", category = "WINE", type = "Красное, сухое"))
        assertEquals(listOf("IPA"), dao.observeUsedTypes("BEER").first())
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.data.EntryDaoTest"`
Expected: FAIL (нет `EntryEntity`/`EntryDao`).

- [ ] **Step 3: Реализация**

`EntryEntity.kt`:
```kotlin
package ru.kaufd.bodega.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val category: String,          // Category.name
    val name: String,
    val photoPath: String?,
    val type: String?,
    val priceRub: Int?,
    val rating: Int?,
    val abvPercent: Double?,
    val volumeMl: Int?,
    val note: String?,
    val tastedAt: Long?,
    val createdAt: Long,
    val grape: String?,
    val country: String?,
    val vintageYear: Int?,
    val style: String?,
    val ageStatement: String?,
)

fun EntryEntity.toDomain() = Entry(
    id = id, category = Category.valueOf(category), name = name, photoPath = photoPath,
    type = type, priceRub = priceRub, rating = rating, abvPercent = abvPercent,
    volumeMl = volumeMl, note = note, tastedAt = tastedAt, createdAt = createdAt,
    grape = grape, country = country, vintageYear = vintageYear,
    style = style, ageStatement = ageStatement,
)

fun Entry.toEntity() = EntryEntity(
    id = id, category = category.name, name = name, photoPath = photoPath,
    type = type, priceRub = priceRub, rating = rating, abvPercent = abvPercent,
    volumeMl = volumeMl, note = note, tastedAt = tastedAt, createdAt = createdAt,
    grape = grape, country = country, vintageYear = vintageYear,
    style = style, ageStatement = ageStatement,
)
```

`EntryDao.kt`:
```kotlin
package ru.kaufd.bodega.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query(
        """
        SELECT * FROM entries WHERE
            (:category IS NULL OR category = :category)
        AND (:query = '' OR name LIKE '%' || :query || '%'
             OR type LIKE '%' || :query || '%'
             OR grape LIKE '%' || :query || '%'
             OR style LIKE '%' || :query || '%'
             OR country LIKE '%' || :query || '%')
        AND (:typesEmpty = 1 OR type IN (:types))
        AND (:countriesEmpty = 1 OR country IN (:countries))
        AND (:minRating IS NULL OR rating >= :minRating)
        AND (:maxRating IS NULL OR rating <= :maxRating)
        AND (:minPrice IS NULL OR priceRub >= :minPrice)
        AND (:maxPrice IS NULL OR priceRub <= :maxPrice)
        ORDER BY
            CASE WHEN :sort = 'RATING' THEN rating END DESC,
            CASE WHEN :sort = 'PRICE' THEN priceRub END DESC,
            CASE WHEN :sort = 'DATE' THEN createdAt END DESC,
            createdAt DESC
        """
    )
    fun observeFiltered(
        category: String?,
        query: String,
        typesEmpty: Int,
        types: List<String>,
        countriesEmpty: Int,
        countries: List<String>,
        minRating: Int?,
        maxRating: Int?,
        minPrice: Int?,
        maxPrice: Int?,
        sort: String,
    ): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entries WHERE :category IS NULL OR category = :category")
    fun observeCount(category: String?): Flow<Int>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Upsert
    suspend fun upsert(entity: EntryEntity): Long

    @Insert
    suspend fun insert(entity: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """SELECT DISTINCT type FROM entries
           WHERE type IS NOT NULL AND (:category IS NULL OR category = :category)
           ORDER BY type"""
    )
    fun observeUsedTypes(category: String?): Flow<List<String>>

    @Query("SELECT DISTINCT country FROM entries WHERE country IS NOT NULL ORDER BY country")
    fun observeUsedCountries(): Flow<List<String>>
}
```

`BodegaDatabase.kt`:
```kotlin
package ru.kaufd.bodega.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EntryEntity::class], version = 1, exportSchema = true)
abstract class BodegaDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
}
```

- [ ] **Step 4: Тесты зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.data.EntryDaoTest"`
Expected: PASS (5 тестов). Заодно прогнать `ArchitectureTest` — data не должен был притащить `android.*`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(data): Room entity/DAO/database, JVM-тесты DAO"
```

---

### Task 5: EntryRepositoryImpl

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/data/EntryRepositoryImpl.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/data/EntryRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `EntryDao`, мапперы (Task 4); `EntryRepository`, `EntryFilter`, `PhotoStorage` (Task 2).
- Produces: `class EntryRepositoryImpl(private val dao: EntryDao, private val photoStorage: PhotoStorage) : EntryRepository`.

- [ ] **Step 1: Падающий тест** — `EntryRepositoryImplTest.kt`:

```kotlin
package ru.kaufd.bodega.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.PhotoStorage

private class FakePhotoStorage : PhotoStorage {
    val deleted = mutableListOf<String>()
    override suspend fun delete(relativePath: String) { deleted += relativePath }
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
}

class EntryRepositoryImplTest {
    private lateinit var db: BodegaDatabase
    private lateinit var photos: FakePhotoStorage
    private lateinit var repo: EntryRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder<BodegaDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        photos = FakePhotoStorage()
        repo = EntryRepositoryImpl(db.entryDao(), photos)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun `upsert и чтение через фильтр`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", createdAt = 1))
        val list = repo.observeEntries(EntryFilter(category = Category.WINE)).first()
        assertEquals("Бароло", list.single().name)
    }

    @Test
    fun `delete не трогает фото, purgePhoto удаляет файл`() = runTest {
        val id = repo.upsert(
            Entry(category = Category.BEER, name = "X", photoPath = "photos/a.jpg", createdAt = 1)
        )
        val saved = repo.getById(id)!!
        repo.delete(saved)
        assertTrue(photos.deleted.isEmpty())
        assertNull(repo.getById(id))
        repo.purgePhoto(saved.photoPath)
        assertEquals(listOf("photos/a.jpg"), photos.deleted)
    }

    @Test
    fun `restore возвращает запись с прежним id`() = runTest {
        val id = repo.upsert(Entry(category = Category.SPIRIT, name = "Лагавулин", createdAt = 1))
        val saved = repo.getById(id)!!
        repo.delete(saved)
        repo.restore(saved)
        assertEquals("Лагавулин", repo.getById(id)?.name)
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.data.EntryRepositoryImplTest"`
Expected: FAIL (нет `EntryRepositoryImpl`).

- [ ] **Step 3: Реализация** — `EntryRepositoryImpl.kt`:

```kotlin
package ru.kaufd.bodega.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.PhotoStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepositoryImpl @Inject constructor(
    private val dao: EntryDao,
    private val photoStorage: PhotoStorage,
) : EntryRepository {

    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> =
        dao.observeFiltered(
            category = filter.category?.name,
            query = filter.query.trim(),
            typesEmpty = if (filter.types.isEmpty()) 1 else 0,
            types = filter.types.toList(),
            countriesEmpty = if (filter.countries.isEmpty()) 1 else 0,
            countries = filter.countries.toList(),
            minRating = filter.minRating,
            maxRating = filter.maxRating,
            minPrice = filter.minPrice,
            maxPrice = filter.maxPrice,
            sort = filter.sort.name,
        ).map { list -> list.map { it.toDomain() } }

    override fun observeCount(category: Category?): Flow<Int> =
        dao.observeCount(category?.name)

    override suspend fun getById(id: Long): Entry? = dao.getById(id)?.toDomain()

    override suspend fun upsert(entry: Entry): Long {
        val rowId = dao.upsert(entry.toEntity())
        return if (rowId > 0) rowId else entry.id
    }

    override suspend fun delete(entry: Entry) = dao.deleteById(entry.id)

    override suspend fun restore(entry: Entry) = dao.insert(entry.toEntity())

    override suspend fun purgePhoto(photoPath: String?) {
        if (photoPath != null) photoStorage.delete(photoPath)
    }

    override fun observeUsedTypes(category: Category?): Flow<List<String>> =
        dao.observeUsedTypes(category?.name)

    override fun observeUsedCountries(): Flow<List<String>> =
        dao.observeUsedCountries()
}
```

Примечание: `javax.inject.*` — не `android.*`, Konsist-правило не нарушается.

- [ ] **Step 4: Тесты зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.data.EntryRepositoryImplTest"`
Expected: PASS (3 теста).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(data): EntryRepositoryImpl"
```

---

### Task 6: SettingsStore на DataStore

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/data/DataStoreSettings.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/data/DataStoreSettingsTest.kt`

**Interfaces:**
- Consumes: `SettingsStore`, `Settings`, `Category`, `SortOrder` (Task 2).
- Produces: `class DataStoreSettings(dataStore: DataStore<Preferences>) : SettingsStore`.

- [ ] **Step 1: Падающий тест** — `DataStoreSettingsTest.kt` (DataStore умеет работать с файлом на JVM):

```kotlin
package ru.kaufd.bodega.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SortOrder

class DataStoreSettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `save и чтение переживают пересоздание`() = runTest {
        val file = tmp.newFile("settings.preferences_pb")
        val store1 = DataStoreSettings(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )
        store1.save(Settings(lastCategory = Category.BEER, sort = SortOrder.PRICE))
        val loaded = store1.settings.first()
        assertEquals(Category.BEER, loaded.lastCategory)
        assertEquals(SortOrder.PRICE, loaded.sort)
    }

    @Test
    fun `дефолт - все категории и сортировка по дате`() = runTest {
        val store = DataStoreSettings(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tmp.newFile("s2.preferences_pb")
            }
        )
        assertEquals(Settings(), store.settings.first())
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.data.DataStoreSettingsTest"`
Expected: FAIL.

- [ ] **Step 3: Реализация** — `DataStoreSettings.kt`:

```kotlin
package ru.kaufd.bodega.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.SortOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {

    private val keyCategory = stringPreferencesKey("last_category")
    private val keySort = stringPreferencesKey("sort")

    override val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            lastCategory = prefs[keyCategory]?.let { runCatching { Category.valueOf(it) }.getOrNull() },
            sort = prefs[keySort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                ?: SortOrder.DATE,
        )
    }

    override suspend fun save(settings: Settings) {
        dataStore.edit { prefs ->
            if (settings.lastCategory != null) prefs[keyCategory] = settings.lastCategory.name
            else prefs.remove(keyCategory)
            prefs[keySort] = settings.sort.name
        }
    }
}
```

- [ ] **Step 4: Тесты зелёные + ArchitectureTest**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS все (androidx.datastore не подпадает под запрет `android.*`).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(data): DataStoreSettings (последняя категория, сортировка)"
```

---

### Task 7: PhotoStorage (platform) + Hilt-модули

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/platform/AndroidPhotoStorage.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/di/AppModule.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/platform/AndroidPhotoStorageTest.kt` (JVM-часть: работа с файлами через java.io)

**Interfaces:**
- Consumes: `PhotoStorage`, `EntryRepository`, `SettingsStore` (Task 2); `BodegaDatabase`, `EntryRepositoryImpl`, `DataStoreSettings` (Tasks 4–6).
- Produces:
  - `AndroidPhotoStorage(filesDir: File, contentResolver: ContentResolver) : PhotoStorage` + `suspend fun importFromUri(uri: Uri): String` — возвращает относительный путь `photos/{uuid}.jpg`, изображение сжато (max 1600 px по большей стороне, JPEG q85)
  - Hilt-граф: `@Binds PhotoStorage`, `@Binds EntryRepository`, `@Binds SettingsStore`, `@Provides BodegaDatabase`, `@Provides DataStore<Preferences>`

- [ ] **Step 1: Падающий тест на файловую часть** (`delete`, `absolutePath` — без Android):

```kotlin
package ru.kaufd.bodega.platform

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidPhotoStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `absolutePath собирается из filesDir`() {
        val storage = AndroidPhotoStorage(tmp.root, contentResolver = null)
        assertEquals(
            File(tmp.root, "photos/a.jpg").absolutePath,
            storage.absolutePath("photos/a.jpg"),
        )
    }

    @Test
    fun `delete удаляет файл и не падает на отсутствующем`() = runTest {
        val dir = File(tmp.root, "photos").apply { mkdirs() }
        val f = File(dir, "x.jpg").apply { writeText("data") }
        val storage = AndroidPhotoStorage(tmp.root, contentResolver = null)
        storage.delete("photos/x.jpg")
        assertFalse(f.exists())
        storage.delete("photos/nope.jpg") // не бросает
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.platform.AndroidPhotoStorageTest"`
Expected: FAIL.

- [ ] **Step 3: Реализация** — `AndroidPhotoStorage.kt`:

```kotlin
package ru.kaufd.bodega.platform

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.PhotoStorage
import java.io.File
import java.util.UUID
import kotlin.math.max

class AndroidPhotoStorage(
    private val filesDir: File,
    private val contentResolver: ContentResolver?,
) : PhotoStorage {

    override fun absolutePath(relativePath: String): String =
        File(filesDir, relativePath).absolutePath

    override suspend fun delete(relativePath: String) {
        withContext(Dispatchers.IO) { File(filesDir, relativePath).delete() }
    }

    /** Копирует изображение из Uri во внутреннее хранилище со сжатием. */
    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = requireNotNull(contentResolver)
        val original = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            ?: error("Не удалось прочитать изображение")
        val maxSide = max(original.width, original.height)
        val scaled = if (maxSide > 1600) {
            val k = 1600f / maxSide
            Bitmap.createScaledBitmap(
                original, (original.width * k).toInt(), (original.height * k).toInt(), true
            )
        } else original
        val rel = "photos/${UUID.randomUUID()}.jpg"
        val target = File(filesDir, rel).apply { parentFile?.mkdirs() }
        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        rel
    }
}
```

- [ ] **Step 4: Hilt-модуль** — `di/AppModule.kt`:

```kotlin
package ru.kaufd.bodega.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import ru.kaufd.bodega.data.BodegaDatabase
import ru.kaufd.bodega.data.DataStoreSettings
import ru.kaufd.bodega.data.EntryRepositoryImpl
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.platform.AndroidPhotoStorage
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProvidesModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): BodegaDatabase =
        Room.databaseBuilder<BodegaDatabase>(
            context = context,
            name = context.getDatabasePath("bodega.db").absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides
    fun entryDao(db: BodegaDatabase) = db.entryDao()

    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.filesDir, "settings.preferences_pb")
        }

    @Provides @Singleton
    fun photoStorage(@ApplicationContext context: Context): AndroidPhotoStorage =
        AndroidPhotoStorage(context.filesDir, context.contentResolver)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds abstract fun repository(impl: EntryRepositoryImpl): EntryRepository
    @Binds abstract fun settings(impl: DataStoreSettings): SettingsStore
    @Binds abstract fun photos(impl: AndroidPhotoStorage): PhotoStorage
}
```

- [ ] **Step 5: Тесты и сборка зелёные**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL (Hilt-граф валиден на компиляции).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(platform,di): AndroidPhotoStorage и Hilt-граф"
```

---

### Task 8: Тема — токены из СПЕЦ

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/theme/Type.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/theme/Theme.kt`
- Modify: `app/src/main/AndroidManifest.xml` (permission INTERNET для Google Fonts provider не нужен — провайдер локальный; изменений может не быть)

**Interfaces:**
- Produces:
  - `object BodegaColors` — все цвета-токены (см. код)
  - `fun categoryColor(category: Category): Color`, `fun categoryTint(category: Category): Color`
  - `val Cormorant: FontFamily`, `val Spectral: FontFamily`, `val PlexMono: FontFamily`
  - `@Composable fun BodegaTheme(content: @Composable () -> Unit)`

- [ ] **Step 1: Реализация** (UI-токены; юнит-тестов нет — визуальная сверка со скриншотами в Task 14)

`Color.kt`:
```kotlin
package ru.kaufd.bodega.ui.theme

import androidx.compose.ui.graphics.Color
import ru.kaufd.bodega.domain.Category

object BodegaColors {
    val Paper = Color(0xFFF4ECDB)        // фон экрана
    val Canvas = Color(0xFFF2E9D8)       // полотно списка
    val Surface = Color(0xFFFBF6EC)      // карточки
    val FieldBg = Color(0xFFFFFFFF)      // поля ввода
    val Ink = Color(0xFF2E2620)          // текст
    val Muted = Color(0xFF6B5D4E)
    val Muted2 = Color(0xFF5E5140)
    val Placeholder = Color(0xFF9A8B78)
    val Label = Color(0xFF8A7A66)
    val Hairline = Color(0x1A2E2620)     // rgba(46,38,32,0.10)

    val Wine = Color(0xFF9B3A34)         // бордо
    val Beer = Color(0xFFB0842E)         // янтарь
    val Spirit = Color(0xFF8A6440)       // медь

    val WineTint = Color(0xFFEBD9D3)
    val BeerTint = Color(0xFFEFE3C9)
    val SpiritTint = Color(0xFFE6DBCB)
}

fun categoryColor(category: Category): Color = when (category) {
    Category.WINE -> BodegaColors.Wine
    Category.BEER -> BodegaColors.Beer
    Category.SPIRIT -> BodegaColors.Spirit
}

fun categoryTint(category: Category): Color = when (category) {
    Category.WINE -> BodegaColors.WineTint
    Category.BEER -> BodegaColors.BeerTint
    Category.SPIRIT -> BodegaColors.SpiritTint
}
```

`Type.kt` (шрифты через Google Fonts provider — без бандла файлов):
```kotlin
package ru.kaufd.bodega.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import ru.kaufd.bodega.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val Cormorant = FontFamily(
    Font(GoogleFont("Cormorant Garamond"), provider, weight = FontWeight.Medium),
    Font(GoogleFont("Cormorant Garamond"), provider, weight = FontWeight.SemiBold),
)

val Spectral = FontFamily(
    Font(GoogleFont("Spectral"), provider, weight = FontWeight.Normal),
    Font(GoogleFont("Spectral"), provider, weight = FontWeight.Normal, style = FontStyle.Italic),
)

val PlexMono = FontFamily(
    Font(GoogleFont("IBM Plex Mono"), provider, weight = FontWeight.Normal),
    Font(GoogleFont("IBM Plex Mono"), provider, weight = FontWeight.Medium),
)
```

Для provider нужен ресурс сертификатов: создать `app/src/main/res/values/font_certs.xml` — стандартный массив `com_google_android_gms_fonts_certs` (скопировать из документации androidx «Use Downloadable Fonts / preloaded_fonts»; это два base64-сертификата dev+prod). Если устройство без GMS — фоллбэк на системные serif/monospace задаётся автоматически через `FontFamily`-цепочку; проверить на устройстве в Task 14.

`Theme.kt`:
```kotlin
package ru.kaufd.bodega.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val scheme = lightColorScheme(
    primary = BodegaColors.Ink,
    background = BodegaColors.Paper,
    surface = BodegaColors.Surface,
    onPrimary = BodegaColors.Surface,
    onBackground = BodegaColors.Ink,
    onSurface = BodegaColors.Ink,
    outlineVariant = BodegaColors.Hairline,
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 21.sp),
    bodyLarge = TextStyle(fontFamily = Spectral, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Spectral, fontSize = 14.5.sp),
    labelMedium = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, letterSpacing = 1.2.sp),
    labelLarge = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
)

@Composable
fun BodegaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
```

- [ ] **Step 2: Сборка**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(ui): тема — цвета и шрифты из дизайн-токенов СПЕЦ"
```

---

### Task 9: Навигация + общий стейт фильтра

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/Nav.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/state/FilterStateHolder.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/MainActivity.kt`

**Interfaces:**
- Consumes: `BodegaTheme` (Task 8), `EntryFilter`, `SettingsStore` (Task 2).
- Produces:
  - Маршруты: `object Routes { const val LIST = "list"; const val FILTERS = "filters"; fun detail(id: Long) = "detail/$id"; fun edit(id: Long?) = if (id == null) "edit" else "edit?id=$id" }` (+ шаблоны `"detail/{id}"`, `"edit?id={id}"`)
  - `@Singleton class FilterStateHolder @Inject constructor() { val filter: MutableStateFlow<EntryFilter> }` — единый источник фильтра для list/filters-экранов
  - `@Composable fun BodegaNavHost()`

- [ ] **Step 1: Реализация**

`ui/state/FilterStateHolder.kt`:
```kotlin
package ru.kaufd.bodega.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import ru.kaufd.bodega.domain.EntryFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterStateHolder @Inject constructor() {
    val filter = MutableStateFlow(EntryFilter())
}
```

`ui/Nav.kt`:
```kotlin
package ru.kaufd.bodega.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.kaufd.bodega.ui.detail.DetailScreen
import ru.kaufd.bodega.ui.edit.EditScreen
import ru.kaufd.bodega.ui.filters.FiltersScreen
import ru.kaufd.bodega.ui.list.ListScreen

object Routes {
    const val LIST = "list"
    const val FILTERS = "filters"
    const val DETAIL_PATTERN = "detail/{id}"
    const val EDIT_PATTERN = "edit?id={id}"
    fun detail(id: Long) = "detail/$id"
    fun edit(id: Long?) = if (id == null) "edit" else "edit?id=$id"
}

@Composable
fun BodegaNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            ListScreen(
                onEntryClick = { nav.navigate(Routes.detail(it)) },
                onAddClick = { nav.navigate(Routes.edit(null)) },
                onFiltersClick = { nav.navigate(Routes.FILTERS) },
            )
        }
        composable(
            Routes.DETAIL_PATTERN,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            DetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(Routes.edit(id)) },
            )
        }
        composable(
            Routes.EDIT_PATTERN,
            arguments = listOf(navArgument("id") {
                type = NavType.StringType; nullable = true; defaultValue = null
            }),
        ) {
            EditScreen(onClose = { nav.popBackStack() })
        }
        composable(Routes.FILTERS) {
            FiltersScreen(onClose = { nav.popBackStack() })
        }
    }
}
```

На этом шаге `DetailScreen`/`EditScreen`/`FiltersScreen` ещё не существуют — создать в их пакетах заглушки, которые следующие задачи заменят реализацией:

```kotlin
// ui/detail/DetailScreen.kt
package ru.kaufd.bodega.ui.detail
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
@Composable
fun DetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) { Text("detail") }
```
```kotlin
// ui/edit/EditScreen.kt
package ru.kaufd.bodega.ui.edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
@Composable
fun EditScreen(onClose: () -> Unit) { Text("edit") }
```
```kotlin
// ui/filters/FiltersScreen.kt
package ru.kaufd.bodega.ui.filters
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
@Composable
fun FiltersScreen(onClose: () -> Unit) { Text("filters") }
```
```kotlin
// ui/list/ListScreen.kt — заглушка, заменит Task 10
package ru.kaufd.bodega.ui.list
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
@Composable
fun ListScreen(onEntryClick: (Long) -> Unit, onAddClick: () -> Unit, onFiltersClick: () -> Unit) {
    Text("list")
}
```

`MainActivity.kt` — заменить `setContent`:
```kotlin
setContent { BodegaTheme { BodegaNavHost() } }
```
(добавить импорты `ru.kaufd.bodega.ui.BodegaNavHost`, `ru.kaufd.bodega.ui.theme.BodegaTheme`).

- [ ] **Step 2: Сборка + все тесты**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(ui): навигация, маршруты, FilterStateHolder"
```

---

### Task 10: Главный экран (6a) — список, поиск, таб-бар, пустые состояния, undo-удаление

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/list/ListViewModel.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/list/ListScreen.kt` (заменить заглушку)
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/list/CategoryTabBar.kt`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/list/EmptyState.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/ui/list/ListViewModelTest.kt`

**Interfaces:**
- Consumes: `EntryRepository`, `SettingsStore`, `EntryFilter` (Task 2), `FilterStateHolder` (Task 9), тема (Task 8).
- Produces:
  - `ListViewModel`: `val state: StateFlow<ListState>`, `fun onQueryChange(q: String)`, `fun onCategorySelect(c: Category?)`, `fun onDelete(entry: Entry)`, `fun onUndoDelete()`, `fun onUndoExpired()`
  - `data class ListState(val entries: List<Entry>, val filter: EntryFilter, val totalCount: Int, val pendingDelete: Entry?, val emptyKind: EmptyKind?)`
  - `enum class EmptyKind { COLLECTION, CATEGORY, SEARCH }`

- [ ] **Step 1: Падающий тест VM** — `ListViewModelTest.kt`:

```kotlin
package ru.kaufd.bodega.ui.list

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.ui.state.FilterStateHolder

private class FakeRepo : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val deleted = mutableListOf<Entry>()
    val restored = mutableListOf<Entry>()
    val purged = mutableListOf<String?>()
    override fun observeEntries(filter: EntryFilter) = entries.map { list ->
        list.filter { filter.category == null || it.category == filter.category }
            .filter { filter.query.isEmpty() || it.name.contains(filter.query, ignoreCase = true) }
    }
    override fun observeCount(category: Category?) = entries.map { l ->
        l.count { category == null || it.category == category }
    }
    override suspend fun getById(id: Long) = entries.value.find { it.id == id }
    override suspend fun upsert(entry: Entry) = entry.id
    override suspend fun delete(entry: Entry) {
        deleted += entry; entries.value = entries.value - entry
    }
    override suspend fun restore(entry: Entry) {
        restored += entry; entries.value = entries.value + entry
    }
    override suspend fun purgePhoto(photoPath: String?) { purged += photoPath }
    override fun observeUsedTypes(category: Category?): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
}

private class FakeSettings : SettingsStore {
    val saved = mutableListOf<Settings>()
    override val settings = MutableStateFlow(Settings())
    override suspend fun save(settings: Settings) { saved += settings }
}

class ListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var settings: FakeSettings
    private lateinit var holder: FilterStateHolder

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo(); settings = FakeSettings(); holder = FilterStateHolder()
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = ListViewModel(repo, settings, holder)

    private fun wine(id: Long, name: String) =
        Entry(id = id, category = Category.WINE, name = name, createdAt = id)

    @Test
    fun `пустая база - EmptyKind COLLECTION`() = runTest {
        vm().state.test {
            var s = awaitItem()
            while (s.emptyKind != EmptyKind.COLLECTION) s = awaitItem()
            assertEquals(EmptyKind.COLLECTION, s.emptyKind)
        }
    }

    @Test
    fun `пустая категория при непустой базе - EmptyKind CATEGORY`() = runTest {
        repo.entries.value = listOf(wine(1, "Бароло"))
        val vm = vm()
        vm.onCategorySelect(Category.BEER)
        vm.state.test {
            var s = awaitItem()
            while (s.emptyKind != EmptyKind.CATEGORY) s = awaitItem()
            assertEquals(EmptyKind.CATEGORY, s.emptyKind)
        }
    }

    @Test
    fun `пустой результат поиска - EmptyKind SEARCH`() = runTest {
        repo.entries.value = listOf(wine(1, "Бароло"))
        val vm = vm()
        vm.onQueryChange("кьянти")
        vm.state.test {
            var s = awaitItem()
            while (s.emptyKind != EmptyKind.SEARCH) s = awaitItem()
            assertEquals(EmptyKind.SEARCH, s.emptyKind)
        }
    }

    @Test
    fun `delete затем undo восстанавливает и не чистит фото`() = runTest {
        val e = wine(1, "Бароло").copy(photoPath = "photos/a.jpg")
        repo.entries.value = listOf(e)
        val vm = vm()
        vm.onDelete(e)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(e), repo.deleted)
        vm.onUndoDelete()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(e), repo.restored)
        assertEquals(emptyList<String?>(), repo.purged)
        assertNull(vm.state.first().pendingDelete)
    }

    @Test
    fun `delete затем истечение undo чистит фото`() = runTest {
        val e = wine(1, "Бароло").copy(photoPath = "photos/a.jpg")
        repo.entries.value = listOf(e)
        val vm = vm()
        vm.onDelete(e)
        dispatcher.scheduler.runCurrent()
        vm.onUndoExpired()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf<String?>("photos/a.jpg"), repo.purged)
    }

    @Test
    fun `смена категории сохраняется в settings`() = runTest {
        val vm = vm()
        vm.onCategorySelect(Category.SPIRIT)
        dispatcher.scheduler.runCurrent()
        assertEquals(Category.SPIRIT, settings.saved.last().lastCategory)
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ui.list.ListViewModelTest"`
Expected: FAIL (нет `ListViewModel`).

- [ ] **Step 3: Реализация VM** — `ListViewModel.kt`:

```kotlin
package ru.kaufd.bodega.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.ui.state.FilterStateHolder
import javax.inject.Inject

enum class EmptyKind { COLLECTION, CATEGORY, SEARCH }

data class ListState(
    val entries: List<Entry> = emptyList(),
    val filter: EntryFilter = EntryFilter(),
    val totalCount: Int = -1,          // -1 = ещё не загружено
    val pendingDelete: Entry? = null,
) {
    val emptyKind: EmptyKind?
        get() = when {
            totalCount < 0 || entries.isNotEmpty() -> null
            filter.query.isNotEmpty() || filter.activeFilterCount > 0 -> EmptyKind.SEARCH
            filter.category != null && totalCount > 0 -> EmptyKind.CATEGORY
            totalCount == 0 -> EmptyKind.COLLECTION
            else -> EmptyKind.SEARCH
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val settingsStore: SettingsStore,
    private val filterHolder: FilterStateHolder,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<Entry?>(null)

    init {
        // Восстановить последнюю категорию и сортировку.
        viewModelScope.launch {
            val saved = settingsStore.settings.first()
            filterHolder.filter.value = filterHolder.filter.value.copy(
                category = saved.lastCategory, sort = saved.sort,
            )
        }
    }

    val state: StateFlow<ListState> = combine(
        filterHolder.filter.flatMapLatest { f ->
            combine(
                repository.observeEntries(f),
                repository.observeCount(null),
            ) { entries, total -> Triple(f, entries, total) }
        },
        pendingDelete,
    ) { (f, entries, total), pending ->
        ListState(entries = entries, filter = f, totalCount = total, pendingDelete = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    fun onQueryChange(q: String) {
        filterHolder.filter.value = filterHolder.filter.value.copy(query = q)
    }

    fun onCategorySelect(c: Category?) {
        filterHolder.filter.value = filterHolder.filter.value.copy(category = c)
        viewModelScope.launch {
            settingsStore.save(Settings(lastCategory = c, sort = filterHolder.filter.value.sort))
        }
    }

    fun onResetFilters() {
        filterHolder.filter.value = filterHolder.filter.value.resetFilters().copy(query = "")
    }

    fun onDelete(entry: Entry) {
        pendingDelete.value = entry
        viewModelScope.launch { repository.delete(entry) }
    }

    fun onUndoDelete() {
        val e = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { repository.restore(e) }
    }

    fun onUndoExpired() {
        val e = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { repository.purgePhoto(e.photoPath) }
    }
}
```

- [ ] **Step 4: Тесты VM зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ui.list.ListViewModelTest"`
Expected: PASS (6 тестов).

- [ ] **Step 5: UI списка** — заменить заглушку `ListScreen.kt`. Структура по СПЕЦ 3.1/3.5 (визуальные детали — по скриншотам `docs/screenshots/главная.png`, `пустые состояния.png`):

```kotlin
package ru.kaufd.bodega.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.categoryColor
import ru.kaufd.bodega.ui.theme.categoryTint

@Composable
fun ListScreen(
    onEntryClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onFiltersClick: () -> Unit,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Undo-снекбар: показывается пока есть pendingDelete; по таймауту — purge.
    LaunchedEffect(state.pendingDelete) {
        val pending = state.pendingDelete ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "Запись «${pending.name}» удалена",
            actionLabel = "Отменить",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete()
        else viewModel.onUndoExpired()
    }

    Scaffold(
        containerColor = BodegaColors.Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            CategoryTabBar(
                selected = state.filter.category,
                onSelect = viewModel::onCategorySelect,
                onAddClick = onAddClick,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Header()
            SearchRow(
                query = state.filter.query,
                onQueryChange = viewModel::onQueryChange,
                filterCount = state.filter.activeFilterCount,
                onFiltersClick = onFiltersClick,
            )
            DividerRow(state)
            when (state.emptyKind) {
                EmptyKind.COLLECTION -> EmptyCollection(onAddClick)
                EmptyKind.CATEGORY -> EmptyCategory(state.filter.category!!, onAddClick)
                EmptyKind.SEARCH -> EmptySearch(state.filter.query, viewModel::onResetFilters)
                null -> EntryList(state.entries, onEntryClick, viewModel::onDelete)
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Погребокъ", style = MaterialTheme.typography.displaySmall, color = BodegaColors.Ink)
        Text(
            "журналъ дегустацій",
            style = MaterialTheme.typography.labelMedium,
            color = BodegaColors.Label,
        )
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    filterCount: Int,
    onFiltersClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("искать в журнале…", color = BodegaColors.Placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = if (query.isNotEmpty()) {
                { TextButton(onClick = { onQueryChange("") }) { Text("×", color = BodegaColors.Muted) } }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BodegaColors.FieldBg,
                unfocusedContainerColor = BodegaColors.FieldBg,
                focusedBorderColor = BodegaColors.Hairline,
                unfocusedBorderColor = BodegaColors.Hairline,
            ),
        )
        Spacer(Modifier.width(10.dp))
        BadgedBox(badge = {
            if (filterCount > 0) Badge { Text("$filterCount") }
        }) {
            FilledIconButton(
                onClick = onFiltersClick,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = BodegaColors.Ink),
            ) { Text("≡", color = BodegaColors.Surface) }
        }
    }
}

@Composable
private fun DividerRow(state: ListState) {
    val categoryLabel = when (state.filter.category) {
        null -> "Все категории"
        Category.WINE -> "Вино"
        Category.BEER -> "Пиво"
        Category.SPIRIT -> "Крепкое"
    }
    val sortLabel = when (state.filter.sort) {
        ru.kaufd.bodega.domain.SortOrder.RATING -> "баллъ ↓"
        ru.kaufd.bodega.domain.SortOrder.PRICE -> "цена ↓"
        ru.kaufd.bodega.domain.SortOrder.DATE -> "дата ↓"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "$categoryLabel · ${state.entries.size}",
            style = MaterialTheme.typography.labelMedium, color = BodegaColors.Label,
        )
        Text(sortLabel, style = MaterialTheme.typography.labelMedium, color = BodegaColors.Label)
    }
}

@Composable
private fun EntryList(
    entries: List<Entry>,
    onEntryClick: (Long) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().background(BodegaColors.Canvas)) {
        items(entries, key = { it.id }) { entry ->
            EntryRow(entry, onClick = { onEntryClick(entry.id) })
            HorizontalDivider(color = BodegaColors.Hairline)
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val abs = remember(entry.photoPath) {
            entry.photoPath?.let { java.io.File(ctx.filesDir, it).absolutePath }
        }
        if (abs != null) {
            AsyncImage(
                model = abs, contentDescription = null,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                    .background(categoryTint(entry.category)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            val subtitle = listOfNotNull(entry.type, entry.country).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = BodegaColors.Muted, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (entry.rating != null) {
                Text(
                    "${entry.rating}",
                    style = MaterialTheme.typography.labelLarge,
                    color = categoryColor(entry.category),
                )
            }
            if (entry.priceRub != null) {
                Text("${entry.priceRub} ₽", style = MaterialTheme.typography.labelMedium, color = BodegaColors.Muted)
            }
        }
    }
}
```

Примечание: удаление инициируется с карточки записи (Task 11 — меню «⋯»), колбэк `onDelete` здесь зарезервирован для этого; свайп не делаем (нет в СПЕЦ).

- [ ] **Step 6: Таб-бар** — `CategoryTabBar.kt`:

```kotlin
package ru.kaufd.bodega.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.categoryColor

@Composable
fun CategoryTabBar(
    selected: Category?,
    onSelect: (Category?) -> Unit,
    onAddClick: () -> Unit,
) {
    Surface(color = BodegaColors.Surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TabItem("Все", selected == null, BodegaColors.Ink) { onSelect(null) }
            TabItem("Вино", selected == Category.WINE, categoryColor(Category.WINE)) { onSelect(Category.WINE) }
            // Центральная приподнятая кнопка «+»
            Box(
                Modifier.size(56.dp).offset(y = (-10).dp).clip(CircleShape)
                    .background(BodegaColors.Ink).clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = BodegaColors.Surface, style = MaterialTheme.typography.headlineMedium)
            }
            TabItem("Пиво", selected == Category.BEER, categoryColor(Category.BEER)) { onSelect(Category.BEER) }
            TabItem("Крепкое", selected == Category.SPIRIT, categoryColor(Category.SPIRIT)) { onSelect(Category.SPIRIT) }
        }
    }
}

@Composable
private fun TabItem(label: String, active: Boolean, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) tint.copy(alpha = 0.18f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) tint else BodegaColors.Muted,
            )
        }
    }
}
```

Иконки таб-бара (сетка/бокал/кружка/стакан) в MVP заменены текстовыми лейблами — контурные векторные иконки добавить в Task 14 из макета `.dc.html`, если останется бюджет; это осознанное упрощение, зафиксировать в коммите.

- [ ] **Step 7: Пустые состояния** — `EmptyState.kt` (тексты дословно из СПЕЦ 3.5):

```kotlin
package ru.kaufd.bodega.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.categoryColor
import ru.kaufd.bodega.ui.theme.categoryTint

@Composable
private fun EmptyScaffold(
    circleColor: androidx.compose.ui.graphics.Color,
    title: String,
    text: String,
    button: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(92.dp).clip(CircleShape).background(circleColor))
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium,
            color = BodegaColors.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        button()
    }
}

@Composable
fun EmptyCollection(onAddClick: () -> Unit) {
    EmptyScaffold(
        circleColor = BodegaColors.Surface,
        title = "Погреб пока пуст",
        text = "Заведите первую запись — вино, пиво или крепкое, — и она появится в вашем журнале дегустаций.",
    ) {
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = BodegaColors.Ink),
        ) { Text("+ Добавить первую запись") }
    }
}

@Composable
fun EmptyCategory(category: Category, onAddClick: () -> Unit) {
    val (label, add) = when (category) {
        Category.WINE -> "Вино" to "+ Добавить вино"
        Category.BEER -> "Пиво" to "+ Добавить пиво"
        Category.SPIRIT -> "Крепкое" to "+ Добавить напиток"
    }
    EmptyScaffold(
        circleColor = categoryTint(category),
        title = "В разделе «$label» пусто",
        text = "Здесь появятся ваши записи. Добавьте первую, чтобы начать вести раздел.",
    ) {
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = categoryColor(category)),
        ) { Text(add) }
    }
}

@Composable
fun EmptySearch(query: String, onReset: () -> Unit) {
    EmptyScaffold(
        circleColor = BodegaColors.Surface,
        title = "Ничего не найдено",
        text = "По запросу «$query» с текущими фильтрами записей нет. Попробуйте изменить запрос или сбросить фильтры.",
    ) {
        OutlinedButton(onClick = onReset) { Text("Сбросить фильтры") }
    }
}
```

- [ ] **Step 8: Сборка + все тесты**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat(ui): главный экран — список, поиск, таб-бар, пустые состояния, undo"
```

---

### Task 11: Карточка записи + удаление

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/detail/DetailScreen.kt` (заменить заглушку)
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/detail/DetailViewModel.kt`

**Interfaces:**
- Consumes: `EntryRepository` (Task 2), маршруты (Task 9), тема (Task 8). Удаление из карточки регистрируется в общем `PendingDeleteHolder`, чтобы undo-снекбар на списке сработал после возврата назад.
- Produces:
  - `@Singleton class PendingDeleteHolder @Inject constructor() { val pending: MutableStateFlow<Entry?> }` — создать в `ui/state/PendingDeleteHolder.kt`; `ListViewModel` отрефакторить: заменить приватный `pendingDelete` на этот холдер (инжект), логика undo/expire не меняется
  - `DetailViewModel`: `val entry: StateFlow<Entry?>`, `fun delete(onDone: () -> Unit)`
  - `DetailScreen(onBack, onEdit)`

- [ ] **Step 1: Рефакторинг под общий PendingDeleteHolder**

`ui/state/PendingDeleteHolder.kt`:
```kotlin
package ru.kaufd.bodega.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import ru.kaufd.bodega.domain.Entry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingDeleteHolder @Inject constructor() {
    val pending = MutableStateFlow<Entry?>(null)
}
```

В `ListViewModel`: конструктор получает `private val pendingHolder: PendingDeleteHolder`; все обращения `pendingDelete.value` → `pendingHolder.pending.value`, в `combine` — `pendingHolder.pending`. В тесте `ListViewModelTest` — создавать холдер и передавать в VM (обновить `vm()`).

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ui.list.ListViewModelTest"`
Expected: PASS после рефакторинга.

- [ ] **Step 2: DetailViewModel**

```kotlin
package ru.kaufd.bodega.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.ui.state.PendingDeleteHolder
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: EntryRepository,
    private val pendingHolder: PendingDeleteHolder,
) : ViewModel() {

    private val id: Long = checkNotNull(savedState["id"])
    private val _entry = MutableStateFlow<Entry?>(null)
    val entry: StateFlow<Entry?> = _entry

    fun load() {
        viewModelScope.launch { _entry.value = repository.getById(id) }
    }

    /** Удаляет запись, регистрирует pending для undo-снекбара на списке. */
    fun delete(onDone: () -> Unit) {
        val e = _entry.value ?: return
        viewModelScope.launch {
            repository.delete(e)
            pendingHolder.pending.value = e
            onDone()
        }
    }
}
```

- [ ] **Step 3: DetailScreen** (структура по СПЕЦ 3.2; скриншоты `карточка.png`, `карточка2.png`):

```kotlin
package ru.kaufd.bodega.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.categoryColor
import ru.kaufd.bodega.ui.theme.categoryTint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }   // перезагрузка после возврата с редактора
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val e = entry ?: return

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить запись?") },
            text = { Text("«${e.name}» будет удалена. Отменить можно будет сразу после удаления.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) {
                    Text("Удалить", color = BodegaColors.Wine)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    Scaffold(
        containerColor = BodegaColors.Paper,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Назад", color = BodegaColors.Muted) }
                Row {
                    TextButton(onClick = { onEdit(e.id) }) {
                        Text("Править", color = categoryColor(e.category))
                    }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋯", color = BodegaColors.Muted) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Удалить", color = BodegaColors.Wine) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val abs = remember(e.photoPath) {
                e.photoPath?.let { java.io.File(ctx.filesDir, it).absolutePath }
            }
            if (abs != null) {
                AsyncImage(
                    model = abs, contentDescription = e.name,
                    modifier = Modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Box(
                    Modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(categoryTint(e.category)),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(e.name, style = MaterialTheme.typography.displaySmall)
            val subtitle = listOfNotNull(e.grape ?: e.style, e.type, e.country).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = BodegaColors.Muted)
            }
            Spacer(Modifier.height(20.dp))
            if (e.rating != null) RatingBlock(e.rating!!, categoryColor(e.category))
            Spacer(Modifier.height(20.dp))
            Characteristics(e)
            if (!e.note.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    e.note!!, style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic, color = BodegaColors.Muted2,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RatingBlock(rating: Int, tint: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$rating", style = MaterialTheme.typography.displaySmall, color = tint)
            Text("/10", style = MaterialTheme.typography.labelLarge, color = BodegaColors.Muted)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(10) { i ->
                Box(
                    Modifier.size(width = 18.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < rating) tint else BodegaColors.Hairline),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Моя оценка", style = MaterialTheme.typography.labelMedium, color = BodegaColors.Label)
    }
}

@Composable
private fun Characteristics(e: Entry) {
    val dateFmt = remember { SimpleDateFormat("d MMMM yyyy", Locale("ru")) }
    val rows = buildList {
        when (e.category) {
            Category.WINE -> {
                e.grape?.let { add("Сорт" to it) }
                e.type?.let { add("Тип" to it) }
                e.country?.let { add("Страна" to it) }
                e.vintageYear?.let { add("Год" to "$it") }
            }
            Category.BEER -> {
                e.style?.let { add("Стиль" to it) }
                e.type?.let { add("Тип" to it) }
            }
            Category.SPIRIT -> {
                e.type?.let { add("Тип" to it) }
                e.ageStatement?.let { add("Выдержка" to it) }
            }
        }
        e.abvPercent?.let { add("Крепость" to "$it%") }
        e.volumeMl?.let { add("Объём" to "${it / 1000.0} л") }
        e.priceRub?.let { add("Цена покупки" to "$it ₽") }
        e.tastedAt?.let { add("Дата дегустации" to dateFmt.format(Date(it))) }
    }
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { (k, v) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = BodegaColors.Muted)
                Text(v, style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider(color = BodegaColors.Hairline)
        }
    }
}
```

Примечание: undo-снекбар после удаления из карточки показывается на списке — `ListScreen.LaunchedEffect` реагирует на `pendingHolder.pending` (в Task 11 Step 1 `ListViewModel.state` уже слушает холдер, снекбар сработает при возврате).

- [ ] **Step 4: Сборка + все тесты**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(ui): карточка записи, удаление с подтверждением и undo"
```

---

### Task 12: Создание / редактирование

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditScreen.kt` (заменить заглушку)
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditViewModel.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/ui/edit/EditViewModelTest.kt`
- Test (androidTest): `app/src/androidTest/kotlin/ru/kaufd/bodega/ui/edit/EditScreenTest.kt`

**Interfaces:**
- Consumes: `EntryRepository`, `Suggestions` (Task 2), `AndroidPhotoStorage.importFromUri` (Task 7), маршруты (Task 9).
- Produces:
  - `data class EditState(val entry: Entry, val isNew: Boolean, val dirty: Boolean, val saving: Boolean)`
  - `EditViewModel`: `val state: StateFlow<EditState>`, `fun onField(update: (Entry) -> Entry)`, `fun onCategoryChange(c: Category)`, `fun onPhotoPicked(uri: Uri)`, `fun save(onDone: () -> Unit)`, `val canSave: Boolean` (в стейте через `entry.name.isNotBlank()`)

- [ ] **Step 1: Падающий тест VM** — `EditViewModelTest.kt`:

```kotlin
package ru.kaufd.bodega.ui.edit

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository

private class FakeRepo : EntryRepository {
    val store = MutableStateFlow<Map<Long, Entry>>(emptyMap())
    var nextId = 1L
    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeCount(category: Category?): Flow<Int> = flowOf(0)
    override suspend fun getById(id: Long) = store.value[id]
    override suspend fun upsert(entry: Entry): Long {
        val id = if (entry.id == 0L) nextId++ else entry.id
        store.value = store.value + (id to entry.copy(id = id))
        return id
    }
    override suspend fun delete(entry: Entry) { store.value = store.value - entry.id }
    override suspend fun restore(entry: Entry) { store.value = store.value + (entry.id to entry) }
    override suspend fun purgePhoto(photoPath: String?) {}
    override fun observeUsedTypes(category: Category?): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
}

class EditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo

    @Before fun setup() { Dispatchers.setMain(dispatcher); repo = FakeRepo() }
    @After fun tearDown() { Dispatchers.resetMain() }

    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()

    private fun vm(id: Long? = null) = EditViewModel(
        savedState = SavedStateHandle(mapOf("id" to id?.toString())),
        repository = repo,
        photoStorage = ru.kaufd.bodega.platform.AndroidPhotoStorage(tmp.root, null),
    )

    @Test
    fun `новая запись - категория WINE по умолчанию, сохранять нельзя без имени`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        val s = vm.state.value
        assertTrue(s.isNew)
        assertEquals(Category.WINE, s.entry.category)
        assertFalse(s.entry.name.isNotBlank())
    }

    @Test
    fun `редактирование - загружает запись по id`() = runTest {
        repo.upsert(Entry(category = Category.BEER, name = "Гиннесс", createdAt = 1))
        val vm = vm(id = 1)
        dispatcher.scheduler.runCurrent()
        assertEquals("Гиннесс", vm.state.value.entry.name)
        assertFalse(vm.state.value.isNew)
    }

    @Test
    fun `смена категории очищает категорийные поля, общие сохраняет`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        vm.onField { it.copy(name = "X", grape = "Неббиоло", country = "Италия", priceRub = 500) }
        vm.onCategoryChange(Category.BEER)
        val e = vm.state.value.entry
        assertEquals("X", e.name)
        assertEquals(500, e.priceRub)
        assertNull(e.grape); assertNull(e.country); assertNull(e.vintageYear)
        assertEquals(Category.BEER, e.category)
    }

    @Test
    fun `save проставляет createdAt для новой записи и вызывает onDone`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        vm.onField { it.copy(name = "Бароло") }
        var done = false
        vm.save { done = true }
        dispatcher.scheduler.runCurrent()
        assertTrue(done)
        val saved = repo.store.value.values.single()
        assertEquals("Бароло", saved.name)
        assertTrue(saved.createdAt > 0)
    }

    @Test
    fun `dirty становится true после правки`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.dirty)
        vm.onField { it.copy(name = "Y") }
        assertTrue(vm.state.value.dirty)
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ui.edit.EditViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Реализация VM** — `EditViewModel.kt`:

```kotlin
package ru.kaufd.bodega.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.platform.AndroidPhotoStorage
import javax.inject.Inject

data class EditState(
    val entry: Entry = Entry(category = Category.WINE, name = ""),
    val isNew: Boolean = true,
    val dirty: Boolean = false,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = entry.name.isNotBlank() && !saving
}

@HiltViewModel
class EditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: EntryRepository,
    private val photoStorage: AndroidPhotoStorage,
) : ViewModel() {

    private val editId: Long? = savedState.get<String?>("id")?.toLongOrNull()
    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state

    init {
        if (editId != null) {
            viewModelScope.launch {
                repository.getById(editId)?.let {
                    _state.value = EditState(entry = it, isNew = false)
                }
            }
        }
    }

    fun onField(update: (Entry) -> Entry) {
        _state.value = _state.value.copy(entry = update(_state.value.entry), dirty = true)
    }

    /** Смена категории: общие поля остаются, категорийные очищаются. */
    fun onCategoryChange(c: Category) {
        if (c == _state.value.entry.category) return
        onField {
            it.copy(
                category = c,
                grape = null, country = null, vintageYear = null,
                style = null, ageStatement = null,
            )
        }
    }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val rel = photoStorage.importFromUri(uri)
            onField { it.copy(photoPath = rel) }
        }
    }

    fun save(onDone: () -> Unit) {
        if (!_state.value.canSave) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val e = _state.value.entry
            val stamped = if (e.createdAt == 0L) e.copy(createdAt = System.currentTimeMillis()) else e
            repository.upsert(stamped)
            onDone()
        }
    }
}
```

Примечание: `AndroidPhotoStorage` живёт в `platform` и на JVM работает без Android (contentResolver = null используется только в importFromUri) — поэтому тест конструирует его напрямую с TemporaryFolder.

- [ ] **Step 4: Тесты VM зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ui.edit.EditViewModelTest"`
Expected: PASS (5 тестов).

- [ ] **Step 5: UI редактора** — `EditScreen.kt` (СПЕЦ 3.3; скриншот `создание записей.png`):

```kotlin
package ru.kaufd.bodega.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Suggestions
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.categoryColor

@Composable
fun EditScreen(
    onClose: () -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::onPhotoPicked) }

    fun tryClose() { if (state.dirty) confirmExit = true else onClose() }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Выйти без сохранения?") },
            text = { Text("Изменения будут потеряны.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onClose() }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Остаться") }
            },
        )
    }

    val accent = categoryColor(state.entry.category)

    Scaffold(
        containerColor = BodegaColors.Paper,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = ::tryClose) { Text("Отмена", color = BodegaColors.Muted) }
                TextButton(onClick = { viewModel.save(onClose) }, enabled = state.canSave) {
                    Text("Сохранить", color = if (state.canSave) accent else BodegaColors.Placeholder)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Фото
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val abs = remember(state.entry.photoPath) {
                state.entry.photoPath?.let { java.io.File(ctx.filesDir, it).absolutePath }
            }
            Box(
                Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp))
                    .border(
                        BorderStroke(1.dp, BodegaColors.Placeholder),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (abs != null) AsyncImage(model = abs, contentDescription = null, modifier = Modifier.fillMaxSize())
                else Text("Добавить фото", color = BodegaColors.Placeholder)
            }

            // Категория
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Category.entries.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = state.entry.category == c,
                        onClick = { viewModel.onCategoryChange(c) },
                        shape = SegmentedButtonDefaults.itemShape(i, Category.entries.size),
                    ) {
                        Text(when (c) {
                            Category.WINE -> "Вино"; Category.BEER -> "Пиво"; Category.SPIRIT -> "Крепкое"
                        })
                    }
                }
            }

            Field("Название *", state.entry.name) { v -> viewModel.onField { it.copy(name = v) } }

            when (state.entry.category) {
                Category.WINE -> {
                    SuggestField("Сорт", state.entry.grape ?: "", Suggestions.grapes) { v ->
                        viewModel.onField { it.copy(grape = v.ifBlank { null }) }
                    }
                    SuggestField("Тип", state.entry.type ?: "", Suggestions.types[Category.WINE]!!) { v ->
                        viewModel.onField { it.copy(type = v.ifBlank { null }) }
                    }
                    SuggestField("Страна", state.entry.country ?: "", Suggestions.countries) { v ->
                        viewModel.onField { it.copy(country = v.ifBlank { null }) }
                    }
                    NumberField("Год", state.entry.vintageYear) { v ->
                        viewModel.onField { it.copy(vintageYear = v) }
                    }
                }
                Category.BEER -> {
                    SuggestField("Стиль", state.entry.style ?: "", Suggestions.types[Category.BEER]!!) { v ->
                        viewModel.onField { it.copy(style = v.ifBlank { null }) }
                    }
                    Field("Тип", state.entry.type ?: "") { v ->
                        viewModel.onField { it.copy(type = v.ifBlank { null }) }
                    }
                }
                Category.SPIRIT -> {
                    SuggestField("Тип", state.entry.type ?: "", Suggestions.types[Category.SPIRIT]!!) { v ->
                        viewModel.onField { it.copy(type = v.ifBlank { null }) }
                    }
                    Field("Выдержка", state.entry.ageStatement ?: "") { v ->
                        viewModel.onField { it.copy(ageStatement = v.ifBlank { null }) }
                    }
                }
            }

            DecimalField("Крепость, %", state.entry.abvPercent) { v ->
                viewModel.onField { it.copy(abvPercent = v) }
            }
            NumberField("Объём, мл", state.entry.volumeMl) { v ->
                viewModel.onField { it.copy(volumeMl = v) }
            }
            NumberField("Цена, ₽", state.entry.priceRub) { v ->
                viewModel.onField { it.copy(priceRub = v) }
            }

            // Оценка 1–10
            Text("Моя оценка", style = MaterialTheme.typography.labelMedium, color = BodegaColors.Label)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..10).forEach { r ->
                    val selected = state.entry.rating == r
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) accent else BodegaColors.Surface)
                            .border(BorderStroke(1.dp, BodegaColors.Hairline), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.onField { it.copy(rating = if (selected) null else r) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$r",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) BodegaColors.Surface else BodegaColors.Muted,
                        )
                    }
                }
            }

            Field(
                "Заметка", state.entry.note ?: "",
                singleLine = false, minLines = 4,
            ) { v -> viewModel.onField { it.copy(note = v.ifBlank { null }) } }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BodegaColors.FieldBg,
            unfocusedContainerColor = BodegaColors.FieldBg,
        ),
    )
}

/** Поле со строкой чипов-подсказок под ним; свободный ввод разрешён. */
@Composable
private fun SuggestField(
    label: String,
    value: String,
    suggestions: List<String>,
    onChange: (String) -> Unit,
) {
    Column {
        Field(label, value, onChange = onChange)
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            items(suggestions.size) { i ->
                val s = suggestions[i]
                AssistChip(onClick = { onChange(s) }, label = { Text(s) })
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Field(label, value?.toString() ?: "") { v -> onChange(v.filter { it.isDigit() }.toIntOrNull()) }
}

@Composable
private fun DecimalField(label: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Field(label, text) { v ->
        text = v
        onChange(v.replace(',', '.').toDoubleOrNull())
    }
}
```

- [ ] **Step 6: Compose-тест смены категории** — `androidTest/.../EditScreenTest.kt`:

```kotlin
package ru.kaufd.bodega.ui.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import org.junit.Rule
import org.junit.Test
import ru.kaufd.bodega.ui.theme.BodegaTheme

class EditScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun categorySwitchChangesFields() {
        // VM с фейковым репозиторием собрать по образцу EditViewModelTest.FakeRepo
        // (вынести FakeRepo в androidTest-утилиту при реализации)
        compose.setContent {
            BodegaTheme { EditScreen(onClose = {}) } // hiltViewModel в тесте заменить прямой передачей VM
        }
        compose.onNodeWithText("Сорт").assertIsDisplayed()      // WINE по умолчанию
        compose.onNodeWithText("Пиво").performClick()
        compose.onNodeWithText("Стиль").assertIsDisplayed()     // поля BEER
    }
}
```

Требует устройства/эмулятора: `./gradlew :app:connectedDebugAndroidTest`. Если эмулятор недоступен на машине исполнителя — пометить шаг SKIPPED в коммите и проверить вручную в Task 14.

- [ ] **Step 7: Сборка + все тесты**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(ui): экран создания/редактирования с фото и динамическими полями"
```

---

### Task 13: Экран фильтров

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/filters/FiltersScreen.kt` (заменить заглушку)
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/filters/FiltersViewModel.kt`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/ui/filters/FiltersViewModelTest.kt`

**Interfaces:**
- Consumes: `FilterStateHolder` (Task 9), `EntryRepository.observeUsedTypes/observeUsedCountries/observeEntries` (Task 2), `SettingsStore` (Task 2).
- Produces:
  - `data class FiltersState(val draft: EntryFilter, val availableTypes: List<String>, val availableCountries: List<String>, val resultCount: Int)`
  - `FiltersViewModel`: `val state: StateFlow<FiltersState>`, `fun onDraftChange(update: (EntryFilter) -> EntryFilter)`, `fun apply(onDone: () -> Unit)`, `fun reset()`

- [ ] **Step 1: Падающий тест VM** — `FiltersViewModelTest.kt`:

```kotlin
package ru.kaufd.bodega.ui.filters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.cash.turbine.test
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.SortOrder
import ru.kaufd.bodega.ui.state.FilterStateHolder

private class FakeRepo : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    override fun observeEntries(filter: EntryFilter) = entries.map { list ->
        list.filter { filter.category == null || it.category == filter.category }
            .filter { filter.types.isEmpty() || it.type in filter.types }
    }
    override fun observeCount(category: Category?) = entries.map { it.size }
    override suspend fun getById(id: Long): Entry? = null
    override suspend fun upsert(entry: Entry) = 0L
    override suspend fun delete(entry: Entry) {}
    override suspend fun restore(entry: Entry) {}
    override suspend fun purgePhoto(photoPath: String?) {}
    override fun observeUsedTypes(category: Category?): Flow<List<String>> =
        entries.map { l -> l.mapNotNull { it.type }.distinct().sorted() }
    override fun observeUsedCountries(): Flow<List<String>> =
        entries.map { l -> l.mapNotNull { it.country }.distinct().sorted() }
}

private class FakeSettings : SettingsStore {
    val saved = mutableListOf<Settings>()
    override val settings = MutableStateFlow(Settings())
    override suspend fun save(settings: Settings) { saved += settings }
}

class FiltersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var holder: FilterStateHolder
    private lateinit var settings: FakeSettings

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo(); holder = FilterStateHolder(); settings = FakeSettings()
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = FiltersViewModel(repo, holder, settings)

    @Test
    fun `resultCount пересчитывается по драфту не трогая общий фильтр`() = runTest {
        repo.entries.value = listOf(
            Entry(id = 1, category = Category.BEER, name = "A", type = "IPA", createdAt = 1),
            Entry(id = 2, category = Category.BEER, name = "B", type = "Стаут", createdAt = 2),
        )
        val vm = vm()
        vm.onDraftChange { it.copy(types = setOf("IPA")) }
        vm.state.test {
            var s = awaitItem()
            while (s.resultCount != 1) s = awaitItem()
            assertEquals(1, s.resultCount)
        }
        assertEquals(EntryFilter(), holder.filter.value) // общий фильтр не изменился
    }

    @Test
    fun `apply публикует драфт в холдер и сохраняет сортировку`() = runTest {
        val vm = vm()
        vm.onDraftChange { it.copy(sort = SortOrder.PRICE, minRating = 7) }
        var done = false
        vm.apply { done = true }
        dispatcher.scheduler.runCurrent()
        assertEquals(SortOrder.PRICE, holder.filter.value.sort)
        assertEquals(7, holder.filter.value.minRating)
        assertEquals(SortOrder.PRICE, settings.saved.last().sort)
        assertEquals(true, done)
    }

    @Test
    fun `reset сбрасывает фильтры драфта но не категорию`() = runTest {
        holder.filter.value = EntryFilter(category = Category.WINE, minRating = 5)
        val vm = vm()
        vm.reset()
        assertEquals(
            EntryFilter(category = Category.WINE),
            vm.state.value.draft,
        )
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ui.filters.FiltersViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Реализация VM** — `FiltersViewModel.kt`:

```kotlin
package ru.kaufd.bodega.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.ui.state.FilterStateHolder
import javax.inject.Inject

data class FiltersState(
    val draft: EntryFilter = EntryFilter(),
    val availableTypes: List<String> = emptyList(),
    val availableCountries: List<String> = emptyList(),
    val resultCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val filterHolder: FilterStateHolder,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val draft = MutableStateFlow(filterHolder.filter.value)

    val state: StateFlow<FiltersState> = combine(
        draft,
        draft.flatMapLatest { repository.observeEntries(it).map { l -> l.size } },
        draft.flatMapLatest { repository.observeUsedTypes(it.category) },
        repository.observeUsedCountries(),
    ) { d, count, types, countries ->
        FiltersState(draft = d, availableTypes = types, availableCountries = countries, resultCount = count)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        FiltersState(draft = filterHolder.filter.value),
    )

    fun onDraftChange(update: (EntryFilter) -> EntryFilter) {
        draft.value = update(draft.value)
    }

    fun apply(onDone: () -> Unit) {
        filterHolder.filter.value = draft.value
        viewModelScope.launch {
            settingsStore.save(
                Settings(lastCategory = draft.value.category, sort = draft.value.sort)
            )
            onDone()
        }
    }

    fun reset() {
        draft.value = draft.value.resetFilters()
    }
}
```

Примечание: тест `reset` читает `vm.state.value.draft` — начальное значение `stateIn` строится из холдера; после `reset()` стейт пересобирается из `draft`-флоу при подписке (`test { }`). Если тест флакает на начальном значении — читать `draft` через первый `awaitItem()` с нужным условием, как в остальных тестах.

- [ ] **Step 4: Тесты VM зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.kaufd.bodega.ui.filters.FiltersViewModelTest"`
Expected: PASS (3 теста).

- [ ] **Step 5: UI фильтров** — `FiltersScreen.kt` (СПЕЦ 3.4, полноэкранно; скриншот `фильтры.png`):

```kotlin
package ru.kaufd.bodega.ui.filters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.SortOrder
import ru.kaufd.bodega.ui.theme.BodegaColors

@Composable
fun FiltersScreen(
    onClose: () -> Unit,
    viewModel: FiltersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BodegaColors.Paper,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("Закрыть", color = BodegaColors.Muted) }
                Text("Фильтры", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = viewModel::reset) { Text("Сбросить", color = BodegaColors.Muted) }
            }
        },
        bottomBar = {
            Button(
                onClick = { viewModel.apply(onClose) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BodegaColors.Ink),
            ) { Text("Показать ${state.resultCount} записей") }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Section("Категория") {
                ChipRow(
                    options = listOf<Pair<String, Category?>>(
                        "Все" to null, "Вино" to Category.WINE,
                        "Пиво" to Category.BEER, "Крепкое" to Category.SPIRIT,
                    ),
                    isSelected = { state.draft.category == it },
                ) { c ->
                    viewModel.onDraftChange { it.copy(category = c, types = emptySet()) }
                }
            }

            if (state.availableTypes.isNotEmpty()) {
                Section("Стиль и тип") {
                    MultiChipRow(
                        options = state.availableTypes,
                        selected = state.draft.types,
                    ) { t ->
                        viewModel.onDraftChange {
                            it.copy(types = if (t in it.types) it.types - t else it.types + t)
                        }
                    }
                }
            }

            Section("Оценка (${state.draft.minRating ?: 1}–${state.draft.maxRating ?: 10})") {
                RangeSlider(
                    value = (state.draft.minRating ?: 1).toFloat()..(state.draft.maxRating ?: 10).toFloat(),
                    onValueChange = { r ->
                        viewModel.onDraftChange {
                            val min = r.start.toInt().coerceIn(1, 10)
                            val max = r.endInclusive.toInt().coerceIn(1, 10)
                            it.copy(
                                minRating = if (min <= 1) null else min,
                                maxRating = if (max >= 10) null else max,
                            )
                        }
                    },
                    valueRange = 1f..10f, steps = 8,
                )
            }

            Section("Цена покупки, ₽") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.draft.minPrice?.toString() ?: "",
                        onValueChange = { v ->
                            viewModel.onDraftChange { it.copy(minPrice = v.toIntOrNull()) }
                        },
                        label = { Text("от") }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.draft.maxPrice?.toString() ?: "",
                        onValueChange = { v ->
                            viewModel.onDraftChange { it.copy(maxPrice = v.toIntOrNull()) }
                        },
                        label = { Text("до") }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
            }

            if (state.availableCountries.isNotEmpty()) {
                Section("Страна") {
                    MultiChipRow(
                        options = state.availableCountries,
                        selected = state.draft.countries,
                    ) { c ->
                        viewModel.onDraftChange {
                            it.copy(countries = if (c in it.countries) it.countries - c else it.countries + c)
                        }
                    }
                }
            }

            Section("Сортировка") {
                ChipRow(
                    options = listOf(
                        "По баллам" to SortOrder.RATING,
                        "По цене" to SortOrder.PRICE,
                        "По дате" to SortOrder.DATE,
                    ),
                    isSelected = { state.draft.sort == it },
                ) { s -> viewModel.onDraftChange { it.copy(sort = s) } }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = BodegaColors.Label)
        content()
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = isSelected(value),
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun MultiChipRow(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) })
        }
    }
}
```

- [ ] **Step 6: Сборка + все тесты**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(ui): полноэкранные фильтры с live-счётчиком результатов"
```

---

### Task 14: Финальная проверка на устройстве

**Files:**
- Modify: по результатам проверки (точечные фиксы).

**Interfaces:**
- Consumes: всё предыдущее.
- Produces: рабочий APK, сверенный со скриншотами дизайна.

- [ ] **Step 1: Полный прогон тестов**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS все (domain, data, ui, Konsist).

- [ ] **Step 2: Установка на устройство/эмулятор**

```bash
./gradlew :app:installDebug
adb shell am start -n ru.kaufd.bodega/.MainActivity
```

- [ ] **Step 3: Ручной чек-лист** (сверять со скриншотами в `docs/screenshots/`):

- [ ] Пустая коллекция → экран 8a, кнопка ведёт в редактор
- [ ] Создать запись вина (фото из галереи, все поля) → появляется в списке
- [ ] Шрифты: заголовки Cormorant, числа моно (устройство с GMS)
- [ ] Таб «Пиво» при пустой категории → экран 8b с янтарным акцентом
- [ ] Поиск несуществующего → экран 8c, «Сбросить фильтры» работает
- [ ] Фильтры: диапазон оценки, чипы типов, счётчик на кнопке «Показать N записей»
- [ ] Бейдж количества фильтров на главной
- [ ] Карточка: рейтинг-шкала, характеристики по категории, заметка курсивом
- [ ] Редактирование: смена категории меняет поля, «Отмена» с изменениями → диалог
- [ ] Удаление из карточки → возврат на список → снекбар «Отменить» восстанавливает
- [ ] Перезапуск приложения → категория и сортировка сохранены
- [ ] Поворот экрана на каждом экране → состояние не теряется

- [ ] **Step 4: Фиксы по чек-листу** — каждый фикс отдельным коммитом `fix(ui): …`.

- [ ] **Step 5: Финальный коммит**

```bash
git add -A && git commit -m "chore: MVP готов — прогон чек-листа на устройстве" --allow-empty
```
