package ru.kaufd.bodega.domain

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** Имя манифеста внутри архива. Лежит первым — см. [ImportBackupUseCase]. */
private const val MANIFEST = "entries.json"

/**
 * Версия формата. Растёт, когда архив перестаёт читаться другой версией приложения.
 *
 * 2 — категория COCKTAIL: прежняя сборка падает на неизвестном значении enum и
 * отвергла бы архив как «повреждённый», а по номеру честно скажет, что он новее.
 * Архивы версии 1 эта сборка читает как прежде.
 */
private const val FORMAT_VERSION = 2

private val backupJson = Json { ignoreUnknownKeys = true }

/**
 * Поля без значений по умолчанию намеренно: kotlinx не пишет в JSON то, что равно
 * дефолту, и `version` пропала бы из архива — а вместе с ней и смысл проверки версии.
 */
@Serializable
data class BackupManifest(
    val version: Int,
    val exportedAt: Long,
    val entries: List<Entry>,
)

/** Файл не является архивом дневника или создан несовместимой версией. */
class BackupFormatException(message: String) : Exception(message)

data class ImportResult(val added: Int, val skipped: Int)

/**
 * Пишет журнал в zip: манифест со всеми записями и файлы их фотографий.
 *
 * Поток отдаёт вызывающий (SAF-документ, выбранный пользователем); мы его не
 * закрываем — [ZipOutputStream.use] закрывает обёртку и вместе с ней исходный поток.
 */
class ExportBackupUseCase @Inject constructor(
    private val repository: EntryRepository,
    private val photos: PhotoStorage,
) {
    suspend operator fun invoke(out: OutputStream, now: Long = System.currentTimeMillis()) {
        val entries = repository.observeEntries(EntryFilter(includeRecipes = true)).first()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            val manifest = BackupManifest(FORMAT_VERSION, exportedAt = now, entries = entries)
            zip.write(backupJson.encodeToString(manifest).toByteArray())
            zip.closeEntry()

            for (path in entries.flatMap { it.photos }.distinct()) {
                // Пропавший файл (сирота наоборот: запись есть, снимка нет) не повод
                // ронять весь экспорт — запись уедет в архив без этой фотографии.
                val source = photos.open(path) ?: continue
                zip.putNextEntry(ZipEntry(path))
                source.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

/**
 * Добавляет записи из архива в журнал. Ничего не удаляет и не перезаписывает:
 * записи вставляются как новые, фотографии — под новыми именами.
 *
 * Дубли отсекаются по паре «название + время создания», а не по `id`: `id` —
 * локальный автоинкремент, на другом устройстве под тем же номером лежит другой
 * напиток. Совпадение пары означает буквально ту же запись, поэтому повторный
 * импорт одного файла ничего не добавляет.
 */
class ImportBackupUseCase @Inject constructor(
    private val repository: EntryRepository,
    private val photos: PhotoStorage,
) {
    suspend operator fun invoke(input: InputStream): ImportResult {
        val zip = ZipInputStream(input)
        val manifest = readManifest(zip)
        if (manifest.version > FORMAT_VERSION) {
            throw BackupFormatException("Архив создан более новой версией приложения")
        }

        val existing = repository.observeEntries(EntryFilter(includeRecipes = true)).first()
            .mapTo(mutableSetOf()) { it.name to it.createdAt }
        val (fresh, duplicates) = manifest.entries.partition { (it.name to it.createdAt) !in existing }

        // Распаковываем только те снимки, что нужны вставляемым записям: у пропущенных
        // дублей фотографии уже лежат в хранилище.
        val needed = fresh.flatMapTo(mutableSetOf()) { it.photos }
        val newPaths = mutableMapOf<String, String>()
        try {
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name in needed) newPaths[entry.name] = photos.store(zip)
            }
            // Пачкой в одной транзакции: иначе отказ на середине оставлял бы журнал
            // с половиной архива, а повторный импорт добавлял бы вторую половину
            // поверх — дедуп по «название + время создания» этого уже не разберёт.
            repository.insertAll(
                fresh.map { it.copy(id = 0, photos = it.photos.mapNotNull { p -> newPaths[p] }) },
            )
        } catch (e: Exception) {
            // Ни одна запись не вставлена (вставка — одна транзакция), значит
            // распакованные снимки уже никому не принадлежат. Убираем их сами:
            // иначе они пролежат на диске до уборки сирот, то есть неделю.
            withContext(NonCancellable) {
                for (path in newPaths.values) runCatching { photos.delete(path) }
            }
            throw e
        }
        return ImportResult(added = fresh.size, skipped = duplicates.size)
    }

    /**
     * Манифест обязан быть первой записью архива: иначе пришлось бы держать все
     * фотографии в памяти, не зная, какие из них понадобятся.
     * simplicity: свои архивы такие всегда; чужой zip отвергаем как чужой.
     */
    private fun readManifest(zip: ZipInputStream): BackupManifest {
        // Не-zip не «сломанный ввод-вывод», а просто чужой файл: ZipException
        // здесь означает ровно то же, что и отсутствие манифеста.
        val first = try {
            zip.nextEntry
        } catch (e: java.util.zip.ZipException) {
            throw BackupFormatException("Это не архив дневника")
        }
        if (first?.name != MANIFEST) throw BackupFormatException("Это не архив дневника")
        return try {
            backupJson.decodeFromString<BackupManifest>(zip.readManifest().decodeToString())
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("Архив повреждён: ${e.message}")
        }
    }

    /**
     * Манифест целиком в память, но не более [MANIFEST_MAX_BYTES].
     *
     * Без потолка чужой или битый файл разворачивался бы в байты, строку и разобранный
     * JSON одновременно, и процесс умирал бы от OutOfMemoryError — это Error, его не
     * ловит ни один catch вокруг импорта.
     */
    private fun ZipInputStream.readManifest(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            if (out.size() + read > MANIFEST_MAX_BYTES) {
                throw BackupFormatException("Архив повреждён: список записей больше ${MANIFEST_MAX_BYTES / (1024 * 1024)} МБ")
            }
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        /** Потолок манифеста: 8 МБ — это десятки тысяч записей, больше журнала не бывает. */
        const val MANIFEST_MAX_BYTES = 8 * 1024 * 1024
    }
}
