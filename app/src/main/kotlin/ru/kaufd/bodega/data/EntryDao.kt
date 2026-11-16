package ru.kaufd.bodega.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Проекция «использованный тип + его категория» для сгруппированных фильтров. */
data class TypeWithCategory(
    val category: String,   // Category.name
    val type: String,
)

@Dao
interface EntryDao {

    /**
     * Текстовый поиск перенесён из SQL в репозиторий (токенизированный матчинг
     * по `searchText`). SQL фильтрует только по категории, фасетам, цене, рейтингу
     * и сортирует как прежде.
     *
     * Без категории рецепты (`Category.isRecipe` — сейчас только COCKTAIL) не
     * выбираются, пока не попросили [includeRecipes]: «Все» — это все напитки.
     */
    @Query(
        """
        SELECT * FROM entries WHERE
            (category = :category OR (:category IS NULL AND (:includeRecipes = 1 OR category != 'COCKTAIL')))
        AND (:typesEmpty = 1 OR type IN (:types))
        AND (:stylesEmpty = 1 OR style IN (:styles))
        AND (:colorsEmpty = 1 OR color IN (:colors))
        AND (:wineTypesEmpty = 1 OR wineType IN (:wineTypes))
        AND (:sweetnessEmpty = 1 OR sweetness IN (:sweetness))
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
        includeRecipes: Boolean,
        typesEmpty: Boolean,
        types: List<String>,
        stylesEmpty: Boolean,
        styles: List<String>,
        colorsEmpty: Boolean,
        colors: List<String>,
        wineTypesEmpty: Boolean,
        wineTypes: List<String>,
        sweetnessEmpty: Boolean,
        sweetness: List<String>,
        countriesEmpty: Boolean,
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

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<EntryEntity?>

    /**
     * Сырые JSON-массивы путей по всем записям с фотографиями; разворачивает их
     * репозиторий. Разбор в Kotlin, а не `json_each` в SQL: JSON пишем мы сами,
     * декодер уже есть, а зовётся это раз за запуск — из `OrphanPhotoSweeper`.
     */
    @Query("SELECT photos FROM entries WHERE photos IS NOT NULL")
    suspend fun usedPhotoJson(): List<String>

    @Upsert
    suspend fun upsert(entity: EntryEntity): Long

    @Insert
    suspend fun insert(entity: EntryEntity)

    /** Пачка целиком: Room выполняет multi-insert одной транзакцией. */
    @Insert
    suspend fun insertAll(entities: List<EntryEntity>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Атомарно читает и удаляет строку. Снимок для undo обязан приходить из БД, а не
     * из UI: экран мог прочитать запись до правки, и восстановление вернуло бы
     * устаревшие поля и путь фотографии.
     *
     * @return удалённая строка или null, если её уже не было.
     */
    @Transaction
    suspend fun takeById(id: Long): EntryEntity? = getById(id)?.also { deleteById(id) }

    /** Уникальные пары (категория, тип); при :category != NULL — только эта категория. */
    @Query(
        """SELECT DISTINCT category, type FROM entries
           WHERE type IS NOT NULL AND (:category IS NULL OR category = :category)
           ORDER BY category, type"""
    )
    fun observeUsedTypesGrouped(category: String?): Flow<List<TypeWithCategory>>

    @Query("SELECT DISTINCT country FROM entries WHERE country IS NOT NULL ORDER BY country")
    fun observeUsedCountries(): Flow<List<String>>

    // --- Использованные значения классификации вина (для секций фильтра) ---

    @Query("SELECT DISTINCT color FROM entries WHERE color IS NOT NULL AND category = 'WINE'")
    fun observeUsedWineColors(): Flow<List<String>>

    @Query("SELECT DISTINCT wineType FROM entries WHERE wineType IS NOT NULL AND category = 'WINE'")
    fun observeUsedWineTypes(): Flow<List<String>>

    @Query("SELECT DISTINCT sweetness FROM entries WHERE sweetness IS NOT NULL AND category = 'WINE'")
    fun observeUsedWineSweetness(): Flow<List<String>>

    @Query("SELECT DISTINCT style FROM entries WHERE style IS NOT NULL AND category = 'BEER'")
    fun observeUsedStyles(): Flow<List<String>>

    /**
     * Примеры заметок автора для черновика: своей категории, свежие первыми.
     * `TRIM(note) != ''` — пробельные заметки образцом стиля не служат.
     */
    @Query(
        """
        SELECT note FROM entries
        WHERE category = :category AND note IS NOT NULL AND TRIM(note) != ''
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentNotes(category: String, limit: Int): List<String>
}
