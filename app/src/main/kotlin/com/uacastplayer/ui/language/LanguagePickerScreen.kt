package com.uacastplayer.ui.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.core.i18n.AppLanguage

/**
 * An enum is not one of the types a `Bundle` can hold, so the selection round-trips as its name.
 * `save` returning null simply means "nothing selected yet, nothing to restore".
 */
private val AppLanguageSaver = Saver<AppLanguage?, String>(
    save = { it?.name },
    restore = { AppLanguage.valueOf(it) },
)

/** Keeps the block from stretching edge to edge on a tablet or in landscape, where a
 * full-width column of four short words reads as scattered rather than as a list. */
private val ContentMaxWidth = 420.dp

@Composable
fun LanguagePickerScreen(
    onLanguageConfirmed: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable, not just remembered: MainActivity handles rotation itself (see its `configChanges`),
    // but nothing saves this across a process death, a font-size change or "don't keep activities" -
    // and this is the very first screen a new install shows. Coming back to it with the selection
    // silently cleared and Continue disabled again reads as the tap not having registered.
    var selected by rememberSaveable(stateSaver = AppLanguageSaver) {
        mutableStateOf<AppLanguage?>(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The one screen with no chrome of its own to pad out of the system bars - and the
            // first a new install shows. safeDrawing, not statusBars alone: under edge-to-edge the
            // Continue button would otherwise sit under the navigation bar.
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The header and the list are centred together in whatever space the button leaves, and
        // scroll as one when there is not enough of it. The button stays out of the scroll so it is
        // always reachable - on a short screen this is the difference between a first-run install
        // that can proceed and one that cannot.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.language_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.language_picker_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                AppLanguage.entries.forEach { language ->
                    LanguageRow(
                        language = language,
                        isSelected = language == selected,
                        onClick = { selected = language },
                    )
                }
            }
        }

        Button(
            onClick = { selected?.let(onLanguageConfirmed) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().widthIn(max = ContentMaxWidth),
        ) {
            Text(stringResource(R.string.language_picker_continue))
        }
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LanguageFlag(language)
        Text(
            text = stringResource(language.nativeNameRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun AppLanguage.nativeNameRes(): Int = when (this) {
    AppLanguage.UKRAINIAN -> R.string.language_name_uk
    AppLanguage.ENGLISH -> R.string.language_name_en
    AppLanguage.RUSSIAN -> R.string.language_name_ru
    AppLanguage.SPANISH -> R.string.language_name_es
}
