package ru.kaufd.bodega.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Контурные иконки из макета (viewBox 24×24), рисуются обводкой поверх Canvas.
// Пути взяты дословно из «Погреб - стартовый экран.dc.html».

private const val WINE_GLASS = "M7 4h10l-1 6a4 4 0 01-8 0L7 4z M12 14L12 20 M8 20L16 20"
private const val MUG = "M6 6h9v14h-9z M15 9h3a2 2 0 012 2v3a2 2 0 01-2 2h-3 M6 10h9"
private const val TUMBLER = "M7 5h10l-1.5 14h-7L7 5z M8 12h8"
private const val CAMERA =
    "M5 5h14a2 2 0 012 2v11a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2z " +
        "M8.5 8.9a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z M21 16l-5-5L5 20"
private const val BACK_ARROW = "M19 12H5 M11 6L5 12L11 18"
private const val PENCIL = "M4 20h4L18 10l-4-4L4 16z M13 7L17 11"
private const val CHEVRON_DOWN = "M6 9L12 15L18 9"
private const val SCAN_LINE =
    "M3 7V5a2 2 0 012-2h2 M17 3h2a2 2 0 012 2v2 M21 17v2a2 2 0 01-2 2h-2 " +
        "M7 21H5a2 2 0 01-2-2v-2 M7 12h10"
private const val ARROW_DOWN = "M12 5v14 M19 12l-7 7-7-7"
private const val ARROW_UP_RIGHT = "M7 17L17 7 M8 7h9v9"
private const val PLUS = "M12 5v14 M5 12h14"
private const val CROSS = "M18 6L6 18 M6 6l12 12"
private const val TRASH =
    "M4 7h16 M10 4h4 M9 7v12 M12 7v12 M15 7v12 M6 7l1 13h10l1-13"
private const val SHARE =
    "M12 3v12 M8 7l4-4 4 4 M5 13v6a2 2 0 002 2h10a2 2 0 002-2v-6"

/** Рисует SVG-path (координаты в системе 24×24) обводкой заданным цветом. */
@Composable
private fun StrokeIcon(
    pathData: String,
    size: Dp,
    color: Color,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(
                path, color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
fun WineGlassIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) =
    StrokeIcon(WINE_GLASS, size, color, strokeWidth, modifier)

@Composable
fun MugIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) =
    StrokeIcon(MUG, size, color, strokeWidth, modifier)

@Composable
fun TumblerIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) =
    StrokeIcon(TUMBLER, size, color, strokeWidth, modifier)

/**
 * Лупа. Окружность рисуется примитивом, а не SVG-дугой: PathParser разбирает
 * упакованные флаги дуги («a7 7 0 100 14») неверно и вместо круга даёт полумесяц.
 */
@Composable
fun SearchIcon(size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        drawCircle(color, radius = 7f * s, center = Offset(11f * s, 11f * s), style = Stroke(strokeWidth * s))
        drawLine(color, Offset(16.2f * s, 16.2f * s), Offset(21f * s, 21f * s), strokeWidth * s, StrokeCap.Round)
    }
}

@Composable
fun CameraIcon(size: Dp, color: Color, strokeWidth: Float = 1.6f, modifier: Modifier = Modifier) =
    StrokeIcon(CAMERA, size, color, strokeWidth, modifier)

@Composable
fun BackIcon(size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) =
    StrokeIcon(BACK_ARROW, size, color, strokeWidth, modifier)

@Composable
fun PencilIcon(size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) =
    StrokeIcon(PENCIL, size, color, strokeWidth, modifier)

@Composable
fun ChevronDownIcon(size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) =
    StrokeIcon(CHEVRON_DOWN, size, color, strokeWidth, modifier)

/** Рамка сканирования этикетки (кнопка рядом с поиском). */
@Composable
fun ScanIcon(size: Dp, color: Color, strokeWidth: Float = 1.9f, modifier: Modifier = Modifier) =
    StrokeIcon(SCAN_LINE, size, color, strokeWidth, modifier)

@Composable
fun ArrowDownIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) =
    StrokeIcon(ARROW_DOWN, size, color, strokeWidth, modifier)

@Composable
fun ArrowUpRightIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) =
    StrokeIcon(ARROW_UP_RIGHT, size, color, strokeWidth, modifier)

@Composable
fun PlusIcon(size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) =
    StrokeIcon(PLUS, size, color, strokeWidth, modifier)

@Composable
fun CloseIcon(size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) =
    StrokeIcon(CROSS, size, color, strokeWidth, modifier)

