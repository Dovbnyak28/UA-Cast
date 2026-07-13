package com.uacastplayer.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.theme.DurPress
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.Hairline
import com.uacastplayer.ui.theme.PressScaleIcon

data class TabBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** §5.10 - bottom navigation chrome: real backdrop blur on API 31+, translucent tone below that. */
@Composable
fun GlassTabBar(items: List<TabBarItem>, modifier: Modifier = Modifier) {
    val glassTone = Color(0xB8121214)
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = RenderEffect
                .createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(blurModifier)
            .background(glassTone)
            .padding(top = 1.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            for (item in items) {
                TabBarButton(item = item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabBarButton(item: TabBarItem, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressScaleIcon else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "tabScale",
    )
    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = if (item.selected) com.uacastplayer.ui.theme.Azure else com.uacastplayer.ui.theme.LabelTertiary,
            modifier = Modifier.height(21.dp).padding(bottom = 2.dp),
        )
        TabBarLabel(text = item.label, selected = item.selected)
    }
}
