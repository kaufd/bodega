package ru.kaufd.bodega.ui.edit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kaufd.bodega.ui.util.rememberCameraCapture
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.BeerClassification
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.CocktailIngredient
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.EntryViolation
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.Suggestions
import ru.kaufd.bodega.domain.WineClassification
import ru.kaufd.bodega.ui.recognitionMessage
import ru.kaufd.bodega.ui.rememberPhotoPathResolver
import ru.kaufd.bodega.ui.components.ActionSheet
import ru.kaufd.bodega.ui.components.ConfirmDialog
import ru.kaufd.bodega.ui.components.InputMultiSheet
import ru.kaufd.bodega.ui.components.InputSingleSheet
import ru.kaufd.bodega.ui.components.SelectTrigger
import ru.kaufd.bodega.ui.components.SingleSelectSheet
import ru.kaufd.bodega.ui.theme.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.Year
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@Composable
fun EditScreen(
    onClose: () -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draftAvailable by viewModel.draftAvailable.collectAsStateWithLifecycle()
    val resolvePhotoPath = rememberPhotoPathResolver()
    var showPhotoSource by remember { mutableStateOf(false) }
    var photoMenuFor by remember { mutableStateOf<String?>(null) }

    // Закрытие по состоянию, а не по колбэку из ViewModel: после пересоздания
    // Activity композиция получает актуальный onClose и закрывает экран сама.
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    val snackbar = remember { SnackbarHostState() }
    val photoErrorMessage = stringResource(R.string.photo_import_failed)
    LaunchedEffect(Unit) {
        viewModel.photoError.collect { snackbar.showSnackbar(photoErrorMessage) }
    }

    // Строка резолвится в момент показа, а не на первой композиции: экран
    // рекомпозится на каждый символ формы, и шесть stringResource уходили бы в мусор.
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.draftError.collect {
            snackbar.showSnackbar(resources.getString(recognitionMessage(it)))
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onPhotosPicked(listOf(PhotoSource(it.toString()))) } }

    // Отдельный контракт для пачки: PickMultipleVisualMedia требует maxItems > 1,
    // поэтому при единственном свободном слоте открывается одиночный пикер выше.
    //
    // maxItems — по остатку слотов, а не по PHOTOS_MAX: VM всё равно обрежет пачку
    // через take(freePhotoSlots), и при двух свободных слотах третий выбранный снимок
    // исчезал бы без единого слова. Пусть системный пикер сам не даст его отметить.
    val freeSlots = state.freePhotoSlots
    val photosPicker = rememberLauncherForActivityResult(
        remember(freeSlots) { ActivityResultContracts.PickMultipleVisualMedia(freeSlots.coerceAtLeast(2)) }
    ) { uris -> viewModel.onPhotosPicked(uris.map { PhotoSource(it.toString()) }) }

    val pickCamera = rememberCameraCapture { uri, onConsumed ->
        viewModel.onPhotosPicked(listOf(PhotoSource(uri.toString())), onFinished = onConsumed)
    }

    var confirmDraftOverwrite by remember { mutableStateOf(false) }

    EditScreenContent(
        state = state,
        draftAvailable = draftAvailable,
        snackbar = snackbar,
        resolvePhotoPath = resolvePhotoPath,
        onField = viewModel::onField,
        onAbvInput = viewModel::onAbvInput,
        onRetryLoad = viewModel::onRetryLoad,
        onCategoryChange = viewModel::onCategoryChange,
        onPickPhoto = { showPhotoSource = true },
        onDraftNote = {
            if (state.entry.note.isNullOrBlank()) viewModel.onDraftNote()
            else confirmDraftOverwrite = true
        },
        onPhotoClick = { photoMenuFor = it },
        onSave = viewModel::save,
        // Уход без сохранения: VM удаляет импортированное, но не сохранённое фото.
        onClose = { viewModel.discard(); onClose() },
    )

    if (confirmDraftOverwrite) {
        ConfirmDialog(
            title = stringResource(R.string.edit_note_overwrite_title),
            text = stringResource(R.string.edit_note_overwrite_text),
            confirmLabel = stringResource(R.string.edit_note_overwrite_confirm),
            dismissLabel = stringResource(R.string.edit_cancel),
            onConfirm = { confirmDraftOverwrite = false; viewModel.onDraftNote() },
            onDismiss = { confirmDraftOverwrite = false },
        )
    }

    if (showPhotoSource) {
        ActionSheet(
            title = stringResource(R.string.photo_source_title),
            actions = listOf(
                stringResource(R.string.photo_source_camera) to { pickCamera() },
                stringResource(R.string.photo_source_gallery) to {
                    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    if (state.freePhotoSlots > 1) photosPicker.launch(request) else photoPicker.launch(request)
                },
            ),
            onDismiss = { showPhotoSource = false },
        )
    }

    photoMenuFor?.let { path ->
        val isCover = state.entry.photos.firstOrNull() == path
        ActionSheet(
            title = stringResource(R.string.edit_photo_menu_title),
            actions = listOfNotNull(
                // Обложке пункт «сделать обложкой» не нужен — она уже первая.
                if (isCover) null else stringResource(R.string.edit_photo_make_cover) to
                    { viewModel.onPhotoMakeCover(path) },
                stringResource(R.string.edit_photo_delete) to { viewModel.onPhotoRemove(path) },
            ),
            onDismiss = { photoMenuFor = null },
        )
    }
}

