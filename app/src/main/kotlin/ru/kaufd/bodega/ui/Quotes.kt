package ru.kaufd.bodega.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kaufd.bodega.R
import ru.kaufd.bodega.ui.theme.BodegaColors
import ru.kaufd.bodega.ui.theme.BodegaTheme
import ru.kaufd.bodega.ui.theme.PlexMono
import ru.kaufd.bodega.ui.theme.Rubik

data class Quote(
    @param:StringRes val text: Int,
    @param:StringRes val author: Int,
)

val Quotes = listOf(
    // Вино
    Quote(R.string.quote_wine_galileo, R.string.quote_wine_galileo_author),
    Quote(R.string.quote_wine_stevenson, R.string.quote_wine_stevenson_author),
    Quote(R.string.quote_wine_pliny, R.string.quote_wine_pliny_author),
    Quote(R.string.quote_wine_horace, R.string.quote_wine_horace_author),
    Quote(R.string.quote_wine_bacon, R.string.quote_wine_bacon_author),
    Quote(R.string.quote_wine_shakespeare, R.string.quote_wine_shakespeare_author),
    Quote(R.string.quote_wine_aristophanes, R.string.quote_wine_aristophanes_author),

    // Пиво
    Quote(R.string.quote_beer_poe, R.string.quote_beer_poe_author),
    Quote(R.string.quote_beer_housman, R.string.quote_beer_housman_author),
    Quote(R.string.quote_beer_henry, R.string.quote_beer_henry_author),
    Quote(R.string.quote_beer_german, R.string.quote_beer_german_author),
    Quote(R.string.quote_beer_english, R.string.quote_beer_english_author),
    Quote(R.string.quote_beer_king, R.string.quote_beer_king_author),
    Quote(R.string.quote_beer_hops, R.string.quote_beer_hops_author),

    // Крепкие напитки
    Quote(R.string.quote_spirit_shaw, R.string.quote_spirit_shaw_author),
    Quote(R.string.quote_spirit_twain, R.string.quote_spirit_twain_author),
    Quote(R.string.quote_spirit_fields, R.string.quote_spirit_fields_author),
    Quote(R.string.quote_spirit_burns, R.string.quote_spirit_burns_author),
    Quote(R.string.quote_spirit_remarque, R.string.quote_spirit_remarque_author),
    Quote(R.string.quote_spirit_irish, R.string.quote_spirit_irish_author),
    Quote(R.string.quote_spirit_bi2, R.string.quote_spirit_bi2_author),
)


/**
 * Цитата в подвале настроек. Раньше её показывала заставка при запуске, но там на
 * чтение оставалось меньше секунды: заставка появлялась уже после того, как список
 * поднялся из базы, и была чистой надбавкой ко времени старта. В настройках спешки
 * нет, а низ экрана всё равно пустует.
 *
 * Цитата выбирается на открытие экрана и переживает пересборку: подменяться под
 * рукой у читающего она не должна.
 */
@Composable
fun QuoteFooter(modifier: Modifier = Modifier) {
    val quote = Quotes[rememberSaveable { Quotes.indices.random() }]
    Column(
        // Отступ крупнее интервала внутри секции: цитата — подпись приложения,
        // а не строка блока «О приложении».
        modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(quote.text),
            style = TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
            color = BodegaColors.Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "— ${stringResource(quote.author)}",
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 11.sp,
                letterSpacing = .6.sp,
            ),
            color = BodegaColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Цитата в настройках", showBackground = true)
@Composable
private fun QuoteFooterPreview() {
    BodegaTheme { QuoteFooter() }
}
