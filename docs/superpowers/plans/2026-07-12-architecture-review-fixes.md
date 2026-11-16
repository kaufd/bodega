# Architecture Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Исправить находки 1–3 review коммита `967458d`: потоково импортировать фото, сделать undo отказоустойчивым и вернуть изолированные Compose previews.

**Architecture:** UI передаёт opaque `PhotoSource`, а Android `ContentResolver` и декодирование остаются в `platform`. `DeleteEntryUseCase` сериализует переходы через `Mutex` и изменяет pending только после успешных side effects. Stateless `*ScreenContent` получает явный `(String) -> String` resolver вместо скрытого чтения `CompositionLocal`.

**Tech Stack:** Kotlin, coroutines, Jetpack Compose, Hilt, Robolectric, JUnit 4, Konsist, Gradle.

---

### Task 1: Потоковый PhotoSource и platform-декодирование

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/domain/PhotoStorage.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/platform/AndroidPhotoStorage.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/di/AppModule.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditScreen.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditViewModel.kt`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/ArchitectureTest.kt`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/platform/AndroidPhotoStorageTest.kt`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/ui/edit/EditViewModelTest.kt`
- Modify: fake `PhotoStorage` implementations found by `rg 'PhotoStorage' app/src/test`

- [ ] **Step 1: Add failing architecture and contract tests**

Add to `ArchitectureTest.kt`:

```kotlin
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
```

Replace the concrete storage in `EditViewModelTest.kt` with a fake that records a
`PhotoSource`, then add:

```kotlin
private class FakePhotoStorage : PhotoStorage {
    val imported = mutableListOf<PhotoSource>()
    override suspend fun delete(relativePath: String) = Unit
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
    override suspend fun importImage(source: PhotoSource): String {
        imported += source
        return "photos/imported.jpg"
    }
}

private fun vm(
    id: Long? = null,
    photoStorage: PhotoStorage = FakePhotoStorage(),
) = EditViewModel(
    savedState = SavedStateHandle(mapOf("id" to id?.toString())),
    repository = repo,
    photoStorage = photoStorage,
)

@Test
fun `выбранный PhotoSource импортируется через порт`() = runTest {
    val photos = FakePhotoStorage()
    val vm = vm(photoStorage = photos)
    val source = PhotoSource("content://picker/photo")

    vm.onPhotoPicked(source)
    dispatcher.scheduler.runCurrent()

    assertEquals(listOf(source), photos.imported)
    assertEquals("photos/imported.jpg", vm.state.value.entry.photoPath)
}
```