/** Stateless-версия редактора: пригодна для @Preview и тестов. */
@Composable
fun EditScreenContent(
    state: EditState,
    /** Облачная модель настроена — только тогда есть чем писать черновик. */
    draftAvailable: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    resolvePhotoPath: (String) -> String,
    onField: ((Entry) -> Entry) -> Unit,
    onAbvInput: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onCategoryChange: (Category) -> Unit,
    onPickPhoto: () -> Unit,
    onDraftNote: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    // Пока сохранение в полёте, уходить нельзя: discard() удалил бы фото, путь к
    // которому уже уходит в БД, а сама запись сохранилась бы наполовину.
    fun tryClose() {
        if (state.saving) return
        if (state.dirty) confirmExit = true else onClose()
    }

    // Обработчик остаётся включённым и во время сохранения — иначе «назад»
    // обработала бы система и экран закрылся бы в обход tryClose().
    BackHandler { tryClose() }

    if (confirmExit) {
        ConfirmDialog(
            title = stringResource(R.string.edit_exit_title),
            text = stringResource(R.string.edit_exit_text),
            confirmLabel = stringResource(R.string.edit_exit_confirm),
            dismissLabel = stringResource(R.string.edit_exit_stay),
            onConfirm = { confirmExit = false; onClose() },
            onDismiss = { confirmExit = false },
        )
    }

    val accent = categoryColor(state.entry.category)

    Scaffold(
        modifier = Modifier.ambientBackground(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) { BodegaSnackbar(it) } },
        topBar = {
            Box(
                Modifier.fillMaxWidth().screenTopBar().statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .pressAction({ tryClose() }, enabled = !state.saving)
                        .glassButton()
                        // Высота — как у «Сохранить» справа, подпись по центру: с
                        // вертикальным padding она прижималась к верху плашки.
                        .height(46.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.edit_cancel),
                        style = ButtonLabelStyle,
                        color = if (state.saving) BodegaColors.Placeholder else BodegaColors.Muted,
                    )
                }
                Text(
                    stringResource(if (state.entry.category.isRecipe) {
                        if (state.isNew) R.string.cocktail_new else R.string.cocktail_edit
                    } else if (state.isNew) R.string.edit_new else R.string.edit_edit),
                    style = ScreenTitleStyle,
                    color = BodegaColors.Ink,
                    modifier = Modifier.align(Alignment.Center),
                )
                val saveLabel = stringResource(R.string.edit_save)
                Box(
                    Modifier.align(Alignment.CenterEnd).size(46.dp)
                        .pressAction(onSave, enabled = state.canSave)
                        .then(
                            if (state.canSave) Modifier.accentSurface(18)
                            else Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.5f))
                        )
                        .semantics { contentDescription = saveLabel },
                    contentAlignment = Alignment.Center,
                ) { CheckIcon(20.dp, if (state.canSave) Color.White else BodegaColors.Placeholder) }
            }
        },
    ) { padding ->
        // Форма существующей записи доступна только после загрузки: иначе ранний
        // ввод затирался пришедшим объектом, а быстрое сохранение создавало
        // вторую запись с id = 0.
        when (state.phase) {
            EditPhase.LOADING -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
            }

            EditPhase.NOT_FOUND -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(R.string.edit_not_found),
                    style = TextStyle(fontFamily = Rubik, fontSize = 15.sp),
                    color = BodegaColors.Muted,
                )
            }

            // Отказ БД лечится повтором, в отличие от «записи нет».
            EditPhase.FAILED -> Box(Modifier.padding(padding).fillMaxSize()) {
                LoadFailed(stringResource(R.string.edit_load_failed), onRetryLoad)
            }

            EditPhase.READY -> EditForm(
                state = state,
                draftAvailable = draftAvailable,
                contentPadding = padding,
                resolvePhotoPath = resolvePhotoPath,
                onField = onField,
                onAbvInput = onAbvInput,
                onCategoryChange = onCategoryChange,
                onPickPhoto = onPickPhoto,
                onDraftNote = onDraftNote,
                onPhotoClick = onPhotoClick,
            )
        }
    }
}

