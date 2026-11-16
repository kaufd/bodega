# Архитектурная чистка Bodega — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Устранить протечку слоёв `ui → platform`/файловая система, починить баг осиротевшего фото при удалении через Detail, закрыть инкапсуляцию pending-состояния, усилить архитектурный тест и подчистить мелкий DRY/мёртвый код.

**Architecture:** Приложение слоистое (`ui → domain ← data/platform`, DI через Hilt). Правки не меняют слоёв: доступ к фото и сценарий удаления централизуются за доменными абстракциями (`PhotoStorage`-порт, `DeleteEntryUseCase`), UI перестаёт знать о `platform` и раскладке файлов.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, kotlinx.coroutines, konsist (арх-тесты), JUnit4 + Turbine + Robolectric.

## Global Constraints

- Пакет `ru.kaufd.bodega`, один Gradle-модуль `:app`.
- `domain` НЕ импортирует `android.*`, Room, DataStore (проверяется `ArchitectureTest`).
- Тесты запускаются: `./gradlew testDebugUnitTest`.
- Все пользовательские строки — через `R.string.*` (в этих задачах новых UI-строк не добавляем).
- Коммиты — после каждой задачи, conventional commits.

---

## Порядок и приоритеты

| Task | Что | Приоритет |
|---|---|---|
| 1 | `EntryLimits` — единые константы валидации | низкий (DRY) |
| 2 | `Category.parseOrNull` — единый safe-parse | низкий (DRY) |
| 3 | `sortByCanonical` — хелпер сортировки фасетов | низкий (DRY) |
| 4 | Удалить мёртвый `observeUsedTypes` | низкий (YAGNI) |
| 5 | `DeleteEntryUseCase` — фикс бага сироты + инкапсуляция | **высокий** |
| 6 | `PhotoStorage`-порт + усиленный арх-тест | **высокий** |

Задачи 1–4 независимы и малорисковы (разминка, меньше конфликтов при последующих правках). 5 и 6 — структурные, независимы друг от друга.

---

## File Structure

- `domain/EntryLimits.kt` — **создать**: константы `RATING`, `ABV`.
- `domain/Category.kt` — модифицировать: companion `parseOrNull`.
- `domain/DeleteEntryUseCase.kt` — **создать**: владелец pending + delete/undo/expire.
- `domain/PhotoStorage.kt` — модифицировать: добавить `importImage(bytes)`.
- `domain/EntryRepository.kt` — модифицировать: убрать `observeUsedTypes`.
- `data/EntryRepositoryImpl.kt` — модифицировать: `sortByCanonical`, убрать `observeUsedTypes`, валидация через `EntryLimits`.
- `data/EntryDao.kt` — модифицировать: убрать `observeUsedTypes`.
- `data/DataStoreSettings.kt` — модифицировать: `Category.parseOrNull`.
- `platform/AndroidPhotoStorage.kt` — модифицировать: `importImage(bytes)`, убрать `contentResolver`/`importFromUri`.
- `di/AppModule.kt` — модифицировать: провайдер `AndroidPhotoStorage` без `contentResolver`.
- `ui/LocalPhotoStorage.kt` — **создать**: `staticCompositionLocalOf<PhotoStorage>`.
- `MainActivity.kt` — модифицировать: инжект `PhotoStorage`, провайд CompositionLocal.
- `ui/edit/EditViewModel.kt` — модифицировать: порт `PhotoStorage`, `onPhotoPicked(bytes)`, `Category.parseOrNull`.
- `ui/edit/EditScreen.kt` — модифицировать: чтение байтов, `LocalPhotoStorage`, `EntryLimits`.
- `ui/detail/DetailScreen.kt`, `ui/list/ListScreen.kt` — модифицировать: `LocalPhotoStorage`.
- `ui/list/ListViewModel.kt`, `ui/detail/DetailViewModel.kt` — модифицировать: делегировать `DeleteEntryUseCase`.
- `ui/state/PendingDeleteHolder.kt` — **удалить**.
- `ui/filters/FiltersScreen.kt`, `ui/detail/DetailScreen.kt` — модифицировать: `EntryLimits.RATING` в границах рейтинга.
- Тесты: `EntryRepositoryImplTest`, `EditViewModelTest`, `ListViewModelTest`, `FiltersViewModelTest`, `AndroidPhotoStorageTest`, `ArchitectureTest` — модифицировать; **создать** `domain/DeleteEntryUseCaseTest.kt`.

