# Исправления архитектурного review — дизайн

Дата: 2026-07-12  
Статус: одобрен

## Цель

Исправить находки 1–3 из review коммита `967458d`:

1. убрать полную буферизацию изображения в `ByteArray` и Android I/O из UI;
2. сделать `DeleteEntryUseCase` устойчивым к ошибкам и конкурентным операциям;
3. восстановить работоспособность Compose previews и явные зависимости stateless UI.

Обновление `docs/ARCHITECTURE.md` из находки 4 в этот scope не входит.

## 1. Потоковый импорт фото

### Контракт

В `domain` вводится opaque-тип `PhotoSource`, содержащий строковый locator источника.
Тип не знает об Android `Uri`, `ContentResolver`, файлах или потоках. `PhotoStorage`
принимает `PhotoSource` вместо `ByteArray`:

```kotlin
@JvmInline
value class PhotoSource(val locator: String)

interface PhotoStorage {
    suspend fun delete(relativePath: String)
    fun absolutePath(relativePath: String): String
    suspend fun importImage(source: PhotoSource): String
}
```

`EditScreen` преобразует результат Activity Result API через
`PhotoSource(uri.toString())` и передаёт его во ViewModel. UI не открывает поток и
не читает содержимое URI. `EditViewModel` зависит только от domain-контракта.

### Android-реализация

`AndroidPhotoStorage` снова получает `ContentResolver` через DI. Импорт выполняется
на `Dispatchers.IO`:

1. locator преобразуется в Android `Uri` только внутри platform;
2. первый поток читает лишь размеры через `BitmapFactory.Options.inJustDecodeBounds`;
3. вычисляется степенной `inSampleSize`, ограничивающий декодируемый bitmap;
4. второй поток декодирует изображение с рассчитанным sample;
5. при необходимости bitmap точно масштабируется до максимальной стороны 1600 px;
6. результат сохраняется как JPEG quality 85 во внутреннем хранилище.

Потоки открываются через небольшую private-функцию, всегда закрываются через
`use`. Полный исходный файл в памяти не материализуется.

## 2. Надёжный DeleteEntryUseCase

Все операции `delete`, `undo` и `expire` сериализуются одним coroutine `Mutex`.
Это гарантирует единственный последовательный переход pending-состояния.

Правила переходов:

- `delete(entry)` при наличии другой pending-записи сначала успешно удаляет её
  фото, затем очищает прежний pending. После этого удаляет новую запись и только
  при успехе назначает её новым pending;
- если удаление новой записи завершилось ошибкой после успешного purge прежней,
  pending остаётся пустым: восстанавливать запись с уже удалённым фото нельзя;
- `undo()` сначала успешно восстанавливает запись и лишь затем очищает pending;
- `expire()` сначала успешно удаляет фото и лишь затем очищает pending;
- при исключении restore или purge pending сохраняется для повторной попытки;
- исключения не поглощаются и продолжают передаваться вызывающему коду.

Mutex удерживается во время repository-вызова намеренно: пользовательские
операции удаления и undo редки, а строгая последовательность важнее параллелизма.

## 3. Явный resolver для stateless UI

`LocalPhotoStorage` остаётся composition-root механизмом, но читается только в
stateful entry points: `ListScreen`, `DetailScreen`, `EditScreen`.

Каждый `*ScreenContent` получает явную функцию:

```kotlin
resolvePhotoPath: (String) -> String
```

Эта функция передаётся далее в `EntryList`/`EntryRow`, `Hero` и `PhotoBox`.
Stateless content больше не читает `LocalPhotoStorage`. Production entry points
передают `LocalPhotoStorage.current::absolutePath`, а previews — безопасный
identity resolver `{ it }`. Ошибочный default `LocalPhotoStorage` сохраняется,
чтобы отсутствие production provider не маскировалось.

## 4. Проверка и тесты

Изменения выполняются test-first.

### PhotoStorage

- ViewModel-тест подтверждает передачу `PhotoSource` и сохранение возвращённого
  относительного пути;
- Robolectric-тест `AndroidPhotoStorage` импортирует изображение через URI locator,
  проверяет создание JPEG и ограничение максимальной стороны;
- архитектурная проверка подтверждает отсутствие `ContentResolver`,
  `openInputStream` и `readBytes()` в `ui`.

### DeleteEntryUseCase

- ошибка `restore` сохраняет pending;
- ошибка `purgePhoto` в `expire` сохраняет pending;
- ошибка нового delete после успешного purge прежней записи оставляет pending
  пустым;
- конкурентный `undo` ждёт незавершённый `delete`, после чего восстанавливает
  только что удалённую запись;
- существующие success-path тесты остаются зелёными.

### Previews

- архитектурный тест запрещает обращение к `LocalPhotoStorage` из функций
  `ListScreenContent`, `DetailScreenContent` и `EditScreenContent`;
- все preview-вызовы явно передают resolver;
- production и test компиляция подтверждают согласованность новых сигнатур.

Финальная проверка:

```text
./gradlew test --rerun-tasks
./gradlew lint
```

Lint-ошибки, существовавшие до этого scope, фиксируются отдельно и не маскируются
baseline-файлом.

## Не входит в scope

- обновление общей архитектурной документации из находки 4;
- персистентный undo после смерти процесса;
- очистка временных camera-файлов и фото при отмене редактирования;
- изменение UX ошибок импорта или сохранения;
- разбиение `:app` на Gradle-модули.