/** Собственно форма. Показывается только в фазе READY — см. [EditPhase]. */
@Composable
private fun EditForm(
    state: EditState,
    draftAvailable: Boolean,
    contentPadding: PaddingValues,
    resolvePhotoPath: (String) -> String,
    onField: ((Entry) -> Entry) -> Unit,
    onAbvInput: (String) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onPickPhoto: () -> Unit,
    onDraftNote: () -> Unit,
    onPhotoClick: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        Modifier.padding(contentPadding).fillMaxSize()
            // Без этого нижние поля оказываются под клавиатурой: на targetSdk 35+
            // окно edge-to-edge, система его под IME не ужимает. Отступ снаружи
            // verticalScroll — сокращает видимую область, и поле с фокусом
            // подскроливается к ней само.
            .imePadding()
            // тап по пустому месту снимает фокус и прячет клавиатуру
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state.saveFailed) {
            Text(
                stringResource(R.string.edit_save_failed),
                style = TextStyle(fontFamily = Rubik, fontSize = 14.sp),
                color = BodegaColors.Wine,
            )
        }

        PhotoStrip(state.entry.photos, state.photoImporting, onPickPhoto, onPhotoClick, resolvePhotoPath)

        if (!state.entry.category.isRecipe) Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionLabel(stringResource(R.string.filters_category))
            CategorySegments(selected = state.entry.category, onSelect = onCategoryChange)
        }

        LabeledField(
            stringResource(R.string.edit_name), state.entry.name,
            valueStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 21.sp),
            placeholder = stringResource(if (state.entry.category.isRecipe) R.string.cocktail_name_hint else R.string.edit_name_placeholder),
        ) { v -> onField { it.copy(name = v) } }

        when (state.entry.category) {
            Category.WINE -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownField(stringResource(R.string.edit_type), stringResource(R.string.select_placeholder), WineClassification.types, state.entry.wineType, Modifier.weight(1f)) { v ->
                        onField { it.withWineType(v) }
                    }
                    DropdownField(stringResource(R.string.edit_color), stringResource(R.string.select_placeholder), WineClassification.colors, state.entry.color, Modifier.weight(1f)) { v ->
                        onField { it.copy(color = v) }
                    }
                }
                val sweetnessOptions = WineClassification.sweetnessFor(state.entry.wineType)
                if (sweetnessOptions.isNotEmpty()) {
                    DropdownField(stringResource(R.string.edit_sweetness), stringResource(R.string.select_placeholder), sweetnessOptions, state.entry.sweetness) { v ->
                        onField { it.copy(sweetness = v) }
                    }
                }
                MultiInputField(
                    label = stringResource(R.string.edit_grape),
                    suggestions = Suggestions.grapes,
                    selected = state.entry.grapes,
                ) { g ->
                    onField {
                        val exists = it.grapes.any { x -> x.equals(g, ignoreCase = true) }
                        it.copy(grapes = if (exists) it.grapes.filterNot { x -> x.equals(g, ignoreCase = true) } else it.grapes + g)
                    }
                }
                // Страна занимает строку целиком: втроём с годами ей остаётся треть
                // ширины, и подсказка «Начните вводить» обрезается многоточием.
                InputChoiceField(stringResource(R.string.edit_country), Suggestions.countries, state.entry.country) { v -> onField { it.copy(country = v) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Урожай и розлив — разные годы: виноград собран в один, бутылка
                    // разлита в другой. В постах паблика они и стоят двумя строками.
                    NumberField(stringResource(R.string.edit_vintage), state.entry.vintageYear, Modifier.weight(1f)) { v -> onField { it.copy(vintageYear = v) } }
                    NumberField(stringResource(R.string.edit_bottling), state.entry.bottlingYear, Modifier.weight(1f)) { v -> onField { it.copy(bottlingYear = v) } }
                }
                DropdownField(
                    label = stringResource(R.string.edit_serving_temp),
                    placeholder = stringResource(R.string.select_placeholder),
                    options = WineClassification.servingTemps,
                    selected = state.entry.servingTemp,
                ) { v -> onField { it.copy(servingTemp = v) } }
            }
            Category.BEER -> {
                DropdownField(
                    label = stringResource(R.string.edit_style),
                    placeholder = stringResource(R.string.edit_style_placeholder),
                    options = BeerClassification.styles,
                    selected = state.entry.style,
                ) { v -> onField { it.copy(style = v) } }
            }
            Category.COCKTAIL -> {
                IngredientsEditor(state.entry, onField)
                LabeledField(
                    stringResource(R.string.cocktail_preparation), state.entry.preparation.orEmpty(),
                    valueStyle = TextStyle(fontFamily = Rubik, fontSize = 15.sp, lineHeight = 23.sp),
                    placeholder = stringResource(R.string.cocktail_preparation_hint),
                    singleLine = false, minLines = 5,
                ) { v -> onField { it.copy(preparation = v.ifBlank { null }) } }
            }
            Category.SPIRIT -> {
                InputChoiceField(
                    label = stringResource(R.string.edit_type),
                    suggestions = Suggestions.typesFor(Category.SPIRIT),
                    selected = state.entry.type,
                ) { v -> onField { it.copy(type = v) } }
            }
        }

        if (!state.entry.category.isRecipe) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            DecimalField(
                label = stringResource(R.string.edit_abv), text = state.abvText,
                error = when {
                    state.abvUnparsed -> stringResource(R.string.edit_abv_not_a_number)
                    EntryViolation.ABV in state.violations -> stringResource(R.string.edit_abv_invalid, EntryLimits.ABV.start.toInt(), EntryLimits.ABV.endInclusive.toInt())
                    else -> null
                },
                modifier = Modifier.weight(1f), onChange = onAbvInput,
            )
            NumberField(stringResource(R.string.edit_price), state.entry.priceRub, Modifier.weight(1f)) { v -> onField { it.copy(priceRub = v) } }
        }

        RatingField(state.entry.rating) { r -> onField { it.copy(rating = r) } }

        if (!state.entry.category.isRecipe) {
            TastedAtField(state.entry.tastedAt) { v -> onField { it.copy(tastedAt = v) } }
        }

        if (!state.entry.category.isRecipe) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField(
                stringResource(R.string.edit_note), state.entry.note ?: "",
                valueStyle = TextStyle(fontFamily = Rubik, fontSize = 15.sp, lineHeight = 23.sp),
                placeholder = stringResource(R.string.edit_note_placeholder),
                singleLine = false, minLines = 5,
                // Пока модель пишет черновик, поле заморожено — см. EditState.noteEditable.
                readOnly = !state.noteEditable,
            ) { v -> onField { it.copy(note = v.ifBlank { null }) } }

            // Кнопки нет, пока облачная модель не настроена: нажатие всё равно
            // отвечало бы отказом, а объяснять настройку снекбаром — не место.
            if (draftAvailable) {
                // Без названия писать не о чем — модели нечего было бы описывать.
                AssistButton(
                    text = stringResource(R.string.edit_note_draft),
                    runningText = stringResource(R.string.edit_note_drafting),
                    enabled = state.editable && state.entry.name.isNotBlank(),
                    running = state.drafting,
                    onClick = onDraftNote,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Состав — одна плашка поля со строками через волосяную линию, как «Характеристики»
 * в карточке. Последняя строка всегда пустая: начали в ней печатать — она стала
 * ингредиентом, ниже появилась новая. Кнопки «Добавить» поэтому нет, а пустые строки
 * в базу не попадают (см. `encodeIngredients`).
 */
@Composable
private fun IngredientsEditor(entry: Entry, onField: ((Entry) -> Entry) -> Unit) {
    // Правка считается от текущей записи, а не от строк этой композиции: два события
    // до рекомпозиции иначе затирали бы друг друга. Индекс за концом списка — это
    // пустая строка: правка в ней дописывает ингредиент. Стёртый до пустоты хвост
    // снова становится пустой строкой — двух пустых подряд под составом не бывает.
    fun edit(index: Int, change: (CocktailIngredient) -> CocktailIngredient) = onField { e ->
        val rows = e.ingredientItems
        val edited = if (index < rows.size) rows.mapIndexed { i, row -> if (i == index) change(row) else row }
            else rows + change(CocktailIngredient())
        e.copy(ingredientItems = edited.dropLastWhile { it.name.isBlank() && it.amount.isBlank() })
    }
    // Строки без ключей, поэтому после удаления фокус снимаем: иначе он остался бы
    // в том же слоте, где теперь соседний ингредиент, и ввод правил бы не его.
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val rows = entry.ingredientItems + CocktailIngredient()
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(stringResource(R.string.cocktail_ingredients))
        Column(
            Modifier.fillMaxWidth().fieldSurface(focused).onFocusChanged { focused = it.hasFocus }
                .padding(start = 15.dp),
        ) {
            rows.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = BodegaColors.Hairline)
                val last = index == rows.lastIndex
                Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically) {
                    val nameLabel = stringResource(R.string.cocktail_ingredient_name)
                    val amountLabel = stringResource(R.string.cocktail_ingredient_amount)
                    InlineField(
                        item.name, { value -> edit(index) { it.copy(name = value) } },
                        placeholder = nameLabel, description = "$nameLabel ${index + 1}",
                        modifier = Modifier.weight(1f),
                    )
                    // Ширина под «30 мл» и «по вкусу»: длинному названию остаётся строка.
                    InlineField(
                        item.amount, { value -> edit(index) { it.copy(amount = value) } },
                        placeholder = stringResource(R.string.cocktail_ingredient_amount_hint),
                        description = "$amountLabel ${index + 1}",
                        modifier = Modifier.width(92.dp).padding(start = 8.dp),
                        style = TextStyle(fontFamily = PlexMono, fontSize = 13.sp, textAlign = TextAlign.End),
                        capitalization = KeyboardCapitalization.None,
                    )
                    val removeLabel = stringResource(R.string.cocktail_remove_ingredient, index + 1)
                    Box(
                        Modifier.size(TouchTargetMin)
                            .then(
                                if (last) Modifier
                                else Modifier.pressAction({
                                    focusManager.clearFocus()
                                    onField { e -> e.copy(ingredientItems = e.ingredientItems.filterIndexed { i, _ -> i != index }) }
                                }, onClickLabel = removeLabel).semantics { contentDescription = removeLabel },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!last) CloseIcon(12.dp, BodegaColors.Placeholder, strokeWidth = 1.8f)
                    }
                }
            }
        }
    }
}