---

### Task 1: Единые константы валидации (`EntryLimits`)

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/EntryLimits.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/data/EntryRepositoryImpl.kt:56-57`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditScreen.kt:287`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/detail/DetailScreen.kt:216`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/filters/FiltersScreen.kt:206,214,219-223`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/data/EntryRepositoryImplTest.kt`

**Interfaces:**
- Produces: `object EntryLimits { val RATING: IntRange /* 1..10 */; val ABV: ClosedFloatingPointRange<Double> /* 0.0..96.0 */ }`

- [ ] **Step 1: Написать падающий тест валидации**

Добавить в `EntryRepositoryImplTest` (внутри класса, рядом с прочими `@Test`):

```kotlin
@Test
fun `upsert отклоняет рейтинг и крепость вне границ EntryLimits`() = runTest {
    val repo = EntryRepositoryImpl(db.entryDao(), photos)
    val base = Entry(category = Category.WINE, name = "X")
    assertThrows(IllegalArgumentException::class.java) {
        runBlocking { repo.upsert(base.copy(rating = EntryLimits.RATING.last + 1)) }
    }
    assertThrows(IllegalArgumentException::class.java) {
        runBlocking { repo.upsert(base.copy(abvPercent = EntryLimits.ABV.endInclusive + 1)) }
    }
}
```

Добавить импорты: `import ru.kaufd.bodega.domain.EntryLimits`, `import kotlinx.coroutines.runBlocking`, `import org.junit.Assert.assertThrows`.

- [ ] **Step 2: Запустить — убедиться, что не компилируется/падает**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.data.EntryRepositoryImplTest"`
Expected: FAIL — `EntryLimits` не существует (ошибка компиляции).

- [ ] **Step 3: Создать `EntryLimits`**

`domain/EntryLimits.kt`:

```kotlin
package ru.kaufd.bodega.domain

/** Единый источник границ валидации записи. Используется и в data-валидации, и в UI. */
object EntryLimits {
    /** Допустимый рейтинг: 1..10. */
    val RATING: IntRange = 1..10

    /** Допустимая крепость, %: 0.0..96.0. */
    val ABV: ClosedFloatingPointRange<Double> = 0.0..96.0
}
```

- [ ] **Step 4: Применить в валидации репозитория**

`data/EntryRepositoryImpl.kt:56-57` заменить на:

```kotlin
        entry.rating?.let { require(it in EntryLimits.RATING) { "Рейтинг должен быть от ${EntryLimits.RATING.first} до ${EntryLimits.RATING.last}, получено $it" } }
        entry.abvPercent?.let { require(it in EntryLimits.ABV) { "Крепость должна быть от ${EntryLimits.ABV.start.toInt()} до ${EntryLimits.ABV.endInclusive.toInt()}%, получено $it" } }
```

Добавить импорт `import ru.kaufd.bodega.domain.EntryLimits`.

- [ ] **Step 5: Применить в UI-границах рейтинга**

`ui/edit/EditScreen.kt:287` — заменить `(1..10).forEach { r ->` на `EntryLimits.RATING.forEach { r ->`.

`ui/detail/DetailScreen.kt:216` — заменить `repeat(10) { i ->` на `repeat(EntryLimits.RATING.last) { i ->`.

`ui/filters/FiltersScreen.kt`:
- строка 206: `?: 10` → `?: EntryLimits.RATING.last`; `?: 1` → `?: EntryLimits.RATING.first`.
- строка 214: `(state.draft.minRating ?: 1).toFloat()..(state.draft.maxRating ?: 10).toFloat()` → `(state.draft.minRating ?: EntryLimits.RATING.first).toFloat()..(state.draft.maxRating ?: EntryLimits.RATING.last).toFloat()`.
- строки 219-220: `coerceIn(1, 10)` → `coerceIn(EntryLimits.RATING.first, EntryLimits.RATING.last)`.
- строка 223: `if (max >= 10)` → `if (max >= EntryLimits.RATING.last)`.

В каждый из трёх UI-файлов добавить `import ru.kaufd.bodega.domain.EntryLimits`.

