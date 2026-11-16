package ru.kaufd.bodega.ui.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.SortOrder
import ru.kaufd.bodega.domain.WineFacets
import ru.kaufd.bodega.domain.toggle
import ru.kaufd.bodega.ui.components.MultiSelectSheet
import ru.kaufd.bodega.ui.components.SelectTrigger
import ru.kaufd.bodega.ui.theme.*

@Composable
fun FiltersScreen(
    onClose: () -> Unit,
    viewModel: FiltersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applied by viewModel.applied.collectAsStateWithLifecycle()
    // Закрытие по состоянию, а не по колбэку из ViewModel: после пересоздания
    // Activity композиция получает актуальный onClose.
    LaunchedEffect(applied) { if (applied) onClose() }

    FiltersScreenContent(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onRetryLoad = viewModel::onRetryLoad,
        onReset = viewModel::reset,
        onApply = viewModel::applyDraft,
        onClose = onClose,
    )
}

/** Stateless-версия экрана фильтров: пригодна для @Preview и тестов. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreenContent(
    state: FiltersState,
    onDraftChange: ((EntryFilter) -> EntryFilter) -> Unit,
    onRetryLoad: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = Modifier.ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            ScreenTopBar(onBack = onClose, title = stringResource(R.string.filters_title)) {
                Row(
                    Modifier.pressAction(onReset)
                        .glassButton()
                        .heightIn(min = TouchTargetMin)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ResetIcon(15.dp, BodegaColors.Muted)
                    Text(stringResource(R.string.filters_reset), style = ButtonLabelStyle, color = BodegaColors.Muted)
                }
            }
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(BodegaColors.Paper.copy(alpha = .88f))
                    // Клавиатура вместо навигационной панели, когда открыта:
                    // union, а не два padding подряд, иначе отступы сложатся.
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .pressAction(onApply, scale = .985f)
                        .accentSurface(18)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        pluralStringResource(R.plurals.filters_apply, state.resultCount, state.resultCount),
                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
                        color = Color.White,
                    )
                    ArrowRightIcon(20.dp, Color.White)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                // imePadding здесь не нужен: bottomBar сам растёт на высоту
                // клавиатуры, а его высота уже входит в padding контента.
                // тап по пустому месту снимает фокус и прячет клавиатуру
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Справочники и счётчик приходят из Room; отказ не блокирует работу с
            // черновиком, но объяснить пустые секции надо.
            if (state.loadFailed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.filters_load_failed),
                        style = TextStyle(fontFamily = Rubik, fontSize = 14.sp),
                        color = BodegaColors.Wine,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.retry),
                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                        color = BodegaColors.Ink,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .minimumInteractiveComponentSize()
                            .clickable(role = Role.Button, onClick = onRetryLoad)
                            .padding(horizontal = 6.dp),
                    )
                }
            }

            if (state.draft.category?.isRecipe != true) Section(stringResource(R.string.filters_category), divider = false) {
                ChipFlow(Modifier.selectableGroup()) {
                    // Категорийные фасеты сбрасывает EntryFilter.withCategory — единая
                    // точка нормализации, чтобы экран не забывал очередной фасет.
                    PillChip(stringResource(R.string.all), state.draft.category == null,
                        { onDraftChange { it.withCategory(null) } }, role = Role.RadioButton)
                    Category.drinks.forEach { c ->
                        PillChip(stringResource(c.displayNameRes()), state.draft.category == c,
                            { onDraftChange { it.withCategory(c) } }, role = Role.RadioButton)
                    }
                }
            }

            FacetSection(
                stringResource(R.string.filters_color), state.wineFacets.colors, state.draft.colors,
            ) { c -> onDraftChange { it.copy(colors = it.colors.toggle(c)) } }

            FacetSection(
                stringResource(R.string.filters_type), state.wineFacets.types, state.draft.wineTypes,
            ) { t ->
                // Сладости, выпавшие из шкал выбранных типов, снимает withWineTypes —
                // та же точка нормализации, что у категории.
                onDraftChange { it.withWineTypes(it.wineTypes.toggle(t)) }
            }

            FacetSection(
                stringResource(R.string.filters_sweetness), state.wineFacets.sweetness, state.draft.sweetness,
            ) { s -> onDraftChange { it.copy(sweetness = it.sweetness.toggle(s)) } }

            if (state.availableStyles.isNotEmpty()) {
                Section(stringResource(R.string.filters_style)) {
                    MultiSelectField(
                        title = stringResource(R.string.filters_style),
                        options = state.availableStyles,
                        selected = state.draft.styles,
                        emptyLabel = stringResource(R.string.filters_style_all),
                    ) { s -> onDraftChange { it.copy(styles = it.styles.toggle(s)) } }
                }
            }

            if (state.availableTypes.isNotEmpty()) {
                Section(stringResource(R.string.filters_type)) {
                    state.availableTypes.forEach { (cat, types) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(cat.displayNameRes()),
                                style = TextStyle(
                                    fontFamily = Rubik,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                ),
                                color = BodegaColors.Muted,
                            )
                            ChipFlow {
                                types.forEach { t ->
                                    PillChip(t, t in state.draft.types, {
                                        onDraftChange { it.copy(types = it.types.toggle(t)) }
                                    })
                                }
                            }
                        }
                    }
                }
            }

            Section(stringResource(R.string.filters_rating)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "${state.draft.minRating ?: EntryLimits.RATING.first} – ${state.draft.maxRating ?: EntryLimits.RATING.last}",
                        style = TextStyle(fontFamily = PlexMono, fontSize = 14.sp), color = BodegaColors.Ink,
                    )
                    Text(
                        " / ${EntryLimits.RATING.last}",
                        style = TextStyle(fontFamily = PlexMono, fontSize = 14.sp), color = BodegaColors.Placeholder,
                    )
                }
                val range = (state.draft.minRating ?: EntryLimits.RATING.first).toFloat()..(state.draft.maxRating ?: EntryLimits.RATING.last).toFloat()
                RangeSlider(
                    value = range,
                    onValueChange = { r ->
                        onDraftChange {
                            val min = r.start.toInt().coerceIn(EntryLimits.RATING.first, EntryLimits.RATING.last)
                            val max = r.endInclusive.toInt().coerceIn(EntryLimits.RATING.first, EntryLimits.RATING.last)
                            it.copy(
                                minRating = if (min <= EntryLimits.RATING.first) null else min,
                                maxRating = if (max >= EntryLimits.RATING.last) null else max,
                            )
                        }
                    },
                    valueRange = EntryLimits.RATING.first.toFloat()..EntryLimits.RATING.last.toFloat(),
                    steps = EntryLimits.RATING.last - EntryLimits.RATING.first - 1,
                    // По макету: тонкий трек, ползунки — белые кружки с тёмной обводкой.
                    startThumb = { RatingThumb() },
                    endThumb = { RatingThumb() },
                    track = { st ->
                        SliderDefaults.Track(
                            rangeSliderState = st,
                            modifier = Modifier.height(4.dp),
                            colors = SliderDefaults.colors(
                                activeTrackColor = BodegaColors.Teal,
                                inactiveTrackColor = BodegaColors.Teal.copy(alpha = .14f),
                            ),
                            drawStopIndicator = null,
                            drawTick = { _, _ -> },
                        )
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${EntryLimits.RATING.first}", style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp), color = BodegaColors.Placeholder)
                    Text("${EntryLimits.RATING.last}", style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp), color = BodegaColors.Placeholder)
                }
            }

            if (state.draft.category?.isRecipe != true) Section(stringResource(R.string.filters_price)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val priceLabel = stringResource(R.string.filters_price)
                    PriceField(
                        state.draft.minPrice, stringResource(R.string.filters_price_from),
                        "$priceLabel — ${stringResource(R.string.filters_price_from)}", Modifier.weight(1f),
                    ) { v -> onDraftChange { it.copy(minPrice = v) } }
                    PriceField(
                        state.draft.maxPrice, stringResource(R.string.filters_price_to),
                        "$priceLabel — ${stringResource(R.string.filters_price_to)}", Modifier.weight(1f),
                    ) { v -> onDraftChange { it.copy(maxPrice = v) } }
                }
            }

            if (state.availableCountries.isNotEmpty()) {
                Section(stringResource(R.string.filters_country)) {
                    MultiSelectField(
                        title = stringResource(R.string.filters_country),
                        options = state.availableCountries,
                        selected = state.draft.countries,
                        emptyLabel = stringResource(R.string.filters_country_all),
                    ) { c -> onDraftChange { it.copy(countries = it.countries.toggle(c)) } }
                }
            }

            Section(stringResource(R.string.filters_sort)) {
                ChipFlow(Modifier.selectableGroup()) {
                    SortOrder.entries.filter { state.draft.category?.isRecipe != true || it != SortOrder.PRICE }.forEach { order ->
                        // Подписи свои, а не общие со списком: там «Балл», здесь «По баллам».
                        val label = when (order) {
                            SortOrder.RATING -> R.string.filters_sort_rating
                            SortOrder.PRICE -> R.string.filters_sort_price
                            SortOrder.DATE -> R.string.filters_sort_date
                        }
                        PillChip(stringResource(label), state.draft.sort == order,
                            { onDraftChange { it.copy(sort = order) } }, role = Role.RadioButton)
                    }
                }
            }
        }
    }
}

/** Мультивыбор из справочника: поле-триггер как в редакторе плюс нижний лист. */
@Composable
private fun MultiSelectField(
    title: String,
    options: List<String>,
    selected: Set<String>,
    emptyLabel: String,
    onToggle: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // порядок как в справочнике, чтобы подпись читалась привычно
    val label = options.filter { it in selected }.joinToString(", ")
    SelectTrigger(
        text = label.ifEmpty { emptyLabel },
        placeholder = selected.isEmpty(),
        open = open,
    ) { open = true }
    if (open) {
        MultiSelectSheet(
            title = title,
            options = options,
            selected = selected,
            hint = stringResource(R.string.filters_search_hint),
            onToggle = onToggle,
            onDismiss = { open = false },
        )
    }
}

