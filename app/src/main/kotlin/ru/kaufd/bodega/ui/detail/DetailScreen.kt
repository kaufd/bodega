package ru.kaufd.bodega.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.formatAbv
import ru.kaufd.bodega.ui.components.ConfirmDialog
import ru.kaufd.bodega.ui.rememberPhotoPathResolver
import ru.kaufd.bodega.ui.theme.*
import ru.kaufd.bodega.ui.util.entrySubtitle
import ru.kaufd.bodega.ui.util.formatPrice
import ru.kaufd.bodega.ui.util.shareEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NoteInk = Color(0xFF49362F)

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolvePhotoPath = rememberPhotoPathResolver()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.detail_share)
    // Закрытие по состоянию, а не по колбэку из ViewModel: после пересоздания
    // Activity композиция получает актуальный onBack и уходит с экрана сама.
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    DetailScreenContent(
        state = state,
        resolvePhotoPath = resolvePhotoPath,
        onBack = onBack,
        onEdit = onEdit,
        onShare = { shareEntry(context, it, resolvePhotoPath, shareTitle) },
        onDelete = viewModel::delete,
        onRetryLoad = viewModel::onRetryLoad,
    )
}

/** Stateless-версия карточки: пригодна для @Preview и тестов. */
@Composable
fun DetailScreenContent(
    state: DetailState,
    resolvePhotoPath: (String) -> String,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onShare: (Entry) -> Unit,
    onDelete: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val e = (state.content as? DetailContent.Ready)?.entry
    // Переход владения записью уже начался: уход с экрана и повторные действия
    // в этот момент — источник гонок, поэтому экран заморожен до его конца.
    BackHandler(enabled = state.deleting) {}

    if (confirmDelete && e != null) {
        ConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            text = stringResource(R.string.detail_delete_text, e.name),
            confirmLabel = stringResource(R.string.detail_delete),
            dismissLabel = stringResource(R.string.detail_cancel),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
            confirmEnabled = !state.deleting,
        )
    }

    Scaffold(
        modifier = Modifier.ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            // Заголовка нет: название записи крупно написано на самой обложке.
            ScreenTopBar(onBack = onBack, backEnabled = !state.deleting) {
                // Править и удалять можно только загруженную запись; «назад» доступна всегда.
                if (e != null && !state.deleting) {
                    // Стеклянная, а не цветная: делиться — не разрушительное действие.
                    val shareLabel = stringResource(R.string.detail_share)
                    Box(
                        Modifier.size(46.dp)
                            .pressAction({ onShare(e) })
                            .glassButton()
                            .semantics { contentDescription = shareLabel },
                        contentAlignment = Alignment.Center,
                    ) { ShareIcon(size = 19.dp, color = BodegaColors.Ink) }
                    val editLabel = stringResource(R.string.detail_edit)
                    Box(
                        Modifier.size(46.dp)
                            .pressAction({ onEdit(e.id) })
                            .tealSurface(18)
                            .semantics { contentDescription = editLabel },
                        contentAlignment = Alignment.Center,
                    ) { PencilIcon(size = 19.dp, color = Color.White) }
                    val deleteLabel = stringResource(R.string.detail_delete)
                    Box(
                        Modifier.size(46.dp)
                            .pressAction({ confirmDelete = true })
                            .dangerSurface(18)
                            .semantics { contentDescription = deleteLabel },
                        contentAlignment = Alignment.Center,
                    ) { TrashIcon(size = 19.dp, color = Color.White) }
                }
            }
        },
    ) { padding ->
        when (state.content) {
            DetailContent.Loading -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = BodegaColors.Muted, strokeWidth = 2.dp)
            }

            DetailContent.NotFound -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(R.string.detail_not_found),
                    style = TextStyle(fontFamily = Rubik, fontSize = 15.sp),
                    color = BodegaColors.Muted,
                )
            }

            // Отказ чтения — не «записи нет»: предлагаем повтор, а не тупик.
            DetailContent.Failed -> Box(Modifier.padding(padding).fillMaxSize()) {
                LoadFailed(stringResource(R.string.detail_load_failed), onRetryLoad)
            }

            is DetailContent.Ready -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                val entry = state.content.entry
                if (state.deleteFailed) {
                    Text(
                        stringResource(R.string.detail_delete_failed),
                        style = TextStyle(fontFamily = Rubik, fontSize = 14.sp),
                        color = BodegaColors.Wine,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    )
                }
                Hero(entry, resolvePhotoPath)
                EntryTitle(entry)
                if (entry.rating != null) RatingCard(entry.rating)
                if (entry.category == Category.COCKTAIL) {
                    entry.ingredientsText.takeIf { it.isNotBlank() }?.let { RecipeCard(stringResource(R.string.cocktail_ingredients), it) }
                    entry.preparation?.takeIf { it.isNotBlank() }?.let { RecipeCard(stringResource(R.string.cocktail_preparation), it) }
                }
                Characteristics(entry)
                if (entry.category != Category.COCKTAIL && !entry.note.isNullOrBlank()) NoteCard(entry.note)
            }
        }
    }
}

