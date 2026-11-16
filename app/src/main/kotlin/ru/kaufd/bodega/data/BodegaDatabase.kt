package ru.kaufd.bodega.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Версия 6: у коктейлей появились поля ингредиентов и приготовления.
 * Версия 5: у вина появились колонки `bottlingYear` и `servingTemp`.
 *
 * Номер поднимается при любом изменении схемы. Под номером 2 уже успели существовать
 * две разные схемы (identityHash e6df… и 3e4f…): при совпадающем номере Room падает
 * на валидации identityHash, и destructive fallback не спасает — он завязан именно на
 * смену версии.
 *
 * Версия 5 — базовая линия первого релиза, миграций до неё нет и не будет. Схемы 2–4
 * успели пожить только на машинах разработки: `versionCode` первого APK равен 1, любая
 * установка из Play создаёт сразу v5. Писать 2→3→4→5 значило бы писать и тестировать
 * код для нулевого числа устройств; своя БД чинится реэкспортом бэкапа.
 *
 * Начиная с v5 правило обратное: каждое изменение схемы = +1 к version И своя Migration,
 * зарегистрированная в `ProvidesModule.database`. Destructive fallback снят, так что
 * пропущенная миграция роняет приложение на старте.
 */
@Database(entities = [EntryEntity::class], version = 6, exportSchema = true)
abstract class BodegaDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
}
