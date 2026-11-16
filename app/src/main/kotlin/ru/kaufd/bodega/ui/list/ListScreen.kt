package ru.kaufd.bodega.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.SortOrder
import ru.kaufd.bodega.ui.recognitionMessage
import ru.kaufd.bodega.ui.rememberPhotoPathResolver
import ru.kaufd.bodega.ui.theme.*
import ru.kaufd.bodega.ui.util.entrySubtitle
import ru.kaufd.bodega.ui.util.formatPrice
import ru.kaufd.bodega.ui.util.greetingRes
import ru.kaufd.bodega.ui.util.rememberCameraCapture
import java.time.LocalTime

/** Небольшая обложка с панелью быстрых действий на нижнем краю. */
private val HeroHeight = 190.dp
private val PanelHeight = 70.dp

/**
 * Пути гашения и проявления для закреплённой шапки. Концы диапазонов считаются из
 * измеренной высоты шапки: момент, когда под неё заходит содержимое, зависит от
 * высоты статус-бара, и на одних устройствах наступает заметно раньше, чем на других.
 */
private val HeroFadeDistance = 90.dp
private val HeaderFillDistance = 60.dp
private val PanelFadeDistance = 60.dp

/** Панель гаснет чуть раньше, чем шапка её накроет: иначе видно наполовину срезанное поле. */
private val PanelFadeReserve = 10.dp

/** Диапазон скролла длиной [distance], заканчивающийся на [end]; отрицательный край не даём. */
private fun fadeRange(end: Float, distance: Float): ClosedRange<Float> {
    val to = end.coerceAtLeast(1f)
    return (to - distance).coerceAtLeast(0f)..to
}