Convert `AndroidPhotoStorageTest` to Robolectric and add an import test:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPhotoStorageTest {
    @get:Rule val tmp = TemporaryFolder()

@Test
fun `importImage читает locator потоково и ограничивает сторону 1600`() = runTest {
    val sourceFile = File(tmp.root, "source.png")
    Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
        sourceFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = AndroidPhotoStorage(tmp.root, context.contentResolver)

    val relative = storage.importImage(PhotoSource(Uri.fromFile(sourceFile).toString()))
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(File(tmp.root, relative).absolutePath, options)

    assertTrue(File(tmp.root, relative).isFile)
    assertTrue(maxOf(options.outWidth, options.outHeight) <= 1600)
}

private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests 'ru.kaufd.bodega.ArchitectureTest' \
  --tests 'ru.kaufd.bodega.ui.edit.EditViewModelTest' \
  --tests 'ru.kaufd.bodega.platform.AndroidPhotoStorageTest'
```

Expected: FAIL because UI still contains `readBytes`, `PhotoSource` is absent, and
`PhotoStorage.importImage` still accepts `ByteArray`.

- [ ] **Step 3: Implement the minimal opaque contract**

Change `PhotoStorage.kt` to:

```kotlin
package ru.kaufd.bodega.domain

@JvmInline
value class PhotoSource(val locator: String)

interface PhotoStorage {
    suspend fun delete(relativePath: String)
    fun absolutePath(relativePath: String): String
    suspend fun importImage(source: PhotoSource): String
}
```

Change `EditViewModel.onPhotoPicked` to accept `PhotoSource`. In `EditScreen`, remove
`readImageBytes`, its coroutine scope and IO imports; both Activity Result callbacks
must call:

```kotlin
uri?.let { viewModel.onPhotoPicked(PhotoSource(it.toString())) }
```

Update every test fake to implement `importImage(source: PhotoSource)`.

- [ ] **Step 4: Implement bounded Android decoding**

Inject `ContentResolver` into `AndroidPhotoStorage` and implement two-pass decode:

```kotlin
class AndroidPhotoStorage(
    private val filesDir: File,
    private val contentResolver: ContentResolver,
) : PhotoStorage {
    override suspend fun importImage(source: PhotoSource): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(source.locator)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось прочитать изображение" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val original = open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Не удалось прочитать изображение")
        val maxSide = max(original.width, original.height)
        val scaled = if (maxSide > MAX_SIDE) {
            val ratio = MAX_SIDE.toFloat() / maxSide
            original.scale(
                (original.width * ratio).toInt(),
                (original.height * ratio).toInt(),
            ).also { original.recycle() }
        } else original

        val relative = "photos/${UUID.randomUUID()}.jpg"
        val target = File(filesDir, relative).apply { parentFile?.mkdirs() }
        try {
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } finally {
            scaled.recycle()
        }
        relative
    }

    private fun open(uri: Uri) =
        requireNotNull(contentResolver.openInputStream(uri)) { "Не удалось открыть изображение" }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    private companion object { const val MAX_SIDE = 1600 }
}
```

Update `AppModule.photoStorage` to pass `context.contentResolver`.

- [ ] **Step 5: Verify GREEN**

Run the focused command from Step 2. Expected: PASS for all three test classes.

- [ ] **Step 6: Commit Task 1**

```bash
git add app/src/main app/src/test
git commit -m "fix: stream photo imports through platform port"
```

---

### Task 2: Отказоустойчивые и последовательные delete/undo переходы

**Files:**
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/domain/DeleteEntryUseCase.kt`
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/domain/DeleteEntryUseCaseTest.kt`

- [ ] **Step 1: Add failing failure-path tests**

Extend the fake repository with `restoreFailure`, `purgeFailure`, `deleteFailureId`
and a suspend `beforeDelete` hook. Add tests:

```kotlin
var restoreFailure: Throwable? = null
var purgeFailure: Throwable? = null
var deleteFailureId: Long? = null
var beforeDelete: suspend (Entry) -> Unit = {}

override suspend fun delete(entry: Entry) {
    beforeDelete(entry)
    if (entry.id == deleteFailureId) error("delete failed")
    entries.value = entries.value - entry
}

override suspend fun restore(entry: Entry) {
    restoreFailure?.let { throw it }
    restored += entry
    entries.value = entries.value + entry
}

override suspend fun purgePhoto(photoPath: String?) {
    purgeFailure?.let { throw it }
    purged += photoPath
}

@Test
fun `ошибка restore сохраняет pending для повторного undo`() = runTest {
    val repo = FakeRepo()
    val uc = DeleteEntryUseCase(repo)
    uc.delete(a)
    repo.restoreFailure = IllegalStateException("restore failed")

    assertFailsWith<IllegalStateException> { uc.undo() }

    assertEquals(a, uc.pending.value)
}

@Test
fun `ошибка expire сохраняет pending для повторной очистки`() = runTest {
    val repo = FakeRepo()
    val uc = DeleteEntryUseCase(repo)
    uc.delete(a)
    repo.purgeFailure = IllegalStateException("purge failed")

    assertFailsWith<IllegalStateException> { uc.expire() }

    assertEquals(a, uc.pending.value)
}

@Test
fun `ошибка нового delete после purge не возвращает недействительный pending`() = runTest {
    val repo = FakeRepo()
    val uc = DeleteEntryUseCase(repo)
    uc.delete(a)
    repo.deleteFailureId = b.id

    assertFailsWith<IllegalStateException> { uc.delete(b) }

    assertEquals(listOf("photos/a.jpg"), repo.purged)
    assertNull(uc.pending.value)
}
```

Add a concurrency regression:

```kotlin
@Test
fun `undo ждёт незавершённый delete`() = runTest {
    val repo = FakeRepo()
    val deleteStarted = CompletableDeferred<Unit>()
    val continueDelete = CompletableDeferred<Unit>()
    repo.beforeDelete = {
        deleteStarted.complete(Unit)
        continueDelete.await()
    }
    val uc = DeleteEntryUseCase(repo)

    val deleteJob = launch { uc.delete(a) }
    deleteStarted.await()
    val undoJob = launch { uc.undo() }
    runCurrent()
    continueDelete.complete(Unit)
    deleteJob.join()
    undoJob.join()

    assertEquals(listOf(a), repo.restored)
    assertNull(uc.pending.value)
}
```

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests 'ru.kaufd.bodega.domain.DeleteEntryUseCaseTest'
```

