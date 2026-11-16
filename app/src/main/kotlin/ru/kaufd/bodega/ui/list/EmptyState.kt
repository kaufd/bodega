package ru.kaufd.bodega.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.ui.theme.*

@Composable
private fun EmptyScaffold(
    title: String,
    text: String,
    icon: @Composable () -> Unit,
    button: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = BodegaColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = TextStyle(fontFamily = Rubik, fontSize = 13.sp, lineHeight = 19.sp),
            color = BodegaColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        button()
    }
}

@Composable
private fun IconCircle(
    background: Color,
    border: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(58.dp)
            .shadow(6.dp, CircleShape, ambientColor = BodegaColors.Shadow, spotColor = BodegaColors.Shadow)
            .clip(CircleShape)
            .background(background)
            .then(if (border) Modifier.border(1.dp, Color.White.copy(alpha = .72f), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun FilledEmptyButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .pressAction(onClick)
            .accentSurface(18)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        PlusIcon(19.dp, Color.White)
        Text(
            label,
            style = TextStyle(fontFamily = Rubik, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = Color.White,
        )
    }
}

@Composable
fun EmptyCollection(onAddClick: () -> Unit) {
    EmptyScaffold(
        title = stringResource(R.string.empty_collection_title),
        text = stringResource(R.string.empty_collection_text),
        icon = {
            IconCircle(background = BodegaColors.Surface, border = true) {
                GridIcon(box = 12.dp, gap = 7.dp, color = BodegaColors.Ink.copy(alpha = 0.25f))
            }
        },
    ) {
        FilledEmptyButton(stringResource(R.string.empty_collection_button), onAddClick)
    }
}

@Composable
fun EmptyCategory(category: Category, onAddClick: () -> Unit) {
    val addLabel = when (category) {
        Category.WINE -> stringResource(R.string.empty_category_add_wine)
        Category.BEER -> stringResource(R.string.empty_category_add_beer)
        Category.SPIRIT -> stringResource(R.string.empty_category_add_spirit)
        Category.COCKTAIL -> stringResource(R.string.empty_category_add_cocktail)
    }
    EmptyScaffold(
        title = if (category == Category.COCKTAIL) stringResource(R.string.empty_cocktail_title)
        else stringResource(R.string.empty_category_title, stringResource(category.displayNameRes())),
        text = stringResource(if (category == Category.COCKTAIL) R.string.empty_cocktail_text else R.string.empty_category_text),
        icon = {
            IconCircle(background = categoryTint(category), border = false) {
                CategoryIcon(category, size = 42.dp, color = categoryColor(category), strokeWidth = 1.6f)
            }
        },
    ) {
        FilledEmptyButton(addLabel, onAddClick)
    }
}

@Composable
fun EmptySearch(query: String, onReset: () -> Unit) {
    EmptyScaffold(
        title = stringResource(R.string.empty_search_title),
        text = stringResource(R.string.empty_search_text, query),
        icon = {
            IconCircle(background = BodegaColors.Surface, border = true) {
                SearchIcon(size = 40.dp, color = BodegaColors.Placeholder, strokeWidth = 1.7f)
            }
        },
    ) {
        Row(
            Modifier
                .pressAction(onReset)
                .glassButton()
                .padding(horizontal = 22.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.empty_search_button),
                style = TextStyle(fontFamily = Rubik, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = BodegaColors.Ink,
            )
        }
    }
}