/** Доля пройденного диапазона скролла первого элемента списка: 0 — не начат, 1 — пройден. */
private fun LazyListState.progress(range: ClosedRange<Float>): Float =
    if (firstVisibleItemIndex > 0) 1f
    else ((firstVisibleItemScrollOffset - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

@Composable
fun ListScreen(
    onEntryClick: (Long) -> Unit,
    onAddClick: (Category?) -> Unit,
    onFiltersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sectionCache by viewModel.sectionCache.collectAsStateWithLifecycle()
    // Значение поля берём из быстрого filter, а не из Room-зависимого state:
    // иначе введённый текст отстаёт на запрос к БД и курсор прыгает назад.
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val compact by viewModel.compactList.collectAsStateWithLifecycle()
    val resolvePhotoPath = rememberPhotoPathResolver()

    val launchScan = rememberCameraCapture { uri, onConsumed ->
        viewModel.onScanLabel(PhotoSource(uri.toString()), onFinished = onConsumed)
    }

    // Обложка занимает верх экрана — тёмные иконки статус-бара на ней не читаются.
    StatusBarIcons(darkIcons = false)

    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.error.collect { error ->
            val message = when (error) {
                is ListError.Recognition -> recognitionMessage(error.error)
                ListError.Scan -> R.string.scan_failed
                ListError.ScanEmpty -> R.string.scan_empty
                ListError.Undo -> R.string.undo_failed
            }
            snackbar.showSnackbar(resources.getString(message))
        }
    }

    ListScreenContent(
        state = state,
        sectionCache = sectionCache,
        filter = filter,
        compact = compact,
        onCompactChange = viewModel::onCompactChange,
        snackbar = snackbar,
        resolvePhotoPath = resolvePhotoPath,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelect = viewModel::onCategorySelect,
        onSectionSelect = viewModel::onSectionSelect,
        onFiltersClick = onFiltersClick,
        onSettingsClick = onSettingsClick,
        onEntryClick = onEntryClick,
        onAddClick = { onAddClick(filter.category) },
        onResetFilters = viewModel::onResetFilters,
        onClearFilters = viewModel::onClearFilters,
        onUndoDelete = viewModel::onUndoDelete,
        onUndoExpired = viewModel::onUndoExpired,
        onScanLabel = { launchScan() },
        onRetryLoad = viewModel::onRetryLoad,
    )
}

/** Stateless-версия главного экрана: пригодна для @Preview и тестов. */
@Composable
fun ListScreenContent(
    state: ListState,
    sectionCache: Map<LibrarySection, SectionListSnapshot> = emptyMap(),
    /**
     * Быстрый фильтр — не ждёт пересчёта списка в Room. Из него берутся элементы
     * управления (поле ввода, сегменты категорий, бейдж фильтров, «+»), иначе
     * они отстают на один запрос к БД. [state] описывает уже показанный список.
     */
    filter: EntryFilter,
    /** Вид коллекции: компактный список или плитка. Хранится в настройках. */
    compact: Boolean,
    onCompactChange: (Boolean) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    resolvePhotoPath: (String) -> String,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (Category?) -> Unit,
    onSectionSelect: (Boolean) -> Unit,
    onFiltersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onResetFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onUndoDelete: () -> Unit,
    onUndoExpired: () -> Unit,
    onScanLabel: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    // LocalResources, а не LocalContext: чтение ресурса через контекст не
    // пересоздаёт композицию при смене локали или конфигурации.
    val cocktails = filter.librarySection() == LibrarySection.COCKTAILS
    val shownState = sectionView(state, filter, sectionCache)
    val waitingForSectionData = shownState == null
    val displayedState = shownState ?: state.copy(entries = emptyList(), filter = filter)
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.delete_snackbar_undo)

    // Ключ включает счётчик отказов: при неудавшемся undo pending не меняется,
    // и снекбар с действием «Отменить» иначе больше не появился бы.
    LaunchedEffect(state.pendingDelete, state.undoFailures) {
        val pending = state.pendingDelete ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            // Имя записи известно только в момент показа — форматируем здесь.
            message = resources.getString(R.string.delete_snackbar, pending.name),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDelete()
        else onUndoExpired()
    }

    // Первая запись уходит в витрину, остальные — в коллекцию. Списки считаются
    // один раз на выдачу, а не на каждую рекомпозицию списка.
    val rest = remember(displayedState.entries) { displayedState.entries.drop(1) }
    val rows = remember(rest) { rest.chunked(2) }

    // Прогрессы скролла держим как State и читаем лямбдой внутри graphicsLayer:
    // обычное чтение рекомпоновало бы весь экран на каждый кадр скролла.
    // Each tab keeps its own scroll position, as two separate screens would.
    val drinksListState = rememberLazyListState()
    val recipesListState = rememberLazyListState()
    val listState = if (cocktails) recipesListState else drinksListState
    val density = LocalDensity.current
    // Высоту шапки меряем: она зависит от статус-бара и масштаба шрифта.
    var headerHeight by remember { mutableIntStateOf(0) }
    // Leave room beneath the measured header even with larger accessibility fonts.
    val heroHeight = maxOf(HeroHeight, with(density) { headerHeight.toDp() } + 80.dp * density.fontScale)
    val heroFade = remember(listState, density, heroHeight) {
        val anchor = with(density) { (heroHeight - 70.dp).toPx() }
        val distance = with(density) { HeroFadeDistance.toPx() }
        derivedStateOf { listState.progress(fadeRange(anchor - headerHeight, distance)) }
    }
    // Первой под шапку заходит панель быстрых действий: к этому моменту она прозрачна,
    // а фон шапки — наоборот, залит.
    val panelTop = with(density) { heroHeight.toPx() }
    val headerFill = remember(listState, density, panelTop) {
        val distance = with(density) { HeaderFillDistance.toPx() }
        derivedStateOf { listState.progress(fadeRange(panelTop - headerHeight, distance)) }
    }
    val panelFade = remember(listState, density, panelTop) {
        val reserve = with(density) { PanelFadeReserve.toPx() }
        val distance = with(density) { PanelFadeDistance.toPx() }
        derivedStateOf { listState.progress(fadeRange(panelTop - headerHeight - reserve, distance)) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        snackbarHost = { SnackbarHost(snackbar) { BodegaSnackbar(it) } },
        bottomBar = { SectionTabBar(cocktails, onSectionSelect) },
        floatingActionButton = {
            if (!displayedState.loadFailed && displayedState.emptyKind == null) AddButton(cocktails, onAddClick)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().ambientBackground(),
                state = listState,
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    TopBlock(
                        total = if (cocktails) state.categoryCounts[Category.COCKTAIL] ?: 0
                            else state.totalCount.coerceAtLeast(0),
                        cocktails = cocktails,
                        query = filter.query,
                        onQueryChange = onQueryChange,
                        filterCount = filter.activeFilterCount,
                        onFiltersClick = onFiltersClick,
                        onScanLabel = onScanLabel,
                        scanning = state.scanning,
                        heroHeight = heroHeight,
                        heroFade = heroFade::value,
                        panelFade = panelFade::value,
                    )
                }
                if (filter.queryFuzzy && filter.query.isNotEmpty()) item { PhotoSearchHint() }
                if (filter.activeFilterCount > 0) item { FilterResetBar(filter.activeFilterCount, onClearFilters) }
                else if (!cocktails) item {
                    CategorySegments(filter.category, state.totalCount.coerceAtLeast(0), state.categoryCounts, onCategorySelect)
                }
                when {
                    waitingForSectionData -> item {
                        Box(Modifier.fillMaxWidth().height(290.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = BodegaColors.Teal,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    // Заглушка вместо коллекции — одна коробка на все причины: содержимое
                    // в ней центрируется, и разъехаться по высоте им незачем.
                    displayedState.loadFailed || displayedState.emptyKind != null -> item {
                        Box(Modifier.fillMaxWidth().height(290.dp)) {
                            when {
                                displayedState.loadFailed -> LoadFailed(stringResource(R.string.list_load_failed), onRetryLoad)
                                displayedState.emptyKind == EmptyKind.COLLECTION -> EmptyCollection(onAddClick)
                                displayedState.emptyKind == EmptyKind.CATEGORY -> EmptyCategory(displayedState.filter.category!!, onAddClick)
                                else -> EmptySearch(displayedState.filter.query, onResetFilters)
                            }
                        }
                    }
                    cocktails && displayedState.entries.isNotEmpty() -> {
                        item { CocktailCollectionHeader(displayedState.entries.size) }
                        items(displayedState.entries, key = { "cocktail-${it.id}" }) { entry ->
                            CocktailEntryCard(entry, resolvePhotoPath) { onEntryClick(entry.id) }
                        }
                    }
                    displayedState.entries.isNotEmpty() -> {
                        item { FeaturedSection(displayedState.entries.first(), displayedState.filter.sort, onFiltersClick, onEntryClick, resolvePhotoPath) }
                        if (rest.isNotEmpty()) item { CollectionHeader(rest.size, compact, onCompactChange) }
                        // Карточки — собственные элементы списка, а не один общий item:
                        // иначе LazyColumn компонует и меряет всю коллекцию разом.
                        if (compact) {
                            items(rest, key = { "drink-${it.id}" }) { entry ->
                                CompactEntryCard(entry, resolvePhotoPath) { onEntryClick(entry.id) }
                            }
                        } else {
                            items(rows, key = { "drink-row-${it.first().id}" }) { rowEntries ->
                                GridRow(rowEntries, onEntryClick, resolvePhotoPath)
                            }
                        }
                    }
                }
            }
            StickyHeader(
                cocktails = cocktails,
                fill = headerFill::value,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.onSizeChanged { headerHeight = it.height },
            )
        }
    }
}

/**
 * Закреплённая шапка: заголовок и кнопка настроек всегда на экране. Фон проявляется
 * только когда обложка уходит вверх — иначе полоса лежала бы поверх её же градиента.
 * Сама коробка не перехватывает жесты: список под ней скроллится пальцем как обычно.
 */
@Composable
private fun StickyHeader(cocktails: Boolean, fill: () -> Float, onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = fill() }
                .background(Brush.horizontalGradient(listOf(BodegaColors.TealBright, BodegaColors.Wine))),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    stringResource(R.string.header_title),
                    fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 27.sp,
                    color = Color.White, letterSpacing = (-.7).sp,
                )
                Text(
                    stringResource(if (cocktails) R.string.header_recipes_subtitle else R.string.header_subtitle).uppercase(),
                    fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                    letterSpacing = 1.8.sp, color = Color.White.copy(.65f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val settingsLabel = stringResource(R.string.settings_open)
            // 46dp и 18dp — как у кнопок в шапках остальных экранов: при 40dp тот же
            // радиус делал её почти круглой, ни кругом, ни стандартной плашкой.
            Box(
                Modifier.size(46.dp)
                    .pressAction(onSettingsClick)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x402C0D18))
                    .border(1.dp, Color.White.copy(.4f), RoundedCornerShape(18.dp))
                    .semantics { contentDescription = settingsLabel },
                contentAlignment = Alignment.Center,
            ) { SettingsIcon(20.dp, Color.White) }
        }
    }
}