Expected: failure-path tests show pending is cleared too early; the concurrency test
shows undo returns before the in-flight delete publishes pending.

- [ ] **Step 3: Implement serialized state transitions**

Add `private val mutex = Mutex()` and wrap each public operation in `withLock`:

```kotlin
suspend fun delete(entry: Entry) = mutex.withLock {
    val previous = _pending.value
    if (previous != null && previous.id != entry.id) {
        repository.purgePhoto(previous.photoPath)
        _pending.value = null
    }
    repository.delete(entry)
    _pending.value = entry
}

suspend fun undo() = mutex.withLock {
    val entry = _pending.value ?: return@withLock
    repository.restore(entry)
    _pending.value = null
}

suspend fun expire() = mutex.withLock {
    val entry = _pending.value ?: return@withLock
    repository.purgePhoto(entry.photoPath)
    _pending.value = null
}
```

- [ ] **Step 4: Verify GREEN**

Run the focused command from Step 2. Expected: all success, failure and concurrency
tests PASS.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/kotlin/ru/kaufd/bodega/domain/DeleteEntryUseCase.kt \
  app/src/test/kotlin/ru/kaufd/bodega/domain/DeleteEntryUseCaseTest.kt
git commit -m "fix: make delete undo transitions failure safe"
```

---

### Task 3: Явные photo resolvers для stateless Compose content

**Files:**
- Modify: `app/src/test/kotlin/ru/kaufd/bodega/ArchitectureTest.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/list/ListScreen.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/detail/DetailScreen.kt`
- Modify: `app/src/main/kotlin/ru/kaufd/bodega/ui/edit/EditScreen.kt`

- [ ] **Step 1: Add a failing Konsist rule**

Add:

```kotlin
@Test
fun `LocalPhotoStorage читают только stateful screen entry points`() {
    val allowed = setOf("ListScreen", "DetailScreen", "EditScreen")
    Konsist.scopeFromProduction()
        .functions()
        .filter { it.text.contains("LocalPhotoStorage") }
        .assertTrue { it.name in allowed }
}
```

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests 'ru.kaufd.bodega.ArchitectureTest.LocalPhotoStorage*'
```

If Gradle cannot select the Unicode method reliably, run the whole class. Expected:
FAIL naming `EntryRow`, `Hero` and `PhotoBox` as forbidden readers.

- [ ] **Step 3: Pass resolvers explicitly**

In each stateful screen, read the local once:

```kotlin
val photoStorage = LocalPhotoStorage.current
```

Add this required parameter to each `*ScreenContent`:

```kotlin
resolvePhotoPath: (String) -> String
```

Pass `{ photoStorage.absolutePath(it) }` from the stateful screen. Thread the resolver
through `EntryList`/`EntryRow`, `Hero`, and `PhotoBox`. Resolve without hidden state:

```kotlin
val absolutePath = photoPath?.let(resolvePhotoPath)
```

Every preview call must pass:

```kotlin
resolvePhotoPath = { it }
```

- [ ] **Step 4: Verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests 'ru.kaufd.bodega.ArchitectureTest'
./gradlew compileDebugKotlin compileDebugUnitTestKotlin
```

Expected: architecture tests and both compilation tasks PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add app/src/main/kotlin/ru/kaufd/bodega/ui \
  app/src/test/kotlin/ru/kaufd/bodega/ArchitectureTest.kt
git commit -m "fix: make photo resolution explicit in compose content"
```

---

### Task 4: Full verification

**Files:**
- Verify only; do not create a lint baseline or modify out-of-scope lint findings.

- [ ] **Step 1: Run all unit and architecture tests from scratch**

```bash
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, including `ArchitectureTest`,
`AndroidPhotoStorageTest`, `EditViewModelTest` and `DeleteEntryUseCaseTest`.

- [ ] **Step 2: Run Android lint and classify output**

```bash
./gradlew lint
```

Expected current repository status: lint reports the two pre-existing
`LocalContextGetResourceValueCall` errors in `ListScreen.kt:93-94`. No new error may
point to a file or line changed by this plan.

- [ ] **Step 3: Confirm repository state and review scope**

```bash
git status --short
git log --oneline -5
git diff 967458d..HEAD --stat
```

Expected: clean worktree; commits for design, plan and three implementation tasks;
diff limited to findings 1–3 plus their tests and planning documents.