- [ ] **Step 6: Запустить весь тест-сет**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: единые границы валидации EntryLimits (rating/abv)"
```

---

### Task 2: Единый safe-parse `Category.parseOrNull`

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/domain/Category.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditViewModel.kt:35-37`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/data/DataStoreSettings.kt:26`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/domain/CategoryTest.kt` (создать)

**Interfaces:**
- Produces: `Category.parseOrNull(raw: String?): Category?` — `null` для `null`/невалидной строки.

- [ ] **Step 1: Написать падающий тест**

Create `app/src/test/kotlin/ru/kaufd/bodega/domain/CategoryTest.kt`:

```kotlin
package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryTest {
    @Test fun `parseOrNull возвращает enum для валидного имени`() {
        assertEquals(Category.WINE, Category.parseOrNull("WINE"))
    }
    @Test fun `parseOrNull возвращает null для мусора и null`() {
        assertNull(Category.parseOrNull("nope"))
        assertNull(Category.parseOrNull(null))
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.domain.CategoryTest"`
Expected: FAIL — `parseOrNull` не существует.

- [ ] **Step 3: Добавить companion в `Category`**

`domain/Category.kt` целиком:

```kotlin
package ru.kaufd.bodega.domain

enum class Category {
    WINE, BEER, SPIRIT;

    companion object {
        /** Безопасный разбор имени enum: `null`/невалидное → `null`. */
        fun parseOrNull(raw: String?): Category? =
            raw?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
```

- [ ] **Step 4: Применить в двух местах**

`ui/edit/EditViewModel.kt:35-37` заменить на:

```kotlin
    private val initialCategory: Category =
        Category.parseOrNull(savedState.get<String?>("category")) ?: Category.WINE
```

`data/DataStoreSettings.kt:26` заменить на:

```kotlin
            lastCategory = Category.parseOrNull(prefs[keyCategory]),
```

- [ ] **Step 5: Запустить весь тест-сет**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: единый Category.parseOrNull вместо дублированного runCatching"
```

---

### Task 3: Хелпер канонической сортировки фасетов

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/data/EntryRepositoryImpl.kt:83-94`
- Test: `app/src/test/kotlin/ru/kaufd/bodega/data/EntryRepositoryImplTest.kt` (существующий тест фасетов покрывает; при отсутствии — см. Step 1)

**Interfaces:**
- Produces (private): `List<String>.sortByCanonical(order: List<String>): List<String>` — сортировка по индексу в `order`, неизвестные значения в конец.

- [ ] **Step 1: Убедиться в наличии теста фасетов**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.data.EntryRepositoryImplTest" -i` и проверить, есть ли тест на `observeWineFacets`. Если теста нет — добавить:

```kotlin
@Test
fun `observeWineFacets сортирует по каноническому порядку, неизвестное в конец`() = runTest {
    val repo = EntryRepositoryImpl(db.entryDao(), photos)
    repo.upsert(Entry(category = Category.WINE, name = "b", color = "Белое"))
    repo.upsert(Entry(category = Category.WINE, name = "r", color = "Красное"))
    val facets = repo.observeWineFacets().first()
    // WineClassification.colors = [Красное, Белое, Розовое]
    assertEquals(listOf("Красное", "Белое"), facets.colors)
}
```

- [ ] **Step 2: Ввести хелпер и переписать `observeWineFacets`**

В `data/EntryRepositoryImpl.kt` добавить приватную функцию верхнего уровня (внизу файла):

```kotlin
/** Сортирует значения по каноническому [order]; отсутствующие в справочнике — в конец. */
private fun List<String>.sortByCanonical(order: List<String>): List<String> =
    sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
```

Заменить тело `observeWineFacets` (строки 83-94) на:

```kotlin
    override fun observeWineFacets(): Flow<WineFacets> = combine(
        dao.observeUsedWineColors(),
        dao.observeUsedWineTypes(),
        dao.observeUsedWineSweetness(),
    ) { colors, types, sweetness ->
        WineFacets(
            colors = colors.sortByCanonical(WineClassification.colors),
            types = types.sortByCanonical(WineClassification.types),
            sweetness = sweetness.sortByCanonical(WineClassification.allSweetness),
        )
    }
```

