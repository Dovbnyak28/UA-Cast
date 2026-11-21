package com.uacastplayer.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.SecondaryButton

/** Selection is not a disabled action; large labels get their own row rather than splitting words. */
@Composable
internal fun RemoteModeSelector(mode: PhoneRemoteMode, onMode: (PhoneRemoteMode) -> Unit) {
    val minimumRowWidth = 224.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        if (maxWidth < minimumRowWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteModeChoice(PhoneRemoteMode.REMOTE, mode, onMode, Modifier.fillMaxWidth())
                RemoteModeChoice(PhoneRemoteMode.TOUCHPAD, mode, onMode, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteModeChoice(PhoneRemoteMode.REMOTE, mode, onMode, Modifier.weight(1f))
                RemoteModeChoice(PhoneRemoteMode.TOUCHPAD, mode, onMode, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RemoteModeChoice(choice: PhoneRemoteMode, mode: PhoneRemoteMode, onMode: (PhoneRemoteMode) -> Unit,
    modifier: Modifier) {
    val isSelected = choice == mode
    val label = stringResource(if (choice == PhoneRemoteMode.REMOTE) R.string.remote_mode_buttons
        else R.string.remote_mode_touchpad)
    val semantics = modifier.semantics { role = Role.RadioButton; selected = isSelected }
    val select = { if (!isSelected) onMode(choice) }
    if (isSelected) PrimaryButton(label, select, semantics) else SecondaryButton(label, select, semantics)
}
