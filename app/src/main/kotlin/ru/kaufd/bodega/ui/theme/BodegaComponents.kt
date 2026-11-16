package ru.kaufd.bodega.ui.theme

import android.app.Activity
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kaufd.bodega.R

/**
 * Минимальная зона нажатия по спеке. Визуал кнопки может быть меньше: размер
 * ставится до `pressAction`, а разница добирается `padding` после него — тогда
 * фон и клип получают уже уменьшенную область, а палец попадает по полной.
 */
val TouchTargetMin = 44.dp

// ─────────────────────── Типографика макета ───────────────────────

/** Микро-лейбл секции: капс, разрядка. Моно тут не используется — только основная гарнитура. */
val LabelStyle = TextStyle(
    fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp,
)

/** Заголовок экрана в topbar. */
val ScreenTitleStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)

/** Заголовок секции на главном («Последняя дегустация», «В погребе»). */
val SectionTitleStyle = TextStyle(
    fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-.7).sp,
)

/** Подпись кнопки-«таблетки» (Отмена, Сброс, Балл). */
val ButtonLabelStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 12.sp)

/** Значение в поле ввода и в строках характеристик. */
val FieldValueStyle = TextStyle(fontFamily = Rubik, fontSize = 14.sp)

/** Заголовок секции: капс, приглушённый (лейблы полей и разделов). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = LabelStyle, color = BodegaColors.Label, modifier = modifier)
}

// ─────────────────────── Нажатие ───────────────────────

/**
 * Отклик на нажатие как в макете: `transform: scale(.97)` вместо ripple.
 * Ripple на стеклянных поверхностях макета нет — залитый круг поверх
 * полупрозрачной плашки читается как дефект, поэтому indication отключён.
 */
@Composable
fun Modifier.pressAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    scale: Float = .97f,
): Modifier = pressScale(scale, enabled) { interaction ->
    clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
}

/** То же нажатие для выбора (сегменты, чипы, пункты списка). */
@Composable
fun Modifier.pressSelect(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Tab,
    scale: Float = .97f,
): Modifier = pressScale(scale, enabled) { interaction ->
    selectable(
        selected = selected,
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

@Composable
private fun Modifier.pressScale(
    scale: Float,
    enabled: Boolean,
    attach: Modifier.(MutableInteractionSource) -> Modifier,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val factor by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = tween(110),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = factor; scaleY = factor }
        .attach(interaction)
}

// ─────────────────────── Поверхности макета ───────────────────────

/**
 * Мягкая тень макета (`0 Ydp Bdp rgba(...)`), нарисованная вручную.
 *
 * Платформенная `Modifier.shadow` здесь не годится: у полупрозрачных плашек она
 * видна сквозь заливку, и её внутренняя граница читается как светлый
 * прямоугольник внутри плашки. Рисуем стопкой расширяющихся скруглённых
 * прямоугольников — размытие приближённое, зато одинаковое на всех версиях.
 */
private fun Modifier.softShadow(shape: Shape, blur: Dp, offsetY: Dp, color: Color): Modifier = drawWithCache {
    val corner = (shape.createOutline(size, layoutDirection, this) as? Outline.Rounded)
        ?.roundRect?.topLeftCornerRadius?.x ?: 0f
    val dy = offsetY.toPx()
    val radius = blur.toPx() / 2f
    // Paint с нативным BlurMaskFilter строим один раз на размер: в drawBehind он
    // пересоздавался каждый кадр на каждой стеклянной плашке.
    val blurPaint = if (color.alpha > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }
    onDrawBehind {
        if (color.alpha == 0f) return@onDrawBehind
        if (blurPaint != null) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    0f, dy, size.width, size.height + dy, corner, corner, blurPaint,
                )
            }
        } else {
            // BlurMaskFilter на аппаратном холсте появился в API 28. Ниже — стопка
            // расширяющихся прямоугольников с квадратичным затуханием.
            val steps = 6
            fun falloff(t: Float) = (1f - t) * (1f - t)
            for (i in steps downTo 1) {
                val grow = radius * i / steps
                drawRoundRect(
                    color = color.copy(
                        alpha = color.alpha / 2f * (falloff((i - 1f) / steps) - falloff(i.toFloat() / steps)),
                    ),
                    topLeft = Offset(-grow, dy - grow),
                    size = Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = CornerRadius(corner + grow),
                )
            }
        }
    }
}

private fun Modifier.surface(
    shape: Shape,
    blur: Dp,
    offsetY: Dp,
    fill: Color,
    border: Color?,
    shadowColor: Color,
): Modifier = this
    .softShadow(shape, blur, offsetY, shadowColor)
    .clip(shape)
    .background(fill)
    .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)