/** Корзина: удаление записи. */
@Composable
fun TrashIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) =
    StrokeIcon(TRASH, size, color, strokeWidth, modifier)

/** Стрелка из коробки: отдать запись наружу. */
@Composable
fun ShareIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) =
    StrokeIcon(SHARE, size, color, strokeWidth, modifier)

/**
 * Шестерёнка: настройки. Рисуется примитивами, а не SVG-путём, — по той же причине,
 * что и [SearchIcon]: PathParser путает флаги дуг, а зубцы здесь и есть дуги.
 */
@Composable
fun SettingsIcon(size: Dp, color: Color, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val center = Offset(12f * s, 12f * s)
        drawCircle(color, radius = 3.4f * s, center = center, style = Stroke(strokeWidth * s))
        drawCircle(color, radius = 7.4f * s, center = center, style = Stroke(strokeWidth * s))
        repeat(8) { i ->
            val angle = (i * 45f) * (PI / 180f).toFloat()
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                color,
                Offset(center.x + dx * 7.4f * s, center.y + dy * 7.4f * s),
                Offset(center.x + dx * 10f * s, center.y + dy * 10f * s),
                strokeWidth * s,
                StrokeCap.Round,
            )
        }
    }
}

/** Иконка категории для таб-бара / пустых состояний. */
@Composable
fun CategoryIcon(
    category: ru.kaufd.bodega.domain.Category,
    size: Dp,
    color: Color,
    strokeWidth: Float = 1.8f,
    modifier: Modifier = Modifier,
) = when (category) {
    ru.kaufd.bodega.domain.Category.WINE -> WineGlassIcon(size, color, strokeWidth, modifier)
    ru.kaufd.bodega.domain.Category.BEER -> MugIcon(size, color, strokeWidth, modifier)
    ru.kaufd.bodega.domain.Category.SPIRIT -> TumblerIcon(size, color, strokeWidth, modifier)
    ru.kaufd.bodega.domain.Category.COCKTAIL -> WineGlassIcon(size, color, strokeWidth, modifier)
}

/** Сетка 2×2 из залитых квадратиков — иконка «Все» / пустой коллекции. */
@Composable
fun GridIcon(box: Dp, gap: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(box * 2 + gap)) {
        val b = box.toPx(); val g = gap.toPx(); val r = b * 0.18f
        val step = b + g
        for (col in 0..1) for (row in 0..1) {
            drawRoundRect(
                color,
                topLeft = Offset(col * step, row * step),
                size = Size(b, b),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

/** Иконка фильтров: две линии с «ползунками» (tune). */
@Composable
fun SlidersIcon(size: Dp, lineColor: Color, knobFill: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val sw = 2f * s
        fun row(y: Float, knobX: Float) {
            drawLine(lineColor, Offset(5 * s, y * s), Offset(19 * s, y * s), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(knobFill, radius = 2.4f * s, center = Offset(knobX * s, y * s))
            drawCircle(lineColor, radius = 2.4f * s, center = Offset(knobX * s, y * s), style = Stroke(width = sw))
        }
        row(8f, 15f)
        row(16f, 9f)
    }
}

@Composable
fun ListIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        repeat(3) { index ->
            val y = (6 + index * 6) * s
            drawCircle(color, 1.25f * s, Offset(4f * s, y))
            drawLine(color, Offset(8f * s, y), Offset(21f * s, y), 1.8f * s, StrokeCap.Round)
        }
    }
}

@Composable
fun CheckIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val path = Path().apply {
            moveTo(5f * s, 12.5f * s)
            lineTo(9.5f * s, 17f * s)
            lineTo(19f * s, 7f * s)
        }
        drawPath(path, color, style = Stroke(2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ArrowRightIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        drawLine(color, Offset(5f * s, 12f * s), Offset(19f * s, 12f * s), 2f * s, StrokeCap.Round)
        drawLine(color, Offset(14f * s, 7f * s), Offset(19f * s, 12f * s), 2f * s, StrokeCap.Round)
        drawLine(color, Offset(14f * s, 17f * s), Offset(19f * s, 12f * s), 2f * s, StrokeCap.Round)
    }
}

@Composable
fun ResetIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        drawArc(color, 210f, 270f, false, Offset(5f * s, 5f * s), Size(14f * s, 14f * s), style = Stroke(1.8f * s, cap = StrokeCap.Round))
        drawLine(color, Offset(5f * s, 5f * s), Offset(5f * s, 10f * s), 1.8f * s, StrokeCap.Round)
        drawLine(color, Offset(5f * s, 5f * s), Offset(10f * s, 5f * s), 1.8f * s, StrokeCap.Round)
    }
}