/** Поле без своей плашки — для строк внутри общей. «Далее» ведёт к следующему полю. */
@Composable
private fun InlineField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    description: String,
    modifier: Modifier = Modifier,
    style: TextStyle = FieldValueStyle,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = style, color = BodegaColors.Placeholder, modifier = Modifier.fillMaxWidth())
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style.copy(color = BodegaColors.Ink),
            cursorBrush = SolidColor(BodegaColors.Teal),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        )
    }
}

/**
 * Оценка 1–10 слайдером.
 *
 * Десять плашек в одну строку давали ~33 dp на элемент: до рекомендованных 48×48 dp
 * их не растянуть, а расширение цели нажатия за визуальные границы заставляло
 * соседей перекрываться. У слайдера цель нажатия штатная, а перетаскивание и
 * жесты TalkBack Material берёт на себя.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RatingField(rating: Int?, onChange: (Int?) -> Unit) {
    val range = EntryLimits.RATING
    val label = stringResource(R.string.edit_rating)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(label)
        Column(Modifier.fillMaxWidth().glassCard(22).padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.edit_rating_hint),
                    fontFamily = Rubik, fontSize = 12.sp, color = BodegaColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (rating == null) {
                    Text(
                        stringResource(R.string.edit_rating_none),
                        style = TextStyle(fontFamily = Rubik, fontSize = 13.sp),
                        color = BodegaColors.Placeholder,
                    )
                } else {
                    Text("$rating", style = TextStyle(fontFamily = PlexMono, fontSize = 18.sp), color = BodegaColors.Wine)
                    Text(
                        " / ${range.last}",
                        style = TextStyle(fontFamily = PlexMono, fontSize = 18.sp),
                        color = BodegaColors.Placeholder,
                    )
                    // Оценка опциональна — без явного действия её было бы не снять.
                    Text(
                        stringResource(R.string.edit_rating_clear),
                        style = TextStyle(fontFamily = Rubik, fontSize = 12.sp),
                        color = BodegaColors.Muted,
                        modifier = Modifier.padding(start = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .minimumInteractiveComponentSize()
                            .clickable(role = Role.Button) { onChange(null) }
                            .padding(horizontal = 6.dp),
                    )
                }
            }
            Slider(
                value = (rating ?: range.first).toFloat(),
                onValueChange = { onChange(it.toInt().coerceIn(range.first, range.last)) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
                // По макету: тонкий трек 4dp и белый кружок с бирюзовой обводкой.
                thumb = { RatingThumb() },
                track = { st ->
                    SliderDefaults.Track(
                        sliderState = st,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = if (rating == null) BodegaColors.FieldBorder else BodegaColors.Teal,
                            inactiveTrackColor = BodegaColors.Teal.copy(alpha = .14f),
                        ),
                        drawStopIndicator = null,
                        drawTick = { _, _ -> },
                    )
                },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

/**
 * Кнопка-помощник под полем: написать черновик заметки. Не главное действие
 * формы, поэтому плоская, без заливки акцентом.
 *
 * Пока работа идёт, кнопка сама показывает [runningText] и не принимает нажатий:
 * вызывающему остаётся один признак [running] вместо трёх производных от него.
 */
