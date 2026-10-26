package com.uacastplayer.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.data.media.AndroidLocalVideoReader
import com.uacastplayer.data.media.LocalVideoReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalVideoSelectionState(
    val video: LocalVideo? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val requestId: Long = 0,
)

/** Latest selection wins; retains no Activity, descriptor, bitmap or video bytes. */
class LocalVideoOpenViewModel @JvmOverloads constructor(
    application: Application,
    private val reader: LocalVideoReader = AndroidLocalVideoReader(application),
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(LocalVideoSelectionState())
    val state = mutableState.asStateFlow()
    private var selection: Job? = null
    private var generation = 0L

    fun open(uri: String, persist: Boolean = true) {
        selection?.cancel()
        val attempt = ++generation
        mutableState.value = LocalVideoSelectionState(loading = true, requestId = attempt)
        selection = viewModelScope.launch {
            runCatchingNonFatal { reader.read(uri, persist) }
                .onSuccess {
                    if (attempt == generation) {
                        mutableState.value = LocalVideoSelectionState(video = it, requestId = attempt)
                    }
                }
                .onFailure {
                    if (attempt == generation) {
                        mutableState.value = LocalVideoSelectionState(error = true, requestId = attempt)
                    }
                }
        }
    }

    fun consumed(requestId: Long) {
        if (mutableState.value.requestId == requestId && mutableState.value.video != null) {
            mutableState.value = LocalVideoSelectionState()
        }
    }

    fun dismissError() { mutableState.value = mutableState.value.copy(error = false) }

    fun cancelSelection() {
        generation++
        selection?.cancel()
        mutableState.value = LocalVideoSelectionState()
    }
}