/** Панель быстрых действий: самая прозрачная плашка, чтобы поле внутри читалось. */
fun Modifier.glassPanel(radius: Int = 26): Modifier = surface(
    RoundedCornerShape(radius.dp), 42.dp, 18.dp,
    Color.White.copy(.68f), Color.White.copy(.76f), BodegaColors.Shadow.copy(.13f),
)

/** «Жёлоб» под сегменты и переключатель вида. */
fun Modifier.glassTrack(shape: Shape = RoundedCornerShape(18.dp)): Modifier = surface(
    shape, 24.dp, 8.dp,
    Color.White.copy(.58f), Color.White.copy(.72f), BodegaColors.Shadow.copy(.08f),
)

/** Светлая кнопка/иконка/чип. */
fun Modifier.glassButton(shape: Shape = RoundedCornerShape(18.dp)): Modifier = surface(
    shape, 18.dp, 7.dp,
    Color.White.copy(.76f), Color.White.copy(.72f), BodegaColors.Shadow.copy(.10f),
)

/** Карточка контента: плотнее кнопок, без обводки. */
fun Modifier.glassCard(radius: Int = 24, alpha: Float = .86f): Modifier = surface(
    RoundedCornerShape(radius.dp), 30.dp, 12.dp,
    Color.White.copy(alpha), null, BodegaColors.Shadow.copy(.09f),
)

/** Первичное действие: плоская розовая заливка. */
fun Modifier.accentSurface(radius: Int = 18): Modifier = surface(
    RoundedCornerShape(radius.dp), 24.dp, 10.dp,
    BodegaColors.Accent, Color.White.copy(.24f), BodegaColors.AccentShadow.copy(.22f),
)

/** Кнопка сканирования этикетки. */
fun Modifier.darkSurface(radius: Int = 18): Modifier = surface(
    RoundedCornerShape(radius.dp), 21.dp, 9.dp,
    BodegaColors.DarkButton, Color.White.copy(.22f), Color(0xFF1D364B).copy(.19f),
)

/** Бирюзовая кнопка: «выбрано» в роли действия (правка записи). */
fun Modifier.tealSurface(radius: Int = 18): Modifier = surface(
    RoundedCornerShape(radius.dp), 24.dp, 10.dp,
    BodegaColors.Teal, Color.White.copy(.24f), BodegaColors.Teal.copy(.24f),
)

/** Разрушающее действие: красная заливка (удаление). */
fun Modifier.dangerSurface(radius: Int = 18): Modifier = surface(
    RoundedCornerShape(radius.dp), 24.dp, 10.dp,
    BodegaColors.Danger, Color.White.copy(.24f), BodegaColors.Danger.copy(.26f),
)

/** Тёмная пилюля тоста (`.toast-message` в макете). */
private fun Modifier.toastSurface(): Modifier = surface(
    RoundedCornerShape(50), 36.dp, 14.dp,
    BodegaColors.Ink.copy(.94f), Color.White.copy(.18f), Color(0xFF211B19).copy(.28f),
)

/**
 * Поле ввода. Форму задаёт тонкая холодная граница, а не белая на белом:
 * в макете поле отличается от фона именно ей. В фокусе — бирюзовая рамка с кольцом.
 */
fun Modifier.fieldSurface(focused: Boolean = false, radius: Int = 18): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this
        .clip(shape)
        .background(Color.White.copy(.82f))
        .then(if (focused) Modifier.border(4.dp, BodegaColors.Teal.copy(.10f), shape) else Modifier)
        .border(1.dp, if (focused) BodegaColors.Teal.copy(.48f) else BodegaColors.FieldBorder, shape)
}

/**
 * Пока экран в композиции — иконки статус-бара такие, как он просит; при уходе
 * возвращаем прежние. Светлые нужны поверх обложки (тёмные на насыщенном фото
 * не читаются), тёмные — на бумажных экранах.
 */
@Composable
fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(darkIcons) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = darkIcons
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/** Шапка вложенного экрана: полупрозрачная бумага с волосяной линией снизу. */
fun Modifier.screenTopBar(): Modifier = this.drawBehind {
    drawRect(BodegaColors.Paper.copy(alpha = .88f))
    val line = 1.dp.toPx()
    drawRect(
        color = Color(0x1A799AAA),
        topLeft = Offset(0f, size.height - line),
        size = size.copy(height = line),
    )
}

/**
 * Шапка вложенного экрана: кнопка «назад» слева, заголовок по центру, свои кнопки
 * справа. Одна на карточку, фильтры и настройки — копии успели разъехаться по
 * размеру кнопки и кеглю иконки.
 *
 * Заголовок необязателен: у карточки его нет, там шапка лежит поверх обложки.
 * Редактор сюда не подходит — слева у него текстовая «Отмена», а не иконка.
 */
