package com.uacastplayer.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import com.uacastplayer.ui.theme.UaTheme

val LocalTvMode = staticCompositionLocalOf { false }
private const val TV_MIN_FONT_SCALE = 1.3f

@Composable
fun TvPresentation(television: Boolean, registry: TvInputRegistry, content: @Composable () -> Unit) {
    val inputMode = LocalInputModeManager.current
    SideEffect { if (television) inputMode.requestInputMode(InputMode.Keyboard) }
    val original = LocalDensity.current
    val density = if (television) Density(original.density, maxOf(original.fontScale, TV_MIN_FONT_SCALE)) else original
    CompositionLocalProvider(LocalTvMode provides television, LocalTvInputRegistry provides registry,
        LocalDensity provides density, content = content)
}

fun Context.isTelevision(): Boolean =
    (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType ==
        Configuration.UI_MODE_TYPE_TELEVISION

/** Applied before clickable/selectable. Those already own a focus target; adding another traps D-pad traversal. */
@Composable
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(12.dp), enabled: Boolean = true): Modifier {
    if (!LocalTvMode.current) return this
    TvDialogInputRegistration()
    var focused by remember { mutableStateOf(false) }
    return focusProperties { canFocus = enabled }.onFocusChanged { focused = it.isFocused }
        .border(3.dp, if (focused) UaTheme.palette.azure else Color.Transparent, shape)
}

/** Read-only lazy content needs a focus stop too, otherwise D-pad users cannot scroll to read it. */
@Composable
fun Modifier.tvReadingFocus(): Modifier = if (LocalTvMode.current) tvFocus().focusable() else this