/** Секция мультивыбора из плоского списка значений; скрыта, если список пуст. */
@Composable
private fun FacetSection(
    title: String,
    values: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (values.isEmpty()) return
    Section(title) {
        ChipFlow {
            values.forEach { v -> PillChip(v, v in selected, { onToggle(v) }) }
        }
    }
}

/** Секция фильтра. По макету секции разделены волосяной линией — кроме первой. */
@Composable
private fun Section(title: String, divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (divider) {
            HorizontalDivider(Modifier.padding(bottom = 11.dp), color = BodegaColors.SectionRule)
        }
        SectionLabel(title)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun PriceField(
    value: Int?,
    placeholder: String,
    description: String,
    modifier: Modifier,
    onChange: (Int?) -> Unit,
) {
    FormField(
        value = value?.toString() ?: "",
        onChange = { v -> onChange(v.filter { it.isDigit() }.toIntOrNull()) },
        placeholder = placeholder,
        description = description,
        modifier = modifier,
        textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp),
        keyboard = KeyboardType.Number,
    )
}

@Preview(name = "Фильтры", showBackground = true, showSystemUi = true)
@Composable
private fun FiltersScreenPreview() {
    BodegaTheme {
        FiltersScreenContent(
            state = FiltersState(
                draft = EntryFilter(category = Category.WINE, colors = setOf("Красное"), minRating = 8),
                wineFacets = WineFacets(
                    colors = listOf("Красное", "Белое", "Розовое"),
                    types = listOf("Тихое", "Игристое"),
                    sweetness = listOf("Сухое", "Полусухое", "Brut"),
                ),
                availableCountries = listOf("Италия", "Германия", "Бельгия", "Ирландия"),
                resultCount = 42,
            ),
            onDraftChange = {}, onRetryLoad = {}, onReset = {}, onApply = {}, onClose = {},
        )
    }
}
