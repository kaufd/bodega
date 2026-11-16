package ru.kaufd.bodega.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kaufd.bodega.ui.theme.*

/**
 * Потолок высоты нижнего листа — доля доступного места. Сам лист тянется по
 * содержимому; доля ограничивает только длинные справочники (сорта, страны),
 * чтобы они не разворачивались на весь экран.
 */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.66f

/**
 * Поле-триггер выбора: значение (или плейсхолдер) + шеврон; по клику открывает лист.
 * Пока лист открыт, поле подсвечено как в фокусе — по макету открытого селекта.
 */
@Composable
fun SelectTrigger(
    text: String,
    placeholder: Boolean,
    open: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .pressAction(onClick, scale = .99f)
            .fieldSurface(focused = open)
            .defaultMinSize(minHeight = 50.dp)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = FieldValueStyle,
            color = if (placeholder) BodegaColors.Placeholder else BodegaColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ChevronDownIcon(size = 18.dp, color = BodegaColors.Muted, strokeWidth = 1.8f)
    }
}

/**
 * Оформление нижнего листа: бумажный фон и скругление макета. Одно на все листы —
 * иначе правка радиуса или цвета уезжает в одном из пяти.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodegaSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    title: String,
    input: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BodegaColors.Paper,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        SheetFrame(title, input, content)
    }
}

/**
 * Закрытие с анимацией: сначала лист уезжает вниз, и только потом снимается
 * состояние открытия — иначе он исчезает рывком.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun CoroutineScope.hideThen(sheetState: SheetState, onDismiss: () -> Unit) {
    launch { sheetState.hide() }.invokeOnCompletion {
        if (!sheetState.isVisible) onDismiss()
    }
}

/** Выбор одного значения из списка через нижний лист. Повторный выбор снимает значение. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleSelectSheet(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BodegaSheet(sheetState, onDismiss, title) {
        items(options) { opt ->
            val active = opt == selected
            SheetRow(opt, active, role = Role.RadioButton) {
                onSelect(if (active) null else opt)
                scope.hideThen(sheetState, onDismiss)
            }
        }
    }
}

/** Мультивыбор из списка через нижний лист; лист остаётся открытым при отметке. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectSheet(
    title: String,
    options: List<String>,
    selected: Set<String>,
    /** Задан — над списком появляется поиск по значениям (для длинных справочников). */
    hint: String? = null,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    BodegaSheet(
        sheetState,
        onDismiss,
        title,
        // Своих значений здесь нет: фильтровать можно только по тому, что есть
        // в коллекции, поэтому ввод лишь сужает список.
        input = { if (hint != null) SheetInput(query, hint, onValue = { query = it }, onDone = {}) },
    ) {
        items(options.filterByQuery(query)) { opt ->
            SheetRow(opt, opt in selected, role = Role.Checkbox) { onToggle(opt) }
        }
    }
}

/** Выбор одного значения из подсказок ИЛИ ввод своего (открытый набор). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSingleSheet(
    title: String,
    hint: String,
    suggestions: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = suggestions.filterByQuery(query)

    fun commit(value: String) {
        val v = value.trim()
        val normalized = suggestions.find { it.equals(v, ignoreCase = true) } ?: v
        onSelect(normalized.ifBlank { null })
        scope.hideThen(sheetState, onDismiss)
    }

    BodegaSheet(
        sheetState,
        onDismiss,
        title,
        input = { SheetInput(query, hint, onValue = { query = it }, onDone = { if (query.isNotBlank()) commit(query) }) },
    ) {
        items(filtered) { opt ->
            SheetRow(opt, opt == selected, role = Role.RadioButton) { commit(opt) }
        }
    }
}

/** Мультивыбор из подсказок ИЛИ ввод своих значений (открытый набор). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputMultiSheet(
    title: String,
    hint: String,
    suggestions: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    // выбранные кастомные значения тоже показываем в списке
    val options = (selected + suggestions).distinct().filterByQuery(query)

    fun addCustom() {
        val v = query.trim()
        if (v.isEmpty()) return
        val normalized = (selected + suggestions).find { it.equals(v, ignoreCase = true) } ?: v
        if (selected.none { it.equals(normalized, ignoreCase = true) }) onToggle(normalized)
        query = ""
    }

    BodegaSheet(
        sheetState,
        onDismiss,
        title,
        input = { SheetInput(query, hint, onValue = { query = it }, onDone = { addCustom() }) },
    ) {
        items(options) { opt ->
            SheetRow(
                opt, selected.any { it.equals(opt, ignoreCase = true) },
                role = Role.Checkbox,
            ) { onToggle(opt) }
        }
    }
}

/** Фильтрация подсказок по подстроке запроса (регистронезависимо). */
private fun List<String>.filterByQuery(query: String): List<String> {
    val q = query.trim()
    return if (q.isEmpty()) this else filter { it.contains(q, ignoreCase = true) }
}

/**
 * Строка ввода внутри листа: поиск среди подсказок или ввод своего значения.
 * Само поле — общий [FormField]: высота, рамка в фокусе и плейсхолдер должны
 * совпадать с полями формы, иначе лист выбивается из остальных экранов.
 */
@Composable
private fun SheetInput(value: String, hint: String, onValue: (String) -> Unit, onDone: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) {
        FormField(
            value = value,
            onChange = onValue,
            placeholder = hint,
            description = hint,
            imeAction = ImeAction.Done,
            onDone = onDone,
        )
    }
}

/** Лист действий (несколько кнопок), высота по содержимому. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BodegaSheet(sheetState, onDismiss, title) {
        items(actions) { (label, action) ->
            SheetRow(label, active = false, role = Role.Button) {
                action()
                scope.hideThen(sheetState, onDismiss)
            }
        }
    }
}

/**
 * Каркас листа: заголовок, необязательное поле ввода и список.
 *
 * Высота — по содержимому, но не выше [SHEET_MAX_HEIGHT_FRACTION] доступного
 * места. Поэтому у списка нет `weight`: с ним колонка забирала бы весь остаток
 * и лист с тремя пунктами всё равно раскрывался бы на треть экрана.
 *
 * Список ленивый: справочники сортов и стран — это сотни строк, а видно из них
 * от силы десяток. LazyColumn так же тянется по содержимому, пока оно меньше
 * потолка, поэтому короткие листы выглядят как прежде.
 */
@Composable
private fun SheetFrame(
    title: String,
    input: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * SHEET_MAX_HEIGHT_FRACTION)) {
            SheetHeading(title)
            input()
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 28.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SheetHeading(title: String) {
    Text(
        title,
        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        color = BodegaColors.Ink,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
    )
}

/**
 * Строка листа: значение слева, галочка справа у выбранного.
 *
 * [role] отдаёт TalkBack смысл строки: галочка нарисована текстом «✓» и сама по
 * себе состоянием не является, поэтому выбор сообщается через selectable.
 * Для листа действий ([ActionSheet]) роль — Button, и строка просто кликабельна.
 */
@Composable
private fun SheetRow(
    label: String,
    active: Boolean,
    role: Role = Role.Checkbox,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .then(
                if (role == Role.Button) Modifier.pressAction(onClick, scale = .98f)
                else Modifier.pressSelect(active, onClick, role = role, scale = .98f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) BodegaColors.Teal else Color.Transparent)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 15.sp),
            color = if (active) Color.White else BodegaColors.Ink,
        )
        if (active) {
            Spacer(Modifier.width(12.dp))
            CheckIcon(18.dp, Color.White)
        }
    }
}