@Composable
fun ScreenTopBar(
    onBack: () -> Unit,
    title: String? = null,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier.fillMaxWidth().screenTopBar().statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val backLabel = stringResource(R.string.back)
        Box(
            Modifier.align(Alignment.CenterStart).size(46.dp)
                .pressAction(onBack, enabled = backEnabled)
                .glassButton()
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) { BackIcon(size = 22.dp, color = BodegaColors.Ink) }
        if (title != null) {
            Text(
                title,
                style = ScreenTitleStyle,
                color = BodegaColors.Ink,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

private class Glow(val brush: Brush, val center: Offset, val radius: Float)

/**
 * Фон экрана: холодная бумага с тёплыми пятнами по углам. Градиенты строим один
 * раз на размер — ShaderBrush кэширует нативный шейдер внутри себя, и в
 * drawBehind этот кэш обнулялся каждый кадр скролла.
 */
fun Modifier.ambientBackground(): Modifier = this.drawWithCache {
    fun glow(color: Color, cx: Float, cy: Float, r: Float): Glow {
        val center = Offset(size.width * cx, size.height * cy)
        val radius = size.minDimension * r
        return Glow(
            Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            center,
            radius,
        )
    }
    val glows = listOf(
        glow(BodegaColors.TealBright.copy(alpha = .22f), -.08f, .28f, .72f),
        glow(BodegaColors.Coral.copy(alpha = .18f), 1.08f, .58f, .68f),
        glow(BodegaColors.Rating.copy(alpha = .14f), .45f, .84f, .62f),
    )
    onDrawBehind {
        drawRect(BodegaColors.Paper)
        glows.forEach { drawCircle(brush = it.brush, radius = it.radius, center = it.center) }
    }
}

// ─────────────────────── Общие элементы ───────────────────────

/** Пилюля-чип фильтров: выбранная — бирюзовая заливка, обычная — стекло. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Мультивыбор (фасеты) — Checkbox, взаимоисключающий выбор — RadioButton. */
    role: Role = Role.Checkbox,
) {
    val shape = RoundedCornerShape(50)
    // Внешний бокс — только цель нажатия (48dp по гайдлайнам). Сама пилюля
    // внутри и остаётся низкой, как в макете: иначе чип раздувается в «колобок».
    Box(
        modifier
            .pressSelect(selected, onClick, role = role, scale = .96f)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(
                    if (selected) {
                        Modifier.surface(shape, 18.dp, 7.dp, BodegaColors.Teal, null, BodegaColors.Teal.copy(.18f))
                    } else {
                        Modifier.glassButton(shape)
                    }
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = TextStyle(fontFamily = Rubik, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = if (selected) Color.White else BodegaColors.Ink,
            )
        }
    }
}

/**
 * Пилюля поверх обложки: капс на стекле, моно — для чисел. Одна на витрину
 * главного экрана и на шапку карточки, иначе они расходятся по кеглю и радиусу.
 */
@Composable
fun FeaturePill(text: String, mono: Boolean = false) {
    Box(Modifier.glassCard(50, alpha = .82f).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text,
            fontFamily = if (mono) PlexMono else Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = if (mono) 0.sp else 1.2.sp,
            color = BodegaColors.Ink,
        )
    }
}

/**
 * Ряд из [count] равных сегментов с одной плашкой выбора: при смене [selected] она
 * переезжает к новому сегменту, а не гаснет в одном и вспыхивает в другом. Сегменты
 * равной ширины, поэтому место плашки считается из индекса, без замеров.
 *
 * [modifier] — дорожка вместе с её внутренним отступом; [indicatorShape] — по
 * правилу «внутренний радиус = внешний − отступ». [indicatorInset] сужает плашку
 * внутри сегмента, когда видимая кнопка меньше её тап-таргета.
 */
@Composable
fun SlidingSegmentRow(
    count: Int,
    selected: Int,
    indicatorShape: Shape,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    indicatorInset: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val segment = (maxWidth - spacing * (count - 1)) / count
        val offset by animateDpAsState(
            (segment + spacing) * selected,
            // Без отскока: перелетев край, плашка вылезала бы за дорожку.
            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
            label = "segment-indicator",
        )
        Box(
            Modifier.matchParentSize().wrapContentWidth(Alignment.Start)
                .offset(x = offset).width(segment).padding(indicatorInset)
                .clip(indicatorShape).background(BodegaColors.Teal),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/**
 * Цвет подписи над плашкой выбора. Уходящий сегмент гаснет сразу, а новый белеет с
 * задержкой — к приезду плашки: белая подпись раньше неё пропала бы на белой дорожке.
 */
@Composable
fun segmentTint(active: Boolean, idle: Color = BodegaColors.Muted, selected: Color = Color.White): Color =
    animateColorAsState(
        if (active) selected else idle,
        tween(durationMillis = 160, delayMillis = if (active) 160 else 0),
        label = "segment-tint",
    ).value

/**
 * Сегмент слитого контрола категорий; плашку выбора под ним рисует
 * [SlidingSegmentRow]. [count] == null — сегмент без счётчика (редактор): вторая
 * строка не рисуется, и описание берётся из самой подписи.
 * [compact] — ниже и мельче, для главного экрана под небольшой обложкой.
 */
@Composable
fun Segment(
    label: String,
    active: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    compact: Boolean = false,
) {
    // Счётчик нарисован отдельной строкой — для TalkBack склеиваем его с названием
    // в одно описание вкладки, иначе читаются два несвязанных фрагмента.
    val description = count?.let { pluralStringResource(R.plurals.segment_description, it, label, it) }
    Column(
        modifier
            .pressSelect(active, onSelect, scale = .95f)
            .defaultMinSize(minHeight = if (compact) TouchTargetMin else 54.dp)
            .then(
                if (description != null) {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = description }
                } else {
                    Modifier
                }
            )
            .padding(vertical = if (compact) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = Rubik, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Medium),
            color = segmentTint(active),
            textAlign = TextAlign.Center,
        )
        if (count != null) {
            Text(
                "$count",
                style = TextStyle(fontFamily = PlexMono, fontSize = 9.sp),
                color = segmentTint(active, BodegaColors.Placeholder, Color.White.copy(alpha = .72f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (compact) 0.dp else 6.dp),
            )
        }
    }
}

/** Белый круглый ползунок с бирюзовой обводкой: оценка в редакторе и диапазон в фильтрах. */
@Composable
fun RatingThumb() {
    Box(
        Modifier.size(22.dp)
            .shadow(4.dp, CircleShape, ambientColor = BodegaColors.Teal, spotColor = BodegaColors.Teal)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, BodegaColors.Teal, CircleShape),
    )
}

/**
 * Поле ввода макета: плейсхолдер отдельной нодой, бирюзовая рамка в фокусе.
 * Единственная реализация на все экраны — иначе поля расходятся по мелочам.
 */
@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    /** Лейбл — отдельная нода, при фокусе TalkBack её не читает; связываем описанием. */
    description: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = FieldValueStyle,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = ImeAction.Default,
    /** Действие клавиши Done — для полей ввода внутри нижних листов. */
    onDone: (() -> Unit)? = null,
    /** Маскировка — для секретов вроде API-ключа. */
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** Значение видно, но не правится: поле, которым в этот момент занят кто-то другой. */
    readOnly: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier.fillMaxWidth().fieldSurface(focused)
            .defaultMinSize(minHeight = 50.dp)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        contentAlignment = if (minLines > 1) Alignment.TopStart else Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = textStyle, color = BodegaColors.Placeholder)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle.copy(color = BodegaColors.Ink),
            cursorBrush = SolidColor(BodegaColors.Teal),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
            keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        )
    }
}