- [ ] **Step 3: Запустить тесты**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.data.EntryRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: хелпер sortByCanonical для фасетов вина (DRY)"
```

---

### Task 4: Удалить мёртвый `observeUsedTypes`

`observeUsedTypes` (не-`Grouped`) объявлен во всех слоях, но не вызывается production-кодом (используется только `observeUsedTypesGrouped`).

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/domain/EntryRepository.kt:25`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/data/EntryRepositoryImpl.kt:70-71`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/data/EntryDao.kt` (метод `observeUsedTypes` + его `@Query`)
- Modify (fakes): `EditViewModelTest.kt:41`, `ListViewModelTest.kt`, `FiltersViewModelTest.kt:40`

- [ ] **Step 1: Убедиться, что вызовов из production нет**

Run: `grep -rn "observeUsedTypes\b" app/src/main/kotlin` — ожидается только объявления (domain/data/dao), без вызовов из `ui/`.

- [ ] **Step 2: Удалить из domain-интерфейса**

`domain/EntryRepository.kt` — удалить строку 25:

```kotlin
    fun observeUsedTypes(category: Category?): Flow<List<String>>
```

- [ ] **Step 3: Удалить реализацию и DAO-запрос**

`data/EntryRepositoryImpl.kt` — удалить строки 70-71 (override `observeUsedTypes`).

`data/EntryDao.kt` — удалить метод `fun observeUsedTypes(category: String?): Flow<List<String>>` вместе с его аннотацией `@Query(...)` (около строки 89).

- [ ] **Step 4: Удалить override из тест-фейков**

В `EditViewModelTest.kt`, `ListViewModelTest.kt`, `FiltersViewModelTest.kt` удалить строку:

```kotlin
    override fun observeUsedTypes(category: Category?): Flow<List<String>> = flowOf(emptyList())
```

(В `FiltersViewModelTest` — многострочный вариант строк 40-41, удалить целиком.)

- [ ] **Step 5: Собрать и прогнать тесты**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (компиляция чистая, все фейки обновлены).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: удалить неиспользуемый observeUsedTypes (YAGNI)"
```

---

### Task 5: `DeleteEntryUseCase` — фикс осиротевшего фото + инкапсуляция

**Баг:** удаление через Detail (`DetailViewModel.delete`) перезаписывает pending без очистки фото предыдущей pending-записи → файл сироты навсегда. Корректная логика «очистить прежнее фото» существует в `ListViewModel.onDelete` (покрыта тестом), но путь Detail её не использует. Выносим единый владелец pending + delete/undo/expire; оба ViewModel делегируют.

**Files:**
- Create: `app/src/main/kotlin/ru/kaufd/bodega/domain/DeleteEntryUseCase.kt`
- Delete: `app/src/main/kotlin/ru/kaufd/bodega/ui/state/PendingDeleteHolder.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/list/ListViewModel.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/detail/DetailViewModel.kt`
- Create: `app/src/test/kotlin/ru/kaufd/bodega/domain/DeleteEntryUseCaseTest.kt`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/ui/list/ListViewModelTest.kt`

**Interfaces:**
- Produces: `@Singleton class DeleteEntryUseCase @Inject constructor(repository: EntryRepository)` с:
  - `val pending: StateFlow<Entry?>`
  - `suspend fun delete(entry: Entry)` — очищает фото прежней pending (если id другой), удаляет строку, ставит pending.
  - `suspend fun undo()` — восстанавливает pending-запись, очищает pending.
  - `suspend fun expire()` — очищает фото pending-записи, очищает pending.

- [ ] **Step 1: Написать падающий тест use case (сценарий сироты)**

Create `app/src/test/kotlin/ru/kaufd/bodega/domain/DeleteEntryUseCaseTest.kt`:

```kotlin
package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeRepo : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val purged = mutableListOf<String?>()
    val restored = mutableListOf<Entry>()
    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = entries
    override fun observeCount(category: Category?): Flow<Int> = flowOf(0)
    override fun observeById(id: Long): Flow<Entry?> = flowOf(null)
    override suspend fun getById(id: Long): Entry? = entries.value.find { it.id == id }
    override suspend fun upsert(entry: Entry): Long = entry.id
    override suspend fun delete(entry: Entry) { entries.value = entries.value - entry }
    override suspend fun restore(entry: Entry) { restored += entry; entries.value = entries.value + entry }
    override suspend fun purgePhoto(photoPath: String?) { purged += photoPath }
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets(): Flow<WineFacets> = flowOf(WineFacets())
}

class DeleteEntryUseCaseTest {
    private val a = Entry(id = 1, category = Category.WINE, name = "A", photoPath = "photos/a.jpg")
    private val b = Entry(id = 2, category = Category.WINE, name = "B", photoPath = "photos/b.jpg")

    @Test
    fun `удаление второй записи очищает фото первой pending`() = runTest {
        val repo = FakeRepo()
        val uc = DeleteEntryUseCase(repo)
        uc.delete(a)
        assertEquals(emptyList<String?>(), repo.purged) // фото A ещё не тронуто (undo активно)
        uc.delete(b)
        assertEquals(listOf("photos/a.jpg"), repo.purged) // фото A очищено при вытеснении
        assertEquals(b, uc.pending.value)
    }

    @Test
    fun `undo восстанавливает и не чистит фото`() = runTest {
        val repo = FakeRepo()
        val uc = DeleteEntryUseCase(repo)
        uc.delete(a)
        uc.undo()
        assertEquals(listOf(a), repo.restored)
        assertEquals(emptyList<String?>(), repo.purged)
        assertNull(uc.pending.value)
    }

    @Test
    fun `expire чистит фото pending-записи`() = runTest {
        val repo = FakeRepo()
        val uc = DeleteEntryUseCase(repo)
        uc.delete(a)
        uc.expire()
        assertEquals(listOf("photos/a.jpg"), repo.purged)
        assertNull(uc.pending.value)
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.domain.DeleteEntryUseCaseTest"`
Expected: FAIL — `DeleteEntryUseCase` не существует.

- [ ] **Step 3: Создать `DeleteEntryUseCase`**

`domain/DeleteEntryUseCase.kt`:

```kotlin
package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый владелец сценария «удаление с undo». Хранит одну ожидающую (pending)
 * запись; при вытеснении прежней pending её фото очищается (иначе — сирота на диске).
 */
@Singleton
class DeleteEntryUseCase @Inject constructor(
    private val repository: EntryRepository,
) {
    private val _pending = MutableStateFlow<Entry?>(null)
    val pending: StateFlow<Entry?> = _pending.asStateFlow()

    /** Удаляет строку; фото прежней pending-записи (если это другая запись) очищается. */
    suspend fun delete(entry: Entry) {
        _pending.value?.let { prev -> if (prev.id != entry.id) repository.purgePhoto(prev.photoPath) }
        repository.delete(entry)
        _pending.value = entry
    }

    /** Undo: восстановить последнюю удалённую запись, снять pending. */
    suspend fun undo() {
        val e = _pending.value ?: return
        _pending.value = null
        repository.restore(e)
    }

    /** Истечение undo: удалить осиротевший файл фото, снять pending. */
    suspend fun expire() {
        val e = _pending.value ?: return
        _pending.value = null
        repository.purgePhoto(e.photoPath)
    }
}
```

- [ ] **Step 4: Запустить тест use case**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.domain.DeleteEntryUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Переключить `DetailViewModel` на use case (фикс бага)**

`ui/detail/DetailViewModel.kt` — заменить импорт `import ru.kaufd.bodega.ui.state.PendingDeleteHolder` на `import ru.kaufd.bodega.domain.DeleteEntryUseCase`; конструктор и `delete`:

```kotlin
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: EntryRepository,
    private val deleteUseCase: DeleteEntryUseCase,
) : ViewModel() {

    private val id: Long = checkNotNull(savedState["id"])

    val entry: StateFlow<Entry?> = repository.observeById(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Удаляет запись через единый use case (чистит фото прежней pending), регистрирует undo. */
    fun delete(onDone: () -> Unit) {
        val e = entry.value ?: return
        viewModelScope.launch {
            deleteUseCase.delete(e)
            onDone()
        }
    }
}
```

- [ ] **Step 6: Переключить `ListViewModel` на use case; удалить мёртвый `onDelete`**

`ui/list/ListViewModel.kt`:
- заменить импорт `PendingDeleteHolder` на `import ru.kaufd.bodega.domain.DeleteEntryUseCase`;
- в конструкторе `private val pendingHolder: PendingDeleteHolder` → `private val deleteUseCase: DeleteEntryUseCase`;
- в `combine(...)` источник `pendingHolder.pending` → `deleteUseCase.pending`;
- удалить метод `onDelete` (строки 112-121 — он не подключён к UI; логика теперь в use case);
- `onUndoDelete`/`onUndoExpired` переписать на делегирование:

```kotlin
    fun onUndoDelete() {
        viewModelScope.launch { deleteUseCase.undo() }
    }

    fun onUndoExpired() {
        viewModelScope.launch { deleteUseCase.expire() }
    }
```

- [ ] **Step 7: Удалить `PendingDeleteHolder`**

```bash
git rm app/src/main/kotlin/ru/kaufd/bodega/ui/state/PendingDeleteHolder.kt
```

- [ ] **Step 8: Обновить `ListViewModelTest`**

В `app/src/test/kotlin/ru/kaufd/bodega/ui/list/ListViewModelTest.kt`:
- заменить импорт `import ru.kaufd.bodega.ui.state.PendingDeleteHolder` на `import ru.kaufd.bodega.domain.DeleteEntryUseCase`;
- добавить поле и инициализацию use case; в `vm()` передавать его:

```kotlin
    private lateinit var deleteUseCase: DeleteEntryUseCase
    // ... в setup(): deleteUseCase = DeleteEntryUseCase(repo)
    private fun vm() = ListViewModel(repo, settings, holder, deleteUseCase)
```

(Инициализировать `deleteUseCase = DeleteEntryUseCase(repo)` в том же `@Before`, где создаётся `repo`.)
- в тестах undo/expire заменить `vm.onDelete(e)` на `deleteUseCase.delete(e)` (это `suspend`, вызывается внутри `runTest`);
- удалить тест «сироты» (строки ~155-162 с двойным `vm.onDelete`) — его сценарий теперь покрыт `DeleteEntryUseCaseTest`.

- [ ] **Step 9: Прогнать весь тест-сет**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "fix: единый DeleteEntryUseCase устраняет осиротевшее фото при удалении через Detail"
```

---

### Task 6: `PhotoStorage`-порт вместо протечки в `platform`/ФС + усиленный арх-тест

Убираем: (1) инъекцию конкретного `AndroidPhotoStorage` и `android.net.Uri` в `EditViewModel`; (2) три копии `File(ctx.filesDir, path).absolutePath` в экранах. Доступ к фото идёт через доменный порт `PhotoStorage`, прокинутый в Compose через `CompositionLocal`. Импорт изображения принимает `ByteArray` (Android-специфика `Uri`→байты остаётся в экране, где Android допустим).

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/domain/PhotoStorage.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/platform/AndroidPhotoStorage.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/di/AppModule.kt:51-53`
- Create: `app/src/main/kotlin/ru/kaufd/bodega/ui/LocalPhotoStorage.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/MainActivity.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditViewModel.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditScreen.kt` (launchers + `PhotoBox`)
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/detail/DetailScreen.kt:149`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/list/ListScreen.kt:418`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/ArchitectureTest.kt`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/ui/edit/EditViewModelTest.kt:57-61`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/platform/AndroidPhotoStorageTest.kt`

**Interfaces:**
- Produces: `PhotoStorage.importImage(bytes: ByteArray): String` (suspend); `val LocalPhotoStorage: ProvidableCompositionLocal<PhotoStorage>`.
- Consumes: `EntryLimits`? нет. Использует `DeleteEntryUseCase`? нет.

- [ ] **Step 1: Усилить архитектурный тест (сделать RED)**

В `app/src/test/kotlin/ru/kaufd/bodega/ArchitectureTest.kt` заменить тело теста `` `направление зависимостей ui - domain - data` `` на полный граф слоёв:

```kotlin
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
```

- [ ] **Step 2: Запустить арх-тест — убедиться, что падает на `ui → platform`**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.ArchitectureTest"`
Expected: FAIL — `Ui` зависит от `Platform` (импорт `AndroidPhotoStorage` в `EditViewModel`).

- [ ] **Step 3: Расширить доменный порт `PhotoStorage`**

`domain/PhotoStorage.kt`:

```kotlin
package ru.kaufd.bodega.domain

interface PhotoStorage {
    suspend fun delete(relativePath: String)
    /** Абсолютный путь для загрузчика изображений. */
    fun absolutePath(relativePath: String): String
    /** Импорт изображения из сырых байтов (сжатие/масштаб). Возвращает относительный путь. */
    suspend fun importImage(bytes: ByteArray): String
}
```