@Composable
private fun Hero(e: Entry, resolvePhotoPath: (String) -> String) {
    val paths = e.photos
    val pagerState = rememberPagerState(pageCount = { paths.size })
    var zoomOpen by remember { mutableStateOf(false) }
    if (zoomOpen && paths.isNotEmpty()) {
        // Полноэкранно открывается именно та фотография, что видна в пейджере:
        // листание внутри отдано пинчу и панораме, страниц там нет.
        val current = paths[pagerState.currentPage.coerceIn(paths.indices)]
        FullscreenPhoto(
            model = remember(current) { resolvePhotoPath(current) },
            contentDescription = stringResource(R.string.entry_photo, e.name),
            onDismiss = { zoomOpen = false },
        )
    }
    Box(
        Modifier.fillMaxWidth().height(330.dp)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(categoryTint(e.category), BodegaColors.Teal, BodegaColors.Ink))),
    ) {
        if (paths.isNotEmpty()) {
            val openLabel = stringResource(R.string.photo_open)
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = remember(paths, page) { resolvePhotoPath(paths[page]) },
                    contentDescription = stringResource(
                        R.string.entry_photo_position, page + 1, paths.size, e.name,
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .clickable(onClickLabel = openLabel, role = Role.Button) { zoomOpen = true },
                )
            }
        }
        if (paths.size > 1) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(20.dp)
                    // Своя плашка вместо прежнего затемнения на всю высоту: оно
                    // держалось на белом заголовке, а ради одних точек гасить
                    // фотографию незачем.
                    .clip(RoundedCornerShape(999.dp))
                    .background(BodegaColors.Ink.copy(alpha = .38f))
                    .padding(horizontal = 9.dp, vertical = 7.dp)
                    // Точки декоративны: позиция уже озвучена подписью фотографии.
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(paths.size) { page ->
                    val active = page == pagerState.currentPage
                    Box(
                        Modifier.size(if (active) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (active) .95f else .45f)),
                    )
                }
            }
        }
        val label = stringResource(e.category.displayNameRes())
        Row(Modifier.align(Alignment.TopStart).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill(label.uppercase())
            e.vintageYear?.let { year -> FeaturePill("$year", mono = true) }
        }
    }
}

/**
 * Название и подзаголовок под фотографией, а не поверх неё: на этикетке уже есть
 * свой набранный текст, и белый заголовок читался как ещё одна надпись на ней.
 */
@Composable
private fun EntryTitle(e: Entry) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp)) {
        val subtitle = entrySubtitle(e)
        if (subtitle.isNotEmpty()) SectionLabel(subtitle)
        Text(
            e.name,
            fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 31.sp,
            letterSpacing = (-0.8).sp, color = BodegaColors.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Полноэкранный просмотр фото: pinch-to-zoom, панорама, double-tap, тап по фону — закрыть. */
@Composable
private fun FullscreenPhoto(model: Any, contentDescription: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        var boxW by remember { mutableStateOf(0) }
        var boxH by remember { mutableStateOf(0) }

        fun clampOffsets() {
            val maxX = (boxW * (scale - 1f) / 2f).coerceAtLeast(0f)
            val maxY = (boxH * (scale - 1f) / 2f).coerceAtLeast(0f)
            offsetX = offsetX.coerceIn(-maxX, maxX)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }

        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .onSizeChanged { boxW = it.width; boxH = it.height }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                            else scale = 2.5f
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                        clampOffsets()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    translationX = offsetX, translationY = offsetY,
                ),
            )
        }
    }
}

