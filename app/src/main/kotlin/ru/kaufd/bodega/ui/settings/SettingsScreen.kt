package ru.kaufd.bodega.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import ru.kaufd.bodega.domain.RecognitionConfig
import ru.kaufd.bodega.ui.theme.FormField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.kaufd.bodega.R
import ru.kaufd.bodega.ui.QuoteFooter
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.CheckIcon
import ru.kaufd.bodega.ui.theme.BodegaSnackbar
import ru.kaufd.bodega.ui.theme.BodegaTheme
import ru.kaufd.bodega.ui.theme.Rubik
import ru.kaufd.bodega.ui.theme.ScreenTopBar
import ru.kaufd.bodega.ui.theme.SectionLabel
import ru.kaufd.bodega.ui.theme.ambientBackground
import ru.kaufd.bodega.ui.theme.glassCard
import ru.kaufd.bodega.ui.theme.pressAction
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Имя предлагается системе, окончательное решение — за пользователем.
    val fileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.export(uri.toString())
    }
    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.import(uri.toString())
    }

    val message = state.result?.let { messageFor(it) }
    LaunchedEffect(state.result) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.onResultShown()
        }
    }

    SettingsScreenContent(
        state = state,
        snackbar = snackbar,
        onExport = { createFile.launch(fileName) },
        // Не "application/zip": часть провайдеров отдаёт архивы как octet-stream,
        // и при узком фильтре файл не выбрать вовсе.
        onImport = { openFile.launch(arrayOf("*/*")) },
        onRecognitionChange = viewModel::onRecognitionChange,
        onClose = onClose,
    )
}

@Composable
private fun messageFor(result: BackupResult): String = when (result) {
    BackupResult.Exported -> stringResource(R.string.backup_exported)
    BackupResult.WrongFormat -> stringResource(R.string.backup_wrong_format)
    BackupResult.Failed -> stringResource(R.string.backup_failed)
    is BackupResult.Imported -> when {
        result.added > 0 && result.skipped > 0 ->
            stringResource(R.string.backup_imported_with_skipped, result.added, result.skipped)
        result.added > 0 -> pluralStringResource(R.plurals.backup_imported, result.added, result.added)
        result.skipped > 0 -> stringResource(R.string.backup_imported_all_known)
        else -> stringResource(R.string.backup_imported_empty)
    }
}

/** Stateless-версия: пригодна для @Preview. */
@Composable
fun SettingsScreenContent(
    state: SettingsState,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onExport: () -> Unit,
    onImport: () -> Unit,
    /** Правка приходит функцией: конфиг собирает ViewModel — см. её onRecognitionChange. */
    onRecognitionChange: ((RecognitionConfig) -> RecognitionConfig) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.ambientBackground(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) { BodegaSnackbar(it) } },
        topBar = { ScreenTopBar(onBack = onClose, title = stringResource(R.string.settings_title)) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.settings_backup_section))
                ActionRow(
                    title = stringResource(R.string.settings_export),
                    hint = stringResource(R.string.settings_export_hint),
                    enabled = !state.busy,
                    running = state.running == BackupOperation.EXPORT,
                    onClick = onExport,
                )
                ActionRow(
                    title = stringResource(R.string.settings_import),
                    hint = stringResource(R.string.settings_import_hint),
                    enabled = !state.busy,
                    running = state.running == BackupOperation.IMPORT,
                    onClick = onImport,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.settings_recognition_section))
                RecognitionToggle(
                    checked = state.recognition.enabled,
                    onCheckedChange = { on -> onRecognitionChange { it.copy(enabled = on) } },
                )
                // Ключ и модель показываются только при включённом распознавании:
                // выключенному они ничего не объясняют и только занимают экран.
                if (state.recognition.enabled) {
                    SecretField(
                        label = stringResource(R.string.settings_recognition_key),
                        value = state.recognition.apiKey,
                        placeholder = stringResource(R.string.settings_recognition_key_hint),
                        onChange = { key -> onRecognitionChange { it.copy(apiKey = key) } },
                    )
                    SettingField(
                        label = stringResource(R.string.settings_recognition_model),
                        value = state.recognition.model,
                        placeholder = stringResource(R.string.settings_recognition_model_hint),
                        onChange = { model -> onRecognitionChange { it.copy(model = model) } },
                    )
                }
            }

            QuoteFooter()
        }
    }
}