@Composable
private fun AssistButton(
    text: String,
    runningText: String,
    enabled: Boolean,
    running: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .pressAction(onClick, enabled = enabled && !running)
            .glassButton()
            .heightIn(min = TouchTargetMin)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            CircularProgressIndicator(
                Modifier.size(16.dp), color = BodegaColors.Teal, strokeWidth = 2.dp,
            )
        }
        Text(
            if (running) runningText else text,
            fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (enabled) BodegaColors.Ink else BodegaColors.Placeholder,
        )
    }
}

/**
 * Фотографии записи: пока их нет — крупная плитка «добавить», дальше лента
 * миниатюр (первая — обложка) и плитка «+», исчезающая на [EntryLimits.PHOTOS_MAX].
 */
@Composable
private fun PhotoStrip(
    photos: List<String>,
    importing: Boolean,
    onPick: () -> Unit,
    onPhotoClick: (String) -> Unit,
    resolvePhotoPath: (String) -> String,
) {
    if (photos.isEmpty()) {
        PhotoPlaceholder(importing, onPick)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        photos.forEachIndexed { index, path ->
            PhotoThumb(
                path = path,
                index = index,
                total = photos.size,
                onClick = { onPhotoClick(path) },
                resolvePhotoPath = resolvePhotoPath,
                modifier = Modifier.weight(1f),
            )
        }
        if (photos.size < EntryLimits.PHOTOS_MAX) {
            AddPhotoTile(importing, onPick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoPlaceholder(importing: Boolean, onPick: () -> Unit) {
    val clickLabel = stringResource(R.string.edit_add_photo)
    Box(
        Modifier.fillMaxWidth().height(170.dp)
            .pressAction({ onPick() }, onClickLabel = clickLabel, scale = .99f, enabled = !importing)
            .glassCard(24, alpha = 1f)
            .background(Brush.linearGradient(listOf(BodegaColors.TealBright, Color(0xFF7FCED0), Color(0xFFFF9A78)))),
        contentAlignment = Alignment.Center,
    ) {
        if (importing) {
            // Пока идёт импорт, «Сохранить» заблокирована — показываем, почему.
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = .22f))
                        .border(1.dp, Color.White.copy(alpha = .42f), RoundedCornerShape(18.dp)),
                    Alignment.Center,
                ) { CameraIcon(size = 25.dp, color = Color.White) }
                Text(
                    stringResource(R.string.edit_photo_add).uppercase(),
                    fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                    letterSpacing = 1.4.sp, color = Color.White.copy(alpha = .8f),
                )
            }
        }
    }
}