/**
 * Тост макета: тёмная пилюля у нижнего края экрана. Это визуал для
 * [androidx.compose.material3.SnackbarHost] — очередь сообщений, тайминги и
 * озвучивание остаются материаловскими, своё здесь только оформление.
 */
@Composable
fun BodegaSnackbar(data: SnackbarData) {
    Row(
        // Высоту задаёт heightIn, а не вертикальный padding: тап-таргет 48.dp
        // у «Отменить» иначе прибавлялся бы к отступам и раздувал пилюлю.
        Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            .toastSurface()
            .heightIn(min = 48.dp)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            data.visuals.message,
            style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // fill = false: короткое сообщение не растягивает пилюлю на всю ширину,
            // длинное — переносится внутри пилюли, а не выдавливает «Отменить».
            modifier = Modifier.weight(1f, fill = false),
        )
        data.visuals.actionLabel?.let { label ->
            Text(
                label,
                style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = BodegaColors.TealBright,
                maxLines = 1,
                modifier = Modifier
                    .pressAction({ data.performAction() })
                    .minimumInteractiveComponentSize(),
            )
        }
    }
}

/**
 * Экран отказа чтения: объяснение и повтор. Отдельное состояние нужно, чтобы
 * «не смогли прочитать» не выглядело как «ничего не найдено».
 */
@Composable
fun LoadFailed(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = Rubik, fontSize = 15.sp),
            color = BodegaColors.Muted,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.retry),
            style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 13.sp),
            color = Color.White,
            modifier = Modifier
                .pressAction(onRetry)
                .accentSurface(18)
                .padding(horizontal = 20.dp, vertical = 13.dp),
        )
    }
}