/** Обложка с панелью действий, наезжающей на её нижний край. */
@Composable
private fun TopBlock(
    total: Int,
    cocktails: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    filterCount: Int,
    onFiltersClick: () -> Unit,
    onScanLabel: () -> Unit,
    scanning: Boolean,
    heroHeight: Dp,
    heroFade: () -> Float,
    panelFade: () -> Float,
) {
    Box(Modifier.fillMaxWidth()) {
        Hero(total, cocktails, heroFade, heroHeight)
        // Recompose only at the transparency boundary. Removing the controls also
        // removes their click/focus/accessibility targets; retain their measured space.
        val controlsVisible by remember(panelFade) { derivedStateOf { panelFade() < 1f } }
        var controlsHeight by remember { mutableIntStateOf(0) }
        Column(Modifier.fillMaxWidth().padding(top = heroHeight)) {
            if (!controlsVisible) Spacer(Modifier.height(with(LocalDensity.current) { controlsHeight.toDp() }))
            else Column(Modifier.onSizeChanged { controlsHeight = it.height }) {
                SearchPanel(
                    query = query,
                    recipes = cocktails,
                    onQueryChange = onQueryChange,
                    filterCount = filterCount,
                    onFiltersClick = onFiltersClick,
                    onScanLabel = onScanLabel,
                    scanning = scanning,
                    panelFade = panelFade,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero(
    total: Int,
    cocktails: Boolean,
    fade: () -> Float,
    heroHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().height(heroHeight)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(BodegaColors.TealBright, BodegaColors.Wine, BodegaColors.Coral))),
    ) {
        // Обложка всегда одна и та же — декоративный кадр из airy-cellar.html
        // (Unsplash photo-1510812431401-41d2bd2722f3). Снимок последней записи сюда
        // не подставляется: случайное фото меняет вид главного экрана каждый раз.
        Image(
            painterResource(R.drawable.hero_cover),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Цветной вуаль поверх фото: по макету обложка всегда сине-розово-янтарная.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color(0xBD409BBE), Color(0xABE35B89), Color(0x7AFFAE5C)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        )
        // Заголовок и настройки живут в StickyHeader поверх списка — здесь их нет.
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Приветствие гаснет до того, как окажется под закреплённой шапкой.
            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - fade() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        stringResource(if (cocktails) R.string.section_recipes else greetingRes(LocalTime.now().hour)),
                        fontFamily = Rubik,
                        fontWeight = if (cocktails) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = if (cocktails) 24.sp else 13.sp,
                        color = Color.White.copy(.9f),
                    )
                    if (cocktails) Text(
                        stringResource(R.string.cocktail_hero_subtitle),
                        fontFamily = Rubik, fontSize = 11.sp,
                        color = Color.White.copy(.72f), modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    "$total " + pluralStringResource(
                        if (cocktails) R.plurals.header_recipe_count_label else R.plurals.header_count_label, total,
                    ),
                    fontFamily = PlexMono, fontSize = 11.sp, color = Color.White.copy(.82f),
                )
            }
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    recipes: Boolean,
    onQueryChange: (String) -> Unit,
    filterCount: Int,
    onFiltersClick: () -> Unit,
    onScanLabel: () -> Unit,
    scanning: Boolean,
    panelFade: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(PanelHeight)) {
        Box(
            Modifier.matchParentSize()
                .glassPanel(26)
                .graphicsLayer { alpha = 1f - panelFade() },
        )
        Row(
            Modifier.matchParentSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(query, onQueryChange, recipes, panelFade, Modifier.weight(1f))

            if (!recipes) {
                val scanLabel = stringResource(if (scanning) R.string.scan_in_progress else R.string.scan_label)
                Box(
                    Modifier.size(54.dp).graphicsLayer { alpha = 1f - panelFade() }
                        .pressAction(onScanLabel, enabled = !scanning)
                        .darkSurface(18)
                        .semantics { contentDescription = scanLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    if (scanning) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else ScanIcon(22.dp, Color.White)
                }
            }

            Box(Modifier.size(54.dp).graphicsLayer { alpha = 1f - panelFade() }) {
                // Число активных фильтров нарисовано бейджем — для TalkBack его надо проговорить.
                val filtersLabel = if (filterCount > 0) {
                    stringResource(R.string.home_filters_active, filterCount)
                } else {
                    stringResource(R.string.filters_open)
                }
                Box(
                    Modifier.matchParentSize()
                        .pressAction(onFiltersClick)
                        .accentSurface(18)
                        .semantics { contentDescription = filtersLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    SlidersIcon(size = 21.dp, lineColor = Color.White, knobFill = BodegaColors.Accent)
                }
                if (filterCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp)
                            .size(20.dp).clip(CircleShape).background(BodegaColors.Rating)
                            .border(2.dp, BodegaColors.Paper, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$filterCount", fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = BodegaColors.Ink)
                    }
                }
            }
        }
    }
}

/** Поле поиска: сплошное белое на полупрозрачной панели — иначе оно сливается с ней. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    recipes: Boolean,
    panelFade: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.height(54.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
        Row(
            Modifier.fillMaxSize().graphicsLayer { alpha = 1f - panelFade() }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            SearchIcon(size = 20.dp, color = BodegaColors.Muted)
            val searchLabel = stringResource(if (recipes) R.string.search_recipe_placeholder else R.string.search_placeholder)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        searchLabel,
                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                        color = BodegaColors.Placeholder,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = BodegaColors.Ink),
                    cursorBrush = SolidColor(BodegaColors.Teal),
                    // Плейсхолдер — отдельная нода, при фокусе TalkBack её не читает.
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
                )
            }
            if (query.isNotEmpty()) {
                val clearLabel = stringResource(R.string.search_clear)
                Box(
                    Modifier.size(TouchTargetMin)
                        .pressAction({ onQueryChange("") })
                        .padding((TouchTargetMin - 36.dp) / 2)
                        .clip(CircleShape)
                        .semantics { contentDescription = clearLabel },
                    contentAlignment = Alignment.Center,
                ) { CloseIcon(15.dp, BodegaColors.Muted, strokeWidth = 1.8f) }
            }
        }
    }
}

@Composable
private fun AddButton(cocktails: Boolean, onAddClick: () -> Unit) {
    val label = stringResource(if (cocktails) R.string.empty_category_add_cocktail else R.string.add_entry)
    Row(
        Modifier.height(56.dp)
            .pressAction(onAddClick)
            .accentSurface(18)
            .semantics { contentDescription = label }
            .padding(horizontal = 21.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        PlusIcon(21.dp, Color.White)
        Text(if (cocktails) label else stringResource(R.string.add_entry_short), fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.White)
    }
}

/** Подсказка активного режима «поиск по фото»: приглушённая строка с иконкой-камерой. */
@Composable
private fun PhotoSearchHint() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CameraIcon(size = 13.dp, color = BodegaColors.Muted)
        Text(
            stringResource(R.string.photo_search_hint),
            style = TextStyle(fontFamily = Rubik, fontSize = 11.sp, letterSpacing = 0.4.sp),
            color = BodegaColors.Muted,
        )
    }
}