@Composable
private fun PhotoThumb(
    path: String,
    index: Int,
    total: Int,
    onClick: () -> Unit,
    resolvePhotoPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val abs = remember(path) { resolvePhotoPath(path) }
    val position = stringResource(R.string.edit_photo_position, index + 1, total)
    val actions = stringResource(R.string.edit_photo_actions)
    Box(
        modifier.aspectRatio(1f)
            .pressAction({ onClick() }, onClickLabel = actions, scale = .97f)
            .glassCard(18, alpha = 1f),
    ) {
        AsyncImage(
            model = abs,
            contentDescription = position,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (index == 0) {
            // Обложка видна в списке и уйдёт первой в пост — метим её явно.
            Text(
                stringResource(R.string.edit_photo_cover).uppercase(),
                fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 8.sp,
                letterSpacing = 1.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BodegaColors.Ink.copy(alpha = .58f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    // Позиция уже названа в contentDescription самой миниатюры.
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun AddPhotoTile(importing: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val clickLabel = stringResource(R.string.edit_add_photo)
    Box(
        modifier.aspectRatio(1f)
            .pressAction({ onPick() }, onClickLabel = clickLabel, scale = .97f, enabled = !importing)
            .glassCard(18, alpha = 1f)
            .background(Brush.linearGradient(listOf(BodegaColors.TealBright, Color(0xFF7FCED0)))),
        contentAlignment = Alignment.Center,
    ) {
        if (importing) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        else CameraIcon(size = 22.dp, color = Color.White)
    }
}

@Composable
private fun CategorySegments(selected: Category, onSelect: (Category) -> Unit) {
    SlidingSegmentRow(
        count = Category.drinks.size,
        selected = Category.drinks.indexOf(selected),
        indicatorShape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().glassTrack().padding(6.dp).selectableGroup(),
    ) {
        Category.drinks.forEach { c ->
            // Без счётчика: в редакторе сегмент выбирает категорию записи, а не фильтрует.
            Segment(
                label = stringResource(c.displayNameRes()),
                active = selected == c,
                onSelect = { onSelect(c) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    valueStyle: TextStyle,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(label)
        FormField(
            value = value,
            onChange = onChange,
            placeholder = placeholder,
            description = label,
            textStyle = valueStyle,
            keyboard = keyboard,
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
        )
    }
}

/**
 * Поле-триггер, открывающее нижний лист. Состояние открытия и лейбл — здесь, сам
 * лист приходит лямбдой: одиночный выбор, открытый ввод и мультиввод отличались
 * только им.
 */
@Composable
private fun SheetField(
    label: String,
    text: String,
    placeholder: Boolean,
    modifier: Modifier = Modifier,
    sheet: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(label)
        SelectTrigger(text = text, placeholder = placeholder, open = open) { open = true }
    }
    if (open) sheet { open = false }
}

/** Выбор одного значения из длинного набора через нижний лист (bottom sheet).
 *  Повторный выбор текущего значения снимает его (значение опционально). */
@Composable
private fun DropdownField(
    label: String,
    placeholder: String,
    options: List<String>,
    selected: String?,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
) {
    SheetField(label, selected ?: placeholder, selected == null, modifier) { onDismiss ->
        SingleSelectSheet(
            title = label,
            options = options,
            selected = selected,
            onSelect = onSelect,
            onDismiss = onDismiss,
        )
    }
}

/** Открытое одиночное поле: значение из подсказок или своё, выбор в нижнем листе. */
@Composable
private fun InputChoiceField(
    label: String,
    suggestions: List<String>,
    selected: String?,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
) {
    SheetField(
        label = label,
        text = selected ?: stringResource(R.string.select_input_placeholder),
        placeholder = selected == null,
        modifier = modifier,
    ) { onDismiss ->
        InputSingleSheet(
            title = label,
            hint = stringResource(R.string.select_input_hint),
            suggestions = suggestions,
            selected = selected,
            onSelect = onSelect,
            onDismiss = onDismiss,
        )
    }
}

/** Открытое мультиполе (сорт): значения из подсказок или свои, выбор в нижнем листе. */
@Composable
private fun MultiInputField(
    label: String,
    suggestions: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    SheetField(
        label = label,
        text = selected.joinToString(", ").ifEmpty { stringResource(R.string.select_input_placeholder) },
        placeholder = selected.isEmpty(),
    ) { onDismiss ->
        InputMultiSheet(
            title = label,
            hint = stringResource(R.string.select_input_hint),
            suggestions = suggestions,
            selected = selected,
            onToggle = onToggle,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Дата дегустации — календарь Material вместо самодельного поля.
 *
 * Хранится как локальная полночь выбранного дня: карточка показывает только
 * день ([DetailScreen] форматирует в зоне устройства), а UTC-полночь, которой
 * оперирует пикер, западнее Гринвича отрисовалась бы предыдущим числом.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TastedAtField(tastedAt: Long?, onChange: (Long?) -> Unit) {
    val label = stringResource(R.string.char_tasted_at)
    val dateFmt = remember { SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ru")) }
    SheetField(
        label = label,
        text = tastedAt?.let { dateFmt.format(Date(it)) } ?: stringResource(R.string.edit_tasted_at_placeholder),
        placeholder = tastedAt == null,
    ) { onDismiss ->
        val currentYear = remember { Year.now().value }
        val tastedAtYear = tastedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }
        val picker = rememberDatePickerState(
            // minOf — от краша: дату из будущего (кривые часы у чужого бэкапа)
            // rememberDatePickerState вне yearRange принять отказывается. Саму
            // запись это не меняет, пока пользователь не нажмёт «Готово».
            initialSelectedDateMillis = tastedAt?.let { toPickerDay(minOf(it, System.currentTimeMillis())) },
            // Дегустация не бывает в будущем: календарь гасит такие дни сам,
            // и валидировать выбранное потом уже не нужно. Списка годов до 2100
            // тоже не нужно — хватает текущего и предыдущего, а нижняя граница
            // опускается до года записи, иначе импортированную из ВК дату 2019-го
            // было бы не показать (rememberDatePickerState отвергает дату вне
            // диапазона).
            yearRange = minOf(tastedAtYear ?: currentYear, currentYear - 1)..currentYear,
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        isPastOrToday(utcTimeMillis, System.currentTimeMillis())
                }
            },
        )
        val colors = calendarColors()
        DatePickerDialog(
            onDismissRequest = onDismiss,
            // Скругление как у диалога выхода выше — материаловские 28 dp выбиваются.
            shape = RoundedCornerShape(24.dp),
            colors = colors,
            confirmButton = {
                TextButton(onClick = { onChange(picker.selectedDateMillis?.let(::fromPickerDay)); onDismiss() }) {
                    Text(stringResource(R.string.edit_tasted_at_confirm), fontWeight = FontWeight.Medium)
                }
            },
            // Дата опциональна — без явного действия её было бы не снять.
            dismissButton = if (tastedAt == null) null else ({
                TextButton(onClick = { onChange(null); onDismiss() }) {
                    Text(stringResource(R.string.edit_tasted_at_clear), color = BodegaColors.Muted)
                }
            }),
        ) {
            // Календарь читает типографику темы, а там заданы три стиля из
            // материаловского набора (см. Theme.kt) — остальное пришло бы Roboto.
            MaterialTheme(typography = rubikTypography()) {
                DatePicker(
                    state = picker,
                    // Заголовок «Выберите дату» дублирует лейбл поля, а материаловский
                    // headline — 32 sp системным шрифтом; оба заменены своим.
                    title = null,
                    headline = {
                        Text(
                            picker.selectedDateMillis?.let { dateFmt.format(Date(fromPickerDay(it))) }
                                ?: stringResource(R.string.edit_tasted_at_placeholder),
                            style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                            color = BodegaColors.Ink,
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                        )
                    },
                    colors = colors,
                )
            }
        }
    }
}

/** Палитра приложения для календаря: материаловская тянет фиолетовый M3-дефолт. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun calendarColors() = DatePickerDefaults.colors(
    containerColor = BodegaColors.Surface,
    headlineContentColor = BodegaColors.Ink,
    weekdayContentColor = BodegaColors.Muted,
    subheadContentColor = BodegaColors.Muted,
    navigationContentColor = BodegaColors.Muted,
    dayContentColor = BodegaColors.Ink,
    disabledDayContentColor = BodegaColors.Placeholder,
    selectedDayContentColor = Color.White,
    selectedDayContainerColor = BodegaColors.Teal,
    todayContentColor = BodegaColors.Teal,
    todayDateBorderColor = BodegaColors.Teal,
    yearContentColor = BodegaColors.Ink,
    currentYearContentColor = BodegaColors.Teal,
    selectedYearContentColor = Color.White,
    selectedYearContainerColor = BodegaColors.Teal,
    dividerColor = BodegaColors.Hairline,
)

/**
 * Типографика Rubik для материаловских компонентов, которые берут стили из темы.
 * Глобально заданы только три (Theme.kt) — здесь дописываются остальные, чтобы
 * календарь не был единственным экраном на системном шрифте.
 */
@Composable
private fun rubikTypography(): Typography = MaterialTheme.typography.let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontFamily = Rubik),
        titleSmall = t.titleSmall.copy(fontFamily = Rubik),
        titleMedium = t.titleMedium.copy(fontFamily = Rubik),
        bodyMedium = t.bodyMedium.copy(fontFamily = Rubik),
        bodySmall = t.bodySmall.copy(fontFamily = Rubik),
        labelMedium = t.labelMedium.copy(fontFamily = Rubik),
        labelSmall = t.labelSmall.copy(fontFamily = Rubik),
    )
}

/** День календаря (UTC-полночь) не позже сегодняшнего по часам устройства. */
internal fun isPastOrToday(utcTimeMillis: Long, nowMillis: Long): Boolean =
    utcTimeMillis <= toPickerDay(nowMillis)

/** Локальный день → UTC-полночь того же дня: пикер считает только в UTC. */
internal fun toPickerDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Обратное преобразование — см. [toPickerDay]. */
internal fun fromPickerDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun NumberField(label: String, value: Int?, modifier: Modifier = Modifier, onChange: (Int?) -> Unit) =
    LabeledField(
        label, value?.toString() ?: "",
        TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
        keyboard = KeyboardType.Number,
        modifier = modifier,
    ) { v -> onChange(v.filter { it.isDigit() }.toIntOrNull()) }

/**
 * Поле дробного числа. Сырой текст живёт в состоянии ViewModel, а не здесь:
 * «14,» и «.» — состояния ввода, и нераспарсенное значение должно блокировать
 * сохранение, а не тихо превращаться в NULL (см. [EditState.abvUnparsed]).
 */
@Composable
private fun DecimalField(label: String, text: String, error: String? = null, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledField(
            label, text,
            TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
            keyboard = KeyboardType.Decimal,
            onChange = onChange,
        )
        if (error != null) {
            Text(
                error,
                style = TextStyle(fontFamily = Rubik, fontSize = 12.sp),
                color = BodegaColors.Wine,
            )
        }
    }
}

@Preview(name = "Редактор — вино", showBackground = true, showSystemUi = true)
@Composable
private fun EditScreenPreview() {
    BodegaTheme {
        EditScreenContent(
            state = EditState(
                entry = Entry(
                    category = Category.WINE, name = "Barolo Riserva 2016",
                    grapes = listOf("Неббиоло"), wineType = "Тихое", color = "Красное",
                    sweetness = "Сухое", country = "Италия",
                    vintageYear = 2016, abvPercent = 14.0, rating = 9,
                ),
                isNew = true, dirty = true, abvText = "14.0",
            ),
            draftAvailable = true,
            resolvePhotoPath = { it },
            onField = {}, onAbvInput = {}, onRetryLoad = {}, onCategoryChange = {}, onPickPhoto = {},
            onDraftNote = {}, onPhotoClick = {}, onSave = {}, onClose = {},
        )
    }
}
