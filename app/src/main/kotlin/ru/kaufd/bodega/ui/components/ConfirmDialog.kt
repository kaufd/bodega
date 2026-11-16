package ru.kaufd.bodega.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.Rubik
import ru.kaufd.bodega.ui.theme.dangerSurface
import ru.kaufd.bodega.ui.theme.pressAction

/**
 * Подтверждение необратимого действия.
 *
 * Обе кнопки — плашки во всю ширину, а не текст в углу: выбор здесь главное,
 * его должно быть видно сразу. Подтверждающая всегда красная: удаление записи,
 * потеря черновика и перезапись заметки одинаково теряют данные, и одинаково
 * безобидными выглядеть не должны. Свой лэйаут вместо AlertDialog: у него кнопки
 * — TextButton с ripple, а материаловские 28 dp и M3-палитра выбиваются из
 * остальных поверхностей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(BodegaColors.Surface).padding(24.dp),
        ) {
            Text(title, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = BodegaColors.Ink)
            Spacer(Modifier.height(12.dp))
            Text(text, fontFamily = Rubik, fontSize = 15.sp, lineHeight = 22.sp, color = BodegaColors.Muted)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Стекло на белом диалоге почти не видно — вторичной кнопке нужна своя заливка и обводка.
                val shape = RoundedCornerShape(18.dp)
                DialogButton(
                    dismissLabel, BodegaColors.Ink, onDismiss,
                    Modifier.clip(shape).background(BodegaColors.Paper).border(1.dp, BodegaColors.FieldBorder, shape),
                    Modifier.weight(1f),
                )
                DialogButton(
                    confirmLabel, Color.White, onConfirm, Modifier.dangerSurface(),
                    Modifier.weight(1f).alpha(if (confirmEnabled) 1f else .5f), enabled = confirmEnabled,
                )
            }
        }
    }
}

/** [surface] — заливка кнопки; pressAction стоит перед ней, чтобы сжималась плашка целиком. */
@Composable
private fun DialogButton(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
    surface: Modifier,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier.pressAction(onClick, enabled = enabled).then(surface).height(48.dp).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 15.sp,
            color = textColor,
        )
    }
}