/** Контейнер-«жёлоб» под панелью поиска: ровно одна позиция для сегментов или плашки фильтров. */
private fun Modifier.segmentTrough(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
    .padding(top = 12.dp)
    .glassTrack()

/** Нижняя вкладочная навигация двух самостоятельных разделов, в стиле стеклянных контролов. */
@Composable
private fun SectionTabBar(recipes: Boolean, onSelect: (Boolean) -> Unit) {
    SlidingSegmentRow(
        count = 2,
        selected = if (recipes) 1 else 0,
        indicatorShape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassTrack(RoundedCornerShape(22.dp)).padding(4.dp).selectableGroup(),
    ) {
        SectionTab(
            label = stringResource(R.string.section_drinks),
            selected = !recipes,
            onClick = { onSelect(false) },
            icon = { tint -> GridIcon(6.dp, 3.dp, tint) },
            modifier = Modifier.weight(1f),
        )
        SectionTab(
            label = stringResource(R.string.section_recipes),
            selected = recipes,
            onClick = { onSelect(true) },
            icon = { tint -> WineGlassIcon(19.dp, tint) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = segmentTint(selected)
    Column(
        modifier.heightIn(min = 48.dp)
            .pressSelect(selected, onClick, role = Role.Tab)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(tint)
        Text(label, fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = tint,
            modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun CategorySegments(
    selected: Category?,
    totalCount: Int,
    categoryCounts: Map<Category, Int>,
    onSelect: (Category?) -> Unit,
) {
    // «Все» первым, за ним категории коллекции — в том же порядке, что и сегменты.
    SlidingSegmentRow(
        count = Category.drinks.size + 1,
        selected = Category.drinks.indexOf(selected) + 1,
        indicatorShape = RoundedCornerShape(14.dp),
        modifier = Modifier.segmentTrough().padding(4.dp).selectableGroup(),
    ) {
        Segment(
            label = stringResource(R.string.all),
            active = selected == null,
            onSelect = { onSelect(null) },
            modifier = Modifier.weight(1f),
            count = totalCount,
            compact = true,
        )
        Category.drinks.forEach { category ->
            Segment(
                label = stringResource(category.displayNameRes()),
                active = selected == category,
                onSelect = { onSelect(category) },
                modifier = Modifier.weight(1f),
                count = categoryCounts[category] ?: 0,
                compact = true,
            )
        }
    }
}

/**
 * Плашка активных продвинутых фильтров: слева «Фильтры · N», справа «Сбросить ✕».
 * Заменяет сегменты ровно на одну строку, чтобы не стекать.
 */
@Composable
private fun FilterResetBar(activeFilterCount: Int, onClearFilters: () -> Unit) {
    val resetLabel = stringResource(R.string.home_filters_reset)
    Row(
        Modifier.segmentTrough()
            .pressAction({ onClearFilters() }, onClickLabel = resetLabel, scale = .99f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.home_filters_active, activeFilterCount),
            style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 13.sp),
            color = BodegaColors.Ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(resetLabel, style = ButtonLabelStyle, color = BodegaColors.Muted)
            CloseIcon(13.dp, BodegaColors.Muted, strokeWidth = 1.8f)
        }
    }
}

@Composable
private fun FeaturedSection(
    entry: Entry,
    sort: SortOrder,
    onSort: () -> Unit,
    onEntryClick: (Long) -> Unit,
    resolvePhotoPath: (String) -> String,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.featured_eyebrow).uppercase(),
                    fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 9.sp,
                    letterSpacing = 1.6.sp, color = BodegaColors.Placeholder,
                )
                Text(
                    stringResource(R.string.featured_title),
                    style = SectionTitleStyle, color = BodegaColors.Ink,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            val sortLabel = when (sort) {
                SortOrder.RATING -> stringResource(R.string.sort_rating)
                SortOrder.PRICE -> stringResource(R.string.sort_price)
                SortOrder.DATE -> stringResource(R.string.sort_date)
            }
            val sortDescription = stringResource(R.string.sort_description, sortLabel)
            Row(
                Modifier.height(44.dp)
                    .pressAction(onSort)
                    .glassButton()
                    .semantics(mergeDescendants = true) { contentDescription = sortDescription }
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sortLabel, style = ButtonLabelStyle, color = BodegaColors.Muted)
                ArrowDownIcon(15.dp, BodegaColors.Muted)
            }
        }
        val photo = remember(entry.photos) { entry.photos.firstOrNull()?.let(resolvePhotoPath) }
        Box(
            Modifier.padding(top = 16.dp).fillMaxWidth().height(318.dp)
                .pressAction({ onEntryClick(entry.id) }, scale = .99f)
                .glassCard(28, alpha = 1f)
                .background(Brush.linearGradient(listOf(categoryTint(entry.category), BodegaColors.Teal, BodegaColors.Ink))),
        ) {
            if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, BodegaColors.Ink.copy(.35f), BodegaColors.Ink.copy(.9f))),
                ),
            )
            Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeaturePill(stringResource(entry.category.displayNameRes()).uppercase())
                entry.vintageYear?.let { FeaturePill("$it", mono = true) }
            }
            entry.rating?.let { rating ->
                Column(
                    Modifier.align(Alignment.TopEnd).padding(16.dp).size(54.dp)
                        .glassCard(18, alpha = .8f),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$rating", fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = BodegaColors.Ink)
                    Text("/ ${EntryLimits.RATING.last}", fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = BodegaColors.Muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                val subtitle = entrySubtitle(entry)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White.copy(.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    entry.name,
                    fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 30.sp,
                    lineHeight = 31.sp, letterSpacing = (-.85).sp, color = Color.White, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 16.dp), color = Color.White.copy(.25f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    entry.priceRub?.let { FeaturePill("${formatPrice(it)} ₽", mono = true) }
                    ArrowUpRightIcon(20.dp, Color.White.copy(.75f))
                }
            }
        }
    }
}

