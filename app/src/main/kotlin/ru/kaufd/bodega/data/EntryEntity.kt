package ru.kaufd.bodega.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.CocktailIngredient

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val category: String,          // Category.name
    val name: String,
    /** JSON-массив относительных путей: ["photos/a.jpg","photos/b.jpg"]; null = фото нет. */
    val photos: String?,
    val type: String?,
    val priceRub: Int?,
    val rating: Int?,
    val abvPercent: Double?,
    val note: String?,
    val tastedAt: Long?,
    val createdAt: Long,
    val grape: String?,          // JSON-массив сортов: ["Неббиоло","Сира"]; null = нет
    val wineType: String?,       // WINE — Тихое / Игристое / Креплёное
    val color: String?,          // WINE — Красное / Белое / Розовое
    val sweetness: String?,      // WINE — шкала зависит от wineType
    val country: String?,
    val vintageYear: Int?,
    val bottlingYear: Int?,     // WINE — год розлива
    val servingTemp: String?,   // WINE — диапазон «16-18°С»
    val style: String?,
    /** COCKTAIL — JSON-массив [CocktailIngredient]; null = состава нет. */
    val ingredients: String? = null,
    val preparation: String? = null,
    /**
     * Производное поле для полнотекстового поиска: конкатенация искомых полей
     * в нижнем регистре. SQLite LIKE регистронезависим только для ASCII, поэтому
     * кириллицу сворачиваем на стороне Kotlin. Заполняется в [Entry.toEntity] и
     * [buildSearchText]; в domain-модель не попадает.
     */
    val searchText: String,
)

private val stringListSerializer = ListSerializer(String.serializer())
private val json = Json { ignoreUnknownKeys = true }
private val ingredientSerializer = ListSerializer(CocktailIngredient.serializer())

/**
 * Префикс, с которым колонку писала первая сборка с коктейлями: тогда рядом жил ещё
 * и свободный текст. Строки с ним остались только на машинах разработки, но читать
 * их дешевле, чем объяснять пропавший состав.
 */
private const val LEGACY_INGREDIENTS_PREFIX = "@cocktail-ingredients:"

/** Пустые строки формы — не ингредиенты: в базу уходят только заполненные. */
private fun encodeIngredients(items: List<CocktailIngredient>): String? =
    items.filterNot { it.name.isBlank() && it.amount.isBlank() }
        .takeIf { it.isNotEmpty() }?.let { json.encodeToString(ingredientSerializer, it) }

/** Состав для показа: пустая или битая колонка — рецепт без ингредиентов. */
private fun decodeIngredients(raw: String?): List<CocktailIngredient> =
    raw?.let { runCatching { json.decodeFromString(ingredientSerializer, it.removePrefix(LEGACY_INGREDIENTS_PREFIX)) }.getOrNull() }
        .orEmpty()

/**
 * Кодирует список строк в JSON для колонок [EntryEntity.grape] и [EntryEntity.photos]:
 * сорта винограда и пути фотографий хранятся одинаково. Пустой список → null.
 */
internal fun encodeStrings(values: List<String>): String? =
    values.takeIf { it.isNotEmpty() }?.let { json.encodeToString(stringListSerializer, it) }

/**
 * Декодирует JSON-строку, записанную [encodeStrings]: пустая колонка → пустой список,
 * битое значение → null.
 *
 * Единственная точка разбора; «пусто» и «не прочиталось» здесь различимы, а как на
 * это реагировать — решает вызывающий. Для показа записи битую колонку разумно
 * считать пустой ([decodeStrings]), а вот решать по ней судьбу файлов на диске —
 * нет (см. `EntryRepositoryImpl.usedPhotoPaths`).
 */
internal fun decodeStringsOrNull(raw: String?): List<String>? =
    if (raw == null) emptyList()
    else runCatching { json.decodeFromString(stringListSerializer, raw) }.getOrNull()

/** Декодирует JSON-строку, записанную [encodeStrings]. null/битое → пустой список. */
internal fun decodeStrings(raw: String?): List<String> = decodeStringsOrNull(raw) ?: emptyList()

/**
 * Нижнерегистровая конкатенация искомых полей для поиска по [EntryEntity.searchText].
 *
 * Принимает запись целиком, а не восемь строк подряд: перепутать местами `style`
 * и `country` в таком списке можно без единой ошибки компиляции.
 */
fun buildSearchText(entry: Entry): String = with(entry) {
    (listOf(name) + listOfNotNull(type, wineType, color, sweetness, style, country, ingredientsText.ifEmpty { null }) + grapes)
        .joinToString(" ")
        .lowercase()
}

fun EntryEntity.toDomain() = Entry(
    id = id, category = Category.valueOf(category), name = name, photos = decodeStrings(photos),
    type = type, priceRub = priceRub, rating = rating, abvPercent = abvPercent,
    note = note, tastedAt = tastedAt, createdAt = createdAt,
    grapes = decodeStrings(grape), wineType = wineType, color = color, sweetness = sweetness,
    country = country, vintageYear = vintageYear,
    bottlingYear = bottlingYear, servingTemp = servingTemp,
    style = style, ingredientItems = decodeIngredients(ingredients), preparation = preparation,
)

fun Entry.toEntity() = EntryEntity(
    id = id, category = category.name, name = name, photos = encodeStrings(photos),
    type = type, priceRub = priceRub, rating = rating, abvPercent = abvPercent,
    note = note, tastedAt = tastedAt, createdAt = createdAt,
    grape = encodeStrings(grapes), wineType = wineType, color = color, sweetness = sweetness,
    country = country, vintageYear = vintageYear,
    bottlingYear = bottlingYear, servingTemp = servingTemp,
    style = style, ingredients = encodeIngredients(ingredientItems), preparation = preparation,
    searchText = buildSearchText(this),
)
