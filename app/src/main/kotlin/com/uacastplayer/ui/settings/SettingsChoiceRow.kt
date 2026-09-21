package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.UaTheme

/** A readable alternative to horizontal chips when choices have long localized names. */
@Composable
internal fun SettingsChoiceRow(
    label: String,
    icon: ImageVector,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    selectedLabel: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.settingsSearchTarget(label)) {
        SettingsNavigationRow(
            label, selectedLabel ?: options.getOrNull(selectedIndex).orEmpty(), icon, onClick = { expanded = true },
        )
    }
    if (!expanded) return
    AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = index == selectedIndex,
                            role = Role.RadioButton,
                            onClick = { expanded = false; onSelected(index) },
                        ).heightIn(min = 48.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Text(option, style = BodyRegular, color = UaTheme.palette.labelPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.common_close)) }
        },
    )
}