/** Включатель облачного распознавания: та же карточка, что у пунктов-действий. */
@Composable
private fun RecognitionToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(
        title = stringResource(R.string.settings_recognition_toggle),
        hint = stringResource(R.string.settings_recognition_toggle_hint),
        modifier = Modifier.toggleable(
            value = checked, role = Role.Checkbox, onValueChange = onCheckedChange,
        ),
    ) {
        // Клик обрабатывает вся строка (toggleable выше) — иначе TalkBack
        // объявил бы два отдельных элемента с одним действием.
        CheckMark(checked)
    }
}

/**
 * Квадрат чекбокса в стиле поля формы: выключен — белый с обводкой поля, включён —
 * бирюзовая заливка «выбрано» с галочкой. Сам не кликабелен — это делает строка.
 */
@Composable
private fun CheckMark(checked: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val fill by animateColorAsState(if (checked) BodegaColors.Teal else Color.White, tween(160), label = "check-fill")
    val border by animateColorAsState(if (checked) BodegaColors.Teal else BodegaColors.FieldBorder, tween(160), label = "check-border")
    Box(
        Modifier.size(26.dp).clip(shape).background(fill).border(1.5.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(checked, enter = scaleIn(tween(160)) + fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            CheckIcon(16.dp, Color.White)
        }
    }
}

/**
 * Пункт-действие: заголовок, пояснение и индикатор занятости вместо повторного нажатия.
 *
 * [enabled] и [running] разведены намеренно: пока идёт операция, недоступны оба
 * пункта, но крутиться индикатор должен только у того, который её запустил.
 */
@Composable
private fun ActionRow(
    title: String,
    hint: String,
    enabled: Boolean,
    running: Boolean,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        hint = hint,
        modifier = Modifier.pressAction(onClick, enabled = enabled),
        titleColor = if (enabled) BodegaColors.Ink else BodegaColors.Placeholder,
    ) {
        if (running) {
            CircularProgressIndicator(color = BodegaColors.Teal, strokeWidth = 2.dp)
        }
    }
}

/** Карточка настройки: заголовок с пояснением и слот справа — чекбокс или индикатор. */
@Composable
private fun SettingsRow(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    titleColor: Color = BodegaColors.Ink,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(modifier)
            .glassCard(20)
            .heightIn(min = 68.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 17.sp),
                color = titleColor,
            )
            Text(
                hint,
                style = TextStyle(fontFamily = Rubik, fontSize = 13.sp),
                color = BodegaColors.Muted,
            )
        }
        trailing()
    }
}

/** Поле настройки с подписью сверху: подпись остаётся видимой, когда поле заполнено. */
@Composable
private fun SettingField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    /**
     * Не Text: клавиатурная автозамена «исправляла» бы набранный ключ и имя
     * модели, а система предлагала бы ключ в автозаполнении.
     */
    keyboard: KeyboardType = KeyboardType.Uri,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    labelRow: @Composable RowScope.() -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = Rubik, fontSize = 13.sp),
                color = BodegaColors.Muted,
            )
            labelRow()
        }
        FormField(
            value = value,
            onChange = onChange,
            placeholder = placeholder,
            description = label,
            keyboard = keyboard,
            visualTransformation = visualTransformation,
        )
    }
}

/** Поле секрета: значение замаскировано, рядом с подписью — переключатель показа. */
@Composable
private fun SecretField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    SettingField(
        label = label,
        value = value,
        placeholder = placeholder,
        onChange = onChange,
        keyboard = KeyboardType.Password,
        visualTransformation = if (revealed) VisualTransformation.None
        else PasswordVisualTransformation(),
    ) {
        val toggleLabel = stringResource(
            if (revealed) R.string.settings_recognition_key_hide
            else R.string.settings_recognition_key_show
        )
        Text(
            toggleLabel,
            modifier = Modifier
                .pressAction({ revealed = !revealed })
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = toggleLabel },
            style = TextStyle(fontFamily = Rubik, fontSize = 13.sp),
            color = BodegaColors.Teal,
        )
    }
}

@Preview(name = "Настройки", showBackground = true, showSystemUi = true)
@Composable
private fun SettingsPreview() {
    BodegaTheme {
        SettingsScreenContent(
            state = SettingsState(),
            onExport = {}, onImport = {}, onRecognitionChange = {}, onClose = {},
        )
    }
}