@Composable
private fun RatingCard(rating: Int) {
    Row(
        Modifier.padding(horizontal = 20.dp).padding(top = 20.dp).fillMaxWidth()
            .glassCard(24).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$rating", style = TextStyle(fontFamily = PlexMono, fontSize = 42.sp), color = BodegaColors.Wine)
            Text(
                "/${EntryLimits.RATING.last}",
                style = TextStyle(fontFamily = PlexMono, fontSize = 13.sp),
                color = BodegaColors.Placeholder,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            SectionLabel(stringResource(R.string.char_my_rating))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(EntryLimits.RATING.last) { i ->
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50))
                            .background(if (i < rating) BodegaColors.Teal else BodegaColors.Teal.copy(alpha = .14f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Characteristics(e: Entry) {
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")) }
    // Triple(ключ, значение, моно-значение?)
    val rows = buildList {
        when (e.category) {
            Category.WINE -> {
                e.wineType?.let { add(Triple(stringResource(R.string.char_type), it, false)) }
                e.color?.let { add(Triple(stringResource(R.string.char_color), it, false)) }
                e.sweetness?.let { add(Triple(stringResource(R.string.char_sweetness), it, false)) }
                e.grapes.takeIf { it.isNotEmpty() }?.let { add(Triple(stringResource(R.string.char_grape), it.joinToString(", "), false)) }
                e.country?.let { add(Triple(stringResource(R.string.char_country), it, false)) }
                e.vintageYear?.let { add(Triple(stringResource(R.string.char_vintage), "$it", true)) }
                e.bottlingYear?.let { add(Triple(stringResource(R.string.char_bottling), "$it", true)) }
                e.servingTemp?.let { add(Triple(stringResource(R.string.char_serving_temp), it, true)) }
            }
            Category.BEER -> {
                e.style?.let { add(Triple(stringResource(R.string.char_style), it, false)) }
            }
            Category.COCKTAIL -> Unit
            Category.SPIRIT -> {
                e.type?.let { add(Triple(stringResource(R.string.char_type), it, false)) }
            }
        }
        e.abvPercent?.let { add(Triple(stringResource(R.string.char_abv), "${formatAbv(it)} %", true)) }
        e.priceRub?.let { add(Triple(stringResource(R.string.char_price), "${formatPrice(it)} ₽", true)) }
        if (e.category != Category.COCKTAIL) {
            e.tastedAt?.let { add(Triple(stringResource(R.string.char_tasted_at), dateFmt.format(Date(it)), true)) }
        }
    }
    if (rows.isEmpty()) return
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
        SectionLabel(stringResource(R.string.char_characteristics))
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().glassCard(24).padding(horizontal = 16.dp)) {
            rows.forEachIndexed { i, (k, v, mono) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    // Зазор и вес вместо SpaceBetween: у вина в «Сорте» бывает пять
                    // сортов, и многострочное значение налезало на лейбл.
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(k, style = TextStyle(fontFamily = Rubik, fontSize = 13.sp), color = BodegaColors.Muted)
                    Text(
                        v,
                        style = if (mono) TextStyle(fontFamily = PlexMono, fontSize = 13.sp)
                        else TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 18.sp),
                        color = BodegaColors.Ink,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (i < rows.lastIndex) HorizontalDivider(color = BodegaColors.Hairline)
            }
        }
    }
}

@Composable
private fun RecipeCard(title: String, text: String) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp).fillMaxWidth().glassCard(24).padding(20.dp)) {
        SectionLabel(title)
        Spacer(Modifier.height(12.dp))
        Text(text, style = TextStyle(fontFamily = Rubik, fontSize = 15.sp, lineHeight = 24.sp), color = BodegaColors.Ink)
    }
}

@Composable
private fun NoteCard(note: String) {
    Column(
        Modifier.padding(horizontal = 20.dp).padding(top = 20.dp).fillMaxWidth()
            .glassCard(24).padding(20.dp),
    ) {
        SectionLabel(stringResource(R.string.char_note))
        Spacer(Modifier.height(12.dp))
        Text(
            note,
            style = TextStyle(fontFamily = Rubik, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 24.sp),
            color = NoteInk,
        )
    }
}

@Preview(name = "Карточка", showBackground = true, showSystemUi = true)
@Composable
private fun DetailScreenPreview() {
    BodegaTheme {
        DetailScreenContent(
            state = DetailState(DetailContent.Ready(Entry(
                id = 1, category = Category.WINE, name = "Barolo Riserva 2016",
                wineType = "Тихое", color = "Красное", sweetness = "Сухое",
                grapes = listOf("Неббиоло"), country = "Италия · Пьемонт",
                vintageYear = 2016, abvPercent = 14.0, priceRub = 8900,
                rating = 9, tastedAt = 1_700_000_000_000,
                note = "Букет из вяленой розы, дёгтя и сушёной вишни. Плотный танин, долгое послевкусие.",
                createdAt = 1,
            ))),
            resolvePhotoPath = { it },
            onBack = {}, onEdit = {}, onShare = {}, onDelete = {}, onRetryLoad = {},
        )
    }
}
