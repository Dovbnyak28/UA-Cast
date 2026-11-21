package com.uacastplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppSpacing
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.UaCastTheme

/** Developer catalog, not an application destination. No network, playback or account state. */
@Composable
internal fun ComponentCatalogPreview(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var selected by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            Text("UA CAST", style = MaterialTheme.typography.displaySmall, color = colors.onBackground)
            Text("Cinema V2 · компоненти", style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant)
        }
        Text("Зрозумілі дії", style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
        PrimaryButton("Продовжити перегляд", {}, Modifier.fillMaxWidth(), leadingIcon = AppIcons.Play)
        SecondaryButton("Відкрити відеофайл", {}, Modifier.fillMaxWidth(), leadingIcon = AppIcons.Channels)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            RoundIconButton(AppIcons.SkipPrevious, {}, contentDescription = "Попередній канал")
            GradientPlayButton(AppIcons.Play, {}, contentDescription = "Відтворити")
            RoundIconButton(AppIcons.SkipNext, {}, contentDescription = "Наступний канал")
            SmallRoundIconButton(AppIcons.Settings, {}, contentDescription = "Налаштування")
        }
        SegmentedControl(listOf("Канали", "Обране", "Телегід"), selected, { selected = it })
        UaCard(Modifier.fillMaxWidth(), selected = true, onClick = {}) {
            Text("Ваші канали поруч", style = MaterialTheme.typography.titleLarge)
            Text("Джерела, обране й телегід — у вашому застосунку.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = AppSpacing.xs))
        }
        Text("Читабельні списки", style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
        UaListItem("Канал користувача", supportingText = "Зараз · програма передач", leadingIcon = AppIcons.Channels,
            selected = true, onClick = {})
        UaListItem("Наступний канал", supportingText = "Зі списку вашого джерела",
            leadingIcon = AppIcons.Channels, onClick = {})
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            StatusPill("Ефір", StatusPillVariant.Good)
            StatusPill("Проксі", StatusPillVariant.Proxy)
            StatusPill("Помилка", StatusPillVariant.Bad)
        }
        SecondaryButton("Недоступна дія", {}, Modifier.fillMaxWidth(), enabled = false)
    }
}

@Preview(name = "Cinema V2 components · 320dp", widthDp = 320, heightDp = 900, showBackground = true)
@Composable
private fun CatalogPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) { ComponentCatalogPreview() }
}
