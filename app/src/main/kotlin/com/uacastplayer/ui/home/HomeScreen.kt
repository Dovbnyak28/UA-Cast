package com.uacastplayer.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.ui.components.EmptyState
import com.uacastplayer.ui.theme.AppIcons

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = AppIcons.Upload,
        title = stringResource(R.string.home_empty_message),
        subtitle = stringResource(R.string.home_empty_subtitle),
        modifier = modifier.fillMaxSize(),
    )
}