- [ ] **Step 4: Реализовать в `AndroidPhotoStorage`, убрать `contentResolver`/`importFromUri`**

`platform/AndroidPhotoStorage.kt` целиком:

```kotlin
package ru.kaufd.bodega.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.PhotoStorage
import java.io.File
import java.util.UUID
import kotlin.math.max

class AndroidPhotoStorage(
    private val filesDir: File,
) : PhotoStorage {

    override fun absolutePath(relativePath: String): String =
        File(filesDir, relativePath).absolutePath

    override suspend fun delete(relativePath: String) {
        withContext(Dispatchers.IO) { File(filesDir, relativePath).delete() }
    }

    /** Декодирует байты, масштабирует до 1600px и сохраняет JPEG во внутреннее хранилище. */
    override suspend fun importImage(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Не удалось прочитать изображение")
        val maxSide = max(original.width, original.height)
        val scaled = if (maxSide > 1600) {
            val k = 1600f / maxSide
            original.scale((original.width * k).toInt(), (original.height * k).toInt()).also { original.recycle() }
        } else original
        val rel = "photos/${UUID.randomUUID()}.jpg"
        val target = File(filesDir, rel).apply { parentFile?.mkdirs() }
        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        rel
    }
}
```

- [ ] **Step 5: Обновить провайдер в `AppModule`**

`di/AppModule.kt:51-53` заменить на:

```kotlin
    @Provides @Singleton
    fun photoStorage(@ApplicationContext context: Context): AndroidPhotoStorage =
        AndroidPhotoStorage(context.filesDir)
```

- [ ] **Step 6: Создать `LocalPhotoStorage`**

`ui/LocalPhotoStorage.kt`:

```kotlin
package ru.kaufd.bodega.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ru.kaufd.bodega.domain.PhotoStorage

/** Доступ к [PhotoStorage] из Compose без знания о раскладке файлов. Провайдится в MainActivity. */
val LocalPhotoStorage = staticCompositionLocalOf<PhotoStorage> {
    error("LocalPhotoStorage не предоставлен")
}
```

- [ ] **Step 7: Провайдить порт в `MainActivity`**

`MainActivity.kt` — добавить инъекцию и провайдер (сохранить существующие импорты, добавить недостающие):

```kotlin
    @Inject lateinit var photoStorage: PhotoStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalPhotoStorage provides photoStorage) {
                BodegaTheme { BodegaNavHost() }
            }
        }
    }
```

Импорты: `import javax.inject.Inject`, `import androidx.compose.runtime.CompositionLocalProvider`, `import ru.kaufd.bodega.domain.PhotoStorage`, `import ru.kaufd.bodega.ui.LocalPhotoStorage`.

- [ ] **Step 8: Очистить `EditViewModel` от `platform`/`Uri`**

`ui/edit/EditViewModel.kt`:
- удалить `import android.net.Uri` и `import ru.kaufd.bodega.platform.AndroidPhotoStorage`; добавить `import ru.kaufd.bodega.domain.PhotoStorage`;
- параметр `private val photoStorage: AndroidPhotoStorage` → `private val photoStorage: PhotoStorage`;
- `onPhotoPicked`:

```kotlin
    fun onPhotoPicked(bytes: ByteArray) {
        viewModelScope.launch {
            val rel = photoStorage.importImage(bytes)
            onField { it.copy(photoPath = rel) }
        }
    }
```

- [ ] **Step 9: Обновить `EditScreen` — чтение байтов и показ через порт**

`ui/edit/EditScreen.kt`:
- добавить `val scope = rememberCoroutineScope()` в `EditScreen` (рядом с `context`);
- launchers `photoPicker` и `cameraLauncher` — вместо `uri?.let(viewModel::onPhotoPicked)` читать байты:

```kotlin
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { u -> scope.launch { readImageBytes(context, u)?.let(viewModel::onPhotoPicked) } } }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { u -> scope.launch { readImageBytes(context, u)?.let(viewModel::onPhotoPicked) } } }
```

- добавить приватный хелпер рядом с `createCameraUri`:

```kotlin
/** Читает байты изображения из content-Uri (IO-поток) для передачи в PhotoStorage. */
private suspend fun readImageBytes(context: Context, uri: Uri): ByteArray? =
    withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
```