@Composable
private fun CollectionHeader(
    count: Int,
    compact: Boolean,
    onCompactChange: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.collection_title), style = SectionTitleStyle, color = BodegaColors.Ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.collection_more, count),
                    fontFamily = PlexMono, fontSize = 10.sp, color = BodegaColors.Placeholder,
                )
                // Ширина задана: ряд со скользящей плашкой делит её поровну.
                SlidingSegmentRow(
                    count = 2,
                    selected = if (compact) 1 else 0,
                    // 18 − (отступ дорожки 4 + поле кнопки 4) = 10: углы концентричны.
                    indicatorShape = RoundedCornerShape(10.dp),
                    modifier = Modifier.glassTrack().padding(4.dp)
                        .width(TouchTargetMin * 2).selectableGroup(),
                    spacing = 0.dp,
                    indicatorInset = ViewButtonInset,
                ) {
                    ViewButton(!compact, stringResource(R.string.view_grid), { onCompactChange(false) }) {
                        GridIcon(5.dp, 3.dp, segmentTint(!compact))
                    }
                    ViewButton(compact, stringResource(R.string.view_list), { onCompactChange(true) }) {
                        ListIcon(18.dp, segmentTint(compact))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CocktailCollectionHeader(count: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cocktail_collection_title), style = SectionTitleStyle, color = BodegaColors.Ink)
            Text(
                pluralStringResource(R.plurals.recipe_count, count, count),
                fontFamily = PlexMono, fontSize = 10.sp, color = BodegaColors.Placeholder,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Карточка рецепта показывает его состав и способ приготовления, без винных оценок и цены. */
@Composable
private fun CocktailEntryCard(entry: Entry, resolvePhotoPath: (String) -> String, onClick: () -> Unit) {
    val photo = remember(entry.photos) { entry.photos.firstOrNull()?.let(resolvePhotoPath) }
    val ingredients = entry.ingredientsText.replace("\n", " · ")

    Row(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth()
            .pressAction(onClick, scale = .985f)
            .glassCard(16, alpha = .82f)
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = BodegaColors.Ink,
            )
            if (ingredients.isNotEmpty()) {
                Text(
                    ingredients, fontFamily = Rubik, fontSize = 11.sp, lineHeight = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = BodegaColors.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            entry.preparation?.takeIf(String::isNotBlank)?.let { preparation ->
                Text(
                    preparation, fontFamily = Rubik, fontSize = 10.sp, lineHeight = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = BodegaColors.Placeholder,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        Box(
            Modifier.size(width = 72.dp, height = 84.dp)
                .clip(RoundedCornerShape(12.dp)).background(categoryTint(Category.COCKTAIL)),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else WineGlassIcon(32.dp, categoryColor(Category.COCKTAIL).copy(.72f))
        }
    }
}

/** Пара карточек сетки. Отступ снизу — у самой строки: между элементами LazyColumn Spacer не поставить. */
@Composable
private fun GridRow(
    entries: List<Entry>,
    onEntryClick: (Long) -> Unit,
    resolvePhotoPath: (String) -> String,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entries.forEach { entry -> GridEntryCard(entry, resolvePhotoPath, Modifier.weight(1f)) { onEntryClick(entry.id) } }
        if (entries.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** Видимый кружок кнопки вида — 36dp внутри тап-таргета. */
private val ViewButtonInset = (TouchTargetMin - 36.dp) / 2

@Composable
private fun ViewButton(selected: Boolean, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(TouchTargetMin)
            .pressSelect(selected, onClick, role = Role.RadioButton, scale = .92f)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun GridEntryCard(entry: Entry, resolvePhotoPath: (String) -> String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val photo = remember(entry.photos) { entry.photos.firstOrNull()?.let(resolvePhotoPath) }
    Column(
        modifier
            .pressAction(onClick, scale = .985f)
            .glassCard(24)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(categoryTint(entry.category))) {
            if (photo != null) {
                AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                // Затемнение нужно только под фото — на цветной подложке оно мутит плитку.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.5f)))))
            } else {
                CategoryIcon(entry.category, 44.dp, categoryColor(entry.category).copy(.42f), modifier = Modifier.align(Alignment.Center))
            }
            entry.rating?.let {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp).glassCard(14, alpha = .82f),
                    Alignment.Center,
                ) {
                    Text("$it", fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = BodegaColors.Ink)
                }
            }
            Text(
                stringResource(entry.category.displayNameRes()).uppercase(),
                fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(.28f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                entry.name,
                fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 21.sp,
                letterSpacing = (-.35).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = BodegaColors.Ink,
            )
            val subtitle = entrySubtitle(entry)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = BodegaColors.Muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
            entry.priceRub?.let {
                Text("${formatPrice(it)} ₽", fontFamily = PlexMono, fontSize = 11.sp, color = BodegaColors.Ink, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun CompactEntryCard(entry: Entry, resolvePhotoPath: (String) -> String, onClick: () -> Unit) {
    val photo = remember(entry.photos) { entry.photos.firstOrNull()?.let(resolvePhotoPath) }
    Row(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth().height(72.dp)
            .pressAction(onClick, scale = .985f)
            .glassCard(16, alpha = .78f)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(categoryTint(entry.category)), Alignment.Center) {
            if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else CategoryIcon(entry.category, 23.dp, categoryColor(entry.category).copy(.55f))
        }
        Column(Modifier.weight(1f)) {
            Text(
                entry.name, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                letterSpacing = (-.25).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = BodegaColors.Ink,
            )
            val subtitle = entrySubtitle(entry)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = BodegaColors.Muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // Разделитель слева от оценки — как в макете (border-left у блока с баллом).
        Column(
            Modifier.widthIn(min = 68.dp).fillMaxHeight()
                .drawBehind { drawRect(Color(0x214F7789), size = size.copy(width = 1.dp.toPx())) }
                .padding(start = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            entry.rating?.let { Text("$it", fontFamily = PlexMono, fontSize = 18.sp, color = BodegaColors.Wine) }
            entry.priceRub?.let {
                Text("${formatPrice(it)} ₽", fontFamily = PlexMono, fontSize = 10.sp, color = BodegaColors.Placeholder, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

// ─────────────────────────── Preview ───────────────────────────

internal val sampleEntries = listOf(
    Entry(
        id = 1, category = Category.WINE, name = "Rioja Gran Reserva 2014",
        wineType = "Тихое", color = "Красное", sweetness = "Сухое", grapes = listOf("Темпранильо"), country = "Испания",
        rating = 9, priceRub = 4200, vintageYear = 2014, createdAt = 5,
    ),
    Entry(
        id = 2, category = Category.SPIRIT, name = "Don Julio 1942",
        type = "Текила аньехо", rating = 9, priceRub = 18500, createdAt = 4,
    ),
    Entry(
        id = 3, category = Category.BEER, name = "Chimay Bleue",
        style = "Квадрюпель", rating = 9, priceRub = 640, createdAt = 3,
    ),
    Entry(
        id = 4, category = Category.BEER, name = "Guinness Draught",
        style = "Сухой стаут", rating = 8, priceRub = 290, createdAt = 2,
    ),
)

/** Превью главного экрана: заглушками отличается только состояние. */
@Composable
private fun PreviewList(state: ListState) {
    BodegaTheme {
        ListScreenContent(
            state = state,
            filter = state.filter,
            compact = true,
            onCompactChange = {},
            resolvePhotoPath = { it },
            onQueryChange = {}, onCategorySelect = {}, onSectionSelect = {}, onFiltersClick = {}, onSettingsClick = {}, onEntryClick = {},
            onAddClick = {}, onResetFilters = {}, onClearFilters = {}, onUndoDelete = {}, onUndoExpired = {},
            onScanLabel = {}, onRetryLoad = {},
        )
    }
}

private val sampleCounts = mapOf(Category.WINE to 42, Category.BEER to 23, Category.SPIRIT to 14)

@Preview(name = "Список", showBackground = true, showSystemUi = true)
@Composable
private fun ListScreenPreview() = PreviewList(
    ListState(
        entries = sampleEntries,
        filter = EntryFilter(),
        totalCount = 79,
        categoryCounts = sampleCounts,
    ),
)

@Preview(name = "Пустая коллекция", showBackground = true, showSystemUi = true)
@Composable
private fun ListScreenEmptyPreview() = PreviewList(
    ListState(entries = emptyList(), filter = EntryFilter(), totalCount = 0),
)

@Preview(name = "Активные фильтры", showBackground = true, showSystemUi = true)
@Composable
private fun ListScreenFiltersActivePreview() = PreviewList(
    ListState(
        entries = sampleEntries.take(2),
        filter = EntryFilter(category = Category.WINE, colors = setOf("Красное"), minRating = 8),
        totalCount = 79,
        categoryCounts = sampleCounts,
    ),
)

@Preview(name = "Коктейли — пусто", showBackground = true, showSystemUi = true)
@Composable
private fun EmptyCocktailsPreview() = PreviewList(
    ListState(filter = EntryFilter(category = Category.COCKTAIL), totalCount = 79, categoryCounts = sampleCounts),
)

@Preview(name = "Коктейли — поиск без результатов", showBackground = true, showSystemUi = true)
@Composable
private fun CocktailSearchPreview() = PreviewList(
    ListState(filter = EntryFilter(category = Category.COCKTAIL, query = "Негрони"), totalCount = 79,
        categoryCounts = sampleCounts + (Category.COCKTAIL to 3)),
)

@Preview(name = "Крупный шрифт", fontScale = 1.5f, showBackground = true, showSystemUi = true)
@Composable
private fun LargeFontListPreview() = PreviewList(
    ListState(entries = sampleEntries, totalCount = 79, categoryCounts = sampleCounts),
)
