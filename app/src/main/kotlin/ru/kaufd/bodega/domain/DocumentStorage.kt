package ru.kaufd.bodega.domain

import java.io.InputStream
import java.io.OutputStream

/**
 * Файл, выбранный пользователем в системном диалоге (SAF).
 *
 * Локатор непрозрачен для домена — как [PhotoSource]: за ним стоит `content://`-URI,
 * но знать об этом позволено только платформенному слою. Открытие потоков живёт
 * здесь, а не в экране, чтобы UI не занимался вводом-выводом (см. ArchitectureTest).
 */
interface DocumentStorage {
    /** @throws java.io.IOException если документ недоступен. */
    suspend fun read(locator: String): InputStream

    /** @throws java.io.IOException если в документ нельзя писать. */
    suspend fun write(locator: String): OutputStream
}
