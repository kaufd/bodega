package ru.kaufd.bodega.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.buildPostText
import java.io.File

/**
 * Отдаёт запись наружу системным шэрингом: фотографии как вложения, готовый
 * текст поста как `EXTRA_TEXT`. Клиент ВК открывает свой редактор поста, куда
 * доезжает и то и другое; публикует человек сам.
 *
 * Фотографии лежат во внутреннем `files/photos/` и наружу видны только через
 * общий FileProvider приложения (см. `file_paths.xml`, `files-path photos/`).
 */
fun shareEntry(
    context: Context,
    entry: Entry,
    resolvePhotoPath: (String) -> String,
    chooserTitle: String,
) {
    context.startActivity(
        Intent.createChooser(buildShareIntent(context, entry, resolvePhotoPath), chooserTitle),
    )
}

/**
 * Собирает интент отдельно от отправки — чтобы проверять тестом.
 *
 * Без фотографий это `ACTION_SEND` с `text/plain`: `ACTION_SEND_MULTIPLE` с
 * пустым списком вложений принимающие приложения понимают по-разному, вплоть
 * до пустого экрана вместо редактора.
 */
internal fun buildShareIntent(
    context: Context,
    entry: Entry,
    resolvePhotoPath: (String) -> String,
): Intent {
    val uris = ArrayList(
        entry.photos.map { path ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(resolvePhotoPath(path)),
            )
        },
    )
    val intent = if (uris.isEmpty()) {
        Intent(Intent.ACTION_SEND).setType("text/plain")
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    return intent
        .putExtra(Intent.EXTRA_TEXT, buildPostText(entry))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