- в `PhotoBox` (строки 317-337) заменить резолв пути:

```kotlin
@Composable
private fun PhotoBox(photoPath: String?, onPick: () -> Unit) {
    val photoStorage = LocalPhotoStorage.current
    val abs = remember(photoPath) { photoPath?.let { photoStorage.absolutePath(it) } }
    // ... остальное без изменений
```

Импорты: `import androidx.compose.runtime.rememberCoroutineScope`, `import kotlinx.coroutines.Dispatchers`, `import kotlinx.coroutines.launch`, `import kotlinx.coroutines.withContext`, `import ru.kaufd.bodega.ui.LocalPhotoStorage`. Удалить `import java.io.File`, если он больше нигде не нужен (проверить: `createCameraUri` использует `File(context.cacheDir, ...)` — значит `File` остаётся, импорт не удалять).

- [ ] **Step 10: Обновить показ фото в `DetailScreen` и `ListScreen`**

`ui/detail/DetailScreen.kt:149` — заменить `e.photoPath?.let { java.io.File(ctx.filesDir, it).absolutePath }` на `e.photoPath?.let { LocalPhotoStorage.current.absolutePath(it) }`.

`ui/list/ListScreen.kt:418` — заменить `entry.photoPath?.let { java.io.File(ctx.filesDir, it).absolutePath }` на `entry.photoPath?.let { LocalPhotoStorage.current.absolutePath(it) }`.

В обоих файлах добавить `import ru.kaufd.bodega.ui.LocalPhotoStorage`. Убедиться, что `LocalPhotoStorage.current` вызывается в `@Composable`-контексте (обе строки внутри composable-функций).

- [ ] **Step 11: Обновить тесты под новые сигнатуры**

`EditViewModelTest.kt` — в `vm()` (строки 57-61) конструктор хранилища без `contentResolver`:

```kotlin
        photoStorage = ru.kaufd.bodega.platform.AndroidPhotoStorage(tmp.root),
```

`AndroidPhotoStorageTest.kt` — обе конструкции `AndroidPhotoStorage(tmp.root, contentResolver = null)` заменить на `AndroidPhotoStorage(tmp.root)`.

- [ ] **Step 12: Запустить арх-тест — теперь GREEN**

Run: `./gradlew testDebugUnitTest --tests "ru.kaufd.bodega.ArchitectureTest"`
Expected: PASS — `ui` больше не зависит от `platform`.

- [ ] **Step 13: Прогнать весь тест-сет и сборку**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 14: Commit**

```bash
git add -A
git commit -m "refactor: доступ к фото через PhotoStorage-порт, устранена протечка ui→platform; арх-тест покрывает platform/di"
```

---

## Self-Review

**Спецификация → задачи (покрытие ревью):**
- Протечка `EditViewModel → AndroidPhotoStorage`/`Uri` → Task 6 (Step 8).
- Три экрана с `filesDir`, неиспользуемый `absolutePath()` → Task 6 (Steps 9-10).
- Баг осиротевшего фото через Detail → Task 5 (Steps 3, 5).
- `PendingDeleteHolder` раскрывает `MutableStateFlow` → Task 5 (инкапсуляция в use case, Step 7).
- Ложноположительный арх-тест (нет `platform`/`di`) → Task 6 (Step 1).
- Мёртвый `observeUsedTypes` → Task 4. Мёртвый `ListViewModel.onDelete` → Task 5 (Step 6).
- Мелкий DRY: `Category`-parse → Task 2; тройной sort → Task 3; константы `1..10`/`0..96` → Task 1.
- `@Serializable` на `Entry` — **сознательно не трогаем**: по дизайн-спеке это задел под экспорт (отложенная фича), не дефект.

**Проверка типов/имён:** `DeleteEntryUseCase.{delete,undo,expire,pending}` согласованы между Task 5 impl и вызовами в `DetailViewModel`/`ListViewModel`/тестах. `PhotoStorage.importImage(ByteArray)` согласован между портом, `AndroidPhotoStorage`, `EditViewModel`, `EditScreen`. `EntryLimits.{RATING,ABV}` — единый тип во всех местах.

**Плейсхолдеры:** отсутствуют — весь код и команды приведены целиком.
